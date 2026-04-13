package appctr

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"runtime/debug"
	"strings"
	"sync"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/device"
	wireproxy "github.com/bropines/awg-wireproxy"
	"github.com/things-go/go-socks5"
	_ "golang.org/x/mobile/bind"
)

// --- LOGGING SYSTEM ---
type LogManager struct {
	mu      sync.RWMutex
	logs    []string
	maxSize int
}

var logManager = &LogManager{
	logs:    make([]string, 0, 1000),
	maxSize: 1000,
}

func AddLog(level, msg string) {
	logManager.mu.Lock()
	defer logManager.mu.Unlock()
	if len(logManager.logs) >= logManager.maxSize {
		logManager.logs = logManager.logs[len(logManager.logs)/2:]
	}
	timestamp := time.Now().Format("15:04:05")
	logManager.logs = append(logManager.logs, fmt.Sprintf("%s[%s] %s", timestamp, level, msg))
}

func GetLogs() string {
	logManager.mu.RLock()
	defer logManager.mu.RUnlock()
	return strings.Join(logManager.logs, "\n")
}

func ClearLogs() {
	logManager.mu.Lock()
	defer logManager.mu.Unlock()
	logManager.logs = make([]string, 0, logManager.maxSize)
}

var stopLogRedirect = make(chan struct{})

func redirectGlobalLogs() {
	r, w, _ := os.Pipe()
	os.Stdout = w
	os.Stderr = w

	go func() {
		scanner := bufio.NewScanner(r)
		for scanner.Scan() {
			msg := scanner.Text()
			if !strings.Contains(msg, "UAPI: Getting") {
				AddLog("CORE", msg)
			}
		}
	}()

	go func() {
		<-stopLogRedirect
		w.Close()
		r.Close()
	}()
}

// --- PROXY AND CREDENTIAL INTERCEPTOR ---
func extractAndStripProxies(configStr string) (string, string, string, string, string, bool) {
	var newLines []string
	var socksPort, httpPort, socksUser, socksPass string
	disableUDP := false
	lines := strings.Split(configStr, "\n")
	inSocks := false
	inHttp := false

	for _, line := range lines {
		trimmed := strings.TrimSpace(line)

		if strings.HasPrefix(trimmed, "[") && strings.HasSuffix(trimmed, "]") {
			inSocks = false
			inHttp = false
		}
		if strings.EqualFold(trimmed, "[Socks5]") {
			inSocks = true
			continue
		}
		if strings.EqualFold(trimmed, "[http]") {
			inHttp = true
			continue
		}

		if inSocks {
			lower := strings.ToLower(trimmed)
			if strings.HasPrefix(lower, "bindaddress") {
				parts := strings.SplitN(trimmed, "=", 2)
				if len(parts) == 2 {
					_, port, _ := net.SplitHostPort(strings.TrimSpace(parts[1]))
					socksPort = port
				}
				continue
			}
			if strings.HasPrefix(lower, "username") {
				parts := strings.SplitN(trimmed, "=", 2)
				if len(parts) == 2 {
					socksUser = strings.TrimSpace(parts[1])
				}
				continue
			}
			if strings.HasPrefix(lower, "password") {
				parts := strings.SplitN(trimmed, "=", 2)
				if len(parts) == 2 {
					socksPass = strings.TrimSpace(parts[1])
				}
				continue
			}
			if strings.HasPrefix(lower, "disableudp") {
				parts := strings.SplitN(trimmed, "=", 2)
				if len(parts) == 2 {
					val := strings.ToLower(strings.TrimSpace(parts[1]))
					if val == "true" || val == "1" || val == "yes" {
						disableUDP = true
					}
				}
				continue
			}
			continue
		}
		if inHttp {
			if strings.HasPrefix(strings.ToLower(trimmed), "bindaddress") {
				parts := strings.SplitN(trimmed, "=", 2)
				if len(parts) == 2 {
					_, port, _ := net.SplitHostPort(strings.TrimSpace(parts[1]))
					httpPort = port
				}
			}
			continue
		}
		newLines = append(newLines, line)
	}
	return strings.Join(newLines, "\n"), socksPort, httpPort, socksUser, socksPass, disableUDP
}

