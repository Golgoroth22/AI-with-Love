# create_embedding Tool Documentation

## Overview

The `create_embedding` tool generates text embeddings using Ollama's nomic-embed-text model. It creates 768-dimensional vector representations of text that can be used for semantic similarity search and RAG applications.

**Tool Type**: Local (direct Ollama integration)
**Emoji**: 🔢
**Embedding Model**: nomic-embed-text
**Dimensions**: 768
**Ollama API**: http://localhost:11434/api/embeddings

---

## Implementation

**File**: `server/http_mcp_server.py`
**Lines**: 721-772
**Method**: `tool_create_embedding(args: dict) -> dict`

---

## Parameters

### `text` (required)
- **Type**: String
- **Required**: Yes
- **Description**: Text content to generate embedding for
- **Max Length**: 8192 tokens (~32000 characters)
- **Example**: `"REST API это архитектурный стиль для веб-сервисов"`

---

## Returns

### Success Response
```json
{
  "success": true,
  "embedding": [0.123, -0.456, 0.789, ...],  // 768 dimensions
  "dimensions": 768,
  "model": "nomic-embed-text"
}
```

### Error Response (Ollama Not Available)
```json
{
  "success": false,
  "error": "Failed to create embedding: Connection refused to localhost:11434",
  "embedding": [],
  "dimensions": 0
}
```

### Error Response (Empty Text)
```json
{
  "success": false,
  "error": "Text parameter is required",
  "embedding": [],
  "dimensions": 0
}
```

---

## Implementation Details

### Ollama API Request

```python
import urllib.request
import json

def tool_create_embedding(args: dict) -> dict:
    try:
        # 1. Extract text parameter
        text = args.get('text', '').strip()

        if not text:
            return {
                'success': False,
                'error': 'Text parameter is required',
                'embedding': [],
                'dimensions': 0
            }

        # 2. Build Ollama API request
        ollama_url = 'http://localhost:11434/api/embeddings'
        ollama_request = {
            'model': 'nomic-embed-text',
            'prompt': text
        }

        # 3. Make HTTP request to Ollama
        req = urllib.request.Request(
            ollama_url,
            data=json.dumps(ollama_request).encode('utf-8'),
            headers={'Content-Type': 'application/json'}
        )

        with urllib.request.urlopen(req, timeout=30) as response:
            data = json.loads(response.read().decode('utf-8'))

        # 4. Extract embedding
        embedding = data.get('embedding', [])

        # 5. Return result
        return {
            'success': True,
            'embedding': embedding,
            'dimensions': len(embedding),
            'model': 'nomic-embed-text'
        }

    except Exception as e:
        return {
            'success': False,
            'error': f'Failed to create embedding: {str(e)}',
            'embedding': [],
            'dimensions': 0
        }
```

### nomic-embed-text Model

**Model Information**:
- **Dimensions**: 768
- **Context Length**: 8192 tokens
- **Training**: Contrastive learning on diverse text corpus
- **Use Cases**: Semantic search, document clustering, similarity comparison

**Model Download** (if not already installed):
```bash
ollama pull nomic-embed-text
```

---

## Usage Examples

### Example 1: Generate Embedding for English Text

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "create_embedding",
      "arguments": {
        "text": "REST API is an architectural style for web services"
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
    "content": [
      {
        "type": "text",
        "text": "{\"success\":true,\"embedding\":[0.0234,-0.0567,0.0891,...(768 values)],\"dimensions\":768,\"model\":\"nomic-embed-text\"}"
      }
    ]
  }
}
```

### Example 2: Generate Embedding for Russian Text

**Request**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "create_embedding",
      "arguments": {
        "text": "REST API это архитектурный стиль для веб-сервисов"
      }
    }
  }'
```

**Response**:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"success\":true,\"embedding\":[0.0123,-0.0456,0.0789,...],\"dimensions\":768,\"model\":\"nomic-embed-text\"}"
      }
    ]
  }
}
```

### Example 3: Direct Ollama API Call

Bypass MCP server and call Ollama directly:
```bash
curl -X POST http://localhost:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "nomic-embed-text",
    "prompt": "Your text here"
  }'
```

**Response**:
```json
{
  "embedding": [0.0234, -0.0567, 0.0891, ...]
}
```

---

## Integration Workflow

### Document Indexing Pipeline

```
Text Input
     ↓
create_embedding (generates vector)
     ↓
768-dimensional embedding
     ↓
save_document (stores with embedding)
     ↓
SQLite database (embeddings.db)
     ↓
Later: search_similar or semantic_search
     ↓
