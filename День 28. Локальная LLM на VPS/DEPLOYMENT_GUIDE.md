# Ollama Deployment and Testing Guide

## Prerequisites

- Linux/Mac/Windows machine for Ollama server (or remote VPS)
- Android Studio installed locally
- Android device or emulator
- Network connectivity between Android app and Ollama server

## Part 1: Ollama Server Setup

### Step 1: Install Ollama

#### Linux/Mac
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

#### Windows
Download installer from https://ollama.com/download

### Step 2: Pull gemma:2b model

```bash
ollama pull gemma:2b
```

This will download the gemma:2b model (~1.5GB). Wait for it to complete.

> You can also use other models. See **Part 4: Model Selection** below.

### Step 3: Verify Ollama is running

```bash
# Check version
curl http://localhost:11434/api/version

# Expected output:
# {"version":"0.x.x"}
```

### Step 4: Configure Ollama for network access (Optional)

If you want to access Ollama from a remote device (not localhost):

#### Linux (systemd)
```bash
# Edit service file
sudo systemctl edit ollama.service

# Add these lines in the editor:
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"

# Save and exit, then restart
sudo systemctl daemon-reload
sudo systemctl restart ollama
```

#### Mac
```bash
# Set environment variable in ~/.zshrc or ~/.bashrc
export OLLAMA_HOST=0.0.0.0:11434

# Restart Ollama
pkill ollama
ollama serve
```

#### Windows
```powershell
# Set environment variable
$env:OLLAMA_HOST="0.0.0.0:11434"

# Restart Ollama service
Restart-Service Ollama
```

### Step 5: Test Ollama Chat API

```bash
curl http://localhost:11434/api/chat -d '{
  "model": "gemma:2b",
  "messages": [
    {"role": "user", "content": "Hello!"}
  ],
  "stream": false
}'
```

Expected response:
```json
{
  "model": "gemma:2b",
  "created_at": "2024-XX-XXTXX:XX:XX.XXXXXXZ",
  "message": {
    "role": "assistant",
    "content": "Hello! How can I help you today?"
  },
  "done": true
}
```

## Part 2: Android App Configuration

### Step 1: Update SecureData.kt

Edit `app/src/main/java/com/example/aiwithlove/util/SecureData.kt`:

```kotlin
object SecureData {
    // For local testing on emulator
    const val REMOTE_SERVER_IP = "10.0.2.2"  // Android emulator to host machine
    const val SERVER_PORT = 11434

    // For physical device on same network
    // const val REMOTE_SERVER_IP = "192.168.1.100"  // Your machine's IP

    // For remote VPS server
    // const val REMOTE_SERVER_IP = "148.253.209.151"  // VPS public IP

    val OLLAMA_SERVER_URL: String
        get() = "http://$REMOTE_SERVER_IP:$SERVER_PORT"
}
```

**Note**:
- `10.0.2.2` - Use this for Android emulator to access host machine
- `192.168.x.x` - Use your local network IP for physical devices
- Public IP - Use for remote VPS servers (ensure firewall allows port 11434)

### Step 2: Verify Network Security Config

**IMPORTANT**: Android 9+ blocks cleartext HTTP by default. Ensure `app/src/main/res/xml/network_security_config.xml` exists and allows HTTP to your server:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">127.0.0.1</domain>
        <!-- Add your server IP if using physical device or VPS -->
        <domain includeSubdomains="true">148.253.209.151</domain>
    </domain-config>
</network-security-config>
```

And verify it's referenced in `AndroidManifest.xml`:
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

### Step 3: Sync Gradle

1. Open Android Studio
2. File → Sync Project with Gradle Files
3. Wait for sync to complete

### Step 4: Build the app

```bash
./gradlew assembleDebug
```

Or in Android Studio: Build → Make Project

## Part 3: Testing

### Test 1: Basic Chat

1. Launch the app
2. You should see: "Привет! Я AI ассистент на основе gemma:2b..."
3. Type: "Hello"
4. Click send button
5. Wait for AI response (may take 10-30 seconds on first request)
6. Verify you receive a response from gemma:2b

### Test 2: Conversation Context

1. Ask: "What is 2+2?"
2. Wait for response: "4"
3. Ask: "What was my previous question?"
4. Verify the AI remembers the context

### Test 3: Long Response

1. Ask: "Explain how a computer works in detail"
2. Wait for lengthy response
3. Verify entire response is displayed

### Test 4: Unicode/Emoji

1. Type: "Tell me about 🚀 rockets"
2. Verify emojis display correctly in both directions

### Test 5: Error Handling

**Network error test:**
1. Disconnect WiFi / stop Ollama
2. Send a message
3. Verify error message appears: "❌ Ошибка: ... Проверьте подключение к Ollama серверу"

**Wrong server test:**
1. Change `REMOTE_SERVER_IP` to invalid address
2. Rebuild app
3. Send message
4. Verify connection error

### Test 6: Clear Chat

1. Have a conversation (3-4 messages)
2. Click "Новый чат" button
3. Verify chat history clears
4. Verify welcome message reappears
5. Start new conversation to verify context is reset

## Part 4: Model Selection

### Current Model: gemma:2b

The app is configured to use `gemma:2b` by default. This is set in `di/AppModule.kt`:

```kotlin
single {
    OllamaClient(
        serverUrl = ServerConfig.OLLAMA_SERVER_URL,
        modelName = "gemma:2b"
    )
}
```

### Available Models

```bash
# Pull different models
ollama pull gemma:2b      # Current - fast, ~1.5GB
ollama pull llama3        # More capable, ~4GB
ollama pull mistral       # Balanced speed/quality
ollama pull codellama     # Better for programming
ollama pull phi3          # Small and fast
```

To switch models: update `modelName` in `di/AppModule.kt` and ensure the model is pulled on the server.

## Part 5: Performance Optimization

### Check Ollama Model Status

```bash
# List loaded models
curl http://localhost:11434/api/tags

