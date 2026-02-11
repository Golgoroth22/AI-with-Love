# save_document Tool (Day 21: Local Processing)

## Overview

Saves a single document with its embedding to the **local SQLite database**. As of Day 21, all processing happens locally using Ollama on your Mac.

## Architecture (Day 21)

```
MCP Server (save_document)
    ↓ [Generate embedding]
Local Ollama (localhost:11434)
    ↓ [nomic-embed-text: 768 dimensions]
Local SQLite (/server/data/embeddings.db)
    ↓ [Save document + embedding + metadata]
Return: document_id
```

## Parameters

- `content` (required): The document text content
- `source_file` (optional, default="manual_entry"): Source filename
- `source_type` (optional, default="manual"): Type of document ("pdf", "txt", "markdown", "manual")
- `chunk_index` (optional, default=0): Position in multi-chunk document
- `page_number` (optional): Page number for PDFs
- `total_chunks` (optional, default=1): Total chunks for this document
- `metadata` (optional, default="{}"): Additional metadata as JSON string

## Response Format

```json
{
  "success": true,
  "document_id": 123,
  "message": "Document saved successfully with embedding",
  "embedding_dimensions": 768
}
```

## Processing Flow

1. **Validate** content is not empty
2. **Generate Embedding** via local Ollama (`tool_create_embedding`)
3. **Save to Database** via `EmbeddingsDatabase.save_document_with_embedding()`
4. **Return** document ID and success status

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
      "name": "save_document",
      "arguments": {
        "content": "REST API documentation for authentication",
        "source_file": "api_guide.pdf",
        "source_type": "pdf",
        "chunk_index": 5,
        "page_number": 12,
        "total_chunks": 20
      }
    }
  }'
```

## Database Schema

Saved to `documents` table:
```sql
CREATE TABLE documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content TEXT NOT NULL,
    embedding BLOB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source_file TEXT DEFAULT 'unknown',
    source_type TEXT DEFAULT 'manual',
    chunk_index INTEGER DEFAULT 0,
    page_number INTEGER,
    total_chunks INTEGER DEFAULT 1,
    metadata TEXT DEFAULT '{}'
);
```

## Day 21 Changes

- **Local Processing**: Removed remote proxy, uses local Ollama
- **Direct Database**: Uses `EmbeddingsDatabase` helper class
- **Faster**: No network overhead
- **Same API**: Backward compatible response format
