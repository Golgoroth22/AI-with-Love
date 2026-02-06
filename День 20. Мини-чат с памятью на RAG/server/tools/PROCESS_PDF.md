# process_pdf Tool Documentation

## Overview

The `process_pdf` tool processes PDF files by extracting text, chunking it into smaller pieces, generating embeddings for each chunk, and saving everything to the database. This enables semantic search across PDF documents.

**Tool Type**: Proxied (forwarded to remote MCP server)
**Emoji**: 📄
**Remote Server**: 148.253.209.151:8080
**Timeout**: 120 seconds (for large PDFs)
**Embedding Model**: nomic-embed-text (768 dimensions)

---

## Implementation

**File**: `server/http_mcp_server.py`
**Lines**: 1074-1172
**Method**: `tool_process_pdf(args: dict) -> dict`

---

## Parameters

### `pdf_base64` (required)
- **Type**: String (base64-encoded)
- **Required**: Yes
- **Description**: PDF file content encoded in base64
- **Example**: `"JVBERi0xLjQKJeLjz9MKMyAwIG9iago8PAovVHlwZS..."`

### `filename` (required)
- **Type**: String
- **Required**: Yes
- **Description**: Original filename for metadata
- **Example**: `"REST_API_Guide.pdf"`

### `chunk_size` (optional)
- **Type**: Integer
- **Required**: No
- **Default**: 1000
- **Description**: Characters per chunk
- **Valid Range**: 100-5000
- **Example**: `1500`

### `chunk_overlap` (optional)
- **Type**: Integer
- **Required**: No
- **Default**: 200
- **Description**: Overlap between consecutive chunks (for context)
- **Valid Range**: 0-500
- **Example**: `300`

---

## Returns

### Success Response
```json
{
  "success": true,
  "chunks_saved": 15,
  "total_characters": 23000,
  "filename": "REST_API_Guide.pdf",
  "chunk_size": 1000,
  "chunk_overlap": 200,
  "processing_time_seconds": 8.5
}
```

### Error Response
```json
{
  "success": false,
  "error": "Failed to process PDF: invalid base64 encoding",
  "chunks_saved": 0
}
```

---

## Implementation Details

### Proxy to Remote Server

```python
def tool_process_pdf(args: dict) -> dict:
    try:
        # 1. Extract parameters
        pdf_base64 = args.get('pdf_base64', '').strip()
        filename = args.get('filename', 'document.pdf')
        chunk_size = args.get('chunk_size', 1000)
        chunk_overlap = args.get('chunk_overlap', 200)

        if not pdf_base64:
            return {
                'success': False,
                'error': 'pdf_base64 parameter is required',
                'chunks_saved': 0
            }

        # Validate parameters
        if chunk_size < 100:
            chunk_size = 100
        elif chunk_size > 5000:
            chunk_size = 5000

        if chunk_overlap < 0:
            chunk_overlap = 0
        elif chunk_overlap > 500:
            chunk_overlap = 500

        # 2. Forward to remote server
        remote_url = 'http://148.253.209.151:8080'
        request_body = {
            'jsonrpc': '2.0',
            'id': 1,
            'method': 'tools/call',
            'params': {
                'name': 'process_pdf',
                'arguments': {
                    'pdf_base64': pdf_base64,
                    'filename': filename,
                    'chunk_size': chunk_size,
                    'chunk_overlap': chunk_overlap
                }
            }
        }

        # 3. Make HTTP request with extended timeout
        req = urllib.request.Request(
            remote_url,
            data=json.dumps(request_body).encode('utf-8'),
            headers={'Content-Type': 'application/json'}
        )

        # 120 second timeout for large PDFs
        with urllib.request.urlopen(req, timeout=120) as response:
            data = json.loads(response.read().decode('utf-8'))

        # 4. Parse and return results
        result = data.get('result', {})
        content_list = result.get('content', [])

        if content_list:
            text = content_list[0].get('text', '{}')
            parsed = json.loads(text)
            return parsed

        return {
            'success': False,
            'error': 'Invalid response from remote server',
            'chunks_saved': 0
        }

    except Exception as e:
        return {
            'success': False,
            'error': f'Failed to process PDF: {str(e)}',
            'chunks_saved': 0
        }
```

