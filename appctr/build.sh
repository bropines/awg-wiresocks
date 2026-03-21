#!/bin/bash
set -e

# Настройка окружения
source ~/.bashrc

echo "--- Toolchain Check ---"
echo "Go version: $(go version)"
echo "NDK path: $ANDROID_NDK_HOME"

# Удаляем старый артефакт
rm -f appctr.aar

# Обновляем зависимости
go mod tidy

echo "Building AAR (Global Cache Mode)..."
# Собираем БЕЗ флага -x (чтобы меньше мусора в логах), но с -v
gomobile bind -target=android/arm,android/arm64,android/386,android/amd64 -androidapi 24 -v -o appctr.aar .

if [ -f "appctr.aar" ]; then
    echo "------------------------------------------------"
    echo "🔥 SUCCESS! appctr.aar is ready"
    echo "------------------------------------------------"
else
    echo "❌ Build failed"
    exit 1
fi