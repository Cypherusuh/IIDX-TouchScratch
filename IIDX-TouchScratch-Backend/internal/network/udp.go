package network

import (
	"fmt"
	"log"
	"net"
	"time"

	"iidxtouchscratch/internal/protocol"
)

func ListenUDP(
	address string,
	onTouch func(protocol.Packet) *protocol.Packet,
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

	buffer := make([]byte, 2048)

	var activeSession uint64
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
		n, remote, err := conn.ReadFromUDP(buffer)
		if err != nil {
			return fmt.Errorf("read UDP packet: %w", err)
		}

		packet, err := protocol.Decode(buffer[:n])
		if err != nil {
			log.Printf("Rejected packet from %s: %v", remote, err)
			continue
		}

		switch packet.Type {
		case protocol.TypeHello:
			activeSession = packet.SessionID

			log.Printf(
				"Android connected: session=%016X address=%s",
				activeSession,
				remote,
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

			if _, err := conn.WriteToUDP(ack, remote); err != nil {
				log.Printf("Failed to send ACK: %v", err)
			}

		case protocol.TypeTouch:
			if packet.SessionID != activeSession || activeSession == 0 {
				continue
			}

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

			// Never move the timestamp backwards.
			if !hasLastTimestamp || packet.Timestamp >= lastTimestamp {
				lastTimestamp = packet.Timestamp
				hasLastTimestamp = true
			}

			// Forward touch data and send telemetry when available.
			if onTouch != nil {
				response := onTouch(packet)

				if response != nil {
					encoded := protocol.Encode(*response)

					if _, err := conn.WriteToUDP(encoded, remote); err != nil {
						log.Printf("Failed to send telemetry: %v", err)
					}
				}
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
			if packet.SessionID != activeSession || activeSession == 0 {
				continue
			}

		default:
			log.Printf("Unknown packet type: %d", packet.Type)
		}
	}
}
