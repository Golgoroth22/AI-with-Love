# Semantic Search Implementation Guide (Day 21 Update)

## Overview

The `semantic_search` tool provides RAG (Retrieval Augmented Generation) by finding relevant document chunks from locally indexed documents using vector similarity search. As of Day 21, the architecture has been **simplified to single-tier local processing** with all operations happening on your development machine.

## 🎯 Key Features

### Citations & Source Tracking
- **Automatic Citations**: Every document includes `citation` and `citation_info` fields
- **Source Tracking**: Documents tracked by `source_file`, `source_type`, `page_number`, `chunk_index`
- **Citation Format**: `[filename.pdf, стр. 12, фрагмент 5/45]`
- **Sources Summary**: Generated list of all unique sources with counts
- **AI Integration**: AI MUST include inline citations and "Источники:" section

### Threshold-Based Filtering
- **Configurable Threshold**: Range 0.3-0.95 (default: 0.6)
- **UI Slider**: Users can adjust threshold in ChatScreen
- **Compare Mode**: Returns both filtered and unfiltered results for transparency
- **Graceful Degradation**: Shows lower-quality results when high-quality ones aren't available

### File Type Support (Day 21)
- **PDF** (.pdf) - Client-side extraction via PDFBox
- **Plain Text** (.txt) - Direct text processing
- **Markdown** (.md) - NEW! Full markdown file support

## Architecture (Day 21: Local-Only)

### Single-Tier Architecture

**IMPORTANT**: All processing now happens locally on your Mac. No remote proxy.

```
┌─────────────────┐
│  Android App    │
│   (Emulator)    │
└────────┬────────┘
         │ http://10.0.2.2:8080
         ↓
┌─────────────────────────────┐
│  LOCAL MCP Server           │
│  (Your Mac)                 │
│  Port: 8080                 │
│                             │
│  Tools:                     │
│  • create_embedding         │
│  • save_document            │
│  • search_similar           │
│  • semantic_search ✅       │
│  • process_text_chunks      │
│  • process_pdf              │
└────────┬────────────────────┘
         │ http://localhost:11434
         ↓
┌─────────────────────────────┐
│  Local Ollama               │
│  Model: nomic-embed-text    │
│  Dimensions: 768            │
└────────┬────────────────────┘
         ↓
┌─────────────────────────────┐
│  Local SQLite Database      │
│  /server/data/embeddings.db │
│  - Document chunks          │
│  - Vector embeddings        │
│  - Citation metadata        │
└─────────────────────────────┘
```

### Data Flow

```
User Question
    ↓
Android App (ChatViewModel)
    ↓ [Keyword Detection]
Local MCP Server (semantic_search tool)
    ↓ [Generate query embedding via Ollama]
Local Database (cosine similarity search)
    ↓ [Apply threshold filtering]
Return Relevant Chunks with Citations
    ↓
Perplexity AI Agent (with context)
    ↓
Final Answer to User
```

## Implementation Details

### 1. MCP Server Tool (server/http_mcp_server.py)

**Tool: `semantic_search`**
- **Purpose**: Search locally indexed documents with threshold filtering
- **Processing**: 100% local (no remote calls)
- **Parameters**:
  - `query` (required): Question or search text
  - `limit` (optional, default=3): Maximum number of chunks to return
  - `threshold` (optional, default=0.6): Similarity threshold (0.3-0.95)
  - `compare_mode` (optional, default=false): Return both filtered and unfiltered results

**Implementation Flow**:
1. Generate query embedding using local Ollama (nomic-embed-text)
2. Search local SQLite database using cosine similarity
3. Apply threshold filtering
4. Add citation metadata to each result
5. Generate sources summary
6. Return results (filtered only or comparison mode)

**Response Format**:
```json
{
  "success": true,
  "count": 2,
  "threshold": 0.6,
  "isFiltered": true,
  "source": "local_database",
  "documents": [
    {
      "id": 123,
      "content": "OAuth 2.0 authentication requires...",
      "similarity": 0.89,
      "source_file": "api_guide.pdf",
      "source_type": "pdf",
      "chunk_index": 12,
      "page_number": 15,
      "total_chunks": 45,
      "citation": "[api_guide.pdf, стр. 15, фрагмент 13/45]",
      "citation_info": {
        "source_file": "api_guide.pdf",
        "source_type": "pdf",
        "chunk_index": 12,
        "page_number": 15,
        "total_chunks": 45,
        "formatted": "[api_guide.pdf, стр. 15, фрагмент 13/45]"
      }
    }
  ],
  "sources_summary": ["api_guide.pdf (2 фрагмента)"]
}
```

