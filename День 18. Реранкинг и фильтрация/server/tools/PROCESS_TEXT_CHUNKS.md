# process_text_chunks Tool Documentation

## Overview

The `process_text_chunks` tool processes pre-extracted text by chunking it into smaller pieces, generating embeddings for each chunk, and saving everything to the database. This is used when PDF text extraction is done client-side (e.g., in the Android app using PDFBox).

**Tool Type**: Proxied (forwarded to remote MCP server)
**Emoji**: 📝
**Remote Server**: 148.253.209.151:8080
**Timeout**: 300 seconds (5 minutes - for very large documents)
**Embedding Model**: nomic-embed-text (768 dimensions)

---

## Implementation

**File**: `server/http_mcp_server.py`
**Lines**: 1174-1257
**Method**: `tool_process_text_chunks(args: dict) -> dict`

---

## Parameters

### `text` (required)
- **Type**: String
- **Required**: Yes
- **Description**: Extracted text content to process
- **Max Length**: 1,000,000 characters (recommended)
- **Example**: `"REST API это архитектурный стиль..."`

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
- **Description**: Overlap between consecutive chunks
- **Valid Range**: 0-500
- **Example**: `300`

---

## Returns

### Success Response
```json
{
  "success": true,
  "chunks_saved": 12,
  "total_characters": 18500,
  "filename": "REST_API_Guide.pdf",
  "chunk_size": 1000,
  "chunk_overlap": 200,
  "processing_time_seconds": 6.2
}
```

### Error Response
```json
{
  "success": false,
  "error": "Failed to process text: text parameter is empty",
  "chunks_saved": 0
}
```

---

## Implementation Details

### Proxy to Remote Server

```python
def tool_process_text_chunks(args: dict) -> dict:
    try:
        # 1. Extract parameters
        text = args.get('text', '').strip()
        filename = args.get('filename', 'document.txt')
        chunk_size = args.get('chunk_size', 1000)
        chunk_overlap = args.get('chunk_overlap', 200)

        if not text:
            return {
                'success': False,
                'error': 'Text parameter is required',
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
                'name': 'process_text_chunks',
                'arguments': {
                    'text': text,
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

        # 300 second timeout for very large texts
        with urllib.request.urlopen(req, timeout=300) as response:
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
            'error': f'Failed to process text: {str(e)}',
            'chunks_saved': 0
        }
```

### Remote Processing Pipeline

On remote server:
1. Receive text content
2. Chunk text with overlap
3. Generate embedding for each chunk via Ollama
4. Save chunks with embeddings to database
5. Return statistics

---

## Usage Examples

### Example 1: Process Extracted PDF Text

**Request**:
```bash
# Extract text first (in Android app with PDFBox)
TEXT="REST API это архитектурный стиль для веб-сервисов..."

curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "process_text_chunks",
      "arguments": {
        "text": "'$TEXT'",
        "filename": "document.pdf",
        "chunk_size": 1000,
        "chunk_overlap": 200
      }
    }
  }'
```

### Example 2: Process Plain Text Document

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "process_text_chunks",
      "arguments": {
        "text": "Long article text here...",
        "filename": "article.txt",
        "chunk_size": 1500,
        "chunk_overlap": 300
      }
    }
  }'
