# create_embedding Tool (Day 21: Local Processing)

## Overview

Generates vector embeddings for text using **local Ollama** with the `nomic-embed-text` model. This tool was already local-only and remains unchanged in Day 21.

## Architecture

```
MCP Server (create_embedding)
    ↓ [HTTP POST to Ollama]
Local Ollama (localhost:11434)
    ↓ [Model: nomic-embed-text]
    ↓ [Generate 768-dimensional vector]
Return: Embedding array
```

## Parameters

- `text` (required): The text to generate an embedding for

## Response Format

```json
{
  "success": true,
  "embedding": [0.123, -0.456, 0.789, ...],
  "dimensions": 768
}
```

## Technical Details

**Model**: `nomic-embed-text`
- **Dimensions**: 768
- **Context Length**: 8192 tokens
- **Best For**: Semantic search, document retrieval, clustering

**Ollama API**:
- **Endpoint**: `http://localhost:11434/api/embeddings`
- **Method**: POST
- **Timeout**: 60 seconds
- **Request**:
  ```json
  {
    "model": "nomic-embed-text",
    "prompt": "Your text here"
  }
  ```

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
      "name": "create_embedding",
      "arguments": {
        "text": "REST API authentication"
      }
    }
  }'
```

### Direct Ollama Test
```bash
curl http://localhost:11434/api/embeddings \
  -d '{
    "model": "nomic-embed-text",
    "prompt": "test"
  }'
```

## Performance

- **Latency**: ~50-100ms per embedding
- **Throughput**: ~10 embeddings/second on M1 Mac
- **Memory**: ~500MB for model
- **CPU Usage**: Moderate (optimized for Apple Silicon)

## Troubleshooting

### "Failed to generate embedding"
**Check Ollama is running**:
```bash
ps aux | grep ollama
```

**Check model is available**:
```bash
ollama list | grep nomic-embed-text
```

**Pull model if missing**:
```bash
ollama pull nomic-embed-text
```

### "Connection refused"
**Check Ollama is listening**:
```bash
curl http://localhost:11434/api/embeddings -d '{"model":"nomic-embed-text","prompt":"test"}'
```

**Restart Ollama** (on Mac: quit and relaunch Ollama app)

## Day 21 Status

**No Changes** - This tool was already local-only. All other tools now match this pattern.
