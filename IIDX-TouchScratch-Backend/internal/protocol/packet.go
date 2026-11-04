package protocol

import (
	"encoding/binary"
	"errors"
	"math"
)

const (
	PacketSize = 40
	Version    = 2

	TypeHello     = 1
	TypeTouch     = 2
	TypeHeartbeat = 3
	TypeAck       = 4
	TypeTelemetry = 5
)

var magic = [4]byte{'I', 'D', 'X', 'T'}

type Packet struct {
	Type          uint8
	SessionID     uint64
	Sequence      uint32
	Timestamp     uint64
	DeltaAngle    float32
	AverageRadius float32
	TouchAngle    float32
	FingerCount   uint8
}

func Encode(p Packet) []byte {
	data := make([]byte, PacketSize)

	copy(data[0:4], magic[:])
	data[4] = Version
	data[5] = p.Type

	binary.LittleEndian.PutUint64(data[8:16], p.SessionID)
	binary.LittleEndian.PutUint32(data[16:20], p.Sequence)
	binary.LittleEndian.PutUint64(data[20:28], p.Timestamp)

	binary.LittleEndian.PutUint32(data[28:32], math.Float32bits(p.DeltaAngle))
	binary.LittleEndian.PutUint32(data[32:36], math.Float32bits(p.AverageRadius))

	// Encode angle in the range [-PI, PI] as a signed 16-bit value.
	angle := math.Max(-math.Pi, math.Min(math.Pi, float64(p.TouchAngle)))
	encodedAngle := int16(math.Round(angle / math.Pi * 32767))

	binary.LittleEndian.PutUint16(data[37:39], uint16(encodedAngle))
	data[36] = p.FingerCount

	return data
}

func Decode(data []byte) (Packet, error) {
	if len(data) != PacketSize {
		return Packet{}, errors.New("invalid packet size")
	}

	if string(data[0:4]) != string(magic[:]) {
		return Packet{}, errors.New("invalid packet magic")
	}

	if data[4] != Version {
		return Packet{}, errors.New("unsupported protocol version")
	}

	encodedAngle := int16(binary.LittleEndian.Uint16(data[37:39]))
	touchAngle := float32(float64(encodedAngle) / 32767 * math.Pi)

	return Packet{
		Type:          data[5],
		SessionID:     binary.LittleEndian.Uint64(data[8:16]),
		Sequence:      binary.LittleEndian.Uint32(data[16:20]),
		Timestamp:     binary.LittleEndian.Uint64(data[20:28]),
		DeltaAngle:    math.Float32frombits(binary.LittleEndian.Uint32(data[28:32])),
		AverageRadius: math.Float32frombits(binary.LittleEndian.Uint32(data[32:36])),
		FingerCount:   data[36],
		TouchAngle:    touchAngle,
	}, nil
}
