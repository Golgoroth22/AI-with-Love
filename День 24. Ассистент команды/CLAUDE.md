# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**AI with Love** is an educational Android application demonstrating agentic AI patterns with RAG (Retrieval-Augmented Generation) capabilities. The app integrates with a custom MCP (Model Context Protocol) server for tool execution and uses Perplexity's Agentic API for intelligent responses.

**Current Day**: День 23 (Day 23) - Ассистент для поддержки пользователей (Support Assistant)

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

## 🔍 Code Review Automation (Day 22)

**New Feature**: The app can now perform automated code reviews on GitHub pull requests using AI-powered analysis.

**Implementation Note**: PR reviews are currently triggered **manually through the chat interface**, not via CI/CD pipeline. The app fetches PR data via GitHub's MCP server and performs analysis client-side.

**Available Operations:**
- **PR Analysis**: `get_pull_request` - Fetch PR metadata (title, author, stats, status)
- **File Review**: `get_pr_files` - Get list of changed files with diffs and patches
- **Code Analysis**: Pattern-based detection for security, bugs, performance, and style issues

**Setup:**
1. Ensure GitHub Personal Access Token is configured (see Day 21 setup above)
2. Enable "GitHub Assistant" in MCP dialog (chat screen, wrench icon)
3. Optionally: Index project documentation (CLAUDE.md, etc.) via Ollama screen for context-aware reviews

**Example Queries:** (use "ReviewPR" keyword to activate)
```
"ReviewPR сделай ревью для PR #45"
"ReviewPR проверь pull request Golgoroth22/AI-with-Love#12"
"код-ревью для PR #8"
```

**PR Reference Parsing:**
The AI automatically parses PR references from user messages:
- Owner is always "Golgoroth22" (hardcoded, never asks for clarification)
- Supported formats:
  - `ReviewPR AI-with-Love#123` → repo="AI-with-Love", pr_number=123
  - `ReviewPR #123` → uses default repo "AI-with-Love"
  - `ReviewPR Day 22#1` → interprets "Day 22" as "AI-with-Love" repository
  - Repository names with spaces (like "День 22. Автоматизация ревью кода") → "AI-with-Love"

**Review Output:**
- Overall score and recommendation (0-10 scale)
- Issue breakdown by severity: 🔴 critical, 🟠 major, 🟡 minor, 🔵 info
- File-by-file analysis with specific line numbers
- Recommendations with actionable fixes
- Optional RAG-enhanced citations from indexed project docs

**Review Output Format:**
```
# 📋 Code Review: PR #123

**Название:** Add new feature
**Автор:** username
**Файлов изменено:** 5 (+120, -30 строк)

## Общая оценка: 8/10 ⭐

**Проблемы:**
- 🔴 0 критических
- 🟠 1 серьёзных
- 🟡 3 незначительных

### Файл: `ChatViewModel.kt`
**🟠 Line 425:** Отсутствует обработка ошибок
**Рекомендация:** Используйте runAndCatch wrapper
```

**Analysis Categories** (equal priority):
- **Security**: Hardcoded secrets, SQL injection, unsafe API usage
- **Bugs**: Missing null checks, empty catch blocks, force unwraps (!!)
- **Performance**: Nested loops, inefficient algorithms
- **Style**: Debug prints, unresolved TODOs, naming conventions

**Limitations:**
- Max 30 files per PR (warns for larger PRs and suggests scope reduction)
- Analysis focuses on Kotlin and Python patterns
- RAG context requires pre-indexed .md documentation

**Architecture:**
- Extends existing GitHub MCP server with PR-specific tools
- Uses agentic loop for multi-step analysis workflow
- Integrates semantic search for project standards when available

**Future Enhancements (Planned):**
- GitHub Actions workflow for automatic PR review on pull_request events
- Comment posting directly to PR via GitHub API
- Integration with GitHub Checks API for status reporting

## 🎫 Support Assistant (Day 23)

**New Feature**: Dedicated support screen with automatic CRM ticket creation and FAQ-powered answers.

**Architecture:** Separate `SupportScreen` and `SupportViewModel` (independent from main chat) that integrates:
- **CRM Tools** (via MCP server) - Ticket creation, retrieval, and updates
- **RAG/FAQ Search** - Semantic search for solutions in indexed documentation
- **Automatic Ticket Management** - One ticket per conversation session

**Key Differences from Main Chat:**

