package input

type Sample struct {
	DeltaAngle    float64
	AverageRadius float64
	TouchAngle    float64
	Timestamp     uint64
	FingerCount   uint8
}

type ProcessedSample struct {
	DeltaAngle    float64
	AverageRadius float64
	TouchAngle    float64
	Timestamp     uint64
	FingerCount   uint8
	Velocity      float64
}
