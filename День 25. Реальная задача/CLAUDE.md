# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Webpage Creator App - An Android application that creates HTML webpages through MCP (Model Context Protocol). Users type text in a chat interface, and the app calls an MCP server to create a public webpage, returning a URL.

**Key difference from typical chat apps**: This app makes **direct MCP tool calls** rather than using an AI intermediary. The `create_webpage` tool is called immediately when the user sends a message.

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

## Architecture: MVVM + MCP Integration

```
UI Layer (Compose)
  └─ ChatScreen.kt
       └─ Displays messages, handles user input
       └─ Clickable webpage URLs open in browser

ViewModel Layer
  └─ ChatViewModel.kt
       ├─ StateFlow<List<Message>> - message list
       ├─ StateFlow<Boolean> - loading state
       └─ sendMessage() - orchestrates MCP tool call

Data Layer
  └─ McpClient.kt
       └─ JSON-RPC 2.0 HTTP client (Ktor)

DI Layer (Koin)
  └─ AppModule.kt
       ├─ Provides McpClient (singleton)
       └─ Provides ChatViewModel (viewModel)
```

**Initialization**: `MainActivity.onCreate()` calls `startKoin()` before setting content.

## MCP Communication Pattern

The app uses JSON-RPC 2.0 over HTTP to communicate with an MCP server:

### Request Flow
1. User types text → `ChatViewModel.sendMessage()`
2. ViewModel calls `mcpClient.callTool("create_webpage", mapOf("text" to userText))`
3. McpClient constructs JSON-RPC request:
   ```json
   {
     "jsonrpc": "2.0",
     "id": 1,
     "method": "tools/call",
     "params": {
       "name": "create_webpage",
       "arguments": {"text": "user text here"}
     }
   }
   ```
4. Server responds with nested JSON structure
5. ViewModel parses: `response.result.content[0].text` → JSON string → parse again
6. Extracts `url`, `filename` from parsed tool result
7. Updates UI with success message and clickable URL

### Response Parsing Pattern (ChatViewModel.kt:53-69)
```kotlin
val mcpResult = json.parseToJsonElement(result) as JsonObject
val content = mcpResult["content"] as? JsonArray
val textContent = content?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

// textContent is a JSON string that needs parsing AGAIN
val toolResult = json.parseToJsonElement(textContent) as JsonObject
val success = toolResult["success"]?.jsonPrimitive?.boolean
val url = toolResult["url"]?.jsonPrimitive?.content
```

**Critical**: MCP responses are double-encoded. The tool result is a JSON string inside the JSON-RPC response.

## MCP Server Deployment

The MCP server code lives in a **separate directory**:
```
/Users/falin/AndroidStudioProjects/AI-with-Love/День 24. Ассистент команды/server/
```

To deploy server changes:
```bash
cd "/Users/falin/AndroidStudioProjects/AI-with-Love/День 24. Ассистент команды/server"
./deploy_quick.sh
```

Test server after deployment:
```bash
cd "/Users/falin/AndroidStudioProjects/AI-with-Love/День 25. Реальная задача"
./test_server.sh
```

The server URL is configured in `util/ServerConfig.kt` which references `SecureData.kt` (gitignored).

## Key Files and Their Responsibilities

**`viewmodel/ChatViewModel.kt`**:
- Manages chat state (messages, loading)
- Handles MCP tool calls and response parsing
- Error handling for network/server/parsing failures
- Updates message list reactively

**`mcp/McpClient.kt`**:
- Ktor HTTP client with JSON-RPC support
- Long timeouts (10 min request, 30 sec connect)
- Converts Kotlin `Map<String, Any>` to JSON params
- Returns raw JSON string (caller must parse)

**`ui/ChatScreen.kt`**:
- LazyColumn for message list with auto-scroll
- MessageBubble components (user right-aligned, assistant left-aligned)
- ClickableText for webpage URLs → launches browser via `UriHandler`
- Input field with send button
- "Новый чат" button clears history

**`di/AppModule.kt`**:
- Koin module setup
- Single McpClient instance (shared across app)
- ViewModel factory for ChatViewModel

**`data/model/Message.kt`**:
- Simple data class: `text`, `isFromUser`, `webpageUrl?`
- No database persistence (messages cleared on app restart)

## Testing Strategy

### Server Testing
Run automated test suite before deploying Android changes:
```bash
./test_server.sh
```
Tests include: basic creation, long text, Unicode/emoji, XSS prevention, error handling.

### Android Testing
**Manual testing scenarios**:
1. Send "Hello World" → verify URL received → click URL → browser opens
2. Send emoji text → verify Unicode support
3. Send `<script>alert('xss')</script>` → verify escaping in webpage
4. Turn off WiFi → send message → verify error handling
5. Send multiple messages → verify unique URLs
6. Click "Новый чат" → verify chat clears

**Logcat filtering**:
```bash
# View app logs
adb logcat | grep "aiwithlove"

# View MCP client logs
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

### Update MCP server URL
Edit `app/src/main/java/com/example/aiwithlove/util/SecureData.kt` (gitignored file):
```kotlin
object SecureData {
    const val SERVER_IP = "148.253.209.151"
    const val SERVER_PORT = 8080
    val MCP_SERVER_URL = "http://$SERVER_IP:$SERVER_PORT"
}
```

### Add a new MCP tool call
1. Add method to `McpClient.kt` (or use existing `callTool()`)
2. Call from `ChatViewModel.kt` in a `viewModelScope.launch { }`
3. Parse response following the double-decode pattern
4. Update UI state with results

### Modify webpage display format
The Android app receives URLs as strings. The actual HTML template is generated **server-side** in `http_mcp_server.py` (День 24 directory).

To change webpage appearance, modify the Python server code, not the Android app.

### Handle new error cases
Add error handling in `ChatViewModel.sendMessage()` catch block (line 90-100).
Current errors caught: network failures, JSON parsing errors, tool call failures.

## Security Considerations

- **XSS Prevention**: Handled server-side (HTML escaping in Python)
- **Path Traversal**: Prevented by UUID-based filenames (no user input in paths)
- **Network Security**: App requires `INTERNET` permission in manifest
- **SecureData.kt**: Gitignored file for sensitive configuration (server URLs, credentials)

## Related Documentation

- **README.md**: User-facing guide, usage examples
- **IMPLEMENTATION_SUMMARY.md**: Detailed implementation notes, architecture diagrams, statistics
- **DEPLOYMENT_GUIDE.md**: Step-by-step server setup and testing procedures
- **SECURE_DATA_SETUP.md**: Instructions for setting up SecureData.kt

When making changes that affect deployment, update IMPLEMENTATION_SUMMARY.md. When changing server setup, update DEPLOYMENT_GUIDE.md.
