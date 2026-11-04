package physics

import (
	"math"
	"time"
)

type Input struct {
	Velocity float64
	Touching bool
}

type State struct {
	Position float64
	Velocity float64
	Touching bool
}

type Config struct {
	// Friction controls how quickly the platter slows after release.
	// Higher values mean stronger friction.
	Friction float64
}

type Scratch struct {
	config Config
	state  State
}

func NewScratch(config Config) *Scratch {
	if config.Friction < 0 {
		config.Friction = 0
	}

	return &Scratch{
		config: config,
	}
}

func (s *Scratch) Update(input Input, dt time.Duration) State {
	if dt <= 0 {
		return s.state
	}

	deltaSeconds := dt.Seconds()

	if input.Touching {
		// Direct response while the user is controlling the platter.
		if !math.IsNaN(input.Velocity) &&
			!math.IsInf(input.Velocity, 0) {
			s.state.Velocity = input.Velocity
		}
	} else {
		// Exponential friction while the platter is released.
		s.state.Velocity *= math.Exp(
			-s.config.Friction * deltaSeconds,
		)

		// Stop insignificant residual movement.
		if math.Abs(s.state.Velocity) < 0.05 {
			s.state.Velocity = 0
		}
	}

	s.state.Position += s.state.Velocity * deltaSeconds
	s.state.Touching = input.Touching

	return s.state
}

func (s *Scratch) Reset() {
	s.state = State{}
}

func (s *Scratch) State() State {
	return s.state
}
