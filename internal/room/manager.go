package room

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"log/slog"
	"net"
	"strconv"
	"sync"
	"time"

	"yiuk-switchboard/internal/connlimit"
	"yiuk-switchboard/internal/protocol"
	"yiuk-switchboard/internal/session"
)

var (
	ErrRoomLimit      = fmt.Errorf("room limit reached")
	ErrPortsExhausted = fmt.Errorf("no available room port")
)

type Notify func(protocol.Message) error

type Options struct {
	PortMin, PortMax, MaxRooms, MaxPlayers, MaxPending int
	PendingTimeout, ReconnectGrace, SessionIdleTimeout time.Duration
	ConnectionBudget                                   *connlimit.Budget
}

type room struct {
	id, name, host, mc, loader, token string
	port, max, players                int
	mods                              []protocol.Mod
	owner                             Notify
	ownerKey                          string
	graceTimer                        *time.Timer
	listener                          net.Listener
	pending                           map[string]chan net.Conn
	active                            map[net.Conn]struct{}
	closed                            bool
	mu                                sync.Mutex
}

type Manager struct {
	opts         Options
	mu           sync.RWMutex
	rooms        map[string]*room
	limitLogMu   sync.Mutex
	lastLimitLog time.Time
}

func NewManager(opts Options) *Manager { return &Manager{opts: opts, rooms: make(map[string]*room)} }

