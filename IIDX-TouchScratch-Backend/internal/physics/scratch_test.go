package physics

import (
	"math"
	"testing"
	"time"
)

func TestTouchControlsVelocity(t *testing.T) {
	scratch := NewScratch(Config{Friction: 4})

	state := scratch.Update(Input{
		Velocity: 10,
		Touching: true,
	}, 16*time.Millisecond)

	if state.Velocity != 10 {
		t.Fatalf("expected velocity 10, got %f", state.Velocity)
	}

	if !state.Touching {
		t.Fatal("expected platter to be touching")
	}
}

func TestReleasePreservesMomentum(t *testing.T) {
	scratch := NewScratch(Config{Friction: 4})

	scratch.Update(Input{
		Velocity: 10,
		Touching: true,
	}, 16*time.Millisecond)

	state := scratch.Update(Input{
		Touching: false,
	}, 16*time.Millisecond)

	if state.Velocity <= 0 {
		t.Fatalf("expected positive momentum, got %f", state.Velocity)
	}

	if state.Velocity >= 10 {
		t.Fatalf("expected friction to reduce velocity, got %f", state.Velocity)
	}

	if state.Touching {
		t.Fatal("expected platter to be released")
	}
}

func TestFrictionEventuallySlowsPlatter(t *testing.T) {
	scratch := NewScratch(Config{Friction: 4})

	scratch.Update(Input{
		Velocity: 10,
		Touching: true,
	}, 16*time.Millisecond)

	for i := 0; i < 300; i++ {
		scratch.Update(Input{
			Touching: false,
		}, 16*time.Millisecond)
	}

	state := scratch.State()

	if math.Abs(state.Velocity) > 0.001 {
		t.Fatalf("expected platter to slow nearly to zero, got %f", state.Velocity)
	}
}

func TestDirectionReversalIsImmediate(t *testing.T) {
	scratch := NewScratch(Config{Friction: 4})

	scratch.Update(Input{
		Velocity: 10,
		Touching: true,
	}, 16*time.Millisecond)

	state := scratch.Update(Input{
		Velocity: -8,
		Touching: true,
	}, 16*time.Millisecond)

	if state.Velocity != -8 {
		t.Fatalf("expected immediate reversal to -8, got %f", state.Velocity)
	}
}

func TestPositionAccumulates(t *testing.T) {
	scratch := NewScratch(Config{Friction: 4})

	state := scratch.Update(Input{
		Velocity: 10,
		Touching: true,
	}, time.Second)

	if math.Abs(state.Position-10) > 0.0001 {
		t.Fatalf("expected position 10, got %f", state.Position)
	}
}
