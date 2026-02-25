# Ollama Deployment and Testing Guide

## Prerequisites

- Linux/Mac/Windows machine for Ollama server
- Android Studio
- Android device or emulator

## Part 1: Ollama Server Setup

### Install Ollama

```bash
# Linux/Mac
curl -fsSL https://ollama.com/install.sh | sh

# Windows — download from https://ollama.com/download
```

### Pull model

```bash
ollama pull llama3.2:3b
```

### Verify

```bash
curl http://localhost:11434/api/version
# Expected: {"version":"x.x.x"}
```

### Configure for remote access (optional)

#### Linux (systemd)
```bash
sudo systemctl edit ollama.service
# Add:
# [Service]
# Environment="OLLAMA_HOST=0.0.0.0:11434"
sudo systemctl daemon-reload && sudo systemctl restart ollama
```

#### Mac
```bash
export OLLAMA_HOST=0.0.0.0:11434
pkill ollama && ollama serve
```

### Test chat API

```bash
curl http://localhost:11434/api/chat -d '{
  "model": "llama3.2:3b",
  "messages": [{"role": "user", "content": "Hello!"}],
  "stream": false
}'
```

---

## Part 2: Android App Configuration

### SecureData.kt

```kotlin
object SecureData {
    const val SERVER_IP = "10.0.2.2"   // emulator → host
    const val SERVER_PORT = 11434
    val OLLAMA_SERVER_URL: String get() = "http://$SERVER_IP:$SERVER_PORT"
}
```

Use `192.168.x.x` for physical device on the same network.

### Network Security Config

`app/src/main/res/xml/network_security_config.xml` must allow HTTP to your server IP. Already configured for `10.0.2.2`, `localhost`, `127.0.0.1`.

### Build

```bash
./gradlew assembleDebug
```

---

## Part 3: Testing

### Test 1: Basic Chat

1. Launch app
2. Yuki's Russian greeting appears
3. Type "Что посмотреть в Токио?" and send
4. First message: ML Kit downloads translation models (~10 MB, one-time)
5. Response arrives in Russian

### Test 2: Conversation Context

1. Ask: "Расскажи о Киото"
2. Ask: "А что там есть из еды?"
3. Verify Yuki references Kyoto context

### Test 3: Formatting

1. Ask: "Топ 5 мест в Токио"
2. Verify numbered list appears formatted (not a single line)

### Test 4: Error Handling

1. Disconnect WiFi, send a message
2. Verify: `❌ Ошибка: ... Проверьте подключение к Ollama серверу.`

### Test 5: Clear Chat

1. Have a conversation
2. Tap "Новый чат"
3. Yuki's greeting reappears, context resets

---

## Part 4: Performance

### Tune response speed

Edit `OllamaOptions` in `AppModule.kt`:

```kotlin
OllamaOptions(
    temperature = 0.7,
    num_predict = 512,    // reduce for faster, shorter replies
    num_ctx = 4096,
    top_p = 0.9,
    repeat_penalty = 1.1
)
```

### Smaller/faster models

```bash
ollama pull llama3.2:1b    # smaller, faster
```

Update `modelName` in `AppModule.kt`.

---

## Troubleshooting

### "Connection refused"
```bash
ollama serve
lsof -i :11434     # Mac/Linux
```

### "Model not found"
```bash
ollama list
ollama pull llama3.2:3b
```

### App times out
Increase `requestTimeoutMillis` in `OllamaClient.kt` (default: 300000 ms = 5 min).

### Can't connect from physical device
1. Get host IP: `ifconfig | grep "inet "`
2. Update `SecureData.kt`
3. Open firewall: `sudo ufw allow 11434`

### Response appears as one long line
`TranslationService.translatePreservingStructure()` handles this by translating line-by-line. If broken, check that method.

---

## Success Criteria

- [ ] Ollama installed and running
- [ ] llama3.2:3b downloaded
- [ ] API version endpoint responds
- [ ] App builds without errors
- [ ] Yuki's Russian greeting appears
- [ ] Russian input → Russian response works
- [ ] Conversation context maintained
- [ ] "Новый чат" clears history
- [ ] Error message shown on network loss
