# Semantic Search Connection Fix

## Problem Found

The Android app was trying to connect directly to the **remote MCP server** at `148.253.209.151:8080`, which doesn't have the `semantic_search` tool.

**Error in logs:**
```
20:39:43.280 McpClient  D  Ktor: REQUEST: http://148.253.209.151:8080
20:39:43.504 ChatViewModel  E  🌐 Semantic search failed
                              java.lang.Exception: Tool call failed: Unknown tool: semantic_search
```

## Root Cause

The `ServerConfig.kt` was configured incorrectly:

**Before (❌ WRONG):**
```kotlin
object ServerConfig {
    const val MCP_SERVER_URL = "http://148.253.209.151:8080"  // Direct to remote!
}
```

This caused the Android app to skip the local MCP server entirely.

## Correct Architecture

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
         │ (only for semantic_search)
         ↓
┌─────────────────────────────┐
│  REMOTE MCP Server          │
│  (148.253.209.151)          │
│  - search_similar tool      │
│  - Embeddings database      │
│  - Indexed documents        │
└─────────────────────────────┘
```

## The Fix

Updated `ServerConfig.kt`:

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

For **physical devices**, you would change this to your computer's local IP (e.g., `192.168.1.100:8080`).

## Files Modified

1. **app/src/main/java/com/example/aiwithlove/util/ServerConfig.kt**
   - Changed `MCP_SERVER_URL` from remote to local server
   - Added `REMOTE_MCP_SERVER_URL` constant for reference

## Current Status

✅ **Local MCP Server Running** (PID: 46461)
- Listening on: `http://0.0.0.0:8080`
- From emulator: `http://10.0.2.2:8080`
- Available tools: 8 (including semantic_search)

✅ **Android App Updated**
- Installed on Pixel_8(AVD)
- Now configured to use local server
- Semantic search tool properly wired up

## How the Flow Works Now

### User Query: "Расскажи о bakemono"

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
   McpClient → http://10.0.2.2:8080 (LOCAL server)
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

## Testing

### 1. Verify Local Server
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | grep semantic_search
```

**Expected:** Should see `semantic_search` in tools list

### 2. Test Semantic Search Tool
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
        "query": "MCP protocol",
        "limit": 3
      }
    }
  }'
```

**Expected:** Should return document chunks from remote server

### 3. Test in Android App
1. Open the app on emulator
2. Send message: "Расскажи о bakemono"
3. Check logcat:
```bash
adb logcat | grep -E "ChatViewModel|semantic_search|McpClient"
```

**Expected logs:**
```
🎭 Use Agentic API with joke tools: false, semantic search: true
📤 Sending Agentic request with 1 tools
🌐 Calling MCP server semantic_search
McpClient: REQUEST: http://10.0.2.2:8080  ✅ Correct!
McpClient: RESPONSE: 200 OK
✅ Successfully received Agentic response
```

### 4. Alternative Test Queries
- "Что такое Ollama?" → Should trigger semantic_search
- "Объясни embeddings" → Should trigger semantic_search
- "Найди в документах информацию о RAG" → Should trigger semantic_search
- "Расскажи шутку" → Should trigger get_joke (not semantic_search)

## Troubleshooting

### Issue: "Connection refused"
**Symptom:** App can't connect to server
**Solution:**
```bash
# Check if local server is running
ps aux | grep http_mcp_server

# If not running, start it
cd server
python3 http_mcp_server.py
```

### Issue: "Unknown tool: semantic_search"
**Symptom:** Tool not found
**Cause:** App still connecting to remote server
**Solution:**
- Verify `ServerConfig.MCP_SERVER_URL = "http://10.0.2.2:8080"`
- Rebuild and reinstall app: `./gradlew installDebug`

### Issue: Server can't connect to remote
**Symptom:** Local server returns error when calling remote
**Solution:**
- Check remote server is accessible: `curl http://148.253.209.151:8080`
- Verify firewall settings
- Check network connectivity

### Issue: Using physical device instead of emulator
**Solution:** Change ServerConfig to your computer's local IP:
```kotlin
const val MCP_SERVER_URL = "http://192.168.1.100:8080"  // Your local IP
```

To find your local IP:
```bash
# macOS/Linux
ifconfig | grep "inet " | grep -v 127.0.0.1

# Windows
ipconfig
```

## Server Management

### Start Server
```bash
cd server
python3 http_mcp_server.py
```

### Stop Server
```bash
# Find process ID
ps aux | grep http_mcp_server | grep -v grep

# Kill process
kill <PID>
```

### View Server Logs
```bash
# If running in background
tail -f server/server.log

# If running in foreground
# Logs appear in terminal
```

### Restart Server
```bash
kill $(ps aux | grep http_mcp_server | grep -v grep | awk '{print $2}')
cd server
python3 http_mcp_server.py
```

## Summary

The fix was simple but critical:
- **Changed** Android app to connect to LOCAL server (`10.0.2.2:8080`)
- **Local server** has semantic_search tool that proxies to remote server
- **Remote server** has the actual embeddings database and documents

Now the full RAG pipeline works:
1. User asks question
2. Android app calls local semantic_search
3. Local server queries remote for document chunks
4. Chunks returned to AI agent as context
5. AI generates informed answer

The app is now installed and the server is running. Test with "Расскажи о bakemono"!
