# Semantic Search Implementation Guide

## Overview

Added a new `semantic_search` tool that retrieves relevant document chunks from a remote MCP server using vector similarity search. This enables RAG (Retrieval Augmented Generation) by finding context from indexed documents to help answer user questions.

## 🎯 Citation Feature (Day 19)

**NEW:** The semantic_search tool now ALWAYS returns citations and source references for every document chunk, reducing AI hallucinations by grounding responses in verifiable sources.

### Key Features:
- **Automatic Citations**: Every document includes `citation` and `citation_info` fields
- **Source Tracking**: Documents tracked by `source_file`, `page_number`, `chunk_index`
- **Citation Format**: Standard format - `[filename.pdf, стр. 12, фрагмент 5/45]`
- **Sources Summary**: Generated list of all unique sources with counts
- **Graceful Degradation**: Legacy documents show `[unknown source]`
- **AI Requirements**: AI MUST include inline citations and "Источники:" section in responses

### Updated Response Format:
```json
{
  "success": true,
  "count": 2,
  "documents": [
    {
      "id": 123,
      "content": "OAuth 2.0 authentication requires...",
      "similarity": 0.89,
      "created_at": "2026-02-04 03:41:55",
      "citation": "[api_guide.pdf, стр. 15, фрагмент 13/45]",
      "citation_info": {
        "source_file": "api_guide.pdf",
        "source_type": "pdf",
        "chunk_index": 12,
        "page_number": 15,
        "total_chunks": 45,
        "formatted": "[api_guide.pdf, стр. 15, фрагмент 13/45]"
      }
    }
  ],
  "sources_summary": ["api_guide.pdf (2 фрагмента)"]
}
```

### AI Response Example:
```
API использует OAuth 2.0 для аутентификации [api_guide.pdf, стр. 5, фрагмент 2/10].
Токены действительны 3600 секунд [api_guide.pdf, стр. 6, фрагмент 3/10].

Источники:
- api_guide.pdf (страница 5-6, фрагменты 2-3)
```

**Two Critical Bugs Fixed**:
1. **Keyword Detection Bug**: Semantic search keywords were incorrectly placed in the joke detection function, preventing the tool from being triggered. Fixed by separating keyword detection into distinct functions.
2. **Connection Architecture Bug**: Android app was connecting directly to the remote server (148.253.209.151:8080) which doesn't have the `semantic_search` tool. Fixed by configuring the app to connect to the local MCP server (10.0.2.2:8080) which proxies to the remote server.

## Architecture

### Correct Three-Tier Architecture

**IMPORTANT**: The Android app must connect to the LOCAL MCP server, not directly to the remote server.

```
┌─────────────────┐
│  Android App    │
│   (Emulator)    │
└────────┬────────┘
         │ http://10.0.2.2:8080
         ↓
┌─────────────────────────────┐
│  LOCAL MCP Server           │
│  (Your Computer)            │
│  - 8 tools including:       │
│    • get_joke               │
│    • save_joke              │
│    • get_saved_jokes        │
│    • run_tests              │
│    • create_embedding       │
│    • save_document          │
│    • search_similar (local) │
│    • semantic_search ✅     │
└────────┬────────────────────┘
         │ http://148.253.209.151:8080
         │ (only for semantic_search proxy)
         ↓
┌─────────────────────────────┐
│  REMOTE MCP Server          │
│  (148.253.209.151)          │
│  - search_similar tool      │
│  - Embeddings database      │
│  - Indexed documents        │
└─────────────────────────────┘
```

### Data Flow

```
User Question
    ↓
Android App (ChatViewModel)
    ↓ [Keyword Detection]
Local MCP Server (http://10.0.2.2:8080)
    ↓ [semantic_search tool proxies to remote]
Remote MCP Server (148.253.209.151:8080)
    ↓ [Vector Search in Indexed Documents]
Return Relevant Chunks
    ↓
Local MCP Server
    ↓
Perplexity AI Agent (with context)
    ↓
Final Answer to User
```

