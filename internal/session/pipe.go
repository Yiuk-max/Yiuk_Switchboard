package session

import (
	"io"
	"net"
	"time"
)

// Pipe copies both directions and closes both sockets as soon as either side
// ends. Each direction may be a buffered reader to preserve protocol prefetch.
func Pipe(a net.Conn, aReader io.Reader, b net.Conn, bReader io.Reader, idleTimeout time.Duration) {
	if aReader == nil {
		aReader = a
	}
	if bReader == nil {
		bReader = b
	}
	aReader = idleReader{reader: aReader, conn: a, timeout: idleTimeout}
	bReader = idleReader{reader: bReader, conn: b, timeout: idleTimeout}
	done := make(chan struct{}, 2)
	go func() { _, _ = io.Copy(a, bReader); done <- struct{}{} }()
	go func() { _, _ = io.Copy(b, aReader); done <- struct{}{} }()
	<-done
	_ = a.Close()
	_ = b.Close()
	<-done
}

type idleReader struct {
	reader  io.Reader
	conn    net.Conn
	timeout time.Duration //if there are no data for a while, close the connection,to clean the conn that has lost connection but not closed by the TCP layer
}

func (r idleReader) Read(p []byte) (int, error) {
	if r.timeout > 0 {
		_ = r.conn.SetReadDeadline(time.Now().Add(r.timeout))
	}
	return r.reader.Read(p)
}
