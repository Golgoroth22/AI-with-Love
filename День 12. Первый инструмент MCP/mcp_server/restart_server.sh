#!/bin/bash

# Скрипт перезапуска MCP сервера

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🔄 Перезапуск MCP сервера..."
echo ""

# Остановка сервера
"$SCRIPT_DIR/stop_server.sh"

# Пауза
sleep 2

# Запуск сервера
"$SCRIPT_DIR/start_server.sh"
