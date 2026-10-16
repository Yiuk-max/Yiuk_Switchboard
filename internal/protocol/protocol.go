package protocol

import (
	"bufio"
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
)

const MaxLineBytes = 64 << 10

var ErrLineTooLong = errors.New("protocol line exceeds 64 KiB")

type Mod struct {
	ID  string `json:"id"`
	Ver string `json:"ver"`
}

type RoomSummary struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Host     string `json:"host"`
	Port     int    `json:"port"`
	Players  int    `json:"players"`
	Max      int    `json:"max"`
	MC       string `json:"mc"`
	Loader   string `json:"loader"`
	ModCount int    `json:"mod_count"`
}

type Message struct {
	T      string        `json:"t"`
	PW     string        `json:"pw,omitempty"`
	Name   string        `json:"name,omitempty"`
	Ver    int           `json:"ver,omitempty"`
	Max    int           `json:"max,omitempty"`
	MC     string        `json:"mc,omitempty"`
	Loader string        `json:"loader,omitempty"`
	Mods   []Mod         `json:"mods,omitempty"`
	ID     string        `json:"id,omitempty"`
	SID    string        `json:"sid,omitempty"`
	Token  string        `json:"token,omitempty"`
	Port   int           `json:"port,omitempty"`
	Code   string        `json:"code,omitempty"`
	Msg    string        `json:"msg,omitempty"`
	List   []RoomSummary `json:"list,omitempty"`
}

func (m Message) MarshalJSON() ([]byte, error) {
	type wireMessage struct {
		T      string        `json:"t"`
		PW     string        `json:"pw,omitempty"`
		Name   string        `json:"name,omitempty"`
		Ver    int           `json:"ver,omitempty"`
		Max    int           `json:"max,omitempty"`
		MC     string        `json:"mc,omitempty"`
		Loader string        `json:"loader,omitempty"`
		Mods   *[]Mod        `json:"mods,omitempty"`
		ID     string        `json:"id,omitempty"`
		SID    string        `json:"sid,omitempty"`
		Token  string        `json:"token,omitempty"`
		Port   int           `json:"port,omitempty"`
		Code   string        `json:"code,omitempty"`
		Msg    string        `json:"msg,omitempty"`
		List   []RoomSummary `json:"list,omitempty"`
	}
	var mods *[]Mod
	if m.Mods != nil || m.T == "mods" {
		items := m.Mods
		if items == nil {
			items = []Mod{}
		}
		mods = &items
	}
	return json.Marshal(wireMessage{T: m.T, PW: m.PW, Name: m.Name, Ver: m.Ver, Max: m.Max, MC: m.MC,
		Loader: m.Loader, Mods: mods, ID: m.ID, SID: m.SID, Token: m.Token, Port: m.Port,
		Code: m.Code, Msg: m.Msg, List: m.List})
}

// ReadLine reads exactly one newline-terminated JSON object and enforces the
// protocol limit without losing bytes already buffered after the newline.
func ReadLine(r *bufio.Reader) (Message, error) {
	var msg Message
	line, err := r.ReadSlice('\n')
	if errors.Is(err, bufio.ErrBufferFull) {
		return msg, ErrLineTooLong
	}
	if err != nil {
		return msg, err
	}
	if len(line) > MaxLineBytes {
		return msg, ErrLineTooLong
	}
	line = bytes.TrimSpace(line)
	if len(line) == 0 {
		return msg, fmt.Errorf("empty protocol message")
	}
	if err := json.Unmarshal(line, &msg); err != nil {
		return msg, fmt.Errorf("invalid JSON message: %w", err)
	}
	if msg.T == "" {
		return msg, fmt.Errorf("missing message type")
	}
	return msg, nil
}

func Write(w io.Writer, msg Message) error {
	b, err := json.Marshal(msg)
	if err != nil {
		return err
	}
	b = append(b, '\n')
	_, err = w.Write(b)
	return err
}