// --- UDP FILTERING RULE (STRICT SOCKS5 MODE) ---
type noUDPRule struct{}

func (r *noUDPRule) Allow(ctx context.Context, req *socks5.Request) (context.Context, bool) {
	// Command 0x03 in SOCKS5 is UDP ASSOCIATE.
	if req.Command == 3 {
		AddLog("CORE", "Blocked UDP ASSOCIATE request (Strict Mode is ON)")
		return ctx, false
	}
	return ctx, true
}

// --- STEALTH WRAPPER ---
type stealthListener struct {
	net.Listener
	requireAuth bool
}

type stealthConn struct {
	net.Conn
	reader *bufio.Reader
}

func (c *stealthConn) Read(b []byte) (int, error) {
	return c.reader.Read(b)
}

func (l *stealthListener) Accept() (net.Conn, error) {
	for {
		conn, err := l.Listener.Accept()
		if err != nil {
			return nil, err
		}

		bufConn := bufio.NewReader(conn)

		header, err := bufConn.Peek(2)
		if err != nil || len(header) < 2 || header[0] != 0x05 {
			conn.Close()
			continue
		}

		nMethods := int(header[1])

		fullGreeting, err := bufConn.Peek(2 + nMethods)
		if err != nil || len(fullGreeting) < 2+nMethods {
			conn.Close()
			continue
		}

		if l.requireAuth {
			hasAuthMethod := false
			for i := 2; i < 2+nMethods; i++ {
				if fullGreeting[i] == 0x02 { // 0x02 - Username/Password Auth
					hasAuthMethod = true
					break
				}
			}

			if !hasAuthMethod {
				conn.Close()
				continue
			}
		}

		return &stealthConn{Conn: conn, reader: bufConn}, nil
	}
}

// --- CUSTOM HTTP PROXY (if port is specified) ---
func serveHttp(l net.Listener, tun *wireproxy.VirtualTun) {
	for {
		conn, err := l.Accept()
		if err != nil {
			return
		}
		go handleHttpConn(conn, tun)
	}
}

func handleHttpConn(conn net.Conn, tun *wireproxy.VirtualTun) {
	defer conn.Close()
	rd := bufio.NewReader(conn)
	req, err := http.ReadRequest(rd)
	if err != nil {
		return
	}

	target := req.Host
	if !strings.Contains(target, ":") {
		if req.Method == http.MethodConnect {
			target += ":443"
		} else {
			target += ":80"
		}
	}

	peer, err := tun.Tnet.Dial("tcp", target)
	if err != nil {
		return
	}
	defer peer.Close()

	if req.Method == http.MethodConnect {
		conn.Write([]byte("HTTP/1.1 200 Connection established\r\n\r\n"))
	} else {
		req.Write(peer)
	}
	go io.Copy(peer, rd)
	io.Copy(conn, peer)
}

// --- CORE CONTROLS ---
var (
	stateMu       sync.Mutex
	activeTun     *wireproxy.VirtualTun
	cancelFunc    context.CancelFunc
	socksListener net.Listener
	httpListener  net.Listener
)

func IsRunning() bool {
	stateMu.Lock()
	defer stateMu.Unlock()
	return activeTun != nil
}

func PingTunnel(host string) string {
	stateMu.Lock()
	tun := activeTun
	stateMu.Unlock()

	if tun == nil {
		return "ERR: tunnel not running"
	}

	target := host + ":80"
	start := time.Now()
	conn, err := tun.Tnet.DialContext(context.Background(), "tcp", target)
	if err != nil {
		start = time.Now()
		conn, err = tun.Tnet.DialContext(context.Background(), "tcp", host+":443")
		if err != nil {
			return fmt.Sprintf("ERR: %v", err)
		}
	}
	elapsed := time.Since(start)
	conn.Close()
	return fmt.Sprintf("%d ms", elapsed.Milliseconds())
}

