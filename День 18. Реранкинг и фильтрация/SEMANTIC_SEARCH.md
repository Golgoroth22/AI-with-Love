# Semantic Search Implementation Guide

## Overview

Added a new `semantic_search` tool that retrieves relevant document chunks from a remote MCP server using vector similarity search. This enables RAG (Retrieval Augmented Generation) by finding context from indexed documents to help answer user questions.

## Architecture

```
User Question
    ↓
Android App (ChatViewModel)
    ↓ [Keyword Detection]
Local MCP Server (http_mcp_server.py)
    ↓ [HTTP JSON-RPC Request]
Remote MCP Server (148.253.209.151:8080)
    ↓ [Vector Search in Indexed Documents]
Return Relevant Chunks
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
    name = "semantic_search",
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
- Added semantic search keywords to trigger tool enablement
- Keywords like "найди в документах", "что такое", "объясни", etc.

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

**Expected Response**:
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
            \"created_at\": \"2025-01-31 22:00:00\"
          }
        ],
        \"source\": \"remote_mcp_server\"
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

2. **app/src/main/java/com/example/aiwithlove/mcp/McpServerConfig.kt**
   - Added `semantic_search` tool info with Russian trigger words (lines 55-62)

3. **app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt**
   - Added semantic search keywords to detection list (lines ~101-108)
   - Added `semantic_search` tool execution handler (lines ~394-420)
   - Updated welcome message with semantic search info (lines ~1005-1008)

## Next Steps

1. **Test with real queries** in the Android app
2. **Monitor logs** to verify tool is triggered correctly
3. **Verify remote server** has indexed documents
4. **Tune keyword detection** if needed for better trigger accuracy
5. **Adjust `limit` parameter** based on context window optimization

## Troubleshooting

### Tool Not Triggered
- Check if message contains trigger keywords
- Verify keywords in ChatViewModel.kt (lines ~87-108)
- Check logs for "🌐 Calling MCP server semantic_search"

### Connection Failed
- Verify remote server is running: `curl http://148.253.209.151:8080`
- Check firewall rules on remote server
- Verify port 8080 is accessible

### No Documents Returned
- Check if documents are indexed on remote server
- Verify remote server's `search_similar` tool is working
- Test remote server directly: `curl -X POST http://148.253.209.151:8080 ...`

### Empty/Invalid Response
- Check local server logs: `tail -f server/server.log`
- Verify JSON-RPC request format
- Check remote server response format matches expected structure
