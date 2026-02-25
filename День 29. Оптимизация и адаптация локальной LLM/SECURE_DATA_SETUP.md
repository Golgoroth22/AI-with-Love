# Secure Data Configuration

All sensitive configuration is in `SecureData.kt`, excluded from version control.

## File Structure

```
app/src/main/java/com/example/aiwithlove/util/
├── SecureData.kt           (gitignored — contains server config)
├── SecureData.kt.example   (committed — template)
└── ServerConfig.kt         (committed — exposes OLLAMA_SERVER_URL)
```

## Setup for New Developers

1. Copy the example file:
   ```bash
   cd app/src/main/java/com/example/aiwithlove/util/
   cp SecureData.kt.example SecureData.kt
   ```

2. Edit `SecureData.kt`:
   - **Android Emulator**: `SERVER_IP = "10.0.2.2"` (routes to host localhost)
   - **Physical Device**: your machine's local IP (e.g. `"192.168.1.100"`)
   - **Remote Server**: server's public IP
   - Port: `11434` (Ollama default)

3. Verify it's gitignored:
   ```bash
   git status   # SecureData.kt should NOT appear
   ```

## SecureData.kt Template

```kotlin
object SecureData {
    const val SERVER_IP = "10.0.2.2"
    const val SERVER_PORT = 11434
    val OLLAMA_SERVER_URL: String
        get() = "http://$SERVER_IP:$SERVER_PORT"
}
```

## Where It's Used

`ServerConfig.OLLAMA_SERVER_URL` is read by `AppModule.kt` when constructing `OllamaClient`.

## Updating the Server

Edit `SecureData.kt` and rebuild. `ServerConfig` picks it up automatically — no other files need changing.

## Security Notes

- `SecureData.kt` is in `.gitignore` and will never be committed
- `ServerConfig.kt` only exposes `OLLAMA_SERVER_URL` — safe to commit
- Ollama has no built-in authentication — use VPN or firewall for remote access
- `network_security_config.xml` allows cleartext HTTP to localhost (required for Android 9+)