### Remote Processing Pipeline

On remote server:
1. Decode base64 to PDF bytes
2. Extract text from PDF
3. Chunk text with overlap
4. Generate embedding for each chunk
5. Save chunks with embeddings to database
6. Return statistics

---

## Usage Examples

### Example 1: Process PDF with Default Settings

**Request**:
```bash
# First, encode PDF to base64
PDF_BASE64=$(base64 -i document.pdf)

curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "process_pdf",
      "arguments": {
        "pdf_base64": "'$PDF_BASE64'",
        "filename": "document.pdf"
      }
    }
  }'
```

### Example 2: Custom Chunk Size

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "process_pdf",
      "arguments": {
        "pdf_base64": "'$PDF_BASE64'",
        "filename": "large_document.pdf",
        "chunk_size": 1500,
        "chunk_overlap": 300
      }
    }
  }'
```

---

## Chunking Strategy

### Intelligent Text Splitting

```python
def chunk_text_with_overlap(text: str, chunk_size: int, overlap: int) -> List[str]:
    """
    Split text into overlapping chunks, breaking at sentence boundaries.
    """
    chunks = []
    start = 0

    while start < len(text):
        end = start + chunk_size

        # Try to break at sentence boundary
        if end < len(text):
            last_period = text.rfind('. ', start, end)
            if last_period != -1 and last_period > start + chunk_size // 2:
                end = last_period + 1

        chunk = text[start:end].strip()
        if chunk:
            chunks.append(chunk)

        start = end - overlap  # Overlap for context

    return chunks
```

### Example Chunking

**Input Text** (2500 characters):
```
REST API это архитектурный стиль для веб-сервисов. HTTP методы...
```

**Chunks** (chunk_size=1000, overlap=200):
1. Characters 0-1000: "REST API это архитектурный..."
2. Characters 800-1800: "...HTTP методы используются..."
3. Characters 1600-2500: "...для операций CRUD..."

**Overlap Benefits**:
- Preserves context across chunk boundaries
- Improves search quality
- Prevents information loss

---

## Performance

- **Response Time**: 5-30 seconds (depends on PDF size)
- **Timeout**: 120 seconds
- **Processing Speed**: ~500 pages/minute
- **Network**: ~1-2 seconds
- **Text Extraction**: ~1-5 seconds
- **Chunking**: < 1 second
- **Embedding Generation**: ~100ms per chunk
- **Database Storage**: < 1 second

### Size Limits

- **Max PDF Size**: 50 MB (recommended)
- **Max Pages**: 500 pages (recommended)
- **Max Characters**: 1,000,000 characters

---

## Error Handling

### Invalid Base64
```json
{
  "success": false,
  "error": "Failed to process PDF: invalid base64 encoding"
}
```

**Solution**: Verify base64 encoding is correct

### PDF Extraction Failed
```json
{
  "success": false,
  "error": "Failed to extract text from PDF"
}
```

**Causes**:
- Password-protected PDF
- Corrupted PDF file
- Image-only PDF (no text)

**Solution**: 
- Remove password protection
- Verify PDF integrity
- Use OCR for image-based PDFs

### Timeout
```json
{
  "success": false,
  "error": "Failed to process PDF: timeout after 120 seconds"
}
```

**Solution**: Split large PDF into smaller files

---

## Related Documentation

- [PROCESS_TEXT_CHUNKS.md](PROCESS_TEXT_CHUNKS.md) - Alternative client-side extraction
- [CREATE_EMBEDDING.md](CREATE_EMBEDDING.md) - Embedding generation
- [SAVE_DOCUMENT.md](SAVE_DOCUMENT.md) - Save individual documents
- [OLLAMA_SCREEN.md](../../docs/OLLAMA_SCREEN.md) - PDF upload UI

---

## Version

**Day 18**: Реранкинг и фильтрация

**Last Updated**: 2026-02-04
