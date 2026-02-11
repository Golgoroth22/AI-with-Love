# search_similar Tool (Day 21: Local Processing)

## Overview

Searches for similar documents in the **local SQLite database** using cosine similarity on vector embeddings. As of Day 21, all processing happens locally.

## Architecture (Day 21)

```
MCP Server (search_similar)
    ↓ [Generate query embedding]
Local Ollama (localhost:11434)
    ↓ [nomic-embed-text: 768 dimensions]
Local SQLite (/server/data/embeddings.db)
    ↓ [Cosine similarity search across all documents]
    ↓ [Sort by similarity descending]
Return: Top N documents with scores
```

## Parameters

- `query` (required): Search query text
- `limit` (optional, default=5): Maximum number of documents to return

## Response Format

```json
{
  "success": true,
  "count": 3,
  "documents": [
    {
      "id": 123,
      "content": "REST API authentication uses OAuth 2.0...",
      "similarity": 0.89,
      "source_file": "api_guide.pdf",
      "source_type": "pdf",
      "chunk_index": 12,
      "page_number": 15,
      "total_chunks": 45,
      "metadata": "{}"
    }
  ]
}
```

## Processing Flow

1. **Generate Query Embedding** via local Ollama
2. **Load All Documents** from local database
3. **Deserialize Embeddings** from BLOB format
4. **Calculate Cosine Similarity** for each document
5. **Sort by Similarity** (descending)
6. **Return Top N** documents

## Cosine Similarity

**Formula**:
```
similarity = dot_product(vec1, vec2) / (magnitude(vec1) * magnitude(vec2))
```

**Range**: -1.0 to 1.0 (typically 0.0 to 1.0 for similar content)
- 1.0 = Identical vectors
- 0.7-0.9 = Very similar
- 0.5-0.7 = Moderately similar
- <0.5 = Less relevant

## Example Usage

### Via curl
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
        "query": "REST API authentication",
        "limit": 5
      }
    }
  }'
```

### From semantic_search Tool

The `semantic_search` tool internally calls `search_similar` and adds threshold filtering.

## Performance

**Local Processing**:
- Query embedding: ~50-100ms (Ollama)
- Similarity calculation: ~10-50ms (depends on document count)
- Total: ~60-150ms per search

**Optimization**:
- Pre-computed embeddings stored as BLOBs
- Indexed by `source_file` for faster lookups
- Efficient SQLite queries

## Day 21 Changes

- **Local Processing**: Removed remote proxy
- **Direct Database**: Uses `EmbeddingsDatabase.search_similar_documents()`
- **Cosine Similarity**: Implemented locally in Python
- **Faster**: No network overhead, ~50-100ms per search
- **Same API**: Backward compatible response format