# Check model details
ollama show gemma:2b
```

### Reduce Response Time

**Option 1: Keep model loaded (already configured)**

The app sends `keep_alive: "5m"` with every request. This keeps the model in RAM for 5 minutes after the last request.

**Option 2: Pre-warm the model**
```bash
# Send a dummy request to preload model
curl http://localhost:11434/api/chat -d '{
  "model": "gemma:2b",
  "messages": [{"role": "user", "content": "hi"}],
  "stream": false
}'
```

**Option 3: Use a smaller model**
```bash
# gemma:2b is already quite small
# For even faster inference:
ollama pull phi3:mini
```

## Troubleshooting

### Issue: "Connection refused" error

**Solution:**
```bash
# Check if Ollama is running
ps aux | grep ollama

# Start Ollama if not running
ollama serve

# Check port is open
lsof -i :11434  # Mac/Linux
netstat -ano | findstr :11434  # Windows
```

### Issue: "Model not found"

**Solution:**
```bash
# List available models
ollama list

# Pull gemma:2b if missing
ollama pull gemma:2b
```

### Issue: App times out waiting for response

**Possible causes:**
1. Model is loading for the first time (wait up to 1 minute)
2. Hardware is too slow (consider smaller model)
3. Network timeout too short

The app has a 5-minute timeout configured in `OllamaClient.kt`. This should be sufficient for most cases.

### Issue: Can't connect from physical device

**Solution:**
1. Find your machine's IP:
   ```bash
   # Mac/Linux
   ifconfig | grep "inet "

   # Windows
   ipconfig
   ```

2. Update `SecureData.kt` with your IP

3. Ensure firewall allows port 11434:
   ```bash
   # Mac
   sudo /usr/libexec/ApplicationFirewall/socketfilterfw --add /usr/local/bin/ollama

   # Linux
   sudo ufw allow 11434

   # Windows - Add firewall rule in Windows Defender
   ```

### Issue: Android emulator can't reach localhost

**Solution:**
Use `10.0.2.2` instead of `localhost` in `SecureData.kt`:
```kotlin
const val REMOTE_SERVER_IP = "10.0.2.2"
```

### Issue: Slow responses

**Check system resources:**
```bash
# Monitor CPU/RAM while Ollama runs
top  # Mac/Linux
# Or use Task Manager on Windows
```

gemma:2b requires:
- **RAM**: ~2GB (much less than llama2's 8GB)
- **CPU**: Any modern multi-core CPU

### Issue: "Cleartext HTTP traffic not permitted"

**Cause**: Android 9+ blocks HTTP by default

**Solution**: Verify `network_security_config.xml` is configured and includes your server IP (see Part 2, Step 2 above)

### Issue: Empty or malformed responses

**Cause**: Ollama may return NDJSON format instead of JSON

**Solution**: Already handled by `OllamaClient.parseOllamaResponse()` — if you see issues, check logcat for parsing errors

## Success Criteria Checklist

- [ ] Ollama installed and running
- [ ] gemma:2b model downloaded
- [ ] API version endpoint responds
- [ ] Test chat request succeeds
- [ ] Android app builds without errors
- [ ] App connects to Ollama
- [ ] User can send messages
- [ ] AI responses appear in chat
- [ ] Conversation context maintained
- [ ] Clear chat works
- [ ] Error handling works (network errors)

## Next Steps

After successful deployment:

1. **Explore other models**:
   ```bash
   ollama pull llama3
   ollama pull mistral
   ollama pull codellama
   ```

2. **Enable streaming** for real-time responses:
   - Modify `OllamaClient.kt` to handle streaming
   - Update UI to show incremental text

3. **Add model selection** in app:
   - Create settings screen
   - Allow users to choose between models

4. **Optimize for mobile**:
   - Cache responses locally
   - Implement request queuing
   - Add retry logic

5. **Privacy enhancements**:
   - All data stays local
   - Add option to disable conversation history
   - Implement auto-clear after X minutes
