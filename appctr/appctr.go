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
	"strings"
	"sync"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/device"
	wireproxy "github.com/artem-russkikh/wireproxy-awg"
	"github.com/things-go/go-socks5"
	_ "golang.org/x/mobile/bind"
)

// --- СИСТЕМА ЛОГОВ ---
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
	logManager.logs = append(logManager.logs, fmt.Sprintf("%s [%s] %s", timestamp, level, msg))
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
		for {
			select {
			case <-stopLogRedirect:
				return
			default:
				if scanner.Scan() {
					msg := scanner.Text()
					if !strings.Contains(msg, "UAPI: Getting") {
						AddLog("CORE", msg)
					}
				}
			}
		}
	}()
}

// --- ПЕРЕХВАТЧИК ПРОКСИ (Вырезает секции, чтобы wireproxy не вызвал log.Fatal) ---
func extractAndStripProxies(configStr string) (string, string, string) {
	var newLines []string
	var socksPort, httpPort string
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
			if strings.HasPrefix(strings.ToLower(trimmed), "bindaddress") {
				parts := strings.SplitN(trimmed, "=", 2)
				if len(parts) == 2 {
					addr := strings.TrimSpace(parts[1])
					_, port, _ := net.SplitHostPort(addr)
					socksPort = port
				}
			}
			continue
		}
		if inHttp {
			if strings.HasPrefix(strings.ToLower(trimmed), "bindaddress") {
				parts := strings.SplitN(trimmed, "=", 2)
				if len(parts) == 2 {
					addr := strings.TrimSpace(parts[1])
					_, port, _ := net.SplitHostPort(addr)
					httpPort = port
				}
			}
			continue
		}
		newLines = append(newLines, line)
	}
	return strings.Join(newLines, "\n"), socksPort, httpPort
}

// --- КАСТОМНЫЙ HTTP ПРОКСИ ---
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

// --- УПРАВЛЕНИЕ ЯДРОМ ---
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
	// 1. Вырезаем прокси-секции, чтобы wireproxy их не увидел и не крашнул нам аппку
	cleanConfig, socksPort, httpPort := extractAndStripProxies(configStr)

	stateMu.Lock()
	if activeTun != nil || socksListener != nil || httpListener != nil {
		stateMu.Unlock()
		Stop()
		stateMu.Lock()
	}
	stateMu.Unlock()

	redirectGlobalLogs()

	// 2. Сохраняем чистый конфиг во временный файл
	tmpConf := filepath.Join(cacheDir, "current.conf")
	err := os.WriteFile(tmpConf, []byte(cleanConfig), 0600)
	if err != nil {
		return fmt.Errorf("failed to write config: %v", err)
	}
	defer os.Remove(tmpConf)

	// 3. wireproxy сам прочитает WGConfig путь из current.conf
	conf, err := wireproxy.ParseConfig(tmpConf)
	if err != nil {
		return fmt.Errorf("config parse error: %v", err)
	}

	tun, err := wireproxy.StartWireguard(conf.Device, device.LogLevelVerbose)
	if err != nil {
		return fmt.Errorf("wireguard start error: %v", err)
	}

	stateMu.Lock()
	activeTun = tun
	ctx, cancel := context.WithCancel(context.Background())
	cancelFunc = cancel

	// 4. Поднимаем наш безопасный "SOCKS дома"
	if socksPort != "" {
		sl, err := net.Listen("tcp", "127.0.0.1:"+socksPort)
		if err == nil {
			socksListener = sl
			srv := socks5.NewServer(
				socks5.WithDial(tun.Tnet.DialContext),
				socks5.WithResolver(tun),
			)
			go srv.Serve(sl)
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