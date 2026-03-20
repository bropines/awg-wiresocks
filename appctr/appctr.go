package appctr

import (
	"bufio"
	"context"
	"encoding/base64"
	"encoding/hex"
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

// --- ПРЕПРОЦЕССОР КОНФИГА (ФИКС ДЛЯ I1-I5) ---
func preProcessConfig(configStr string) string {
	lines := strings.Split(configStr, "\n")
	for i, line := range lines {
		trimmed := strings.TrimSpace(line)
		upper := strings.ToUpper(trimmed)
		if strings.HasPrefix(upper, "I1") || strings.HasPrefix(upper, "I2") ||
			strings.HasPrefix(upper, "I3") || strings.HasPrefix(upper, "I4") ||
			strings.HasPrefix(upper, "I5") {

			parts := strings.SplitN(trimmed, "=", 2)
			if len(parts) == 2 {
				key := strings.TrimSpace(parts[0])
				val := strings.TrimSpace(parts[1])
				
				val = strings.TrimSuffix(val, "<c>")
				val = strings.TrimSuffix(val, "<C>")

				if strings.HasPrefix(val, "<b 0x") && strings.HasSuffix(val, ">") {
					hexStr := val[5 : len(val)-1]
					b, err := hex.DecodeString(hexStr)
					if err == nil {
						b64Str := base64.StdEncoding.EncodeToString(b)
						lines[i] = fmt.Sprintf("%s = %s", key, b64Str)
						AddLog("CORE", fmt.Sprintf("Auto-fixed %s: HEX -> Base64", key))
					}
				}
			}
		}
	}
	return strings.Join(lines, "\n")
}

// --- ПЕРЕХВАТЧИК ПРОКСИ (ЗАЩИТА ОТ КРАША) ---
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
		if err != nil { return }
		go handleHttpConn(conn, tun)
	}
}

func handleHttpConn(conn net.Conn, tun *wireproxy.VirtualTun) {
	defer conn.Close()
	rd := bufio.NewReader(conn)
	req, err := http.ReadRequest(rd)
	if err != nil { return }

	target := req.Host
	if !strings.Contains(target, ":") {
		if req.Method == http.MethodConnect {
			target += ":443"
		} else {
			target += ":80"
		}
	}

	peer, err := tun.Tnet.Dial("tcp", target)
	if err != nil { return }
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

func Start(configStr string, cacheDir string) error {
	cleanConfig, socksPort, httpPort := extractAndStripProxies(configStr)

	// Жестко останавливаем всё, если был предыдущий сеанс
	stateMu.Lock()
	if activeTun != nil || socksListener != nil || httpListener != nil {
		stateMu.Unlock()
		Stop()
		stateMu.Lock()
	}
	stateMu.Unlock()

	redirectGlobalLogs()

	// --- МАГИЯ: ИЩЕМ И ИСПРАВЛЯЕМ ФАЙЛ WGConfig ---
	lines := strings.Split(cleanConfig, "\n")
	var tmpWgPath string

	for i, line := range lines {
		trimmed := strings.TrimSpace(line)
		if strings.HasPrefix(strings.ToUpper(trimmed), "WGCONFIG") {
			parts := strings.SplitN(trimmed, "=", 2)
			if len(parts) == 2 {
				wgPath := strings.TrimSpace(parts[1])
				wgBytes, err := os.ReadFile(wgPath)
				if err == nil {
					fixedWg := preProcessConfig(string(wgBytes))
					tmpWgPath = filepath.Join(cacheDir, "fixed_wg.conf")
					err = os.WriteFile(tmpWgPath, []byte(fixedWg), 0600)
					if err == nil {
						lines[i] = fmt.Sprintf("WGConfig = %s", tmpWgPath)
					}
				}
			}
		}
	}
	
	finalConfigStr := strings.Join(lines, "\n")
	if tmpWgPath == "" {
		finalConfigStr = preProcessConfig(finalConfigStr)
	}

	tmpConf := filepath.Join(cacheDir, "current.conf")
	err := os.WriteFile(tmpConf, []byte(finalConfigStr), 0600)
	if err != nil {
		return fmt.Errorf("failed to write config: %v", err)
	}
	defer os.Remove(tmpConf)

	conf, err := wireproxy.ParseConfig(tmpConf)
	if err != nil {
		if tmpWgPath != "" { os.Remove(tmpWgPath) }
		return fmt.Errorf("config parse error: %v", err)
	}

	tun, err := wireproxy.StartWireguard(conf.Device, device.LogLevelVerbose)
	if err != nil {
		if tmpWgPath != "" { os.Remove(tmpWgPath) }
		return fmt.Errorf("wireguard start error: %v", err)
	}
	if tmpWgPath != "" { os.Remove(tmpWgPath) }

	stateMu.Lock()
	activeTun = tun
	ctx, cancel := context.WithCancel(context.Background())
	cancelFunc = cancel

	// Поднимаем СВОЙ управляемый SOCKS5
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

	// Поднимаем СВОЙ управляемый HTTP
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

	tun.StartPingIPs()

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

	// Аккуратно закрываем слушателей, порты освобождаются!
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