| Aspect | Main Chat | Support Screen |
|--------|-----------|----------------|
| **Purpose** | General AI queries | Support tickets only |
| **MCP Servers** | User selects via dialog | Always: Support + RAG |
| **Session** | Continuous dialog | One ticket per session |
| **First Message** | Just sends message | Creates ticket first, then sends |
| **Clear Chat** | Deletes all messages | Creates new ticket on next message |

**Available CRM Tools** (4 tools in `http_mcp_server.py`):
- `create_ticket` - Auto-creates ticket with title, category, priority (user_id=1 mock)
- `get_ticket` - Fetches ticket details including full history
- `update_ticket` - Changes status (open/in_progress/resolved/closed) or adds notes
- `list_user_tickets` - Lists all user tickets with status filtering

**Workflow:**
1. User opens Support screen, sees welcome message
2. User sends first message → `create_ticket` called automatically
3. ViewModel extracts title (first 50 chars) and detects category (authentication/features/troubleshooting)
4. AI uses `semantic_search` to find FAQ solutions based on category
5. AI responds with FAQ citations and ticket context
6. Follow-up messages use same ticket ID (displayed in TopAppBar)
7. "Начать новый диалог" button clears session → next message creates new ticket

**CRM Data Storage:**
- `server/data/crm_tickets.json` - Ticket storage with auto-increment IDs
- `server/data/crm_users.json` - Mock user database (3 users)
- Ticket structure: id, user_id, title, description, status, priority, category, history[], timestamps

**FAQ Integration:**
- `FAQ.md` must be indexed via Ollama screen (52 chunks)
- Semantic search uses threshold 0.6 by default
- FAQ structured with direct answers at top for better ranking
- Example: "Какую LLM ты используешь?" → chunk 0 contains "Perplexity Agentic API"

**Category Detection** (keyword-based in `SupportViewModel.kt`):
- `authentication`: вход, пароль, авторизация, 2FA, аккаунт
- `troubleshooting`: ошибка, не работает, сломал, crash, медленно
- `features`: как, использовать, функция, настроить
- `other`: default for unmatched

**Critical Implementation Details:**
- Ticket ID parsing handles nested MCP JSON-RPC response format with 3-tier fallback
- Type conversion required: SQLite returns similarity as string, must cast to float
- SupportViewModel uses separate agentic loop (max 5 iterations, model: openai/gpt-5-mini)
- Instructions always include current ticket ID context
- No support keywords in main ChatViewModel (removed in Day 23)

**Navigation:**
- LaunchScreen → "Ассистент поддержки пользователей" button → SupportScreen
- Back button returns to LaunchScreen (session not preserved)

**Known Issues Fixed:**
- Semantic search type comparison (`'<' not supported`) → fixed with explicit float() conversion
- Ticket ID parsing null → fixed with MCP content array parsing
- FAQ giving wrong answers → restructured with direct answers at top

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
│  - SupportScreen: Support assistant 🆕      │
│  - LaunchScreen: Navigation hub             │
└────────────────┬────────────────────────────┘
                 │
┌────────────────▼────────────────────────────┐
│  Domain Layer (ViewModels + Repository)     │
│  - ChatViewModel: Agentic orchestration     │
│  - OllamaViewModel: Document processing     │
│  - SupportViewModel: Ticket management 🆕   │
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
│  - CRM JSON files: Tickets & Users 🆕       │
└─────────────────────────────────────────────┘
```

### Multi-Server MCP Architecture (Updated Day 23)

```
ChatViewModel / SupportViewModel
    ├─→ McpClientManager (routes tool calls)
    │       ├─→ McpClient("rag") → RAG Server (semantic_search)
    │       ├─→ McpClient("github") → GitHub MCP Server (get_repo, search_code, etc.)
    │       └─→ McpClient("support") → Support Server (get_ticket, create_ticket, update_ticket) 🆕
    │
    ├─→ OllamaClient → Local Ollama (embeddings)
    └─→ EmbeddingsRepository → Local SQLite (document storage)

