package network

import (
	"fmt"
	"log"
	"net"
	"time"

	"iidxtouchscratch/internal/protocol"
)

type receivedPacket struct {
	data   []byte
	remote *net.UDPAddr
	err    error
}

func ListenUDP(
	address string,
	onTouch func(protocol.Packet),
	onTick func() *protocol.Packet,
) error {
	addr, err := net.ResolveUDPAddr("udp", address)
	if err != nil {
		return fmt.Errorf("resolve UDP address: %w", err)
	}

	conn, err := net.ListenUDP("udp", addr)
	if err != nil {
		return fmt.Errorf("listen UDP: %w", err)
	}
	defer conn.Close()

	log.Printf("UDP receiver listening on %s", address)

	incoming := make(chan receivedPacket, 256)

	// Dedicated reader: copy each packet because the read buffer is reused.
	go func() {
		buffer := make([]byte, 2048)

		for {
			n, remote, err := conn.ReadFromUDP(buffer)
			if err != nil {
				incoming <- receivedPacket{err: err}
				return
			}

			data := make([]byte, n)
			copy(data, buffer[:n])

			incoming <- receivedPacket{
				data:   data,
				remote: remote,
			}
		}
	}()

	ticker := time.NewTicker(time.Second / 60)
	defer ticker.Stop()

	var activeSession uint64
	var activeRemote *net.UDPAddr

	var lastTimestamp uint64
	var hasLastTimestamp bool

	var packetCount int
	var dtCount int
	var dtSum int64
	var dtMin int64
	var dtMax int64
	var zeroDt int
	var outOfOrder int

	lastReport := time.Now()

	for {
		select {
		case received := <-incoming:
			if received.err != nil {
				return fmt.Errorf("read UDP packet: %w", received.err)
			}

			packet, err := protocol.Decode(received.data)
			if err != nil {
				log.Printf("Rejected packet: %v", err)
				continue
			}

			switch packet.Type {
			case protocol.TypeHello:
				activeSession = packet.SessionID
				activeRemote = received.remote

				log.Printf(
					"Android connected: session=%016X address=%s",
					activeSession,
					activeRemote,
				)

				lastTimestamp = 0
				hasLastTimestamp = false

				packetCount = 0
				dtCount = 0
				dtSum = 0
				dtMin = 0
				dtMax = 0
				zeroDt = 0
				outOfOrder = 0
				lastReport = time.Now()

				ack := protocol.Encode(protocol.Packet{
					Type:      protocol.TypeAck,
					SessionID: activeSession,
					Sequence:  packet.Sequence,
				})

				if _, err := conn.WriteToUDP(ack, activeRemote); err != nil {
					log.Printf("Failed to send ACK: %v", err)
				}

			case protocol.TypeTouch, protocol.TypeRelease:
				if packet.SessionID != activeSession ||
					activeSession == 0 ||
					activeRemote == nil ||
					!received.remote.IP.Equal(activeRemote.IP) ||
					received.remote.Port != activeRemote.Port {
					continue
				}

				if packet.Type == protocol.TypeTouch {
					packetCount++

					if hasLastTimestamp {
						dt := int64(packet.Timestamp) - int64(lastTimestamp)

						switch {
						case dt > 0:
							if dtCount == 0 || dt < dtMin {
								dtMin = dt
							}
							if dt > dtMax {
								dtMax = dt
							}

							dtSum += dt
							dtCount++

						case dt == 0:
							zeroDt++

						default:
							outOfOrder++
						}
					}

					if !hasLastTimestamp ||
						packet.Timestamp >= lastTimestamp {
						lastTimestamp = packet.Timestamp
						hasLastTimestamp = true
					}
				}

				if onTouch != nil {
					onTouch(packet)
				}

				now := time.Now()
				elapsed := now.Sub(lastReport)

				if elapsed >= time.Second {
					packetRate := float64(packetCount) / elapsed.Seconds()

					if dtCount > 0 {
						averageDt := float64(dtSum) / float64(dtCount)

						log.Printf(
							"INPUT rate=%.1f packets/s dt(avg/min/max)=%.1f/%d/%d ms zero=%d outOfOrder=%d",
							packetRate,
							averageDt,
							dtMin,
							dtMax,
							zeroDt,
							outOfOrder,
						)
					} else {
						log.Printf(
							"INPUT rate=%.1f packets/s dt unavailable zero=%d outOfOrder=%d",
							packetRate,
							zeroDt,
							outOfOrder,
						)
					}

					packetCount = 0
					dtCount = 0
					dtSum = 0
					dtMin = 0
					dtMax = 0
					zeroDt = 0
					outOfOrder = 0
					lastReport = now
				}

			case protocol.TypeHeartbeat:
				if packet.SessionID != activeSession ||
					activeSession == 0 ||
					activeRemote == nil ||
					!received.remote.IP.Equal(activeRemote.IP) ||
					received.remote.Port != activeRemote.Port {
					continue
				}

			default:
				log.Printf("Unknown packet type: %d", packet.Type)
			}

		case <-ticker.C:
			if activeSession == 0 || activeRemote == nil || onTick == nil {
				continue
			}

			response := onTick()
			if response == nil {
				continue
			}

			response.Type = protocol.TypeTelemetry
			response.SessionID = activeSession

			encoded := protocol.Encode(*response)

			if _, err := conn.WriteToUDP(encoded, activeRemote); err != nil {
				log.Printf("Failed to send telemetry: %v", err)
			}
		}
	}
}
