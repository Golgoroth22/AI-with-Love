# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ollama Chat App - An Android application that communicates with a local llama2 model running on Ollama. Users type messages in a chat interface, and the app sends them to Ollama's HTTP API, receiving AI-generated responses.

**Key features**:
- Direct communication with Ollama's REST API
- Local AI model (llama2) - no cloud services needed
- Real-time streaming responses (or single response mode)
- Full chat history maintained in the app

## Build and Run Commands

### Build the app
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Install to device
```bash
./gradlew installDebug
# Or manually:
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Clean build
```bash
./gradlew clean build
```

### Run tests
```bash
# Unit tests
./gradlew test

# Instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

## Architecture: MVVM + Ollama Integration

```
UI Layer (Compose)
  └─ ChatScreen.kt
       └─ Displays messages, handles user input
       └─ Shows AI responses from llama2

ViewModel Layer
  └─ ChatViewModel.kt
       ├─ StateFlow<List<Message>> - message list
       ├─ StateFlow<Boolean> - loading state
       └─ sendMessage() - orchestrates Ollama API call

Data Layer
  └─ OllamaClient.kt
       └─ HTTP client for Ollama REST API (Ktor)

DI Layer (Koin)
  └─ AppModule.kt
       ├─ Provides OllamaClient (singleton)
       └─ Provides ChatViewModel (viewModel)
```

**Initialization**: `MainActivity.onCreate()` calls `startKoin()` before setting content.

## Ollama Communication Pattern

The app uses REST API over HTTP to communicate with Ollama server:

### Request Flow
1. User types text → `ChatViewModel.sendMessage()`
2. ViewModel calls `ollamaClient.chat(messages)`
3. OllamaClient constructs HTTP POST request to `/api/chat`:
   ```json
   {
     "model": "llama2",
     "messages": [
       {"role": "user", "content": "user message here"}
     ],
     "stream": false,
     "keep_alive": "5m"
   }
   ```
   **IMPORTANT**: `keep_alive: "5m"` keeps model loaded in RAM for 5 minutes, improving subsequent response times by 75%+ (2-5s vs 20s cold start)
4. Ollama responds with AI-generated text
5. ViewModel parses response and extracts message content
6. Updates UI with AI response

### Response Parsing Pattern

**CRITICAL**: Ollama can return **NDJSON** (newline-delimited JSON) even when `stream: false` is set!

The `OllamaClient.parseOllamaResponse()` method handles both formats:

**Format 1: Standard JSON** (single response object)
```json
{
  "model": "llama2",
  "created_at": "2023-12-12T14:13:43.416799Z",
  "message": {
    "role": "assistant",
    "content": "AI response text here"
  },
  "done": true
}
```

**Format 2: NDJSON** (multiple lines, each a JSON object - content spread across lines)
```
{"message":{"content":"Hello"},"done":false}
{"message":{"content":" there"},"done":false}
{"message":{"content":"!"},"done":true}
```

**Parsing Implementation**:
- Detects NDJSON by checking for newlines in response body
- For NDJSON: concatenates all `message.content` from each line
- Trims final result to remove leading/trailing whitespace
- Returns last response object with full concatenated content

See `OllamaClient.parseOllamaResponse()` for full implementation.

**API Endpoint**: `http://{SERVER_IP}:11434/api/chat` (default Ollama port is 11434)

## Ollama Server Setup

Ollama should be installed on a server or local machine that the Android app can reach over the network.

### Installation Steps

1. **Install Ollama** (Linux/Mac):
   ```bash
   curl -fsSL https://ollama.com/install.sh | sh
   ```

2. **Pull llama2 model**:
   ```bash
   ollama pull llama2
   ```

3. **Configure Ollama to accept network requests** (if running on remote server):
   ```bash
   # Edit systemd service file
   sudo systemctl edit ollama.service

   # Add environment variable:
   [Service]
   Environment="OLLAMA_HOST=0.0.0.0:11434"

   # Restart service
   sudo systemctl daemon-reload
   sudo systemctl restart ollama
   ```

4. **Test Ollama** is accessible:
   ```bash
   curl http://localhost:11434/api/version
   ```

The server URL is configured in `util/ServerConfig.kt` which references `SecureData.kt` (gitignored).

**Default Ollama port**: 11434
**API endpoint**: `/api/chat` for chat completions

## Key Files and Their Responsibilities

**`viewmodel/ChatViewModel.kt`**:
- Manages chat state (messages, loading)
- Handles Ollama API calls and response parsing
- Maintains conversation history
- Error handling for network/server/parsing failures
- Updates message list reactively

