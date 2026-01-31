#!/bin/bash

# Скрипт запуска MCP сервера

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$SCRIPT_DIR/server.pid"
LOG_FILE="$SCRIPT_DIR/server.log"

# Проверка, не запущен ли уже сервер
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "❌ Сервер уже запущен (PID: $PID)"
        echo "   Используйте ./stop_server.sh для остановки"
        exit 1
    else
        echo "⚠️  Найден старый PID файл, удаляем..."
        rm -f "$PID_FILE"
    fi
fi

# Запуск сервера
echo "🚀 Запуск MCP сервера..."
cd "$SCRIPT_DIR"
nohup python3 -u http_mcp_server.py > "$LOG_FILE" 2>&1 &
SERVER_PID=$!

# Сохранение PID
echo $SERVER_PID > "$PID_FILE"

# Ожидание запуска
sleep 2

# Проверка, что сервер запустился
if ps -p "$SERVER_PID" > /dev/null 2>&1; then
    echo "✅ Сервер успешно запущен!"
    echo ""
    echo "📋 Информация:"
    echo "   PID: $SERVER_PID"
    echo "   Порт: 8080"
    echo "   Логи: $LOG_FILE"
    echo ""
    echo "🔗 Адреса:"
    echo "   Локально: http://localhost:8080"
    echo "   Эмулятор: http://10.0.2.2:8080"
    echo ""
    echo "💡 Команды:"
    echo "   Остановить: ./stop_server.sh"
    echo "   Статус: ./status_server.sh"
    echo "   Логи: tail -f server.log"
else
    echo "❌ Ошибка запуска сервера!"
    echo "   Проверьте логи: $LOG_FILE"
    rm -f "$PID_FILE"
    exit 1
fi