## Implementation Details

### 1. Local MCP Server (server/http_mcp_server.py)

**New Tool: `semantic_search`**
- **Purpose**: Acts as a proxy to search the remote MCP server for relevant document chunks
- **Location**: Lines 264-282 (tool definition), 905-1006 (implementation)
- **Parameters**:
  - `query` (required): Question or search text
  - `limit` (optional, default=3): Maximum number of chunks to return

**How it works**:
1. Receives search query from Android app
2. Creates JSON-RPC request for remote server's `search_similar` tool
3. Sends HTTP POST to `http://148.253.209.151:8080`
4. Parses remote server's response
5. Returns relevant document chunks with similarity scores

**Configuration**:
```python
REMOTE_MCP_SERVER = {
    'host': '148.253.209.151',
    'port': 8080,
    'url': 'http://148.253.209.151:8080'
}
```

### 2. Android App Configuration

#### McpServerConfig.kt
Added new tool definition with Russian trigger keywords:
```kotlin
McpToolInfo(
    name = "S",
    emoji = "🌐",
    description = "Семантический поиск релевантных фрагментов...",
    triggerWords = listOf(
        "найди в документах",
        "поиск в базе",
        "что говорится в документах",
        "информация о",
        "расскажи о",
        "что такое",
        "как работает",
        "объясни"
    )
)
```

#### ChatViewModel.kt

**Keyword Detection** (Lines ~87-108):
Created separate `userMentionsSemanticSearch()` function:
```kotlin
private fun userMentionsSemanticSearch(message: String): Boolean {
    val lowerMessage = message.lowercase()
    val keywords = listOf(
        "найди в документах",
        "поиск в базе",
        "что говорится в документах",
        "информация о",
        "расскажи о",
        "что такое",
        "как работает",
        "объясни",
        "документ"
    )
    return keywords.any { lowerMessage.contains(it) }
}
```

**Tool Detection Logic** (Lines ~651-655):
```kotlin
val useJokeTools = isJokeServerEnabled() && userMentionsJokes(userMessage)
val useSemanticSearch = isJokeServerEnabled() && userMentionsSemanticSearch(userMessage)
logD("🎭 Use Agentic API with joke tools: $useJokeTools, semantic search: $useSemanticSearch")
sendWithAgenticApi(userMessage, thinkingMessageIndex, useJokeTools, useSemanticSearch)
```

**Dynamic Tool List Building** (Lines ~681-691):
```kotlin
val tools = buildList {
    if (useJokeTools) {
        add(buildAgenticJokeTool())
        add(buildSaveJokeTool())
        add(buildGetSavedJokesTool())
        add(buildRunTestsTool())
    }
    if (useSemanticSearch) {
        add(buildSemanticSearchTool())
    }
}.takeIf { it.isNotEmpty() }
```

This means:
- If only joke keywords detected: 4 tools sent
- If only semantic search keywords detected: 1 tool sent
- If both detected: 5 tools sent
- If neither: 0 tools (null)

**Tool Execution** (Lines ~394-420):
- Added `semantic_search` case in tool execution handler
- Follows same pattern as other tools (get_joke, save_joke, etc.)
- Logs execution with 🌐 emoji for easy debugging

**Updated Welcome Message**:
```kotlin
🌐 Семантический поиск в документах:
• 'найди в документах', 'что такое', 'объясни', 'расскажи о'
  — поиск релевантной информации
```

## Usage Examples

### Example 1: Direct Document Search
**User**: "Найди в документах информацию о REST API"

**Flow**:
1. Keyword "найди в документах" triggers semantic_search tool
2. Local server forwards query to remote server
3. Remote server searches indexed documents
4. Returns top 3 most relevant chunks
5. AI agent uses chunks to generate comprehensive answer

### Example 2: Question Answering
**User**: "Что такое MCP протокол?"