**Compare Mode Response**:
```json
{
  "success": true,
  "threshold": 0.6,
  "source": "local_database",
  "filteredResults": {
    "count": 2,
    "documents": [/* high similarity docs */]
  },
  "unfiltered": {
    "count": 5,
    "documents": [/* all docs */]
  },
  "sources_summary": ["api_guide.pdf (2 фрагмента)"]
}
```

### 2. Database Helper Class (EmbeddingsDatabase)

**Purpose**: Encapsulates all local database operations

**Key Methods**:
- `save_document_with_embedding()` - Save document with embedding to SQLite
- `search_similar_documents()` - Cosine similarity search
- `count_documents()` - Get total document count

**Utility Functions**:
- `_serialize_embedding()` - Convert embedding array to BLOB
- `_deserialize_embedding()` - Convert BLOB to array
- `_cosine_similarity()` - Calculate similarity between vectors

### 3. Android App Integration

#### ChatViewModel.kt

**Keyword Detection**:
```kotlin
private fun userMentionsSemanticSearch(message: String): Boolean {
    val keywords = listOf(
        "найди в документах",
        "поиск в базе",
        "что говорится в документах",
        "информация о",
        "расскажи о",
        "что такое",
        "как работает",
        "объясни",
        "документ"
    )
    return keywords.any { message.lowercase().contains(it) }
}
```

**Tool Building**:
```kotlin
private fun buildSemanticSearchTool(): AgenticTool {
    return AgenticTool(
        type = "function",
        name = "semantic_search",
        description = "Search for relevant document chunks with SOURCE CITATIONS from indexed documents using semantic similarity. Returns documents with 'citation' field containing [filename, page, chunk]. ALWAYS include these citations in your response to the user when presenting information.",
        parameters = /* ... */
    )
}
```

**Threshold Control**:
```kotlin
private val _searchThreshold = MutableStateFlow(0.6f)
val searchThreshold: StateFlow<Float> = _searchThreshold.asStateFlow()

fun updateSearchThreshold(threshold: Float) {
    _searchThreshold.value = threshold.coerceIn(0.3f, 0.95f)
}
```

**Tool Execution**:
```kotlin
"semantic_search" -> {
    val args = parseToolArguments(arguments).toMutableMap()
    args["threshold"] = _searchThreshold.value.toDouble()
    args["compare_mode"] = true

    val mcpResult = mcpClient.callTool("semantic_search", args)
    val semanticResult = parseSemanticSearchResult(mcpResult)

    ToolExecutionResult(
        result = parsedResult,
        mcpToolInfo = McpToolInfo(
            toolName = "semantic_search",
            responseBody = parsedResult,
            semanticSearchResult = semanticResult
        )
    )
}
```

#### ChatScreen.kt

**Threshold Slider**:
```kotlin
Slider(
    value = searchThreshold,
    onValueChange = { viewModel.updateSearchThreshold(it) },
    valueRange = 0.3f..0.95f,
    steps = 12
)
Text("Порог релевантности: ${(searchThreshold * 100).toInt()}%")
```

### 4. Document Upload & Indexing

**OllamaScreen.kt** - File Picker:
```kotlin
documentPickerLauncher.launch(
    arrayOf("application/pdf", "text/plain", "text/markdown")
)
```

**OllamaViewModel.kt** - Upload Handlers:
- `uploadPdf()` - Extract text via PDFBox, send to `process_text_chunks`
- `uploadTxtFile()` - Read text, send to `process_text_chunks`
- `uploadMarkdownFile()` - **NEW!** Read markdown, send to `process_text_chunks`

