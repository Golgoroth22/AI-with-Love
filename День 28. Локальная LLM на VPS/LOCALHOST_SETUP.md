# Localhost Development Setup

## ✅ Current Configuration

**Mode**: Remote VPS server (currently configured)
**Ollama Server**: Running on remote VPS (`148.253.209.151:11434`)
**App Connection**: Via HTTP over network

> **Note**: The current `SecureData.kt` points to a remote VPS. To switch to localhost development, update `REMOTE_SERVER_IP` to `10.0.2.2` (emulator) or your machine's local IP (physical device).

---

## 🔧 Configuration

### SecureData.kt (Current — Remote VPS)

```kotlin
const val REMOTE_SERVER_IP = "148.253.209.151"
const val SERVER_PORT = 11434
```

### SecureData.kt (Localhost — Android Emulator)

```kotlin
const val REMOTE_SERVER_IP = "10.0.2.2"  // Android emulator → host machine
const val SERVER_PORT = 11434             // Ollama default port
```

**Why `10.0.2.2`?**
- This is a special Android emulator IP address
- It maps to `localhost` (127.0.0.1) on your host machine
- Allows the emulator to reach services running on your computer

---

## 🚀 How to Run

### 1. Start Ollama on Your Machine

```bash
# Verify Ollama is running
curl http://localhost:11434/api/version

# If not running, start it:
ollama serve
```

**Expected output**:
```json
{"version":"0.x.x"}
```

### 2. Verify gemma:2b Model

```bash
# Check if gemma:2b is available
ollama list

# If not available, pull it:
ollama pull gemma:2b
```

### 3. Build and Run the App

**Option A: Android Studio**
1. Open project in Android Studio
2. Start an **Android Emulator** (not physical device)
3. Click Run (Shift+F10)

**Option B: Command Line**
```bash
# Build APK
./gradlew assembleDebug

# Install to emulator
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Test the App

1. Launch app in emulator
2. You should see: "Привет! Я AI ассистент на основе gemma:2b..."
3. Type: "Hello"
4. Click Send
5. Wait for first response (time depends on hardware and model)
6. Subsequent messages will be faster (model stays in RAM via `keep_alive`)

---

## 📱 Device-Specific Setup

### Android Emulator (RECOMMENDED for localhost)
Update `SecureData.kt`:
```kotlin
const val REMOTE_SERVER_IP = "10.0.2.2"
```
- Points to host machine's localhost
- No additional network setup needed

### Physical Android Device

If you want to test on a real device:

1. **Find your machine's IP address**:
   ```bash
   # Mac/Linux
   ifconfig | grep "inet " | grep -v 127.0.0.1

   # Windows
   ipconfig
   ```
   Example output: `192.168.1.100`

2. **Update SecureData.kt**:
   ```kotlin
   const val REMOTE_SERVER_IP = "192.168.1.100"  // Your actual IP
   ```

3. **Ensure device is on same WiFi**
   - Connect phone to same network as your computer
   - No firewall blocking port 11434

4. **Rebuild and install**:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🧪 Troubleshooting

### Issue 1: "Failed to connect to Ollama server"

**Check Ollama is running**:
```bash
curl http://localhost:11434/api/version
```

If not running:
```bash
ollama serve
```

---

### Issue 2: Emulator can't connect

**Verify you're using emulator, not physical device**:
```bash
adb devices
```

Should show something like:
```
emulator-5554   device
```

**Test connection from emulator**:
```bash
adb shell curl http://10.0.2.2:11434/api/version
```

Expected: `{"version":"0.x.x"}`

---

### Issue 3: First response very slow

**This is normal!**
- First request: model loads from disk to RAM
- Subsequent requests: faster (model stays in RAM via `keep_alive: "5m"`)

To pre-warm the model:
```bash
curl http://localhost:11434/api/chat -d '{
  "model": "gemma:2b",
  "messages": [{"role": "user", "content": "hi"}],
  "stream": false
}'
```

---

### Issue 4: App crashes on startup

**Check Logcat**:
```bash
adb logcat | grep -i "aiwithlove"
```

Common issues:
- Koin not initialized → Check MainActivity
- OllamaClient error → Check SecureData.kt IP address

---

## 📊 Performance Expectations

### Localhost (Your Machine)

Depends on your hardware:

**Good Hardware** (16GB+ RAM, Modern CPU):
- First request: 5-15 seconds
- Subsequent: 1-3 seconds

**Average Hardware** (8GB RAM, Older CPU):
- First request: 15-25 seconds
- Subsequent: 3-8 seconds

**gemma:2b is faster than llama2** (smaller model, ~1.5GB):
- Requires less RAM
- Faster inference than 7B models

---

## 🔄 Switching to Remote Server

If you want to use a remote server:

1. **Install Ollama on server** and expose on network:
   ```bash
   export OLLAMA_HOST=0.0.0.0:11434
   ollama serve
   ```

2. **Update SecureData.kt**:
   ```kotlin
   const val REMOTE_SERVER_IP = "your-server-ip"
   ```

3. **Open firewall**:
   ```bash
   sudo ufw allow 11434
   ```

4. **Rebuild and test**

---

## ✅ Verification Checklist

Before running the app:

- [ ] Ollama running on your machine (`ollama serve`)
- [ ] gemma:2b model downloaded (`ollama list`)
- [ ] SecureData.kt `REMOTE_SERVER_IP` set correctly
- [ ] Using Android **emulator** or device on same network
- [ ] App built successfully (`./gradlew assembleDebug`)
- [ ] Installed to emulator/device

---

## 🎯 Quick Start (TL;DR)

```bash
# 1. Start Ollama
ollama serve

# 2. Verify
curl http://localhost:11434/api/version

# 3. Build app
./gradlew assembleDebug

# 4. Run in Android Studio
# Use emulator with SecureData.kt → REMOTE_SERVER_IP = "10.0.2.2"
```

---

**Last Updated**: February 23, 2026
**Current Mode**: Remote VPS (`148.253.209.151`) — update SecureData.kt for localhost
**Status**: ✅ Ready to Run
