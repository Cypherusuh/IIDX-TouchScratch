package input

import "time"

type velocityPoint struct {
	timestamp uint64
	angle     float64
}

type VelocityCalculator struct {
	windowMS     uint64
	resetAfterMS uint64

	points []velocityPoint

	position float64

	lastTimestamp uint64
	hasTimestamp  bool
}

func NewVelocityCalculator(
	window time.Duration,
	resetAfter time.Duration,
) *VelocityCalculator {
	windowMS := uint64(window.Milliseconds())
	resetAfterMS := uint64(resetAfter.Milliseconds())

	if windowMS == 0 {
		windowMS = 1
	}

	if resetAfterMS < windowMS {
		resetAfterMS = windowMS
	}

	return &VelocityCalculator{
		windowMS:     windowMS,
		resetAfterMS: resetAfterMS,
	}
}

func (v *VelocityCalculator) Add(sample Sample) float64 {
	timestamp := sample.Timestamp

	if v.hasTimestamp && timestamp < v.lastTimestamp {
		return v.currentVelocity()
	}

	if v.hasTimestamp &&
		timestamp-v.lastTimestamp > v.resetAfterMS {
		v.Reset()
	}

	v.position += sample.DeltaAngle

	v.points = append(v.points, velocityPoint{
		timestamp: timestamp,
		angle:     v.position,
	})

	v.lastTimestamp = timestamp
	v.hasTimestamp = true

	v.prune(timestamp)

	return v.currentVelocity()
}

func (v *VelocityCalculator) prune(timestamp uint64) {
	if timestamp < v.windowMS {
		return
	}

	cutoff := timestamp - v.windowMS

	for len(v.points) > 2 &&
		v.points[1].timestamp <= cutoff {
		v.points = v.points[1:]
	}
}

func (v *VelocityCalculator) currentVelocity() float64 {
	if len(v.points) < 2 {
		return 0
	}

	first := v.points[0]
	last := v.points[len(v.points)-1]

	dt := last.timestamp - first.timestamp
	if dt == 0 {
		return 0
	}

	seconds := float64(dt) / 1000.0

	return (last.angle - first.angle) / seconds
}

func (v *VelocityCalculator) Reset() {
	v.points = nil
	v.position = 0
	v.lastTimestamp = 0
	v.hasTimestamp = false
}
