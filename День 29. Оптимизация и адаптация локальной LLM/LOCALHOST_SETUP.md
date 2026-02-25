# Localhost Development Setup

**Mode**: Localhost development
**Ollama Server**: Running on your development machine
**Model**: llama3.2:3b

---

## Configuration

### SecureData.kt

```kotlin
const val SERVER_IP = "10.0.2.2"   // Android emulator → host machine
const val SERVER_PORT = 11434
val OLLAMA_SERVER_URL = "http://$SERVER_IP:$SERVER_PORT"
```

`10.0.2.2` is a special Android emulator address that maps to `localhost` on the host machine.

---

## How to Run

### 1. Start Ollama

```bash
curl http://localhost:11434/api/version
# If not running:
ollama serve
```

### 2. Verify model

```bash
ollama list
# If llama3.2:3b is missing:
ollama pull llama3.2:3b
```

### 3. Build and run

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or run directly from Android Studio with an emulator.

### 4. First launch

1. App shows Yuki's Russian greeting
2. Type a question about Japan in Russian
3. On first message: ML Kit downloads translation models (~10 MB each, one-time)
4. Subsequent messages translate instantly and work offline

---

## Device Setup

### Android Emulator (recommended)

Already configured — `10.0.2.2` points to host machine. No changes needed.

### Physical Device

1. Find your machine's IP:
   ```bash
   ifconfig | grep "inet "   # Mac/Linux
   ipconfig                  # Windows
   ```

2. Update `SecureData.kt`:
   ```kotlin
   const val SERVER_IP = "192.168.1.100"   // your actual IP
   ```

3. Ensure device and machine are on the same WiFi network.

4. Rebuild and install.

---

## Troubleshooting

### "Failed to connect to Ollama server"

```bash
curl http://localhost:11434/api/version
# Start if needed:
ollama serve
```

### Emulator can't connect

```bash
adb devices                                         # should show emulator-XXXX
adb shell curl http://10.0.2.2:11434/api/version   # should return version JSON
```

### First response is slow

Normal — model cold start (~20s). `keep_alive: "5m"` keeps it warm for 5 minutes after.

### Translation models not downloading

ML Kit requires internet for the initial ~10 MB download per direction. After that, works fully offline.

---

## Performance Expectations

| Hardware | First request | Subsequent |
|----------|--------------|------------|
| 16GB+ RAM, modern CPU | 10-15s | 1-3s |
| 8GB RAM | 20-30s | 3-5s |
| < 8GB RAM | 30-60s | 5-10s |

---

## Quick Start

```bash
ollama serve
curl http://localhost:11434/api/version
./gradlew assembleDebug
# Run on emulator from Android Studio
```

**Last Updated**: February 25, 2026