```

---

## Integration with OllamaScreen

**File**: `app/src/main/java/com/example/aiwithlove/viewmodel/OllamaViewModel.kt`

### PDF Upload Workflow

```kotlin
fun uploadPdf(uri: Uri, fileName: String, context: Context) {
    viewModelScope.launch {
        try {
            // 1. Set state to Reading
            _pdfUploadState.value = PdfUploadState.Reading(fileName)

            // 2. Extract text using PDFBox (client-side)
            val text = extractTextFromPdf(uri, context)

            // 3. Validate text
            if (text.isBlank()) {
                _pdfUploadState.value = PdfUploadState.Error(
                    "PDF не содержит текста"
                )
                return@launch
            }

            // 4. Set state to Uploading
            _pdfUploadState.value = PdfUploadState.Uploading(
                fileName = fileName,
                progress = 0.5f
            )

            // 5. Send to server for chunking and embedding
            val result = mcpClient.callTool(
                toolName = "process_text_chunks",
                arguments = buildJsonObject {
                    put("text", text)
                    put("filename", fileName)
                    put("chunk_size", 1000)
                    put("chunk_overlap", 200)
                }
            )

            // 6. Parse result
            val chunksCount = result["chunks_saved"]?.jsonPrimitive?.int ?: 0

            // 7. Update UI
            _pdfUploadState.value = PdfUploadState.Success(
                fileName = fileName,
                chunksCount = chunksCount
            )

            displayTypewriterMessage(
                "✅ PDF обработан!\n" +
                "Файл: $fileName\n" +
                "Извлечено символов: ${text.length}\n" +
                "Создано чанков: $chunksCount"
            )

        } catch (e: Exception) {
            _pdfUploadState.value = PdfUploadState.Error(
                "Ошибка: ${e.message}"
            )
        }
    }
}
```

See: [OLLAMA_SCREEN.md](../../docs/OLLAMA_SCREEN.md#pdf-upload--processing)

---

## Comparison with process_pdf

| Feature | process_pdf | process_text_chunks |
|---------|-------------|---------------------|
| **Text Extraction** | Server-side | Client-side (PDFBox) |
| **Input Format** | PDF (base64) | Plain text |
| **Timeout** | 120 seconds | 300 seconds |
| **Use Case** | Server has PDF tools | Client extracts text |
| **Flexibility** | Limited | Can extract anywhere |
| **Network Traffic** | Higher (PDF file) | Lower (text only) |

**When to use process_pdf**:
- Server has better PDF extraction tools
- Client doesn't have PDF libraries
- Want to offload processing to server

**When to use process_text_chunks**:
- Client already has text (PDFBox, OCR, etc.)
- Want control over extraction process
- Need to validate/clean text before sending
- PDF file is very large

---

## Performance

- **Response Time**: 3-15 seconds (depends on text length)
- **Timeout**: 300 seconds (5 minutes)
- **Processing Speed**: ~100 chunks/second
- **Network**: ~1-2 seconds
- **Chunking**: < 1 second
- **Embedding Generation**: ~100ms per chunk
- **Database Storage**: < 1 second

### Size Recommendations

- **Small Text**: < 10,000 characters (< 1 second)
- **Medium Text**: 10,000 - 100,000 characters (1-5 seconds)
- **Large Text**: 100,000 - 500,000 characters (5-15 seconds)
- **Very Large**: 500,000 - 1,000,000 characters (15-60 seconds)

---

## Error Handling

### Empty Text
```json
{
  "success": false,
  "error": "Text parameter is required",
  "chunks_saved": 0
}
```

**Solution**: Ensure text is not empty

### Text Too Large
```json
{
  "success": false,
  "error": "Text exceeds maximum length of 1,000,000 characters"
}
```

**Solution**: Split text into multiple requests

### Timeout
```json
{
  "success": false,
  "error": "Failed to process text: timeout after 300 seconds"
}
```

**Solution**: Split text into smaller pieces

### Remote Server Error
```json
{
  "success": false,
  "error": "Failed to process text: remote server error"
}
```

**Solution**: Check remote server logs

---

## Chunking Strategy

### Sentence Boundary Detection

The server intelligently breaks text at sentence boundaries:

```python
def chunk_text_with_overlap(text: str, chunk_size: int, overlap: int) -> List[str]:
    chunks = []
    start = 0

    while start < len(text):
        end = start + chunk_size

        # Try to break at sentence boundary
        if end < len(text):
            # Look for period followed by space
            last_period = text.rfind('. ', start, end)
            if last_period != -1 and last_period > start + chunk_size // 2:
                end = last_period + 1

        chunk = text[start:end].strip()
        if chunk:
            chunks.append(chunk)

        start = end - overlap

    return chunks
```

### Example

**Input**: 2500 characters

**Settings**:
- chunk_size = 1000
- chunk_overlap = 200

**Result**:
- Chunk 1: chars 0-1000 (ends at sentence)
- Chunk 2: chars 800-1800 (200 char overlap)
- Chunk 3: chars 1600-2500

---

## Testing

### Manual Test

**Test 1: Process short text**
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"process_text_chunks","arguments":{"text":"Short test text","filename":"test.txt"}}}'
```

**Expected**: Returns 1 chunk

**Test 2: Process long text**
```bash
# Generate 5000 character text
LONG_TEXT=$(python3 -c "print('Test text. ' * 500)")

curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"process_text_chunks","arguments":{"text":"'$LONG_TEXT'","filename":"long.txt","chunk_size":1000}}}'
```

**Expected**: Returns ~5 chunks

---

## Related Documentation

- [PROCESS_PDF.md](PROCESS_PDF.md) - Alternative server-side extraction
- [CREATE_EMBEDDING.md](CREATE_EMBEDDING.md) - Embedding generation
- [SAVE_DOCUMENT.md](SAVE_DOCUMENT.md) - Save individual documents
- [OLLAMA_SCREEN.md](../../docs/OLLAMA_SCREEN.md) - PDF upload UI

---

## Version

**Day 18**: Реранкинг и фильтрация

**Last Updated**: 2026-02-04