Returns relevant documents
```

### Used By Other Tools

This tool is called indirectly by:
- **save_document** - Generates embedding before saving
- **process_text_chunks** - Generates embeddings for each chunk
- **process_pdf** - Generates embeddings for extracted text chunks

---

## Error Handling

### Ollama Not Running
```json
{
  "success": false,
  "error": "Failed to create embedding: Connection refused to localhost:11434"
}
```

**Solution**:
```bash
# Start Ollama service
ollama serve

# Verify it's running
curl http://localhost:11434/api/tags
```

### Model Not Downloaded
```json
{
  "success": false,
  "error": "Failed to create embedding: model 'nomic-embed-text' not found"
}
```

**Solution**:
```bash
ollama pull nomic-embed-text
```

### Text Too Long
```json
{
  "success": false,
  "error": "Failed to create embedding: prompt exceeds context length"
}
```

**Solution**: Split text into smaller chunks (max 8192 tokens)

### Empty Text
```json
{
  "success": false,
  "error": "Text parameter is required"
}
```

**Solution**: Provide non-empty text parameter

---

## Performance

- **Response Time**: 100-300ms per embedding (CPU)
- **Response Time**: 50-100ms per embedding (GPU)
- **Model Loading**: ~1 second (first request only)
- **Max Context**: 8192 tokens (~32000 characters)
- **Dimensions**: 768 (fixed)

### Optimization Tips

1. **Batch Processing**: Process multiple texts in sequence
2. **GPU Acceleration**: Use Ollama with GPU for faster generation
3. **Cache Results**: Store embeddings in database for reuse
4. **Text Chunking**: Split long documents into manageable chunks

---

## Embedding Similarity

### Cosine Similarity

Calculate similarity between two embeddings:

```python
import numpy as np

def cosine_similarity(embedding1, embedding2):
    """
    Calculate cosine similarity between two embeddings.
    Returns value between -1 (opposite) and 1 (identical).
    """
    dot_product = np.dot(embedding1, embedding2)
    norm1 = np.linalg.norm(embedding1)
    norm2 = np.linalg.norm(embedding2)

    similarity = dot_product / (norm1 * norm2)
    return similarity

# Example
emb1 = [0.123, -0.456, 0.789, ...]  # 768 dimensions
emb2 = [0.111, -0.444, 0.800, ...]  # 768 dimensions

similarity = cosine_similarity(emb1, emb2)
# Result: 0.92 (high similarity)
```

### Similarity Ranges

- **0.9 - 1.0**: Very similar (near duplicates)
- **0.7 - 0.9**: Similar (related topics)
- **0.5 - 0.7**: Somewhat related
- **0.3 - 0.5**: Low similarity
- **< 0.3**: Different topics

---

## Testing

### Manual Test

**Test 1: Generate embedding**
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"create_embedding","arguments":{"text":"test"}}}'
```

**Expected**: Returns embedding array with 768 dimensions

**Test 2: Verify Ollama is running**
```bash
curl http://localhost:11434/api/tags
```

**Expected**: Returns list of installed models including nomic-embed-text

**Test 3: Test with long text**
```bash
# Generate ~1000 word text
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create_embedding","arguments":{"text":"'$(cat large_text.txt)'"}}}'
```

**Expected**: Successfully generates embedding

---

## Ollama Setup

### Install Ollama

**macOS**:
```bash
brew install ollama
```

**Linux**:
```bash
curl https://ollama.ai/install.sh | sh
```

**Windows**:
Download from https://ollama.ai/download

### Start Ollama Service

```bash
ollama serve
```

**Verify**:
```bash
curl http://localhost:11434/api/tags
```

### Download Model

```bash
ollama pull nomic-embed-text
```

**Verify**:
```bash
ollama list
```

Expected output:
```
NAME                 ID            SIZE    MODIFIED
nomic-embed-text     latest        274MB   2 minutes ago
```

---

## Related Documentation

- [SAVE_DOCUMENT.md](SAVE_DOCUMENT.md) - Save documents with embeddings
- [SEARCH_SIMILAR.md](SEARCH_SIMILAR.md) - Search by similarity
- [SEMANTIC_SEARCH.md](../SEMANTIC_SEARCH.md) - Threshold-filtered search
- [PROCESS_TEXT_CHUNKS.md](PROCESS_TEXT_CHUNKS.md) - Chunk and embed text
- [OLLAMA_SCREEN.md](../../docs/OLLAMA_SCREEN.md) - Document indexing UI

---

## External Resources

- **Ollama Documentation**: https://ollama.ai/
- **nomic-embed-text Model**: https://ollama.ai/library/nomic-embed-text
- **Embedding Guide**: https://ollama.ai/blog/embedding-models

---

## Version

**Day 18**: Реранкинг и фильтрация

**Last Updated**: 2026-02-04
