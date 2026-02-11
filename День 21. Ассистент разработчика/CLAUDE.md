# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**AI with Love** is an educational Android application demonstrating agentic AI patterns with RAG (Retrieval-Augmented Generation) capabilities. The app integrates with a custom MCP (Model Context Protocol) server for tool execution and uses Perplexity's Agentic API for intelligent responses.

**Current Day**: День 21 (Day 21) - Ассистент разработчика (Developer Assistant)

## ⚡ Day 21 Updates: Local Processing Architecture

**Major Change**: Documents are now processed **locally** using your machine's Ollama instance:

- ✅ **7x faster** processing (45s vs 320s for 15KB file)
- ✅ **No timeout errors** for large files
- ✅ **Local embeddings**: Generated on your machine via local Ollama
- ✅ **Remote storage**: Only pre-computed chunks saved to server

See `LOCAL_PROCESSING.md` for detailed documentation.

## 🐙 GitHub MCP Integration (Day 21)

**New Feature**: The app now integrates with GitHub's official MCP server for git/GitHub operations:

**Available Operations:**
- **Repository**: `get_repo`, `get_repo_content` - Browse repos and read files
- **Code Search**: `search_code` - Find code patterns across repositories
- **Issues**: `create_issue`, `list_issues` - Manage repository issues
- **Commits**: `list_commits` - View commit history

**Setup:**
1. Generate GitHub Personal Access Token at https://github.com/settings/tokens
2. Required scopes: `repo`, `read:packages`, `read:org`
3. Add token to `SecureData.kt`: `val githubPersonalAccessToken = "ghp_..."`
4. Enable "GitHub Assistant" in MCP dialog (chat screen, wrench icon)

**Example Queries:** (use "GitWithLove" keyword to activate)
```
"GitWithLove покажи информацию о репозитории facebook/react"
"GitWithLove найди код с использованием Compose в android/compose-samples"
"GitWithLove создать issue в owner/repo: Bug in authentication"
"GitWithLove последние 5 коммитов в main ветке owner/repo"
```

**Architecture:**
- Multi-server routing via `McpClientManager`
- Remote GitHub MCP server: `https://api.githubcopilot.com/mcp/`
- Automatic tool detection from keywords in user messages
- Hybrid workflows: combine GitHub data with local RAG documents

See `GITHUB_INTEGRATION.md` for complete documentation.

## Build & Run Commands

### Android App

```bash
# Build the app
./gradlew build

# Run tests
./gradlew test

# Run specific test
./gradlew test --tests ChatViewModelProductionTest

# Lint the code
./gradlew lintDebug

# Format code (Kotlinter)
./gradlew formatKotlin

# Check code formatting
./gradlew lintKotlin

# Clean build
./gradlew clean
```

### MCP Server

```bash
# Start local server
cd server
python3 http_mcp_server.py
# Server runs on http://0.0.0.0:8080

# Run server tests
python3 test_http_mcp_server.py
# Expected: 26 tests passed

# Deploy to remote server (requires credentials)
./deploy_quick.sh

# Test specific functionality
python3 test_citations.py
```

### Android App with Local MCP Server

