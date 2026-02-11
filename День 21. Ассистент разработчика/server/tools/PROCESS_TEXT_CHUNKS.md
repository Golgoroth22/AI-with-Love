# process_text_chunks Tool (Day 21: Local Processing)

## Overview

Processes extracted text by chunking it and generating embeddings for each chunk using **local Ollama**, then saves to **local SQLite database**. As of Day 21, all processing happens locally on your Mac.

## Parameters

- `text` (required): The extracted text content to process
- `filename` (required): Original filename (used for `source_file` metadata)
- `chunk_size` (optional, default=1000): Size of each chunk in characters
- `chunk_overlap` (optional, default=200): Overlap between chunks in characters

## Response Format

```json
{
  "success": true,
  "chunks_saved": 12,
  "total_characters": 10500,
  "filename": "document.txt",
  "chunk_size": 1000,
  "chunk_overlap": 200,
  "processing_time_seconds": 5.42
}
```

## Processing Flow

1. **Determine Source Type**:
   - `.pdf` → `source_type = "pdf"`
   - `.txt` → `source_type = "txt"`
   - `.md` → `source_type = "markdown"` ✨ NEW in Day 21!

2. **Chunk Text** (using `_chunk_text()` method):
   - Split into chunks of `chunk_size` characters
   - Maintain `chunk_overlap` between chunks
   - Try to break at sentence boundaries (`.` or `\n\n`)
   - Only break if sentence ending is within last 200 chars of target size

3. **Process Each Chunk**:
   - Generate embedding via `tool_create_embedding()` (calls local Ollama)
   - Save to local database via `EmbeddingsDatabase.save_document_with_embedding()`
   - Track: `source_file`, `source_type`, `chunk_index`, `total_chunks`

4. **Return Results**: Total chunks saved and processing time

## Architecture (Day 21)

```
Android App
    ↓ [Upload file: PDF/TXT/MD]
    ↓ [Extract text client-side]
MCP Server (process_text_chunks)
    ↓ [Chunk text: 1000 chars, 200 overlap]
    ↓ [For each chunk:]
Local Ollama (localhost:11434)
    ↓ [Generate embedding: 768 dimensions]
Local SQLite (/server/data/embeddings.db)
    ↓ [Save chunk + embedding + metadata]
Return: chunks_saved count
```

## Supported File Types

| Extension | Source Type | Processing |
|-----------|-------------|------------|
| `.pdf` | `pdf` | Client-side extraction (PDFBox), then chunk |
| `.txt` | `txt` | Direct text read, then chunk |
| `.md` | `markdown` | Direct text read, then chunk (NEW!) |

## Example Usage

### From Android App (OllamaViewModel)

```kotlin
val result = mcpClient.callTool(
    toolName = "process_text_chunks",
    arguments = mapOf(
        "text" to extractedText,
        "filename" to fileName  // e.g., "guide.md"
    )
)
```

### Via curl

```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "process_text_chunks",
      "arguments": {
        "text": "Your long document text here...",
        "filename": "README.md"
      }
    }
  }'
```

## Chunking Strategy

**Smart Chunking**:
- Default: 1000 characters per chunk, 200 character overlap
- Breaks at sentence boundaries when possible
- Prevents cutting words mid-sentence
- Maintains context across chunks

**Why Overlap?**:
- Prevents information loss at chunk boundaries
- Improves semantic search accuracy
- Default 200 chars = ~30-40 words of context

## Performance

**Local Processing**:
- ~100-200ms per chunk (embedding generation)
- 10-chunk document: ~2-3 seconds total
- 50-chunk document: ~10-15 seconds total

**Optimization Tips**:
- Larger `chunk_size` = fewer chunks = faster processing
- Smaller `chunk_overlap` = fewer total chunks
- Balance: quality vs. speed

## Metadata Stored

Each chunk saves:
- `content`: The actual text
- `embedding`: 768-dimensional vector (BLOB)
- `source_file`: Original filename
- `source_type`: "pdf", "txt", or "markdown"
- `chunk_index`: Position in document (0-based)
- `total_chunks`: Total number of chunks from this file
- `page_number`: NULL (can be enhanced for PDFs)
- `created_at`: Timestamp

## Testing

```bash
# Test with short text
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "process_text_chunks",
      "arguments": {
        "text": "Short test document.",
        "filename": "test.txt"
      }
    }
  }'

# Verify in database
sqlite3 server/data/embeddings.db \
  "SELECT source_file, source_type, chunk_index, total_chunks FROM documents WHERE source_file='test.txt'"
```

## Day 21 Changes

- **Architecture**: Removed remote proxy, all processing local
- **Markdown Support**: Added `.md` file detection → `source_type="markdown"`
- **Performance**: Faster due to local Ollama (no network overhead)
- **Database Helpers**: Uses new `EmbeddingsDatabase` class
- **Error Handling**: Added traceback logging for debugging
