# search_similar Tool Documentation

## Overview

The `search_similar` tool searches for documents similar to a query using cosine similarity on embedding vectors. It's the base search functionality used by `semantic_search` for RAG applications.

**Tool Type**: Proxied (forwarded to remote MCP server)
**Emoji**: 🔍
**Remote Server**: 148.253.209.151:8080
**Similarity Metric**: Cosine similarity
**Database**: embeddings.db (on remote server)

---

## Implementation

**File**: `server/http_mcp_server.py`
**Lines**: 853-935
**Method**: `tool_search_similar(args: dict) -> dict`

---

## Parameters

### `query` (required)
- **Type**: String
- **Required**: Yes
- **Description**: Search query text
- **Example**: `"REST API архитектура"`

### `limit` (optional)
- **Type**: Integer
- **Required**: No
- **Default**: 5
- **Description**: Maximum number of results to return
- **Valid Range**: 1-100
- **Example**: `10`

---

## Returns

### Success Response (with Citations - Day 19)
```json
{
  "success": true,
  "count": 3,
  "documents": [
    {
      "id": 42,
      "content": "REST API это архитектурный стиль для веб-сервисов...",
      "similarity": 0.89,
      "created_at": "2026-02-04 15:30:00",
      "source_file": "api_guide.pdf",
      "source_type": "pdf",
      "chunk_index": 5,
      "page_number": 12,
      "total_chunks": 45,
      "metadata": "{\"author\": \"John Doe\", \"title\": \"API Guide v2.0\"}"
    },
    {
      "id": 15,
      "content": "HTTP методы используются в REST API для операций CRUD...",
      "similarity": 0.76,
      "created_at": "2026-02-03 10:15:00",
      "source_file": "http_methods.txt",
      "source_type": "txt",
      "chunk_index": 2,
      "page_number": null,
      "total_chunks": 8,
      "metadata": "{}"
    },
    {
      "id": 8,
      "content": "Веб-сервисы могут использовать различные архитектурные стили...",
      "similarity": 0.65,
      "created_at": "2026-02-02 14:20:00",
      "source_file": "unknown",
      "source_type": "manual",
      "chunk_index": 0,
      "page_number": null,
      "total_chunks": 1,
      "metadata": "{}"
    }
  ],
  "source": "remote_mcp_server"
}
```

**New Citation Fields (Day 19)**:
- `source_file`: Original filename or "unknown" for legacy documents
- `source_type`: File type ("pdf", "txt", "manual")
- `chunk_index`: Position of chunk in document (0-based)
- `page_number`: PDF page number (null for non-PDF)
- `total_chunks`: Total chunks from this source file
- `metadata`: JSON string with additional metadata

### Empty Results
```json
{
  "success": true,
  "count": 0,
  "documents": [],
  "source": "remote_mcp_server"
}
```

### Error Response
```json
{
  "success": false,
  "error": "Failed to search: remote server timeout",
  "count": 0,
  "documents": []
}
```

---

## Implementation Details

### Proxy to Remote Server

```python
def tool_search_similar(args: dict) -> dict:
    try:
        # 1. Extract parameters
        query = args.get('query', '').strip()
        limit = args.get('limit', 5)

        if not query:
            return {
                'success': False,
                'error': 'Query parameter is required',
                'count': 0,
                'documents': []
            }

        # Validate limit
        if limit < 1:
            limit = 1
        elif limit > 100:
            limit = 100

        # 2. Forward to remote server
        remote_url = 'http://148.253.209.151:8080'
        request_body = {
            'jsonrpc': '2.0',
            'id': 1,
            'method': 'tools/call',
            'params': {
                'name': 'search_similar',
                'arguments': {
                    'query': query,
                    'limit': limit
                }
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

        # 4. Parse and return results
        result = data.get('result', {})
        content_list = result.get('content', [])

        if content_list:
            text = content_list[0].get('text', '{}')
            parsed = json.loads(text)

            return {
                'success': parsed.get('success', False),
                'count': parsed.get('count', 0),
                'documents': parsed.get('documents', []),
                'source': 'remote_mcp_server'
            }

        return {
            'success': False,
            'error': 'Invalid response from remote server',
            'count': 0,
            'documents': []
        }

    except Exception as e:
        return {
            'success': False,
            'error': f'Failed to search: {str(e)}',
            'count': 0,
            'documents': []
        }
```

### Remote Processing

On remote server:
1. Generate embedding for query text
2. Calculate cosine similarity with all document embeddings
3. Sort by similarity score (highest first)
4. Return top N results

---

## Usage Examples

### Example 1: Basic Search

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "search_similar",
      "arguments": {
        "query": "REST API architecture",
        "limit": 5
      }
    }
  }'
```

**Response**: Returns top 5 most similar documents

### Example 2: Search with Custom Limit

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "search_similar",
      "arguments": {
        "query": "HTTP методы",
        "limit": 10
      }
    }
  }'
```

---

## Similarity Scoring

### Cosine Similarity Range
- **1.0**: Identical documents
- **0.9 - 1.0**: Very similar
- **0.7 - 0.9**: Similar (good matches)
- **0.5 - 0.7**: Somewhat related
- **0.3 - 0.5**: Weakly related
- **< 0.3**: Different topics

### Sort Order
Results are sorted by similarity score in descending order (most similar first).

---

## Used By semantic_search

The `semantic_search` tool uses `search_similar` as its base, then applies threshold filtering:

```python
# In semantic_search tool
raw_results = search_similar(query, limit * 2)
filtered_results = [
    doc for doc in raw_results
    if doc['similarity'] >= threshold
]
return filtered_results[:limit]
```

See: [SEMANTIC_SEARCH.md](../SEMANTIC_SEARCH.md)

---

## Error Handling

### Remote Server Unavailable
```json
{
  "success": false,
  "error": "Failed to search: Connection refused"
}
```

**Solution**: Verify remote server at 148.253.209.151:8080

### Empty Query
```json
{
  "success": false,
  "error": "Query parameter is required"
}
```

**Solution**: Provide non-empty query parameter

### No Documents Found
```json
{
  "success": true,
  "count": 0,
  "documents": []
}
```

**Solution**: This is normal - no similar documents exist yet

---

## Performance

- **Response Time**: 100-300ms
- **Timeout**: 30 seconds
- **Similarity Calculation**: O(n) where n = number of documents
- **Network Latency**: ~50-100ms

---

## Related Documentation

- [SEMANTIC_SEARCH.md](../SEMANTIC_SEARCH.md) - Threshold-filtered search
- [SAVE_DOCUMENT.md](SAVE_DOCUMENT.md) - Save documents for searching
- [CREATE_EMBEDDING.md](CREATE_EMBEDDING.md) - Embedding generation

---

## Version

**Day 19**: Цитаты и источники - Added citation fields to all documents

**Day 18**: Реранкинг и фильтрация

**Last Updated**: 2026-02-05
