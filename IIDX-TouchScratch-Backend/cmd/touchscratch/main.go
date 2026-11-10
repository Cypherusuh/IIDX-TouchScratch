package main

import (
	"log"
	"math"
	"os"
	"os/signal"
	"sync"
	"sync/atomic"
	"time"

	"iidxtouchscratch/internal/input"
	"iidxtouchscratch/internal/network"
	"iidxtouchscratch/internal/physics"
	"iidxtouchscratch/internal/protocol"
	"iidxtouchscratch/internal/spice2x"
)

const (
	spiceAddress = "127.0.0.1:1337"
	analogName   = "Turntable P1"
	outputRate   = time.Second / 120
)

func main() {
	log.Println("IIDX TouchScratch Backend")

	// Connect to Spice2X.
	spiceClient, err := spice2x.Connect(spiceAddress)
	if err != nil {
		log.Fatalf("Spice2X connection failed: %v", err)
	}

	log.Println("Connected to Spice2X")

	// Verify that the requested analog exists.
	analogs, err := spiceClient.ReadAnalogs()
	if err != nil {
		spiceClient.Close()
		log.Fatalf("Failed to read Spice2X analogs: %v", err)
	}

	found := false

	for _, analog := range analogs {
		if analog.Name == analogName {
			found = true
			log.Printf(
				"Found %s: value=%.2f active=%v",
				analog.Name,
				analog.State,
				analog.Active,
			)
			break
		}
	}

	if !found {
		spiceClient.Close()
		log.Fatalf("Spice2X analog not found: %s", analogName)
	}

	// Clear any override left by a previous run.
	if err := spiceClient.ResetAnalog(analogName); err != nil {
		spiceClient.Close()
		log.Fatalf("Failed to reset P1 analog: %v", err)
	}

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

	// Physics publishes its latest position here.
	var latestPosition atomic.Uint64
	latestPosition.Store(math.Float64bits(0))

	stopOutput := make(chan struct{})
	outputDone := make(chan struct{})

	go runAnalogOutput(
		spiceClient,
		&latestPosition,
		stopOutput,
		outputDone,
	)

	// Ensure the analog override is restored on shutdown.
	var cleanupOnce sync.Once

	cleanup := func() {
		cleanupOnce.Do(func() {
			log.Println("Stopping analog output...")

			close(stopOutput)
			<-outputDone

			if err := spiceClient.ResetAnalog(analogName); err != nil {
				log.Printf("Failed to reset P1 analog: %v", err)
			} else {
				log.Println("P1 analog restored")
			}

			if err := spiceClient.Close(); err != nil {
				log.Printf("Spice2X disconnect error: %v", err)
			}
		})
	}

	// Handle Ctrl+C gracefully.
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, os.Interrupt)
	defer signal.Stop(signals)

	go func() {
		<-signals
		log.Println("Shutdown requested")
		cleanup()
		os.Exit(0)
	}()

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
			// Preserve velocity so the platter can coast.
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

		// Publish the latest accumulated rotation.
		latestPosition.Store(math.Float64bits(state.Position))

		return &protocol.Packet{
			DeltaAngle:    float32(state.Velocity),
			AverageRadius: float32(averageRadius),
			TouchAngle:    float32(touchAngle),
			FingerCount:   fingerCount,
		}
	}

	log.Println("Listening for Android touch input on UDP :6699")

	if err := network.ListenUDP(":6699", onTouch, onTick); err != nil {
		log.Printf("UDP server stopped: %v", err)
	}

	cleanup()
}

// runAnalogOutput sends the latest physics position to Spice2X at 60 Hz.
func runAnalogOutput(
	client *spice2x.Client,
	latestPosition *atomic.Uint64,
	stop <-chan struct{},
	done chan<- struct{},
) {
	defer close(done)

	ticker := time.NewTicker(outputRate)
	defer ticker.Stop()

	lastValue := math.NaN()

	for {
		select {
		case <-stop:
			return

		case <-ticker.C:
			position := math.Float64frombits(
				latestPosition.Load(),
			)

			value := positionToAnalog(position)

			// Avoid unnecessary TCP requests.
			if !math.IsNaN(lastValue) &&
				math.Abs(value-lastValue) < 0.0001 {
				continue
			}

			if err := client.WriteAnalog(analogName, value); err != nil {
				log.Printf("Spice2X analog output failed: %v", err)
				return
			}

			lastValue = value
		}
	}
}

// Convert accumulated radians into a wrapped 0.0–1.0 analog position.
func positionToAnalog(position float64) float64 {
	turns := position / (2 * math.Pi)

	value := math.Mod(turns+0.5, 1.0)

	if value < 0 {
		value += 1.0
	}

	return value
}