**Flow**:
1. Keyword "что такое" triggers semantic_search tool
2. Searches for "MCP протокол" in remote document database
3. Retrieves relevant context about MCP
4. AI generates answer based on retrieved context

### Example 3: Explanation Request
**User**: "Объясни, как работает Ollama embeddings"

**Flow**:
1. Keyword "объясни" triggers semantic_search tool
2. Searches for Ollama embeddings documentation
3. Returns relevant technical chunks
4. AI provides detailed explanation using context

## Testing

### 1. Local Server Test
```bash
cd server
python3 http_mcp_server.py
```

### 2. Manual Tool Call Test
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "semantic_search",
      "arguments": {
        "query": "Что такое MCP протокол?",
        "limit": 3
      }
    }
  }'
```

**Expected Response (with Citations)**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [{
      "type": "text",
      "text": "{
        \"success\": true,
        \"count\": 3,
        \"documents\": [
          {
            \"id\": 1,
            \"content\": \"...\",
            \"similarity\": 0.85,
            \"created_at\": \"2025-01-31 22:00:00\",
            \"citation\": \"[api_guide.pdf, стр. 5, фрагмент 2/10]\",
            \"citation_info\": {
              \"source_file\": \"api_guide.pdf\",
              \"source_type\": \"pdf\",
              \"chunk_index\": 1,
              \"page_number\": 5,
              \"total_chunks\": 10,
              \"formatted\": \"[api_guide.pdf, стр. 5, фрагмент 2/10]\"
            }
          }
        ],
        \"source\": \"remote_mcp_server\",
        \"sources_summary\": [\"api_guide.pdf (3 фрагмента)\"]
      }"
    }]
  }
}
```

### 3. Android App Test
1. Build and install the app: `./gradlew installDebug`
2. Start local MCP server: `cd server && python3 http_mcp_server.py`
3. In the app, send message: "Найди в документах информацию о REST API"
4. Observe logs for 🌐 semantic search execution
5. Verify AI response includes retrieved context

## Error Handling

The implementation handles several error cases:

1. **Remote Server Unreachable**: Returns error message with connection details
2. **No Documents Found**: Returns success=true with empty documents array
3. **Remote Server Error**: Parses and returns error from remote MCP server
4. **Network Timeout**: 30-second timeout prevents hanging
5. **Invalid Query**: Returns error if query parameter is empty

## Bug Fix: Separated Keyword Detection

### Problem Identified

The initial implementation had a critical bug where semantic search keywords were incorrectly added to the `userMentionsJokes()` function. This caused semantic search keywords to trigger joke tools instead of the semantic_search tool.

**Symptom:**
```
📤 Sending Agentic request with 4 tools
```
When it should have been 5 tools (or 1 tool for pure semantic search queries).

### Root Cause

**Before (Incorrect):**
```kotlin
private fun userMentionsJokes(message: String): Boolean {
    val keywords = listOf(
        "шутк", "анекдот",
        // ... joke keywords ...
        "расскажи о",  // ❌ Wrong! Should trigger semantic search
        "что такое",   // ❌ Wrong!
        "объясни"      // ❌ Wrong!
    )
}
```

When the user said "Расскажи о bakemono", it triggered `useJokeTools = true`, but the tools list only included 4 joke tools (not semantic_search).

### Solution

1. **Created separate function** `userMentionsSemanticSearch()` with correct keywords
2. **Removed semantic search keywords** from `userMentionsJokes()`
3. **Updated sendWithAgenticApi()** signature to accept both flags:
```kotlin
private suspend fun sendWithAgenticApi(
    userMessage: String,
    thinkingMessageIndex: Int,
    useJokeTools: Boolean = false,
    useSemanticSearch: Boolean = false  // ✅ New parameter
)
```

4. **Created buildSemanticSearchTool()** function (Lines 248-277):
```kotlin
private fun buildSemanticSearchTool(): AgenticTool {
    val parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Question or search text to find relevant document chunks")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of relevant chunks to return")
                put("default", 3)
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("query"))
        }
    }

    return AgenticTool(
        type = "function",
        name = "semantic_search",
        description = "Search for relevant document chunks from indexed documents using semantic similarity...",
        parameters = parameters
    )
}
```

