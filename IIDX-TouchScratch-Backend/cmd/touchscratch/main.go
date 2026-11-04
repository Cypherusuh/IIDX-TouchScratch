package main

import (
	"log"
	"time"

	"iidxtouchscratch/internal/input"
	"iidxtouchscratch/internal/network"
	"iidxtouchscratch/internal/physics"
	"iidxtouchscratch/internal/protocol"
)

func main() {
	log.Println("IIDX TouchScratch Backend")

	processor := input.NewProcessor()
	scratch := physics.NewScratch(physics.Config{
		Friction: 4.0,
	})

	var (
		touching      bool
		inputVelocity float64
		averageRadius float64
		touchAngle    float64
		fingerCount   uint8
	)

	lastPhysicsUpdate := time.Now()

	onTouch := func(packet protocol.Packet) {
		switch packet.Type {
		case protocol.TypeTouch:
			result := processor.Process(input.Sample{
				DeltaAngle:    float64(packet.DeltaAngle),
				AverageRadius: float64(packet.AverageRadius),
				TouchAngle:    float64(packet.TouchAngle),
				Timestamp:     packet.Timestamp,
				FingerCount:   packet.FingerCount,
			})

			touching = packet.FingerCount > 0

			inputVelocity = result.Velocity
			averageRadius = result.AverageRadius
			touchAngle = result.TouchAngle
			fingerCount = result.FingerCount

		case protocol.TypeRelease:
			// Preserve inputVelocity.
			// The physics engine will now apply friction.
			touching = false
			fingerCount = 0
		}
	}

	onTick := func() *protocol.Packet {
		now := time.Now()
		dt := now.Sub(lastPhysicsUpdate)
		lastPhysicsUpdate = now

		state := scratch.Update(physics.Input{
			Velocity: inputVelocity,
			Touching: touching,
		}, dt)

		return &protocol.Packet{
			DeltaAngle:    float32(state.Velocity),
			AverageRadius: float32(averageRadius),
			TouchAngle:    float32(touchAngle),
			FingerCount:   fingerCount,
		}
	}

	if err := network.ListenUDP(":6699", onTouch, onTick); err != nil {
		log.Fatal(err)
	}
}