func (m *Manager) Create(name, host, mc, loader string, max int, mods []protocol.Mod, ownerKey string, notify Notify) (protocol.Message, error) {
	if name == "" || host == "" || mc == "" || loader == "" || notify == nil || max < 1 || max > m.opts.MaxPlayers {
		return protocol.Message{}, fmt.Errorf("invalid room fields")
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, existing := range m.rooms {
		if existing.ownerKey == ownerKey {
			return protocol.Message{}, fmt.Errorf("one room per control connection")
		}
	}
	if len(m.rooms) >= m.opts.MaxRooms {
		return protocol.Message{}, ErrRoomLimit
	}
	var ln net.Listener
	var port int
	for port = m.opts.PortMin; port <= m.opts.PortMax; port++ {
		used := false
		for _, existing := range m.rooms {
			if existing.port == port {
				used = true
				break
			}
		}
		if used {
			continue
		}
		var err error
		ln, err = net.Listen("tcp", net.JoinHostPort("0.0.0.0", strconv.Itoa(port)))
		if err == nil {
			break
		}
	}
	if ln == nil {
		return protocol.Message{}, ErrPortsExhausted
	}
	id, err := randomHex(8)
	if err != nil {
		_ = ln.Close()
		return protocol.Message{}, err
	}
	token, err := randomHex(16)
	if err != nil {
		_ = ln.Close()
		return protocol.Message{}, err
	}
	r := &room{id: id, name: name, host: host, mc: mc, loader: loader, token: token, port: port, max: max,
		mods: append([]protocol.Mod(nil), mods...), owner: notify, ownerKey: ownerKey, listener: ln,
		pending: make(map[string]chan net.Conn), active: make(map[net.Conn]struct{})}
	m.rooms[id] = r
	go m.accept(r)
	slog.Info("room created", "room_id", id, "port", port, "host", host)
	return protocol.Message{T: "created", ID: id, Port: port, Token: token}, nil
}

func (m *Manager) accept(r *room) {
	for {
		conn, err := r.listener.Accept()
		if err != nil {
			return
		}
		if tcp, ok := conn.(*net.TCPConn); ok {
			_ = tcp.SetKeepAlive(true)
		}
		budget := m.opts.ConnectionBudget
		if budget != nil && !budget.TryAcquire() {
			m.logConnectionLimit()
			_ = conn.Close()
			continue
		}
		go func() {
			if budget != nil {
				defer budget.Release()
			}
			m.player(r, conn)
		}()
	}
}

func (m *Manager) logConnectionLimit() {
	m.limitLogMu.Lock()
	defer m.limitLogMu.Unlock()
	if time.Since(m.lastLimitLog) >= 10*time.Second {
		slog.Warn("room connection rejected: global connection limit reached")
		m.lastLimitLog = time.Now()
	}
}

func (m *Manager) player(r *room, player net.Conn) {
	if !m.track(r, player, true) {
		_ = player.Close()
		return
	}
	sid, err := randomHex(16)
	if err != nil {
		m.untrack(r, player)
		_ = player.Close()
		return
	}
	ch := make(chan net.Conn, 1)
	r.mu.Lock()
	if r.closed || len(r.pending) >= m.opts.MaxPending || r.players+len(r.pending) >= r.max || r.owner == nil {
		r.mu.Unlock()
		m.untrack(r, player)
		_ = player.Close()
		return
	}
	r.pending[sid] = ch
	notify := r.owner
	r.mu.Unlock()
	if err := notify(protocol.Message{T: "new_session", SID: sid}); err != nil {
		m.removePending(r, sid)
		m.untrack(r, player)
		_ = player.Close()
		return
	}
	timer := time.NewTimer(m.opts.PendingTimeout)
	defer timer.Stop()
	select {
	case host := <-ch:
		if paired, ok := host.(*readerConn); ok && paired.onClose != nil {
			defer paired.onClose()
		}
		if !m.track(r, host, true) {
			r.mu.Lock()
			r.players--
			r.mu.Unlock()
			_ = host.Close()
			m.untrack(r, player)
			_ = player.Close()
			return
		}
		slog.Info("session paired", "room_id", r.id, "sid", sid)
		session.Pipe(player, nil, host, nil, m.opts.SessionIdleTimeout)
		r.mu.Lock()
		r.players--
		r.mu.Unlock()
		m.untrack(r, player)
		m.untrack(r, host)
		slog.Info("session ended", "room_id", r.id, "sid", sid)
	case <-timer.C:
		r.mu.Lock()
		delete(r.pending, sid)
		var pairedHost net.Conn
		select {
		case pairedHost = <-ch:
			r.players--
		default:
		}
		r.mu.Unlock()
		if pairedHost != nil {
			_ = pairedHost.Close()
		}
		m.untrack(r, player)
		_ = player.Close()
	}
}

func (m *Manager) Pair(sid string, conn net.Conn, reader io.Reader, onClose func()) bool {
	m.mu.RLock()
	rooms := make([]*room, 0, len(m.rooms))
	for _, r := range m.rooms {
		rooms = append(rooms, r)
	}
	m.mu.RUnlock()
	for _, r := range rooms {
		r.mu.Lock()
		ch, ok := r.pending[sid]
		if ok {
			delete(r.pending, sid)
			r.players++
			ch <- &readerConn{Conn: conn, reader: reader, onClose: onClose}
		}
		r.mu.Unlock()
		if ok {
			return true
		}
	}
	return false
}

type readerConn struct {
	net.Conn
	reader  io.Reader
	onClose func()
}

func (c *readerConn) Read(p []byte) (int, error) { return c.reader.Read(p) }

func (m *Manager) removePending(r *room, sid string) {
	r.mu.Lock()
	delete(r.pending, sid)
	r.mu.Unlock()
}
func (m *Manager) track(r *room, conn net.Conn, add bool) bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	if add {
		if r.closed {
			return false
		}
		r.active[conn] = struct{}{}
		return true
	}
	delete(r.active, conn)
	return true
}
func (m *Manager) untrack(r *room, conn net.Conn) { m.track(r, conn, false) }

func (m *Manager) List() []protocol.RoomSummary {
	m.mu.RLock()
	defer m.mu.RUnlock()
	out := make([]protocol.RoomSummary, 0, len(m.rooms))
	for _, r := range m.rooms {
		r.mu.Lock()
		out = append(out, protocol.RoomSummary{ID: r.id, Name: r.name, Host: r.host, Port: r.port, Players: r.players, Max: r.max, MC: r.mc, Loader: r.loader, ModCount: len(r.mods)})
		r.mu.Unlock()
	}
	return out
}