5. **Updated buildInstructions()** to handle semantic search:
```kotlin
if (useSemanticSearch) {
    instructions.add("""
When user asks a question or requests information about a topic, FIRST use the semantic_search tool to find relevant document chunks.
After receiving document chunks, use them as context to answer the user's question accurately.
Include the information from the retrieved documents in your answer.
If no relevant documents are found, answer based on your general knowledge and mention that no specific documents were found.""".trimIndent())
}
```

### Verification

**Expected behavior after fix:**

**Query: "Расскажи о bakemono"**
```
🎭 Use Agentic API with joke tools: false, semantic search: true
📤 Sending Agentic request with 1 tools
🌐 Calling MCP server semantic_search
```

**Query: "Расскажи шутку"**
```
🎭 Use Agentic API with joke tools: true, semantic search: false
📤 Sending Agentic request with 4 tools
🎭 Calling MCP server get_joke
```

**Query: "Расскажи шутку о документах"** (both keywords)
```
🎭 Use Agentic API with joke tools: true, semantic search: true
📤 Sending Agentic request with 5 tools
```

## Bug Fix: Connection Architecture

### Problem Identified

The Android app was incorrectly configured to connect **directly to the remote MCP server** at `148.253.209.151:8080`, which doesn't have the `semantic_search` tool.

**Error in logs:**
```
20:39:43.280 McpClient  D  Ktor: REQUEST: http://148.253.209.151:8080
20:39:43.504 ChatViewModel  E  🌐 Semantic search failed
                              java.lang.Exception: Tool call failed: Unknown tool: semantic_search
```

### Root Cause

**Before (❌ WRONG):**
```kotlin
// ServerConfig.kt
object ServerConfig {
    const val MCP_SERVER_URL = "http://148.253.209.151:8080"  // Direct to remote!
}
```

This caused the Android app to bypass the local MCP server entirely, trying to call `semantic_search` on the remote server which only has `search_similar`.

### Solution

Updated `ServerConfig.kt` to use the local server:

**After (✅ CORRECT):**
```kotlin
package com.example.aiwithlove.util

object ServerConfig {
    // Local MCP server URL (proxies to remote server for semantic_search)
    // For Android emulator: use 10.0.2.2 (emulator's special alias for host machine)
    // For physical device: use your computer's local IP address
    const val MCP_SERVER_URL = "http://10.0.2.2:8080"

    // Remote MCP server (used by local server, not directly by Android app)
    const val REMOTE_MCP_SERVER_URL = "http://148.253.209.151:8080"
}
```

### Why 10.0.2.2?

Android emulator uses a special IP address to access the host machine:
- `10.0.2.2` = Your computer's localhost from emulator's perspective
- This maps to `localhost:8080` on your development machine

For **physical devices**, change this to your computer's local IP (e.g., `192.168.1.100:8080`).

### Complete Flow After Fix

**User Query: "Расскажи о bakemono"**

1. **Keyword Detection** (ChatViewModel.kt)
   ```kotlin
   val useSemanticSearch = isJokeServerEnabled() && userMentionsSemanticSearch(userMessage)
   // Result: true (matches "расскажи о")
   ```

2. **Tool Building**
   ```kotlin
   val tools = buildList {
       if (useSemanticSearch) {
           add(buildSemanticSearchTool())  // ✅ Added to list
       }
   }
   // Tools sent to API: 1 (semantic_search)
   ```

3. **API Response**
   ```json
   {
     "tool_call": "semantic_search",
     "arguments": {"query": "bakemono", "limit": 3}
   }
   ```

4. **Android App Executes Tool**
   ```
   McpClient → http://10.0.2.2:8080 (LOCAL server) ✅
   Tool: semantic_search
   ```

