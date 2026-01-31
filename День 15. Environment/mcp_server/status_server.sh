#!/bin/bash

# Скрипт проверки статуса MCP сервера

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$SCRIPT_DIR/server.pid"
LOG_FILE="$SCRIPT_DIR/server.log"

echo "═══════════════════════════════════════════════════════════"
echo "           📊 Статус MCP Сервера"
echo "═══════════════════════════════════════════════════════════"
echo ""

# Проверка PID файла
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    echo "📄 PID файл: найден (PID: $PID)"
    
    # Проверка процесса
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "✅ Статус: ЗАПУЩЕН"
        echo ""
        
        # Информация о процессе
        echo "🔍 Информация о процессе:"
        ps -p "$PID" -o pid,ppid,user,%cpu,%mem,etime,command | tail -1
        echo ""
        
        # Проверка порта
        PORT_INFO=$(lsof -i:8080 -P 2>/dev/null | grep LISTEN)
        if [ -n "$PORT_INFO" ]; then
            echo "🌐 Порт 8080: ПРОСЛУШИВАЕТСЯ"
            echo "$PORT_INFO" | awk '{print "   "$1" "$2" "$9}'
        else
            echo "⚠️  Порт 8080: НЕ ПРОСЛУШИВАЕТСЯ"
        fi
        echo ""
        
        # Тест подключения
        echo "🧪 Тест подключения:"
        TEST_RESULT=$(curl -s -X POST http://localhost:8080 \
            -H "Content-Type: application/json" \
            -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' \
            -w "\n%{http_code}" 2>/dev/null)
        
        HTTP_CODE=$(echo "$TEST_RESULT" | tail -1)
        if [ "$HTTP_CODE" = "200" ]; then
            echo "   ✅ Сервер отвечает (HTTP $HTTP_CODE)"
        else
            echo "   ❌ Сервер не отвечает (HTTP $HTTP_CODE)"
        fi
        echo ""
        
        # Последние логи
        if [ -f "$LOG_FILE" ]; then
            echo "📝 Последние 5 строк лога:"
            tail -5 "$LOG_FILE" | sed 's/^/   /'
        fi
        
    else
        echo "❌ Статус: НЕ ЗАПУЩЕН (процесс не найден)"
        echo "   Удалите PID файл: rm $PID_FILE"
    fi
else
    echo "📄 PID файл: не найден"
    echo "❌ Статус: НЕ ЗАПУЩЕН"
    echo ""
    
    # Поиск процесса на порту
    PORT_CHECK=$(lsof -ti:8080 2>/dev/null | head -1)
    if [ -n "$PORT_CHECK" ]; then
        echo "⚠️  Внимание: Найден процесс на порту 8080 (PID: $PORT_CHECK)"
        echo "   Возможно, сервер запущен вручную"
    fi
fi

echo ""
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "💡 Доступные команды:"
echo "   Запустить: ./start_server.sh"
echo "   Остановить: ./stop_server.sh"
echo "   Логи: tail -f server.log"
echo "   Очистить логи: > server.log"
echo ""