When testing with the Android emulator:
1. Start MCP server: `python3 http_mcp_server.py`
2. App connects via `http://10.0.2.2:8080` (emulator's special alias for host machine)
3. For physical device, update `ServerConfig.kt` to use your machine's IP

## Architecture

### Three-Layer Architecture

```
┌─────────────────────────────────────────────┐
│  Presentation Layer (Compose UI)            │
│  - ChatScreen: Agentic chat interface       │
│  - OllamaScreen: Document indexing UI       │
│  - LaunchScreen: Navigation hub             │
└────────────────┬────────────────────────────┘
                 │
┌────────────────▼────────────────────────────┐
│  Domain Layer (ViewModels + Repository)     │
│  - ChatViewModel: Agentic orchestration     │
│  - OllamaViewModel: Document processing     │
│  - ChatRepository: Database operations      │
└────────────────┬────────────────────────────┘
                 │
┌────────────────▼────────────────────────────┐
│  Data Layer (Network + Local Storage)       │
│  - PerplexityApiService: AI API client      │
│  - McpClientManager: Multi-server routing   │
│  - McpClient: MCP server communication      │
│  - AppDatabase: Room database (SQLite)      │
│  - EmbeddingsDatabase: Local embeddings     │
└─────────────────────────────────────────────┘
```

### Multi-Server MCP Architecture (New in Day 21)

```
ChatViewModel
    ├─→ McpClientManager (routes tool calls)
    │       ├─→ McpClient("rag") → RAG Server (semantic_search)
    │       └─→ McpClient("github") → GitHub MCP Server (get_repo, search_code, etc.)
    │
    ├─→ OllamaClient → Local Ollama (embeddings)
    └─→ EmbeddingsRepository → Local SQLite (document storage)
```

### Dependency Injection (Koin)

All dependencies are managed via Koin DI. The module is defined in `app/src/main/java/com/example/aiwithlove/di/AppModule.kt`:

- **Singletons**: `PerplexityApiService`, `AppDatabase`, `ChatRepository`, `McpClient`
- **ViewModels**: `ChatViewModel`, `OllamaViewModel`

When adding new dependencies, update `appModule` in `AppModule.kt`.

### MCP Server Architecture

The MCP server (`server/http_mcp_server.py`) implements JSON-RPC 2.0 protocol with the following tools:

**Available Tools:**
1. `semantic_search` - RAG-based semantic search with threshold filtering
2. `process_text_chunks` - Text chunking and embedding generation **with parallel processing**
3. `create_embedding` - Generate embeddings using Ollama

**Key Configuration:**
- `EMBEDDINGS_DB_PATH` - SQLite database for document storage
- `OLLAMA_API_URL` - Ollama endpoint for embeddings (default: localhost:11434)
- `SEMANTIC_SEARCH_CONFIG` - Default threshold: 0.6, range: 0.3-0.95

**Performance Optimization (Day 21):**
- `process_text_chunks` now uses **ThreadPoolExecutor** for parallel processing
- Default: 4 parallel workers (configurable via `max_workers` parameter)
- Performance improvement: **3-4x faster** for large documents
- Example: 15KB file processed in ~80 seconds (vs. 320 seconds before)
- See `server/PERFORMANCE_OPTIMIZATION.md` for detailed benchmarks

## Core Patterns & Implementation Details

### 1. Agentic Tool Execution Loop

The heart of the application is the agentic loop in `ChatViewModel.sendWithAgenticApi()`:

```kotlin
// Pattern: Iterative tool calling with max 5 iterations
var iterations = 0
val maxIterations = 5

while (hasToolCalls(response) && iterations < maxIterations) {
    iterations++
    // Execute tool calls
    response.output?.filter { it.type == "function_call" }?.forEach { toolCall ->
        val result = executeAgenticToolCall(toolCall.name, toolCall.arguments)
        toolResults.add("Tool ${toolCall.name} result: ${result.result}")
    }

    // Send results back to API for next iteration
    currentInput = "$currentInput\n\nTool results:\n${toolResults.joinToString("\n")}"
    response = perplexityService.sendAgenticRequest(...)
}
```

**Why this matters:** The agentic pattern allows the AI to call tools, receive results, and make follow-up decisions. This enables complex workflows like "find documents, extract info, and format response."

### 2. Semantic Search with RAG

Semantic search is triggered by keyword detection in user messages:

```kotlin
private fun userMentionsSemanticSearch(message: String): Boolean {
    val keywords = listOf(
        "найди в документах", "поиск в базе", "что говорится в документах",
        "информация о", "расскажи о", "что такое", "как работает", "объясни"
    )
    return keywords.any { message.lowercase().contains(it) }
}
```

When detected, the `semantic_search` tool is added to the Perplexity API request. The tool definition includes:
- **Parameters**: `query`, `limit`, `threshold`, `compare_mode`
- **Response**: Documents with similarity scores and citation metadata
- **Threshold filtering**: Server-side filtering by similarity score (0.3-0.95 range)

**Critical detail:** The server returns BOTH filtered results and unfiltered results in compare mode, allowing the AI to decide whether to use lower-quality matches if filtered results are empty.

### 3. Dialog Compression

To manage token limits, the chat history is automatically compressed every 5 user messages:

```kotlin
// In ChatViewModel
private fun shouldCompressDialog(): Boolean {
    val userMessagesCount = messagesAfterSummary.count { it.isFromUser }
    return userMessagesCount >= COMPRESSION_THRESHOLD &&
           userMessagesCountSinceAppLaunch >= COMPRESSION_THRESHOLD
}
```

**Compression flow:**
1. Collect all messages since last summary
2. Send to Perplexity API with summary prompt
3. Save summary to database
4. Mark compressed messages (hidden from UI but kept in DB)
5. Summary is prepended to conversation context in future requests

### 4. MCP Client Communication

The `McpClient` uses Ktor HTTP client with JSON-RPC 2.0 protocol:

```kotlin
// Calling a tool
suspend fun callTool(toolName: String, arguments: Map<String, Any>): String {
    val request = McpRequest(
        id = getNextRequestId(),
        method = "tools/call",
        params = buildJsonObject {
            put("name", toolName)
            put("arguments", buildJsonObject { /* ... */ })
        }
    )
    val response = httpClient.post(serverUrl) { setBody(request) }
    return response.result.toString()
}
```

**Timeout configuration:**
- `requestTimeoutMillis`: 600000 (10 minutes) - for large file processing
- `connectTimeoutMillis`: 30000 (30 seconds)
- `socketTimeoutMillis`: 600000 (10 minutes)

### 5. Database Schema (Room)

Three main entities in `AppDatabase`:

1. **UserMessageEntity** - User messages with timestamp
2. **AssistantMessageEntity** - AI responses with token counts and MCP tool metadata
3. **ChatSummaryEntity** - Compressed dialog summaries

**Key feature:** `AssistantMessageEntity` stores `mcpToolInfoJson` as a JSON string, allowing rich metadata about tool calls to be persisted and displayed in the UI.

## Key Files & Responsibilities

### Android App

| File Path | Responsibility |
|-----------|---------------|
| `app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt` | Agentic orchestration, tool execution, dialog management |
| `app/src/main/java/com/example/aiwithlove/viewmodel/OllamaViewModel.kt` | Document indexing, PDF processing |
| `app/src/main/java/com/example/aiwithlove/ui/screen/ChatScreen.kt` | Chat UI, message rendering, MCP tool info display |
| `app/src/main/java/com/example/aiwithlove/ui/screen/OllamaScreen.kt` | Document upload UI, indexing interface |
| `app/src/main/java/com/example/aiwithlove/mcp/McpClient.kt` | MCP server communication via JSON-RPC with auth support |
| `app/src/main/java/com/example/aiwithlove/mcp/McpClientManager.kt` | **NEW (Day 21)**: Multi-server routing for RAG + GitHub |
| `app/src/main/java/com/example/aiwithlove/mcp/McpServerConfig.kt` | **UPDATED (Day 21)**: Added GitHub server configuration |
| `app/src/main/java/com/example/aiwithlove/data/PerplexityApiServiceImpl.kt` | Perplexity Agentic API client |
| `app/src/main/java/com/example/aiwithlove/database/ChatRepository.kt` | Database operations, message persistence |
| `app/src/main/java/com/example/aiwithlove/di/AppModule.kt` | Koin DI configuration |
| `app/src/main/java/com/example/aiwithlove/util/SecureData.kt` | API keys and credentials (NOT in version control) |

### MCP Server

| File Path | Responsibility |
|-----------|---------------|
| `server/http_mcp_server.py` | Main server implementation, tool handlers, JSON-RPC protocol |
| `server/test_http_mcp_server.py` | Test suite (26 tests) |
| `server/data/embeddings.db` | SQLite database for document embeddings |
| `server/deploy_quick.sh` | Remote server deployment script |

## Important Conventions

### 1. Logging

All components implement `ILoggable` interface:

```kotlin
class ChatViewModel(...) : ViewModel(), ILoggable {
    // Use logD(), logE() for consistent logging with class tags
    logD("🔧 Executing tool: $toolName")
    logE("❌ Error occurred", exception)
}
```

### 2. Error Handling

Use `runAndCatch` utility for Result-based error handling:

```kotlin
runAndCatch {
    // Operation that might fail
}.onSuccess { result ->
    // Handle success
}.onFailure { error ->
    logE("Operation failed", error)
}
```

### 3. Coroutine Scopes

- **ViewModels**: Use `viewModelScope.launch(Dispatchers.IO)` for database/network operations
- **UI**: Always switch to `Dispatchers.Main` before updating UI state
- **Repository**: Operations run on caller's dispatcher (usually IO)

### 4. State Management

ViewModels expose state via `StateFlow`:

```kotlin
private val _messages = MutableStateFlow(listOf<Message>())
val messages: StateFlow<List<Message>> = _messages.asStateFlow()
```

UI components collect state in Composables:

```kotlin
val messages by viewModel.messages.collectAsState()
```

## Common Development Tasks

### Adding a New MCP Tool

1. **Server-side** (`server/http_mcp_server.py`):
   ```python
   def handle_my_new_tool(self, arguments):
       # Implement tool logic
       return {"result": "success"}
   ```

2. **Client-side** (`ChatViewModel.kt`):
   ```kotlin
   "my_new_tool" -> {
       val args = parseToolArguments(arguments)
       val mcpResult = mcpClient.callTool("my_new_tool", args)
       ToolExecutionResult(result = parseJokeFromMcpResult(mcpResult), ...)
   }
   ```

3. **Tool definition**:
   ```kotlin
   private fun buildMyNewTool(): AgenticTool {
       return AgenticTool(
           type = "function",
           name = "my_new_tool",
           description = "Clear description for the AI",
           parameters = buildJsonObject { /* schema */ }
       )
   }
   ```

### Modifying Semantic Search Threshold

The default threshold is defined in two places (must match):
- **Server**: `SEMANTIC_SEARCH_CONFIG['default_threshold']` in `http_mcp_server.py`
- **App**: `_searchThreshold.value` initial value in `ChatViewModel.kt`

User can adjust threshold via slider in ChatScreen UI (0.3-0.95 range).

### Changing the AI Model

Update `AGENTIC_MODEL` constant in `ChatViewModel.kt`:

```kotlin
companion object {
    private const val AGENTIC_MODEL = "openai/gpt-5-mini" // or another model
}
```

**Available models** (via Perplexity Agentic API): Check Perplexity documentation for current model list.

### Adding Database Columns

When adding columns to existing tables, use backward-compatible ALTER TABLE:

```python
try:
    cursor.execute("ALTER TABLE documents ADD COLUMN new_column TEXT DEFAULT 'default_value'")
except sqlite3.OperationalError:
    # Column already exists
    pass
```

This pattern is used in `init_database()` for citation columns.

## Testing Strategy

### Android Tests

The app uses MockK for mocking and Turbine for Flow testing:

```kotlin
// Example from ChatViewModelProductionTest.kt
@Test
fun `sendMessage creates agentic response`() = runTest {
    // Test agentic flow with mocked Perplexity API
}
```

Run tests: `./gradlew test`

### Server Tests

Python unittest with mocking for external dependencies:

```bash
python3 test_http_mcp_server.py
```

**Test coverage:**
- Tool execution (semantic_search, process_text_chunks, etc.)
- JSON-RPC protocol handling
- Database operations
- Error handling

### Manual Testing Workflow

1. Start MCP server: `cd server && python3 http_mcp_server.py`
2. Run Android app in emulator
3. Test semantic search: Ask "найди в документах информацию о X"
4. Test document indexing: Go to Ollama screen, upload text or PDF
5. Verify MCP server logs for tool calls

## Deployment

### Server Deployment

The MCP server runs on a remote Linux server with Docker:

```bash
# Quick deployment (from local machine)
cd server
./deploy_quick.sh

# Manual deployment
ssh user@server_ip
cd /path/to/server
docker-compose up -d
```

**Deployment checklist:**
- [ ] Update `server/http_mcp_server.py` if needed
- [ ] Test locally first: `python3 test_http_mcp_server.py`
- [ ] Deploy to remote server
- [ ] Verify server responds: `curl http://server_ip:8080` with JSON-RPC request
- [ ] Check Docker logs: `docker logs mcp-jokes-server`

### Android App Deployment

For production:
1. Update `SecureData.kt` with production server IP
2. Build release APK: `./gradlew assembleRelease`
3. APK location: `app/build/outputs/apk/release/app-release.apk`

**Security reminder**: Never commit `SecureData.kt` with real credentials to version control.

## Troubleshooting

### MCP Server Connection Issues

**Problem:** App can't connect to MCP server

**Solutions:**
- Check server is running: `curl http://10.0.2.2:8080` (from emulator)
- Verify firewall allows port 8080
- Check server logs: `tail -f server/server.log`
- For remote server: Verify SSH tunnel or direct connection

### Semantic Search Returns No Results

**Problem:** Semantic search finds no documents

**Possible causes:**
1. No documents indexed (check `server/data/embeddings.db`)
2. Threshold too high (lower to 0.3-0.4 for testing)
3. Ollama not running (server needs Ollama for embeddings)
4. Query too specific (try broader terms)

**Debug steps:**
```python
# Check document count in database
sqlite3 server/data/embeddings.db "SELECT COUNT(*) FROM documents;"

# Test Ollama connection
curl http://localhost:11434/api/embeddings -d '{"model":"nomic-embed-text","prompt":"test"}'
```

### Dialog Compression Not Working

**Problem:** Chat history not compressing after 5 messages

**Check:**
- `userMessagesCountSinceAppLaunch` counter (resets on app restart)
- Summary creation succeeds (check logs for "Получено резюме")
- Database writes succeed (check for database errors in logs)

### Build Errors

**Common issues:**
- Kotlin version mismatch: Ensure `kotlin("plugin.serialization")` version matches project Kotlin version
- KSP errors: Clean build (`./gradlew clean`) and rebuild
- Room schema changes: Delete app data or increment database version

## Documentation Index

### Root Documentation
- [README.md](README.md) - Main project documentation
- [CLAUDE.md](CLAUDE.md) - This file - development guide
- [SUMMARY.md](SUMMARY.md) - Project summary
- [QUICKSTART.md](QUICKSTART.md) - Quick start guide
- [DEPLOYMENT_INSTRUCTIONS.md](DEPLOYMENT_INSTRUCTIONS.md) - Deployment guide
- [SECURITY.md](SECURITY.md) - Security considerations

### Architecture Documentation
- [LOCAL_PROCESSING.md](LOCAL_PROCESSING.md) - Local processing architecture (Day 21)
- [FULLY_LOCAL_ARCHITECTURE.md](FULLY_LOCAL_ARCHITECTURE.md) - Fully local architecture guide
- [GITHUB_INTEGRATION.md](GITHUB_INTEGRATION.md) - GitHub MCP integration (Day 21)

### Server Documentation
- [server/SERVER_README.md](server/SERVER_README.md) - MCP server documentation
- [server/SEMANTIC_SEARCH.md](server/SEMANTIC_SEARCH.md) - Semantic search implementation
- [server/PERFORMANCE_OPTIMIZATION.md](server/PERFORMANCE_OPTIMIZATION.md) - Performance optimization guide

### UI Documentation
- [docs/CHAT_SCREEN.md](docs/CHAT_SCREEN.md) - Chat screen implementation
- [docs/OLLAMA_SCREEN.md](docs/OLLAMA_SCREEN.md) - Document indexing UI

### MCP Tools Documentation
- [server/tools/CREATE_EMBEDDING.md](server/tools/CREATE_EMBEDDING.md) - Create embedding tool
- [server/tools/PROCESS_TEXT_CHUNKS.md](server/tools/PROCESS_TEXT_CHUNKS.md) - Process text chunks tool
- [server/tools/PROCESS_PDF.md](server/tools/PROCESS_PDF.md) - Process PDF tool
- [server/tools/SAVE_DOCUMENT.md](server/tools/SAVE_DOCUMENT.md) - Save document tool
- [server/tools/SEARCH_SIMILAR.md](server/tools/SEARCH_SIMILAR.md) - Search similar documents tool

## Security Notes

**Sensitive files** (never commit with real values):
- `app/src/main/java/com/example/aiwithlove/util/SecureData.kt` - API keys, server credentials

**Placeholder pattern:**
```kotlin
object SecureData {
    val apiKey = "YOUR_API_KEY_HERE"  // Replace with real key locally
    val serverIp = "YOUR_SERVER_IP"
}
```

Add to `.gitignore` if not already present.
