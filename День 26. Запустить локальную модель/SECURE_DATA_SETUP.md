# Secure Data Configuration

## 🔒 Security Setup

All sensitive configuration data has been moved to a separate `SecureData.kt` file that is excluded from version control.

## 📁 File Structure

```
app/src/main/java/com/example/aiwithlove/util/
├── SecureData.kt           ❌ NOT in Git (contains real credentials)
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
   - Replace `YOUR_SERVER_IP` with actual server IP
   - Update port if different from 8080
   - Add credentials if authentication is enabled

3. **Verify it's gitignored:**
   ```bash
   git status
   # SecureData.kt should NOT appear in the list
   ```

## 📝 What's in SecureData.kt

```kotlin
object SecureData {
    const val SERVER_IP = "YOUR_SERVER_IP"
    const val SERVER_PORT = 8080
    const val SERVER_USERNAME = ""  // Optional
    const val SERVER_PASSWORD = ""  // Optional

    val MCP_SERVER_URL: String
        get() = "http://$SERVER_IP:$SERVER_PORT"
}
```

## 🔐 Security Notes

1. **SecureData.kt** is listed in `.gitignore` and will NEVER be committed
2. **SecureData.kt.example** is a template and safe to commit
3. **ServerConfig.kt** references SecureData and is safe to commit
4. **network_security_config.xml** - Contains server IP for Android's network security policy (acceptable hardcoding)

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

The app uses `ServerConfig.MCP_SERVER_URL` throughout the codebase:

- **McpClient.kt** - HTTP client initialization
- **ChatViewModel.kt** - Server communication
- All MCP tool calls

## 🛠️ Updating Server Configuration

To change the server IP/port:

1. Edit `SecureData.kt` (NOT the example file)
2. Rebuild the app
3. The changes will apply immediately

No need to update multiple files!