func Start(configStr string, cacheDir string) error {
	debug.SetGCPercent(150)

	cleanConfig, socksPort, httpPort, socksUser, socksPass, disableUDP := extractAndStripProxies(configStr)

	stateMu.Lock()
	if activeTun != nil || socksListener != nil || httpListener != nil {
		stateMu.Unlock()
		Stop()
		stateMu.Lock()
	}
	stateMu.Unlock()

	redirectGlobalLogs()

	tmpConf := filepath.Join(cacheDir, "current.conf")
	err := os.WriteFile(tmpConf, []byte(cleanConfig), 0600)
	if err != nil {
		return fmt.Errorf("failed to write config: %v", err)
	}
	defer os.Remove(tmpConf)

	conf, err := wireproxy.ParseConfig(tmpConf)
	if err != nil {
		return fmt.Errorf("config parse error: %v", err)
	}

	tun, err := wireproxy.StartWireguard(conf, device.LogLevelVerbose)
	if err != nil {
		return fmt.Errorf("wireguard start error: %v", err)
	}

	for _, spawner := range conf.Routines {
		go spawner.SpawnRoutine(tun)
		AddLog("CORE", "Spawned additional tunnel from config")
	}

	stateMu.Lock()
	activeTun = tun
	ctx, cancel := context.WithCancel(context.Background())
	cancelFunc = cancel

	if socksPort != "" {
		sl, err := net.Listen("tcp", "127.0.0.1:"+socksPort)
		if err == nil {
			socksListener = sl
			
			hasCredentials := socksUser != "" && socksPass != ""

			stealthL := &stealthListener{
				Listener:    sl,
				requireAuth: hasCredentials,
			}

			options := []socks5.Option{
				socks5.WithDial(tun.Tnet.DialContext),
				socks5.WithResolver(tun),
			}

			if disableUDP {
				options = append(options, socks5.WithRule(&noUDPRule{}))
				AddLog("CORE", "Strict Mode: UDP routing disabled")
			}

			if hasCredentials {
				creds := socks5.StaticCredentials{socksUser: socksPass}
				options = append(options, socks5.WithCredential(creds))
				AddLog("CORE", "SOCKS5 Auth & Stealth Filter Enabled")
			} else {
				AddLog("CORE", "WARNING: SOCKS5 is open. Stealth Filter inactive.")
			}

			srv := socks5.NewServer(options...)
			go srv.Serve(stealthL)
			AddLog("CORE", "SOCKS5 routing active on port "+socksPort)
		} else {
			AddLog("CORE", "SOCKS5 Bind Error: "+err.Error())
		}
	}

	if httpPort != "" {
		hl, err := net.Listen("tcp", "127.0.0.1:"+httpPort)
		if err == nil {
			httpListener = hl
			go serveHttp(hl, tun)
			AddLog("CORE", "HTTP routing active on port "+httpPort)
		} else {
			AddLog("CORE", "HTTP Bind Error: "+err.Error())
		}
	}
	stateMu.Unlock()

	go func() {
		<-ctx.Done()
		stateMu.Lock()
		if activeTun != nil && activeTun.Dev != nil {
			activeTun.Dev.Close()
		}
		activeTun = nil
		stateMu.Unlock()
	}()

	return nil
}

func Stop() {
	stateMu.Lock()
	defer stateMu.Unlock()

	if socksListener != nil {
		socksListener.Close()
		socksListener = nil
	}
	if httpListener != nil {
		httpListener.Close()
		httpListener = nil
	}

	if cancelFunc != nil {
		cancelFunc()
		cancelFunc = nil
		select {
		case stopLogRedirect <- struct{}{}:
		default:
		}
	}
}