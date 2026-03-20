package appctr

import (
	"bufio"
	"context"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/device"
	wireproxy "github.com/artem-russkikh/wireproxy-awg".
		_ "golang.org/x/mobile/bind"
)

// --- СИСТЕМА ЛОГОВ (НЕБЛОКИРУЮЩАЯ) ---
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
// Ядро AWG требует Base64, а в конфигах лежит HEX. Фиксим на лету.
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
				// Ищем паттерн <b 0x...>
				if strings.HasPrefix(val, "<b 0x") && strings.HasSuffix(val, ">") {
					hexStr := val[5 : len(val)-1]
					b, err := hex.DecodeString(hexStr)
					if err == nil {
						b64Str := base64.StdEncoding.EncodeToString(b)
						lines[i] = fmt.Sprintf("%s = %s", key, b64Str)
						AddLog("CORE", fmt.Sprintf("Auto-fixed %s: converted HEX to Base64", key))
					}
				}
			}
		}
	}
	return strings.Join(lines, "\n")
}

// --- УПРАВЛЕНИЕ ЯДРОМ ---
var (
	stateMu    sync.Mutex
	activeTun  *wireproxy.VirtualTun
	cancelFunc context.CancelFunc
)

func IsRunning() bool {
	stateMu.Lock()
	defer stateMu.Unlock()
	return activeTun != nil
}

func Start(configStr string, cacheDir string) error {
	stateMu.Lock()
	if activeTun != nil {
		if cancelFunc != nil {
			cancelFunc()
		}
		if activeTun.Dev != nil {
			activeTun.Dev.Close()
		}
		activeTun = nil
	}
	stateMu.Unlock()

	redirectGlobalLogs()

	// Применяем нашу магию к конфигу перед стартом
	fixedConfig := preProcessConfig(configStr)

	tmpConf := filepath.Join(cacheDir, "current.conf")
	err := os.WriteFile(tmpConf, []byte(fixedConfig), 0600)
	if err != nil {
		return fmt.Errorf("failed to write config: %v", err)
	}
	defer os.Remove(tmpConf)

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
	stateMu.Unlock()

	for _, spawner := range conf.Routines {
		go spawner.SpawnRoutine(tun)
	}
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
	if cancelFunc != nil {
		cancelFunc()
		cancelFunc = nil
		select {
		case stopLogRedirect <- struct{}{}:
		default:
		}
	}
}