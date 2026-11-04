package input

import (
	"math"
	"testing"
	"time"
)

func newTestCalculator() *VelocityCalculator {
	return NewVelocityCalculator(
		30*time.Millisecond,
		100*time.Millisecond,
	)
}

func assertNear(t *testing.T, got, want float64) {
	t.Helper()

	if math.Abs(got-want) > 0.01 {
		t.Fatalf("got %.2f, want %.2f", got, want)
	}
}

func TestVelocityCalculation(t *testing.T) {
	calculator := newTestCalculator()

	calculator.Add(Sample{Timestamp: 1000})

	result := calculator.Add(Sample{
		DeltaAngle: 0.5,
		Timestamp:  1010,
	})

	assertNear(t, result, 50)
}

func TestDuplicateTimestamps(t *testing.T) {
	calculator := newTestCalculator()

	calculator.Add(Sample{
		DeltaAngle: 0.2,
		Timestamp:  1000,
	})

	calculator.Add(Sample{
		DeltaAngle: 0.2,
		Timestamp:  1000,
	})

	result := calculator.Add(Sample{
		DeltaAngle: 0.2,
		Timestamp:  1010,
	})

	assertNear(t, result, 40)
}

func TestIdleReset(t *testing.T) {
	calculator := newTestCalculator()

	calculator.Add(Sample{Timestamp: 1000})

	result := calculator.Add(Sample{
		DeltaAngle: 1,
		Timestamp:  1200,
	})

	assertNear(t, result, 0)
}

func TestOutOfOrderTimestamp(t *testing.T) {
	calculator := newTestCalculator()

	calculator.Add(Sample{Timestamp: 1000})

	calculator.Add(Sample{
		DeltaAngle: 0.5,
		Timestamp:  1010,
	})

	calculator.Add(Sample{
		DeltaAngle: 5,
		Timestamp:  1005,
	})

	result := calculator.Add(Sample{
		DeltaAngle: 0.5,
		Timestamp:  1020,
	})

	assertNear(t, result, 50)
}

func TestVelocityReset(t *testing.T) {
	calculator := newTestCalculator()

	calculator.Add(Sample{Timestamp: 1000})

	calculator.Add(Sample{
		DeltaAngle: 0.5,
		Timestamp:  1010,
	})

	calculator.Reset()

	result := calculator.Add(Sample{
		DeltaAngle: 0.5,
		Timestamp:  1020,
	})

	assertNear(t, result, 0)
}
