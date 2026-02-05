# save_document Tool Documentation

## Overview

The `save_document` tool saves a document with its embedding to a remote SQLite database. It generates an embedding using Ollama's nomic-embed-text model and stores both the content and embedding vector for later semantic search.

**Tool Type**: Proxied (forwarded to remote MCP server)
**Emoji**: 💾
**Remote Server**: 148.253.209.151:8080
**Database**: embeddings.db (on remote server)
**Embedding Model**: nomic-embed-text (768 dimensions)

---

## Implementation

**File**: `server/http_mcp_server.py`
**Lines**: 774-851
**Method**: `tool_save_document(args: dict) -> dict`

---

## Parameters

### `content` (required)
- **Type**: String
- **Required**: Yes
- **Description**: Document content to save and embed
- **Max Length**: 8192 tokens (~32000 characters)
- **Example**: `"REST API это архитектурный стиль для веб-сервисов, использующий HTTP методы"`

---

## Returns

### Success Response
```json
{
  "success": true,
  "document_id": 42,
  "message": "Document saved successfully with embedding",
  "embedding_dimensions": 768
}
```

### Error Response
```json
{
  "success": false,
  "error": "Failed to save document: remote server timeout",
  "document_id": null
}
```

---

## Implementation Details

### Proxy Architecture

```python
def tool_save_document(args: dict) -> dict:
    try:
        # 1. Extract content
        content = args.get('content', '').strip()

        if not content:
            return {
                'success': False,
                'error': 'Content parameter is required',
                'document_id': None
            }

        # 2. Forward to remote MCP server
        remote_url = 'http://148.253.209.151:8080'
        request_body = {
            'jsonrpc': '2.0',
            'id': 1,
            'method': 'tools/call',
            'params': {
                'name': 'save_document',
                'arguments': {'content': content}
            }
        }

        # 3. Make HTTP request
        req = urllib.request.Request(
            remote_url,
            data=json.dumps(request_body).encode('utf-8'),
            headers={'Content-Type': 'application/json'}
        )

        with urllib.request.urlopen(req, timeout=30) as response:
            data = json.loads(response.read().decode('utf-8'))

        # 4. Parse remote response
        result = data.get('result', {})
        content_list = result.get('content', [])

        if content_list:
            text = content_list[0].get('text', '{}')
            parsed = json.loads(text)

            return {
                'success': parsed.get('success', False),
                'document_id': parsed.get('document_id'),
                'message': parsed.get('message', ''),
                'embedding_dimensions': 768
            }

        return {
            'success': False,
            'error': 'Invalid response from remote server',
            'document_id': None
        }

    except Exception as e:
        return {
            'success': False,
            'error': f'Failed to save document: {str(e)}',
            'document_id': None
        }
```

### Remote Processing

On the remote server:
1. Receives document content
2. Generates embedding via Ollama (nomic-embed-text)
3. Stores content + embedding in embeddings.db
4. Returns document ID

---

## Usage Examples

### Example 1: Save English Document

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "save_document",
      "arguments": {
        "content": "REST API is an architectural style that uses HTTP methods for web services."
      }
    }
  }'
```

**Response**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [{
      "type": "text",
      "text": "{\"success\":true,\"document_id\":42,\"message\":\"Document saved successfully with embedding\",\"embedding_dimensions\":768}"
    }]
  }
}
```

### Example 2: Save Russian Document

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "save_document",
      "arguments": {
        "content": "REST API это архитектурный стиль для веб-сервисов, использующий HTTP методы."
      }
    }
  }'
```

---

## Integration with OllamaScreen

**File**: `app/src/main/java/com/example/aiwithlove/viewmodel/OllamaViewModel.kt`

```kotlin
fun sendMessage(userMessage: String) {
    viewModelScope.launch {
        try {
            _isLoading.value = true

            // Call save_document tool
            val result = mcpClient.callTool(
                toolName = "save_document",
                arguments = buildJsonObject {
                    put("content", userMessage)
                }
            )

            // Parse document ID
            val documentId = parseDocumentId(result)

            // Show success message
            displayTypewriterMessage(
                "✅ Документ сохранён!\n" +
                "ID: $documentId\n" +
                "Текст: \"${userMessage.take(50)}...\""
            )

            // Update document count
            updateDocumentsCount()

        } finally {
            _isLoading.value = false
        }
    }
}
```

See: [OLLAMA_SCREEN.md](../../docs/OLLAMA_SCREEN.md#document-indexing--embedding)

---

## Database Schema (Remote)

```sql
CREATE TABLE documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content TEXT NOT NULL,
    embedding BLOB NOT NULL,  -- 768 float32 values
    metadata TEXT,            -- JSON metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_created_at ON documents(created_at DESC);
```

---

## Error Handling

### Remote Server Unavailable
```json
{
  "success": false,
  "error": "Failed to save document: Connection refused"
}
```

**Solution**: Verify remote server is running at 148.253.209.151:8080

### Ollama Not Running (on remote)
```json
{
  "success": false,
  "error": "Failed to generate embedding: Ollama service unavailable"
}
```

**Solution**: Contact remote server administrator

### Empty Content
```json
{
  "success": false,
  "error": "Content parameter is required"
}
```

**Solution**: Provide non-empty content parameter

---

## Performance

- **Response Time**: 200-500ms (network + embedding generation)
- **Timeout**: 30 seconds
- **Max Content**: 8192 tokens (~32000 characters)
- **Embedding Time**: 100-300ms (on remote server)
- **Network Latency**: ~50-100ms

---

## Related Documentation

- [CREATE_EMBEDDING.md](CREATE_EMBEDDING.md) - Embedding generation
- [SEARCH_SIMILAR.md](SEARCH_SIMILAR.md) - Search saved documents
- [SEMANTIC_SEARCH.md](../SEMANTIC_SEARCH.md) - Threshold-filtered search
- [OLLAMA_SCREEN.md](../../docs/OLLAMA_SCREEN.md) - Document indexing UI

---

## Version

**Day 18**: Реранкинг и фильтрация

**Last Updated**: 2026-02-04
