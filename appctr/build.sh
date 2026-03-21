#!/bin/bash
set -e

echo "--- Toolchain Check ---"
echo "Go version: $(go version)"

# Удаляем старый артефакт, чтобы не было путаницы
rm -f appctr.aar

# Обновляем зависимости
go mod tidy

echo "Building AAR for all architectures (Arm + x86)..."
# Собираем под arm (v7), arm64, 386 (x86) и amd64 (x86_64)
# Используем -androidapi 24, так как это твой minSdk
gomobile bind -target=android/arm,android/arm64,android/386,android/amd64 -androidapi 24 -v -o appctr.aar .

if [ -f "appctr.aar" ]; then
    echo "------------------------------------------------"
    echo "🔥 SUCCESS! appctr.aar is ready (Multi-arch)"
    echo "------------------------------------------------"
else
    echo "❌ Build failed"
    exit 1
fi