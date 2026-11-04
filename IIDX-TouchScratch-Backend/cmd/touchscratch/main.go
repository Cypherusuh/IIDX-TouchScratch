package main

import (
	"log"
	"time"

	"iidxtouchscratch/internal/input"
	"iidxtouchscratch/internal/network"
	"iidxtouchscratch/internal/protocol"
)

func main() {
	log.Println("IIDX TouchScratch Backend")

	processor := input.NewProcessor()
	lastTelemetry := time.Time{}

	onTouch := func(packet protocol.Packet) *protocol.Packet {
		result := processor.Process(input.Sample{
			DeltaAngle:    float64(packet.DeltaAngle),
			AverageRadius: float64(packet.AverageRadius),
			TouchAngle:    float64(packet.TouchAngle),
			Timestamp:     packet.Timestamp,
			FingerCount:   packet.FingerCount,
		})

		now := time.Now()

		if !lastTelemetry.IsZero() &&
			now.Sub(lastTelemetry) < 33*time.Millisecond {
			return nil
		}

		lastTelemetry = now

		return &protocol.Packet{
			Type:          protocol.TypeTelemetry,
			SessionID:     packet.SessionID,
			Sequence:      packet.Sequence,
			Timestamp:     packet.Timestamp,
			DeltaAngle:    float32(result.Velocity),
			AverageRadius: float32(result.AverageRadius),
			TouchAngle:    float32(result.TouchAngle),
			FingerCount:   result.FingerCount,
		}
	}

	if err := network.ListenUDP(":6699", onTouch); err != nil {
		log.Fatal(err)
	}
}
