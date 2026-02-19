# Localhost Development Setup

## ✅ Current Configuration

**Mode**: Localhost development (Option B)
**Ollama Server**: Running on your development machine
**App Connection**: Via Android emulator

---

## 🔧 Configuration

### SecureData.kt (Current)

```kotlin
const val SERVER_IP = "10.0.2.2"   // Android emulator → host machine
const val SERVER_PORT = 11434      // Ollama default port
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
{"version":"0.16.2"}
```

### 2. Verify llama2 Model

```bash
# Check if llama2 is available
ollama list

# If not available, pull it:
ollama pull llama2
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
2. You should see: "Привет! Я AI ассистент на основе llama2..."
3. Type: "Hello"
4. Click Send
5. Wait ~20 seconds for first response
6. Subsequent messages will be faster (~2-5 seconds)

---

## 📱 Device-Specific Setup

### Android Emulator (RECOMMENDED)
✅ **Already configured!**
- Uses `10.0.2.2` → Points to host machine
- No additional setup needed

### Physical Android Device (if needed)

If you want to test on a real device instead:

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
   const val SERVER_IP = "192.168.1.100"  // Your actual IP
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

Expected: `{"version":"0.16.2"}`

---

### Issue 3: First response very slow

**This is normal!**
- First request: ~20 seconds (model loads from disk to RAM)
- Subsequent requests: ~2-5 seconds (model stays in RAM)

Our `keep_alive: "5m"` setting keeps the model loaded for 5 minutes.

To pre-warm the model:
```bash
curl http://localhost:11434/api/chat -d '{
  "model": "llama2",
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
- First request: 10-15 seconds
- Subsequent: 1-3 seconds
- Tokens/second: 15-20

**Average Hardware** (8GB RAM, Older CPU):
- First request: 20-30 seconds
- Subsequent: 3-5 seconds
- Tokens/second: 10-15

**Limited Hardware** (< 8GB RAM):
- First request: 30-60 seconds
- Subsequent: 5-10 seconds
- May need smaller model: `ollama pull llama2:7b-chat-q2_K`

---

## 🔄 Switching to Remote Server Later

If you want to deploy to a remote server later:

1. **Install Ollama on server** (e.g., 148.253.209.151)
2. **Configure network access**:
   ```bash
   export OLLAMA_HOST=0.0.0.0:11434
   ollama serve
   ```

3. **Update SecureData.kt**:
   ```kotlin
   const val SERVER_IP = "148.253.209.151"
   ```

4. **Open firewall**:
   ```bash
   sudo ufw allow 11434
   ```

5. **Rebuild and test**

---

## ✅ Verification Checklist

Before running the app:

- [ ] Ollama running on your machine (`ollama serve`)
- [ ] llama2 model downloaded (`ollama list`)
- [ ] SecureData.kt set to `"10.0.2.2"`
- [ ] Using Android **emulator** (not physical device)
- [ ] App built successfully (`./gradlew assembleDebug`)
- [ ] Installed to emulator

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
# Use emulator, not physical device
```

**App Configuration**:
- ✅ SecureData.kt → `SERVER_IP = "10.0.2.2"`
- ✅ Device → Android Emulator
- ✅ Network → Localhost (your machine)

---

**Last Updated**: February 19, 2026
**Mode**: Localhost Development (Option B)
**Status**: ✅ Ready to Run