Note: SupportViewModel always uses "support" + "rag" servers (no user selection needed)
```

### Dependency Injection (Koin)

All dependencies are managed via Koin DI. The module is defined in `app/src/main/java/com/example/aiwithlove/di/AppModule.kt`:

- **Singletons**: `PerplexityApiService`, `AppDatabase`, `ChatRepository`, `McpClient`
- **ViewModels**: `ChatViewModel`, `OllamaViewModel`

When adding new dependencies, update `appModule` in `AppModule.kt`.

### MCP Server Architecture

The MCP server (`server/http_mcp_server.py`) implements JSON-RPC 2.0 protocol with the following tools:

**Available Tools (22 total):**

**RAG/Embeddings (6 tools):**
1. `semantic_search` - RAG-based semantic search with threshold filtering
2. `process_text_chunks` - Text chunking and embedding generation **with parallel processing**
3. `create_embedding` - Generate embeddings using Ollama
4. `save_document` - Save document with embeddings and citations
5. `search_similar` - Cosine similarity search in local DB
6. `process_pdf` - Extract text from PDF, chunk, and index

**GitHub MCP (8 tools):**
7. `get_repo`, `search_code`, `create_issue`, `list_issues`, `list_commits`, `get_repo_content`
8. `get_pull_request`, `get_pr_files` - PR review tools (Day 22)

**Local Git (4 tools):**
9. `git_status`, `git_branch`, `git_diff`, `git_pr_status`

**Support/CRM (4 tools - Day 23):** 🆕
10. `create_ticket` - Auto-creates ticket with title, category, priority
11. `get_ticket` - Fetches ticket details including full history
12. `update_ticket` - Changes status or adds notes to ticket
13. `list_user_tickets` - Lists all user tickets with filtering

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
| `app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt` | Agentic orchestration, tool execution, dialog management, PR review keyword detection and instructions |
| `app/src/main/java/com/example/aiwithlove/viewmodel/OllamaViewModel.kt` | Document indexing, PDF processing |
| `app/src/main/java/com/example/aiwithlove/viewmodel/SupportViewModel.kt` | **NEW (Day 23)**: Support ticket management, CRM integration, FAQ-powered responses |
| `app/src/main/java/com/example/aiwithlove/ui/screen/ChatScreen.kt` | Chat UI, message rendering, MCP tool info display |
| `app/src/main/java/com/example/aiwithlove/ui/screen/OllamaScreen.kt` | Document upload UI, indexing interface |
| `app/src/main/java/com/example/aiwithlove/ui/screen/SupportScreen.kt` | **NEW (Day 23)**: Support assistant UI, ticket display, FAQ answers |
| `app/src/main/java/com/example/aiwithlove/ui/screen/LaunchScreen.kt` | **UPDATED (Day 23)**: Added Support button, made scrollable |
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
| `server/http_mcp_server.py` | Main server implementation, tool handlers, JSON-RPC protocol (RAG + GitHub + CRM tools) |
| `server/test_http_mcp_server.py` | Test suite (26 tests) |
| `server/data/embeddings.db` | SQLite database for document embeddings |
| `server/data/crm_tickets.json` | **NEW (Day 23)**: CRM ticket storage with auto-increment IDs |
| `server/data/crm_users.json` | **NEW (Day 23)**: Mock user database (3 users) |
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

### Support Assistant Issues (Day 23)

**Problem:** Ticket ID returns null after creation

**Solution:**
- MCP wraps responses in `{"content":[{"type":"text","text":"..."}]}` structure
- Use 3-tier fallback parsing in `parseTicketIdFromResponse()`:
  1. Parse from MCP content array (primary)
  2. Fallback to top-level ticket_id
  3. Fallback to nested result object
- Check server logs for actual response structure

**Problem:** Semantic search type comparison error (`'<' not supported`)

**Solution:**
- SQLite returns similarity as string, must cast to float
- Add explicit conversion: `similarity = float(doc.get('similarity', 0))`
- Check `http_mcp_server.py` line ~1154

**Problem:** AI gives wrong answers about LLM/API being used

**Solution:**
- FAQ.md must have **direct answers at the top** of each section
- Don't bury answers in technical details - put them first
- Re-index FAQ after editing: delete old chunks, process_text_chunks again
- Verify with semantic search: query "какую LLM ты используешь" should return chunk 0 with high score (>0.75)

**Problem:** FAQ not indexed or returning 0 results

**Solution:**
- Process FAQ.md via Ollama screen OR manually via curl:
  ```bash
  curl -X POST http://localhost:8080 -H "Content-Type: application/json" -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "process_text_chunks",
      "arguments": {
        "text": "...",
        "source_file": "FAQ.md",
        "chunk_size": 1000,
        "overlap": 200
      }
    },
    "id": 1
  }'
  ```
- Verify chunks saved: `sqlite3 server/data/embeddings.db "SELECT COUNT(*) FROM documents WHERE source_file='FAQ.md';"`
- Should see 52 chunks for current FAQ.md

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
  
