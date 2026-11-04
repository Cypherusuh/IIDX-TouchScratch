package spice2x

import (
	"bufio"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net"
	"sync"
	"time"
)

type Client struct {
	conn    net.Conn
	reader  *bufio.Reader
	mu      sync.Mutex
	nextID  int
	timeout time.Duration
}

type request struct {
	ID       int    `json:"id"`
	Module   string `json:"module"`
	Function string `json:"function"`
	Params   any    `json:"params"`
}

type response struct {
	ID     int             `json:"id"`
	Errors []string        `json:"errors"`
	Data   json.RawMessage `json:"data"`
}

type AnalogState struct {
	Name   string
	State  float64
	Active bool
}

func Connect(address string) (*Client, error) {
	conn, err := net.DialTimeout("tcp", address, 3*time.Second)
	if err != nil {
		return nil, fmt.Errorf("connect to Spice2X: %w", err)
	}

	return &Client{
		conn:    conn,
		reader:  bufio.NewReader(conn),
		nextID:  1,
		timeout: 3 * time.Second,
	}, nil
}

func (c *Client) ReadAnalogs() ([]AnalogState, error) {
	data, err := c.call("analogs", "read", []any{})
	if err != nil {
		return nil, err
	}

	var rows [][]json.RawMessage
	if err := json.Unmarshal(data, &rows); err != nil {
		return nil, fmt.Errorf("decode analog list: %w", err)
	}

	analogs := make([]AnalogState, 0, len(rows))

	for _, row := range rows {
		if len(row) != 3 {
			return nil, errors.New("unexpected analog response format")
		}

		var analog AnalogState

		if err := json.Unmarshal(row[0], &analog.Name); err != nil {
			return nil, err
		}
		if err := json.Unmarshal(row[1], &analog.State); err != nil {
			return nil, err
		}
		if err := json.Unmarshal(row[2], &analog.Active); err != nil {
			return nil, err
		}

		analogs = append(analogs, analog)
	}

	return analogs, nil
}

func (c *Client) WriteAnalog(name string, value float64) error {
	if name == "" {
		return errors.New("analog name cannot be empty")
	}

	if math.IsNaN(value) || math.IsInf(value, 0) ||
		value < 0 || value > 1 {
		return fmt.Errorf("analog value must be between 0 and 1")
	}

	_, err := c.call(
		"analogs",
		"write",
		[][]any{{name, value}},
	)

	return err
}

func (c *Client) ResetAnalog(name string) error {
	if name == "" {
		return errors.New("analog name cannot be empty")
	}

	_, err := c.call(
		"analogs",
		"write_reset",
		[][]any{{name}},
	)

	return err
}

func (c *Client) call(
	module string,
	function string,
	params any,
) (json.RawMessage, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.conn == nil {
		return nil, errors.New("Spice2X client is closed")
	}

	req := request{
		ID:       c.nextID,
		Module:   module,
		Function: function,
		Params:   params,
	}
	c.nextID++

	payload, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("encode request: %w", err)
	}

	payload = append(payload, 0)

	if err := c.conn.SetDeadline(time.Now().Add(c.timeout)); err != nil {
		return nil, err
	}

	if _, err := c.conn.Write(payload); err != nil {
		return nil, fmt.Errorf("send Spice2X request: %w", err)
	}

	rawResponse, err := c.reader.ReadBytes(0)
	if err != nil {
		return nil, fmt.Errorf("receive Spice2X response: %w", err)
	}

	if len(rawResponse) == 0 || rawResponse[len(rawResponse)-1] != 0 {
		return nil, io.ErrUnexpectedEOF
	}

	rawResponse = rawResponse[:len(rawResponse)-1]

	var res response
	if err := json.Unmarshal(rawResponse, &res); err != nil {
		return nil, fmt.Errorf("decode Spice2X response: %w", err)
	}

	if res.ID != req.ID {
		return nil, fmt.Errorf(
			"unexpected response ID: got %d, expected %d",
			res.ID,
			req.ID,
		)
	}

	if len(res.Errors) > 0 {
		return nil, fmt.Errorf(
			"Spice2X API error: %v",
			res.Errors,
		)
	}

	return res.Data, nil
}

func (c *Client) Close() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.conn == nil {
		return nil
	}

	err := c.conn.Close()
	c.conn = nil

	return err
}