5. **Local Server Proxies to Remote**
   ```python
   def tool_semantic_search(args):
       # Call remote server's search_similar tool
       remote_url = 'http://148.253.209.151:8080'
       request = {
           'method': 'tools/call',
           'params': {
               'name': 'search_similar',  # Remote has this tool
               'arguments': {'query': 'bakemono', 'limit': 3}
           }
       }
       # Returns relevant document chunks
   ```

6. **Response Chain**
   ```
   Remote Server → Local Server → Android App → Perplexity API
   ```

7. **AI Answer**
   - Perplexity receives document chunks as context
   - Generates answer based on retrieved information
   - User sees comprehensive response about bakemono

### Verification

**Expected logs after fix:**
```
🎭 Use Agentic API with joke tools: false, semantic search: true
📤 Sending Agentic request with 1 tools
🌐 Calling MCP server semantic_search
McpClient: REQUEST: http://10.0.2.2:8080  ✅ Correct!
McpClient: RESPONSE: 200 OK
✅ Successfully received Agentic response
```

**Test with curl:**
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | grep semantic_search
```

## Deployment

### Deploy to Remote Server
If you need to update the remote MCP server with the same semantic_search capability:

```bash
cd server
./deploy_to_remote.sh
# Enter: 148.253.209.151
# Enter: root
```

This copies `http_mcp_server.py` to the remote server and restarts it.

## Credentials

Remote MCP server access is configured in `app/src/main/java/com/example/aiwithlove/util/SecureData.kt`:
```kotlin
object SecureData {
    val serverIp = "148.253.209.151"
    val serverPort = 22
    val serverLogin = "root"
    val serverPassword = "H1L8gvy075OGDub"
}
```

**Security Note**: These credentials are used for context but the actual HTTP connection to the MCP server uses port 8080 (HTTP), not SSH.

## Files Modified

1. **server/http_mcp_server.py**
   - Added `REMOTE_MCP_SERVER` configuration (line ~20)
   - Added `semantic_search` tool definition (lines 264-282)
   - Added `tool_semantic_search()` method (lines 905-1006)
   - Updated tool count in startup message (line 932)

2. **app/src/main/java/com/example/aiwithlove/util/ServerConfig.kt**
   - **CRITICAL FIX**: Changed `MCP_SERVER_URL` from `http://148.253.209.151:8080` to `http://10.0.2.2:8080`
   - Added `REMOTE_MCP_SERVER_URL` constant for reference
   - Added comments explaining emulator vs. physical device configuration

3. **app/src/main/java/com/example/aiwithlove/mcp/McpServerConfig.kt**
   - Added `semantic_search` tool info with Russian trigger words (lines 55-62)

4. **app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt**
   - Line ~120: Added `userMentionsSemanticSearch()` function
   - Line ~80-106: Cleaned up `userMentionsJokes()` (removed semantic search keywords)
   - Line ~248-277: Added `buildSemanticSearchTool()` function
   - Line ~547-581: Updated `buildInstructions()` with semantic search parameter
   - Line ~651-655: Updated tool detection logic with separate flags
   - Line ~658-662: Updated `sendWithAgenticApi()` signature
   - Line ~681-691: Dynamic tools list building
   - Line ~394-420: Added `semantic_search` tool execution handler
   - Line ~1005-1008: Updated welcome message with semantic search info

## Server Management

### Start Local MCP Server
```bash
cd server
python3 http_mcp_server.py
```

Server will start on `http://0.0.0.0:8080` (accessible from emulator at `http://10.0.2.2:8080`)

### Stop Server
```bash
# Find process ID
ps aux | grep http_mcp_server | grep -v grep

# Kill process
kill <PID>

# Or use one-liner
kill $(ps aux | grep http_mcp_server | grep -v grep | awk '{print $2}')
```

### View Server Logs
```bash
# If running in background
tail -f server/server.log

# Follow logs in real-time
tail -f server/server.log | grep -E "semantic_search|ERROR|Tool"
```