**Processing Flow**:
1. Extract text (client-side for PDF, direct read for TXT/MD)
2. Call MCP `process_text_chunks` tool
3. Server chunks text (1000 chars, 200 overlap)
4. Generate embedding for each chunk (Ollama)
5. Save to local database with metadata
6. Update document count in UI

## Usage Examples

### Example 1: Document Search
**User**: "Найди в документах информацию о REST API"

**Flow**:
1. Keyword "найди в документах" triggers `semantic_search` tool
2. Generate query embedding: "REST API информация"
3. Search local database with cosine similarity
4. Apply threshold (0.6), return top 3 chunks
5. AI generates answer with citations

**AI Response**:
```
REST API использует HTTP методы для взаимодействия [api_guide.pdf, стр. 5, фрагмент 2/10].
Поддерживаются GET, POST, PUT, DELETE запросы [api_guide.pdf, стр. 6, фрагмент 3/10].

Источники:
- api_guide.pdf (страницы 5-6, фрагменты 2-3)
```

### Example 2: Threshold Adjustment
**Scenario**: User lowers threshold to 0.4 via slider

**Effect**:
- More documents pass the filter
- May include less relevant results
- Useful when high-quality matches aren't available

**Scenario**: User raises threshold to 0.8

**Effect**:
- Fewer, higher-quality documents
- May return no results if similarity is low
- Useful for precise matching

### Example 3: Markdown File Indexing
**Action**: User uploads `README.md` in OllamaScreen

**Processing**:
1. App reads markdown content
2. Calls `process_text_chunks` with `filename="README.md"`
3. Server detects `.md` extension → `source_type="markdown"`
4. Text chunked and embedded
5. Saved to database with markdown metadata

**Result**: Markdown content is now searchable via semantic search

## Testing

### 1. Start Local MCP Server
```bash
cd server
python3 http_mcp_server.py
```

Expected startup:
```
======================================================================
🚀 MCP HTTP Server - Local Mode with Ollama & SQLite
======================================================================
Server: http://0.0.0.0:8080
From Android emulator: http://10.0.2.2:8080

Available Tools (6):
  🔮 create_embedding      - Generate embeddings using local Ollama
  📝 save_document         - Save document with embeddings to local DB
  🔍 search_similar        - Search similar documents in local DB
  🌐 semantic_search       - Search relevant chunks from local DB
  📄 process_pdf           - Extract text from PDF, chunk, and index locally
  📝 process_text_chunks   - Process extracted text into chunks locally

Ollama Integration:
  • API URL: http://localhost:11434
  • Model: nomic-embed-text
  • Embedding dimensions: 768
  • Status: Local Mac instance

Supported File Types:
  • PDF (.pdf) - Client-side extraction via PDFBox
  • Text (.txt) - Plain text files
  • Markdown (.md) - Markdown files
======================================================================
```

### 2. Test Tools via curl

**Test semantic_search**:
```bash
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "semantic_search",
      "arguments": {
        "query": "Что такое MCP протокол?",
        "limit": 3,
        "threshold": 0.6,
        "compare_mode": true
      }
    }
  }'
```

**Test document count**:
```bash
sqlite3 server/data/embeddings.db \
  "SELECT COUNT(*), source_type FROM documents GROUP BY source_type"
```

### 3. Run Test Suite
```bash
cd server
python3 test_http_mcp_server.py
```

Expected output:
```
test_markdown_file_detection ... ok
test_process_text_chunks_locally ... ok
test_save_document_locally ... ok
test_search_similar_locally ... ok
test_semantic_search_with_threshold ... ok

----------------------------------------------------------------------
Ran 10 tests in 0.038s

OK
```

### 4. Android App Test
1. Start MCP server: `cd server && python3 http_mcp_server.py`
2. Run app in emulator: `./gradlew installDebug`
3. Navigate to Ollama screen
4. Upload test files (PDF, TXT, MD)
5. Return to Chat screen
6. Adjust threshold slider
7. Ask: "найди в документах информацию о X"
8. Verify response includes citations

## Configuration

### Threshold Configuration
**Server** (`server/http_mcp_server.py`):
```python
SEMANTIC_SEARCH_CONFIG = {
    'default_threshold': 0.6,  # 60% - Matches Android app default
    'min_threshold': 0.3,      # 30% - Minimum allowed
    'max_threshold': 0.95      # 95% - Maximum allowed
}
```

