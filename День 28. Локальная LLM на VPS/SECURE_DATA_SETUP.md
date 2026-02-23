# Secure Data Configuration

## 🔒 Security Setup

All sensitive configuration data has been moved to a separate `SecureData.kt` file that is excluded from version control.

## 📁 File Structure

```
app/src/main/java/com/example/aiwithlove/util/
├── SecureData.kt           ❌ NOT in Git (contains server configuration)
├── SecureData.kt.example   ✅ In Git (template with placeholders)
└── ServerConfig.kt         ✅ In Git (uses SecureData)
```

## 🚀 Setup for New Developers

If you're setting up this project for the first time:

1. **Copy the example file:**
   ```bash
   cd app/src/main/java/com/example/aiwithlove/util/
   cp SecureData.kt.example SecureData.kt
   ```

2. **Edit SecureData.kt:**
   - For **Android Emulator**: Use `REMOTE_SERVER_IP = "10.0.2.2"` (routes to host machine's localhost)
   - For **Physical Device**: Use your machine's local IP (e.g., `"192.168.1.100"`)
   - For **Remote Server / VPS**: Use server's public IP address (e.g., `"148.253.209.151"`)
   - Port should be `11434` (Ollama default)

3. **Verify it's gitignored:**
   ```bash
   git status
   # SecureData.kt should NOT appear in the list
   ```

## 📝 What's in SecureData.kt

```kotlin
object SecureData {
    /**
     * Ollama Server Configuration
     *
     * DEVELOPMENT SETUP (localhost):
     * - Android Emulator: Use "10.0.2.2" (special emulator IP to reach host machine)
     * - Physical Device: Use your machine's local network IP (e.g., "192.168.1.100")
     *
     * PRODUCTION SETUP (remote VPS):
     * - Use server's public IP address
     *
     * Default Ollama port is 11434
     */
    const val REMOTE_SERVER_IP = "your-server-ip-here"
    const val SERVER_PORT = 11434

    /**
     * Derived URL
     */
    val OLLAMA_SERVER_URL: String
        get() = "http://$REMOTE_SERVER_IP:$SERVER_PORT"
}
```

> **Note**: The actual `SecureData.kt` does not have a `SERVER_IP` constant — it uses `REMOTE_SERVER_IP`. `ServerConfig.kt` exposes `SERVER_IP` as a getter that delegates to `SecureData.REMOTE_SERVER_IP` for compatibility.

## 🔐 Security Notes

1. **SecureData.kt** is listed in `.gitignore` and will NEVER be committed
2. **SecureData.kt.example** is a template and safe to commit
3. **ServerConfig.kt** references SecureData and is safe to commit
4. **network_security_config.xml** - Allows cleartext HTTP for configured IPs (required for Android 9+)
5. **No authentication needed** - Ollama has no built-in authentication (use VPN/firewall for remote access)

## ⚠️ Important Warnings

- **NEVER** commit `SecureData.kt` to Git
- **NEVER** share `SecureData.kt` publicly
- **ALWAYS** use `SecureData.kt.example` as a template
- If you accidentally commit sensitive data:
  1. Remove it immediately from Git history
  2. Rotate/change all exposed credentials
  3. Update server security

## 🔍 Verification

Check if SecureData.kt is properly excluded:

```bash
# Should return empty (file is ignored)
git status | grep SecureData.kt

# Example file should be tracked
git status | grep SecureData.kt.example
```

## 📦 Where Secure Data is Used

The app uses `ServerConfig.OLLAMA_SERVER_URL` throughout the codebase:

- **AppModule.kt** - Passes server URL to `OllamaClient` constructor (DI)
- **OllamaClient.kt** - Receives `serverUrl` as constructor parameter

## 🛠️ Updating Server Configuration

To change the server IP/port:

1. Edit `SecureData.kt` (NOT the example file)
2. Rebuild the app
3. The changes will apply immediately

No need to update multiple files!