### Restart Server
```bash
kill $(ps aux | grep http_mcp_server | grep -v grep | awk '{print $2}')
cd server
python3 http_mcp_server.py
```

### Verify Server Status
```bash
# Check if server is running
ps aux | grep http_mcp_server | grep -v grep

# Test server endpoint
curl http://localhost:8080 -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | jq

# Check available tools (should include semantic_search)
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | grep -o '"name":"[^"]*"' | cut -d'"' -f4
```

Expected output should include:
- get_joke
- save_joke
- get_saved_jokes
- run_tests
- create_embedding
- save_document
- search_similar
- semantic_search ✅

## Next Steps

1. **Test with real queries** in the Android app
2. **Monitor logs** to verify tool is triggered correctly
3. **Verify remote server** has indexed documents
4. **Tune keyword detection** if needed for better trigger accuracy
5. **Adjust `limit` parameter** based on context window optimization

## Troubleshooting

### "Unknown tool: semantic_search"
**Symptom:** Error message saying tool not found
**Root Cause:** Android app connecting directly to remote server instead of local server
**Solution:**
1. Verify `ServerConfig.MCP_SERVER_URL = "http://10.0.2.2:8080"` (not `148.253.209.151`)
2. Rebuild and reinstall app: `./gradlew installDebug`
3. Check logs show: `McpClient: REQUEST: http://10.0.2.2:8080` ✅

### Connection Refused
**Symptom:** App can't connect to server
**Solution:**
```bash
# Check if local server is running
ps aux | grep http_mcp_server

# If not running, start it
cd server
python3 http_mcp_server.py

# Verify it's listening on port 8080
curl http://localhost:8080 -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

### Tool Not Triggered
- Check if message contains trigger keywords
- Verify keywords in `userMentionsSemanticSearch()` function (ChatViewModel.kt ~120)
- Check logs for "🌐 Calling MCP server semantic_search"
- **Common Issue**: If logs show "Use Agentic API with joke tools: true" for semantic queries, keywords are in wrong function
  - Solution: Ensure semantic search keywords are ONLY in `userMentionsSemanticSearch()`, not in `userMentionsJokes()`

### Wrong Tool Count in Logs
- If you see "📤 Sending Agentic request with 4 tools" when expecting semantic search
  - Check that `useSemanticSearch` flag is being set correctly
  - Verify `buildSemanticSearchTool()` is being called in the tools list
  - Ensure keywords are in separate detection function

### Using Physical Device Instead of Emulator
**Issue:** Emulator IP (10.0.2.2) doesn't work on physical device
**Solution:** Change ServerConfig to your computer's local IP:
```kotlin
const val MCP_SERVER_URL = "http://192.168.1.100:8080"  // Your local IP
```

**Find your local IP:**
```bash
# macOS/Linux
ifconfig | grep "inet " | grep -v 127.0.0.1

# Windows
ipconfig
```

### Local Server Can't Connect to Remote
**Symptom:** Local server returns error when calling remote
**Solution:**
- Check remote server is accessible: `curl http://148.253.209.151:8080`
- Verify firewall settings
- Check network connectivity
- Test remote server's `search_similar` tool:
  ```bash
  curl -X POST http://148.253.209.151:8080 \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_similar","arguments":{"query":"test","limit":3}}}'
  ```

### No Documents Returned
- Check if documents are indexed on remote server
- Verify remote server's `search_similar` tool is working
- Test remote server directly (see command above)
- Check remote server logs for errors

### Empty/Invalid Response
- Check local server logs: `tail -f server/server.log`
- Verify JSON-RPC request format
- Check remote server response format matches expected structure
- Ensure remote MCP server has the `search_similar` tool implemented

### Keyword Conflicts
- If both joke and semantic search trigger simultaneously (5 tools)
  - This is expected behavior if message contains both types of keywords
  - Example: "Расскажи шутку о документах" contains both "шутк" and "документ"
  - The AI agent will have access to all tools and choose which to use