**Android** (`ChatViewModel.kt`):
```kotlin
private val _searchThreshold = MutableStateFlow(0.6f)  // Must match server default
```

### Database Configuration
**Path** (`server/http_mcp_server.py`):
```python
EMBEDDINGS_DB_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    'data',
    'embeddings.db'
)
```

### Ollama Configuration
**URL** (`server/http_mcp_server.py`):
```python
OLLAMA_API_URL = "http://localhost:11434"  # Local Ollama instance
```

## Troubleshooting

### "No documents found"
**Check 1**: Verify documents are indexed
```bash
sqlite3 server/data/embeddings.db "SELECT COUNT(*) FROM documents"
```

**Check 2**: Verify Ollama is running
```bash
ps aux | grep ollama
curl http://localhost:11434/api/embeddings -d '{"model":"nomic-embed-text","prompt":"test"}'
```

**Check 3**: Lower threshold
```kotlin
// Try threshold 0.3 for testing
viewModel.updateSearchThreshold(0.3f)
```

### Connection Refused
**Symptom**: App can't connect to MCP server

**Solution**:
```bash
# Check server is running
ps aux | grep http_mcp_server

# Start server if not running
cd server
python3 http_mcp_server.py

# Verify port 8080 is listening
lsof -ti:8080
```

### "Ollama connection failed"
**Symptom**: Embedding generation fails

**Solution**:
```bash
# Check Ollama is running
ps aux | grep ollama

# Start Ollama if not running
# (On Mac: Launch Ollama app)

# Verify model is available
ollama list | grep nomic-embed-text

# Pull model if missing
ollama pull nomic-embed-text
```

### Empty Search Results
**Cause**: Threshold too high for available documents

**Solution**:
1. Lower threshold to 0.3-0.4
2. Check compare mode results (shows unfiltered documents)
3. Upload more relevant documents
4. Verify documents were indexed correctly

### Slow Performance
**Cause**: Large number of documents in database

**Optimization**:
1. Add database indexes (already present: `idx_documents_source`)
2. Reduce chunk overlap to decrease total chunks
3. Use lower `limit` parameter (default 3 is optimal)
4. Consider periodic database cleanup

## Files Modified (Day 21 Update)

1. **server/http_mcp_server.py**
   - Removed `REMOTE_MCP_SERVER` configuration
   - Added `EmbeddingsDatabase` helper class
   - Rewrote `save_document` for local operation
   - Rewrote `search_similar` for local operation
   - Rewrote `semantic_search` for local operation
   - Rewrote `process_text_chunks` for local operation
   - Updated startup message

2. **app/src/main/java/com/example/aiwithlove/ui/screen/OllamaScreen.kt**
   - Updated file picker: added `"text/markdown"` MIME type

3. **app/src/main/java/com/example/aiwithlove/viewmodel/OllamaViewModel.kt**
   - Added `uploadMarkdownFile()` method
   - Updated `uploadDocument()` router for .md files

4. **app/src/main/java/com/example/aiwithlove/viewmodel/ChatViewModel.kt**
   - Updated CONGRATS_MESSAGE with document upload info

5. **server/test_http_mcp_server.py**
   - Added `TestLocalDatabaseOperations` class
   - Added markdown detection test

## Performance Characteristics

**Local Processing Benefits**:
- **Latency**: ~50-100ms per search (no network overhead)
- **Throughput**: Limited by Ollama (single Mac can handle ~10 req/sec)
- **Reliability**: No network dependency, 100% uptime
- **Privacy**: All data stays on your Mac

**Scalability Considerations**:
- **Database Size**: SQLite handles millions of documents efficiently
- **Embedding Cache**: Consider caching frequent queries
- **Batch Processing**: Process multiple documents in parallel
- **Index Optimization**: Maintain database indexes for fast lookups

## Next Steps

1. **Deploy to Production**: Consider cloud-hosted Ollama for production
2. **Add Reranking**: Implement cross-encoder reranking for better results
3. **Multimodal Support**: Add image embedding support
4. **Query Expansion**: Enhance queries with synonyms/context
5. **Analytics**: Track popular queries and document usage
