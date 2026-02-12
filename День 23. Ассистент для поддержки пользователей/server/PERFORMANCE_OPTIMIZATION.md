# Performance Optimization: Parallel Chunk Processing

## Problem

When processing large text files (e.g., CLAUDE.md with 15,635 characters):
- Created ~16-20 chunks (chunk_size=1000, overlap=200)
- Each chunk was processed **sequentially** with separate Ollama API calls
- Processing time: 16 chunks × 20 seconds = **320 seconds (~5 minutes)**
- Result: **Timeout error** after 5 minutes

## Solution

Implemented **parallel processing** using Python's `ThreadPoolExecutor`:

```python
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading

# Process chunks in parallel with 4 workers (default)
with ThreadPoolExecutor(max_workers=max_workers) as executor:
    futures = {
        executor.submit(process_chunk, (i, chunk)): i
        for i, chunk in enumerate(chunks)
    }

    for future in as_completed(futures):
        # Handle results
        success, chunk_index = future.result()
```

## Performance Improvement

### Before (Sequential)
- 16 chunks × 20 seconds = **320 seconds** (5+ minutes)
- Single-threaded processing
- Linear scaling with document size

### After (Parallel)
- 16 chunks / 4 workers × 20 seconds = **~80 seconds** (1.3 minutes)
- **4x faster** for large documents
- Better resource utilization

## Configuration

The `process_text_chunks` tool now accepts a `max_workers` parameter:

```json
{
    "text": "Your document content...",
    "filename": "document.md",
    "chunk_size": 1000,
    "chunk_overlap": 200,
    "max_workers": 4  // Number of parallel threads (default: 4)
}
```

### Recommended Settings

| Document Size | Recommended max_workers | Expected Processing Time |
|--------------|------------------------|-------------------------|
| < 5,000 chars | 2 | < 30 seconds |
| 5,000 - 15,000 chars | 4 | 30-90 seconds |
| 15,000 - 50,000 chars | 6-8 | 1-3 minutes |
| > 50,000 chars | 8-10 | 3-5 minutes |

**Note:** More workers = faster processing, but also higher CPU/memory usage and more concurrent Ollama API calls.

## Additional Improvements

1. **Progress Logging**: Real-time progress updates every 10 chunks
2. **Error Handling**: Failed chunks don't stop the entire process
3. **Thread-Safe Counters**: Using threading.Lock for accurate progress tracking
4. **Detailed Metrics**: Returns processing time, average time per chunk, and failure count

## Response Format

```json
{
    "success": true,
    "chunks_saved": 16,
    "chunks_failed": 0,
    "total_characters": 15635,
    "filename": "CLAUDE.md",
    "chunk_size": 1000,
    "chunk_overlap": 200,
    "processing_time_seconds": 82.5,
    "average_time_per_chunk": 5.2
}
```

## Deployment

To deploy these changes to the remote server:

```bash
cd server
./deploy_quick.sh
```

Or manually:
```bash
scp http_mcp_server.py root@SERVER_IP:/opt/mcp-server/
ssh root@SERVER_IP "cd /opt/mcp-server && docker compose restart"
```

## Testing

Run the test suite to verify functionality:

```bash
python3 test_http_mcp_server.py
```

Expected output:
- All 10 tests should pass
- Logs should show parallel processing (multiple "Generating embedding" messages)

## Future Optimizations

Potential further improvements:

1. **Batch Embedding Generation**: If Ollama supports batch requests, process multiple chunks in a single API call
2. **Async Processing**: Convert to async/await for better I/O handling
3. **Job Queue**: For very large documents, use a job queue (e.g., Celery) with progress tracking
4. **Caching**: Cache embeddings for identical chunks to avoid reprocessing
5. **Adaptive Workers**: Automatically adjust max_workers based on document size and system resources

## Related Issues

- Fixes timeout errors for documents > 10,000 characters
- Improves user experience with faster indexing
- Reduces server load per-document (shorter processing time)