**`ollama/OllamaClient.kt`**:
- Ktor HTTP client for Ollama REST API
- Sends chat requests to `/api/chat` endpoint with `keep_alive` parameter
- Configurable timeouts (5 minutes for AI responses)
- **Parses both JSON and NDJSON responses** (critical for handling Ollama's varied response formats)
- Supports conversation context (full message history)
- Custom exception handling with detailed error messages

**`ui/ChatScreen.kt`**:
- LazyColumn for message list with auto-scroll
- MessageBubble components (user right-aligned, assistant left-aligned)
- Input field with send button
- "Новый чат" button clears history
- Loading indicator during AI response

**`di/AppModule.kt`**:
- Koin module setup
- Single OllamaClient instance (shared across app)
- ViewModel factory for ChatViewModel

**`data/model/Message.kt`**:
- Simple data class: `text`, `isFromUser`
- No database persistence (messages cleared on app restart)

## Testing Strategy

### Ollama Server Testing
Test Ollama server before using the Android app:
```bash
# Check version
curl http://localhost:11434/api/version

# Test chat completion
curl http://localhost:11434/api/chat -d '{
  "model": "llama2",
  "messages": [{"role": "user", "content": "Hello!"}],
  "stream": false
}'
```

### Android Testing
**Manual testing scenarios**:
1. Send "Hello" → verify AI response received
2. Send emoji text → verify Unicode support in both directions
3. Ask a question → verify contextual AI response
4. Turn off WiFi → send message → verify error handling
5. Send multiple messages → verify conversation history maintained
6. Click "Новый чат" → verify chat clears

**Logcat filtering**:
```bash
# View app logs
adb logcat | grep "aiwithlove"

# View Ollama client logs
adb logcat | grep "Ktor"
```

## Dependency Management

This project uses Gradle version catalogs (`gradle/libs.versions.toml`).

**Adding a dependency**:
1. Add to `libs.versions.toml` under `[libraries]`
2. Reference in `app/build.gradle.kts` as `implementation(libs.dependency.name)`

**Major dependencies**:
- `ktor-client-*` (3.0.0) - HTTP client with JSON support
- `koin-*` (3.5.6) - Dependency injection
- `kotlinx-serialization-json` (1.7.3) - JSON parsing
- `androidx-compose-*` (BOM 2024.09.00) - UI framework
- `androidx-room-*` (2.6.1) - Database (currently unused)

## Common Development Tasks

### Update Ollama server URL
Edit `app/src/main/java/com/example/aiwithlove/util/SecureData.kt` (gitignored file):

**For Android Emulator** (localhost development):
```kotlin
object SecureData {
    const val SERVER_IP = "10.0.2.2"  // Special emulator IP → host machine's localhost
    const val SERVER_PORT = 11434      // Default Ollama port
    val OLLAMA_SERVER_URL = "http://$SERVER_IP:$SERVER_PORT"
}
```

**For Physical Device** (same network):
```kotlin
object SecureData {
    const val SERVER_IP = "192.168.1.100"  // Your machine's actual IP address
    const val SERVER_PORT = 11434
    val OLLAMA_SERVER_URL = "http://$SERVER_IP:$SERVER_PORT"
}
```

**Android Emulator Networking**: `10.0.2.2` is a special alias that routes to the host machine's `127.0.0.1` (localhost).

### Switch to a different model
Edit `ollama/OllamaClient.kt` and change the model name:
```kotlin
private val modelName = "llama2"  // or "llama3", "mistral", "codellama", etc.
```

Make sure the model is pulled on the server first:
```bash
ollama pull <model-name>
```

### Add conversation context
The `OllamaClient` sends full message history with each request, so the AI maintains context.

To modify context behavior, edit the `chat()` method in `OllamaClient.kt`.

### Enable streaming responses
To get real-time streaming instead of waiting for complete response:
1. Set `"stream": true` in the Ollama request
2. Handle SSE (Server-Sent Events) in the client
3. Update UI incrementally as tokens arrive

### Handle new error cases
Add error handling in `ChatViewModel.sendMessage()` catch block.
Current errors caught: network failures, JSON parsing errors, Ollama API errors.

## Security Considerations

- **Network Security**: App requires `INTERNET` permission in manifest
- **Cleartext HTTP**: Android 9+ blocks HTTP by default. Must configure `app/src/main/res/xml/network_security_config.xml`:
  ```xml
  <network-security-config>
      <domain-config cleartextTrafficPermitted="true">
          <domain includeSubdomains="true">10.0.2.2</domain>
          <domain includeSubdomains="true">localhost</domain>
          <domain includeSubdomains="true">127.0.0.1</domain>
          <!-- Add your server IPs here -->
      </domain-config>
  </network-security-config>
  ```
  Reference in `AndroidManifest.xml`: `android:networkSecurityConfig="@xml/network_security_config"`
- **SecureData.kt**: Gitignored file for server configuration (IP address, port)
- **No Authentication**: Default Ollama has no auth - use VPN/firewall for remote access
- **Data Privacy**: All data stays local (no cloud services)
- **Model Safety**: llama2 has built-in safety guidelines, but monitor outputs

## Performance Considerations

- **Model Size**: llama2 requires significant RAM (~8GB for 7B model)
- **Response Time**: Depends on hardware (2-30 seconds typical)
- **keep_alive parameter**: Critical for performance - keeps model in RAM
  - First request: ~20 seconds (cold start)
  - Subsequent requests (within 5m): ~2-5 seconds (75%+ faster)
- **Network Latency**: Keep app and Ollama on same network for best performance
- **Context Length**: Longer conversations = slower responses

## Troubleshooting

### Error: "Cleartext HTTP traffic not permitted"
**Cause**: Android 9+ blocks unencrypted HTTP by default.
**Solution**: Configure `network_security_config.xml` (see Security Considerations above).

### Error: "NoTransformationFoundException" with ContentType: application/x-ndjson
**Cause**: Ollama returned NDJSON format instead of JSON.
**Solution**: Already handled by `OllamaClient.parseOllamaResponse()`. If you see this error, verify the parsing logic is in place.

### Error: Empty AI responses
**Cause**: NDJSON content not being concatenated across lines.
**Solution**: `parseOllamaResponse()` must concatenate all `message.content` chunks, not just take the last line.

### Error: Extra newline before AI responses
**Cause**: NDJSON chunks contain leading/trailing whitespace.
**Solution**: Use `.trim()` on concatenated content.

### Cannot connect from emulator
**Verify emulator setup**:
```bash
# Check device type
adb devices  # Should show "emulator-XXXX"

# Test connection from emulator shell
adb shell curl http://10.0.2.2:11434/api/version
```
**Expected**: `{"version":"0.16.2"}`

### Slow first response
**This is normal!** First request loads model into RAM (~20s). Use `keep_alive: "5m"` to keep model loaded for subsequent requests.

## Documentation Index

This project has comprehensive documentation across multiple files. Each serves a specific purpose:

### 📚 Core Documentation

#### [README.md](README.md)
**Purpose**: User-facing project overview and quick start guide
**Audience**: End users, developers new to the project
**Contains**:
- Project overview and features
- Ollama installation prerequisites
- Basic usage examples (simple questions, programming, creative tasks)
- Links to detailed documentation

**When to read**: First file to read when discovering the project

---

#### [CLAUDE.md](CLAUDE.md) *(this file)*
**Purpose**: Comprehensive guide for AI assistants (Claude Code) working on this codebase
**Audience**: Claude Code, AI-assisted development
**Contains**:
- Build and run commands
- Architecture overview (MVVM + Ollama)
- Ollama communication patterns
- Key files and responsibilities
- Common development tasks
- Troubleshooting guide

**When to update**: When changing architecture, adding new patterns, or discovering common issues

---

### 🚀 Setup & Deployment

#### [LOCALHOST_SETUP.md](LOCALHOST_SETUP.md)
**Purpose**: Complete localhost development setup guide
**Audience**: Developers running Ollama on their local machine
**Contains**:
- Current configuration (Option B: localhost)
- Android emulator networking (10.0.2.2 explained)
- Step-by-step run instructions
- Device-specific setup (emulator vs physical device)
- Comprehensive troubleshooting

**When to read**: Setting up local development environment for the first time

---

#### [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)
**Purpose**: Production deployment and testing procedures
**Audience**: DevOps, developers deploying to remote servers
**Contains**:
- Ollama server installation (Linux/Mac/Windows)
- Network configuration for remote access
- Android app configuration steps
- Network security config verification
- Testing scenarios (6 different test cases)
- Performance optimization tips
- Troubleshooting common deployment issues

**When to read**: Deploying Ollama to a remote server or troubleshooting production issues

---

#### [SECURE_DATA_SETUP.md](SECURE_DATA_SETUP.md)
**Purpose**: Security and configuration management guide
**Audience**: All developers (first-time setup required)
**Contains**:
- SecureData.kt setup instructions
- Environment-specific configuration (emulator/device/remote)
- Security best practices
- Gitignore verification
- Template file usage (SecureData.kt.example)

**When to read**: First time setting up the project, or when changing server configuration

---

### 📊 Implementation Details

#### [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)
**Purpose**: Complete implementation history and technical decisions
**Audience**: Developers understanding the evolution from День 25 → День 26
**Contains**:
- Full migration history (MCP → Ollama)
- Before/after comparisons
- All code changes documented
- Post-implementation bug fixes (4 critical fixes)
- Architecture diagrams
- Statistics (files created/modified, build times)
- Future enhancement ideas

**When to update**: After major refactoring, significant features, or architectural changes

---

#### [OLLAMA_BEST_PRACTICES.md](OLLAMA_BEST_PRACTICES.md)
**Purpose**: Documentation of best practices from official Ollama docs
**Audience**: Developers optimizing Ollama integration
**Contains**:
- Verification results from context7
- Performance improvements applied (keep_alive, error handling)
- API endpoint reference
- Testing recommendations
- Available but unused features (streaming, image support, etc.)
- Migration guide from old implementation

**When to read**: Optimizing performance, implementing new Ollama features

---

#### [VERIFICATION_REPORT.md](VERIFICATION_REPORT.md)
**Purpose**: Testing verification and compliance checklist
**Audience**: QA, developers verifying implementation correctness
**Contains**:
- Live test results (llama2 model tested)
- Performance metrics (token counts, timing)
- Best practices compliance checklist
- Before/after comparison tables
- Build verification results
- Post-verification bug fixes

**When to read**: Verifying implementation quality, performance benchmarking

---

### 🗂️ Legacy Documentation

#### [server/SERVER_README.md](server/SERVER_README.md)
**Purpose**: Legacy documentation from День 25 (MCP webpage creator)
**Status**: ⚠️ **OBSOLETE** - Not used in current project
**Contains**:
- Old MCP server deployment instructions
- Webpage creation tool documentation
- Docker and nginx configuration

**Note**: Marked as legacy. Current project uses Ollama, not MCP server.

---

## Documentation Maintenance Guidelines

### When to Update Each File

| File | Update When... |
|------|----------------|
| **README.md** | Changing user-facing features, usage examples, or prerequisites |
| **CLAUDE.md** | Adding build commands, architecture changes, new patterns, troubleshooting tips |
| **LOCALHOST_SETUP.md** | Changing localhost setup, Android emulator config, or troubleshooting steps |
| **DEPLOYMENT_GUIDE.md** | Adding deployment steps, server configuration, or production issues |
| **SECURE_DATA_SETUP.md** | Changing SecureData.kt structure or adding new config values |
| **IMPLEMENTATION_SUMMARY.md** | Major refactoring, significant features, or bug fixes |
| **OLLAMA_BEST_PRACTICES.md** | Discovering new Ollama best practices or performance optimizations |
| **VERIFICATION_REPORT.md** | Re-testing implementation, updating compliance checklist |

### Documentation Quick Reference

**Need to...**
- 🚀 **Start the project?** → [README.md](README.md) → [LOCALHOST_SETUP.md](LOCALHOST_SETUP.md)
- 🔧 **Configure SecureData.kt?** → [SECURE_DATA_SETUP.md](SECURE_DATA_SETUP.md)
- 🏗️ **Understand architecture?** → [CLAUDE.md](CLAUDE.md) → [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)
- 🚢 **Deploy to production?** → [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)
- ⚡ **Optimize performance?** → [OLLAMA_BEST_PRACTICES.md](OLLAMA_BEST_PRACTICES.md)
- 🐛 **Fix bugs?** → [CLAUDE.md](CLAUDE.md) Troubleshooting → [LOCALHOST_SETUP.md](LOCALHOST_SETUP.md) Troubleshooting
- ✅ **Verify implementation?** → [VERIFICATION_REPORT.md](VERIFICATION_REPORT.md)

---

## Documentation Coverage

All aspects of the project are documented:

- ✅ **User Guide** - README.md
- ✅ **Development Setup** - LOCALHOST_SETUP.md, SECURE_DATA_SETUP.md
- ✅ **Deployment** - DEPLOYMENT_GUIDE.md
- ✅ **Architecture** - CLAUDE.md, IMPLEMENTATION_SUMMARY.md
- ✅ **Best Practices** - OLLAMA_BEST_PRACTICES.md
- ✅ **Testing** - VERIFICATION_REPORT.md
- ✅ **Troubleshooting** - Multiple files with dedicated sections

**Total Documentation**: 8 active files (1 legacy)