func (m *Manager) Mods(id string) ([]protocol.Mod, bool) {
	r := m.get(id)
	if r == nil {
		return nil, false
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	return append([]protocol.Mod(nil), r.mods...), true
}

func (m *Manager) Close(id, ownerKey string) bool {
	r := m.get(id)
	if r == nil {
		return false
	}
	r.mu.Lock()
	owner := r.ownerKey == ownerKey
	r.mu.Unlock()
	if !owner {
		return false
	}
	m.destroy(r)
	return true
}

func (m *Manager) OwnerDisconnected(ownerKey string) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	for _, r := range m.rooms {
		r.mu.Lock()
		if r.ownerKey == ownerKey && r.owner != nil && !r.closed {
			r.owner = nil
			r.graceTimer = time.AfterFunc(m.opts.ReconnectGrace, func() { m.destroyDisconnected(r, ownerKey) })
		}
		r.mu.Unlock()
	}
}

func (m *Manager) Resume(id, token, ownerKey string, notify Notify) bool {
	r := m.get(id)
	if r == nil {
		return false
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.closed || r.token != token {
		return false
	}
	// A valid room token authorizes the host to take over even if the old
	// control socket is half-open and has not yet hit its heartbeat deadline.
	// The previous connection's deferred OwnerDisconnected call is fenced by
	// ownerKey below, so it cannot mark this replacement connection offline.
	if r.graceTimer != nil {
		r.graceTimer.Stop()
		r.graceTimer = nil
	}
	r.ownerKey = ownerKey
	r.owner = notify
	return true
}

func (m *Manager) get(id string) *room { m.mu.RLock(); defer m.mu.RUnlock(); return m.rooms[id] }

// destroyDisconnected expires only the owner generation that scheduled this
// timer. A timer callback can already be running when Stop returns false, so
// the owner key and disconnected state must be checked atomically with removal.
func (m *Manager) destroyDisconnected(r *room, ownerKey string) {
	m.mu.Lock()
	if m.rooms[r.id] != r {
		m.mu.Unlock()
		return
	}
	r.mu.Lock()
	if r.closed || r.owner != nil || r.ownerKey != ownerKey {
		r.mu.Unlock()
		m.mu.Unlock()
		return
	}
	delete(m.rooms, r.id)
	r.closed = true
	if r.graceTimer != nil {
		r.graceTimer.Stop()
	}
	_ = r.listener.Close()
	for sid := range r.pending {
		delete(r.pending, sid)
	}
	for conn := range r.active {
		_ = conn.Close()
	}
	r.mu.Unlock()
	m.mu.Unlock()
	slog.Info("room closed after owner reconnect timeout", "room_id", r.id)
}

func (m *Manager) destroy(r *room) {
	m.mu.Lock()
	if m.rooms[r.id] != r {
		m.mu.Unlock()
		return
	}
	delete(m.rooms, r.id)
	m.mu.Unlock()
	r.mu.Lock()
	r.closed = true
	if r.graceTimer != nil {
		r.graceTimer.Stop()
	}
	_ = r.listener.Close()
	for sid := range r.pending {
		delete(r.pending, sid)
	}
	for conn := range r.active {
		_ = conn.Close()
	}
	r.mu.Unlock()
	slog.Info("room closed", "room_id", r.id)
}

func (m *Manager) CloseAll() {
	m.mu.RLock()
	rooms := make([]*room, 0, len(m.rooms))
	for _, r := range m.rooms {
		rooms = append(rooms, r)
	}
	m.mu.RUnlock()
	for _, r := range rooms {
		m.destroy(r)
	}
}

func randomHex(bytesLen int) (string, error) {
	b := make([]byte, bytesLen)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}
