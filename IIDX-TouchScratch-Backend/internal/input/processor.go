package input

import "time"

type Processor struct {
	velocity *VelocityCalculator
}

func NewProcessor() *Processor {
	return &Processor{
		velocity: NewVelocityCalculator(
			30*time.Millisecond,
			100*time.Millisecond,
		),
	}
}

func (p *Processor) Process(sample Sample) ProcessedSample {
	velocity := p.velocity.Add(sample)

	return ProcessedSample{
		DeltaAngle:    sample.DeltaAngle,
		AverageRadius: sample.AverageRadius,
		TouchAngle:    sample.TouchAngle,
		Timestamp:     sample.Timestamp,
		FingerCount:   sample.FingerCount,
		Velocity:      velocity,
	}
}

func (p *Processor) Reset() {
	p.velocity.Reset()
}
