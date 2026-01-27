# HTTP MCP Server

Простой HTTP MCP сервер на Python для тестирования Android приложения.

## Установка

```bash
cd mcp_server
pip install -r requirements.txt
```

## Запуск

```bash
python http_mcp_server.py
```

Сервер запустится на `http://0.0.0.0:8080`

## Доступные инструменты

1. **get_system_info** - информация о системе (OS, CPU, память)
2. **get_current_time** - текущее время
3. **list_directory** - список файлов в директории
4. **calculate** - вычисление математических выражений
5. **get_weather_mock** - mock данные погоды

## Подключение из Android

### Эмулятор
```kotlin
McpClient(
    serverUrl = "http://10.0.2.2:8080",
    useMockServer = false
)
```

### Реальное устройство
Узнайте IP вашего компьютера:
```bash
# macOS/Linux
ifconfig | grep "inet "

# Windows
ipconfig
```

Затем используйте:
```kotlin
McpClient(
    serverUrl = "http://192.168.x.x:8080",  // ваш IP
    useMockServer = false
)
```

## Тестирование через curl

```bash
# Initialize
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

# List tools
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'

# Call tool
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_system_info","arguments":{}}}'
```

## Логи

Сервер выводит все запросы в консоль с временными метками.

## Остановка

Нажмите `Ctrl+C`
