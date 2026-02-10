# OllamaScreen - Document Indexing Interface Documentation

## Overview

The OllamaScreen is a specialized interface for document indexing and embedding generation. It allows users to index text messages and PDF documents, creating searchable embeddings using Ollama's nomic-embed-text model. These indexed documents can later be queried using semantic search with threshold filtering.

**File Locations:**
- **UI**: `app/src/main/java/com/example/aiwithlove/ui/screen/OllamaScreen.kt`
- **ViewModel**: `app/src/main/java/com/example/aiwithlove/viewmodel/OllamaViewModel.kt`
- **MCP Server**: `server/http_mcp_server.py`

---

## Table of Contents

1. [Document Indexing & Embedding](#1-document-indexing--embedding)
2. [PDF Upload & Processing](#2-pdf-upload--processing)
3. [Semantic Search Integration](#3-semantic-search-integration)
4. [UI Components](#4-ui-components)
5. [Technical Details](#5-technical-details)

---

## 1. Document Indexing & Embedding

### 1.1 Text Message Indexing

**Implementation**: OllamaViewModel.kt `sendMessage()` method

Users can send text messages to be automatically indexed:

```kotlin
fun sendMessage(userMessage: String) {
    viewModelScope.launch {
        try {
            // 1. Add user message to UI
            addMessage(Message(
                content = userMessage,
                isFromUser = true,
                timestamp = System.currentTimeMillis()
            ))

            _isLoading.value = true

            // 2. Call save_document tool on MCP server
            val result = mcpClient.callTool(
                toolName = "save_document",
                arguments = buildJsonObject {
                    put("content", userMessage)
                }
            )

            // 3. Parse response
            val documentId = result["content"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content
                ?.let { Json.parseToJsonElement(it).jsonObject }
                ?.get("document_id")
                ?.jsonPrimitive?.int

            // 4. Update documents count
            updateDocumentsCount()

            // 5. Show confirmation with typewriter effect
            displayTypewriterMessage(
                "✅ Документ сохранён!\n" +
                "ID: $documentId\n" +
                "Текст: \"${userMessage.take(50)}...\"\n" +
                "Эмбеддинг создан через Ollama (nomic-embed-text)"
            )

        } catch (e: Exception) {
            displayTypewriterMessage(
                "❌ Ошибка при сохранении документа: ${e.message}"
            )
        } finally {
            _isLoading.value = false
        }
    }
}
```

### 1.2 Embedding Generation Workflow

```
User Text Message
     ↓
Android App (OllamaViewModel)
     ↓
Local MCP Server (save_document tool)
     ↓
Remote MCP Server (148.253.209.151:22)
     ↓
Ollama API (localhost:11434)
     ↓
Generate Embedding (nomic-embed-text, 768 dimensions)
     ↓
Save to SQLite Database (embeddings.db)
     ↓
Return Document ID
     ↓
Display Confirmation to User
```

### 1.3 Document Count Tracking

**Implementation**: OllamaViewModel.kt `updateDocumentsCount()` method

```kotlin
private suspend fun updateDocumentsCount() {
    try {
        // Call search_similar with generic query to get total count
        val result = mcpClient.callTool(
            toolName = "search_similar",
            arguments = buildJsonObject {
                put("query", "document")
                put("limit", 1000) // High limit to get approximate total
            }
        )

        // Parse document count from response
        val count = result["content"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.content
            ?.let { Json.parseToJsonElement(it).jsonObject }
            ?.get("count")
            ?.jsonPrimitive?.int ?: 0

        _documentsCount.value = count
        logD("📊 Documents count updated: $count")

    } catch (e: Exception) {
        logE("Failed to update documents count", e)
    }
}
```

---

## 2. PDF Upload & Processing

### 2.1 PDF Selection

**Implementation**: OllamaScreen.kt PDF button click handler

```kotlin
// PDF selection launcher
val pdfPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri: Uri? ->
    uri?.let {
        val fileName = getFileNameFromUri(context, uri)
        viewModel.uploadPdf(uri, fileName, context)
    }
}

// PDF attachment button
IconButton(
    onClick = { pdfPickerLauncher.launch("application/pdf") }
) {
    Icon(
        imageVector = Icons.Default.AttachFile,
        contentDescription = "Прикрепить PDF",
        tint = MaterialTheme.colorScheme.onSecondaryContainer
    )
}
```

### 2.2 PDF Processing Pipeline

**Implementation**: OllamaViewModel.kt `uploadPdf()` method

```kotlin
fun uploadPdf(uri: Uri, fileName: String, context: Context) {
    viewModelScope.launch {
        try {
            // 1. Set state to Reading
            _pdfUploadState.value = PdfUploadState.Reading(fileName)

            // 2. Extract text using PDFBox
            val text = extractTextFromPdf(uri, context)

            if (text.isBlank()) {
                _pdfUploadState.value = PdfUploadState.Error(
                    "PDF не содержит текста или не удалось извлечь текст"
                )
                return@launch
            }

            logD("📄 Extracted ${text.length} characters from PDF")

            // 3. Set state to Uploading
            _pdfUploadState.value = PdfUploadState.Uploading(
                fileName = fileName,
                progress = 0.5f
            )

            // 4. Send to server for chunking and embedding
            val result = mcpClient.callTool(
                toolName = "process_text_chunks",
                arguments = buildJsonObject {
                    put("text", text)
                    put("filename", fileName)
                    put("chunk_size", 1000)
                    put("chunk_overlap", 200)
                }
            )

            // 5. Parse result
            val resultData = result["content"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content
                ?.let { Json.parseToJsonElement(it).jsonObject }

            val chunksCount = resultData?.get("chunks_saved")?.jsonPrimitive?.int ?: 0
            val charCount = text.length

            // 6. Update documents count
            updateDocumentsCount()

            // 7. Set state to Success
            _pdfUploadState.value = PdfUploadState.Success(
                fileName = fileName,
                chunksCount = chunksCount
            )

            // 8. Show success message
            displayTypewriterMessage(
                "✅ PDF обработан!\n" +
                "Файл: $fileName\n" +
                "Извлечено символов: $charCount\n" +
                "Создано чанков: $chunksCount\n" +
                "Эмбеддинги сохранены в базу данных"
            )

        } catch (e: Exception) {
            _pdfUploadState.value = PdfUploadState.Error(
                "Ошибка обработки PDF: ${e.message}"
            )
            displayTypewriterMessage(
                "❌ Ошибка обработки PDF: ${e.message}"
            )
        }
    }
}
```

### 2.3 Text Extraction with PDFBox

**Implementation**: OllamaViewModel.kt `extractTextFromPdf()` method

```kotlin
private suspend fun extractTextFromPdf(uri: Uri, context: Context): String {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = PDDocument.load(inputStream)
                val stripper = PDFTextStripper()

                // Extract text from all pages
                val text = stripper.getText(document)

                document.close()

                text.trim()
            } ?: ""
        } catch (e: Exception) {
            logE("PDF extraction failed", e)
            throw e
        }
    }
}
```

**Dependencies** (build.gradle):
```gradle
dependencies {
    // PDFBox Android library
    implementation 'com.tom-roush:pdfbox-android:2.0.27.0'
}
```

### 2.4 Text Chunking

Performed server-side by `process_text_chunks` tool with intelligent chunking:

**Parameters**:
- `chunk_size`: 1000 characters (default)
- `chunk_overlap`: 200 characters (default)

**Chunking Strategy**:
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

        start = end - overlap  # Overlap for context

    return chunks
```

### 2.5 PDF Upload States

**Implementation**: OllamaViewModel.kt sealed class

```kotlin
sealed class PdfUploadState {
    object Idle : PdfUploadState()

    data class Reading(
        val fileName: String
    ) : PdfUploadState()

    data class Uploading(
        val fileName: String,
        val progress: Float
    ) : PdfUploadState()

    data class Success(
        val fileName: String,
        val chunksCount: Int
    ) : PdfUploadState()

    data class Error(
        val message: String
    ) : PdfUploadState()
}
```

**State Transitions**:
```
Idle
  ↓ (User selects PDF)
Reading (extracting text)
  ↓ (Text extracted)
Uploading (sending to server)
  ↓ (Server processing complete)
Success (showing results)
  or
Error (showing error message)
```

---

## 3. Semantic Search Integration

### 3.1 Search Query Usage

Users can query indexed documents by sending search queries in the ChatScreen:

**ChatScreen Integration**:
```kotlin
// In ChatViewModel.kt
if (userMentionsSemanticSearch(userMessage)) {
    // Trigger semantic_search tool
    val result = mcpClient.callTool(
        toolName = "semantic_search",
        arguments = buildJsonObject {
            put("query", userMessage)
            put("limit", 3)
            put("threshold", 0.7) // From UI slider
        }
    )
}
```

### 3.2 Search Workflow

```
User Query in ChatScreen
     ↓
"найди в документах информацию о REST API"
     ↓
ChatViewModel detects keywords
     ↓
Calls semantic_search tool
     ↓
Local MCP Server proxies to remote
     ↓
Remote server searches embeddings.db
     ↓
Returns top 3 chunks with similarity ≥ 0.7
     ↓
ChatScreen displays results with similarity scores
     ↓
Perplexity API uses results as context
     ↓
Generates answer based on retrieved documents
```

### 3.3 Document Retrieval

Documents indexed via OllamaScreen are automatically available for search:

**Example Flow**:
1. User uploads PDF about REST API in OllamaScreen
2. System creates 15 chunks with embeddings
3. Later, user asks in ChatScreen: "Что такое REST API?"
4. semantic_search finds relevant chunks from uploaded PDF
5. AI generates answer using retrieved context

---

## 4. UI Components

### 4.1 Top App Bar

**Implementation**: OllamaScreen.kt top bar section

```kotlin
TopAppBar(
    title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Индексация документов")

            // Documents count badge
            if (documentsCount > 0) {
                Badge(
                    modifier = Modifier.padding(start = 8.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = documentsCount.toString(),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    },
    navigationIcon = {
        IconButton(onClick = { navController.popBackStack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
        }
    },
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    )
)
```

### 4.2 Message Display

**Implementation**: OllamaScreen.kt message list

```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize()
) {
    items(messages.size) { index ->
        val message = messages[index]

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = if (message.isFromUser) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            } else {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (message.isFromUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
    }
}
```

### 4.3 PDF Upload Progress

**Implementation**: OllamaScreen.kt PDF state display

```kotlin
when (val state = pdfUploadState) {
    is PdfUploadState.Reading -> {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("📖 Извлекаю текст из ${state.fileName}...")
            }
        }
    }

    is PdfUploadState.Uploading -> {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("⬆️ Загружаю ${state.fileName} на сервер...")
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    is PdfUploadState.Success -> {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50) // Green
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "✅ ${state.fileName}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Создано чанков: ${state.chunksCount}",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }

    is PdfUploadState.Error -> {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = "❌ ${state.message}",
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    PdfUploadState.Idle -> {
        // No progress UI shown
    }
}
```

### 4.4 Input Section

**Implementation**: OllamaScreen.kt bottom input area

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
    verticalAlignment = Alignment.Bottom
) {
    // PDF Attachment Button
    IconButton(
        onClick = { pdfPickerLauncher.launch("application/pdf") },
        enabled = !isLoading,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = "Прикрепить PDF",
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    Spacer(modifier = Modifier.width(8.dp))

    // Text Input Field
    OutlinedTextField(
        value = messageText,
        onValueChange = { messageText = it },
        modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp),
        placeholder = { Text("Введите текст для индексации...") },
        enabled = !isLoading,
        shape = RoundedCornerShape(24.dp)
    )

    // Send Button
    FloatingActionButton(
        onClick = {
            if (messageText.isNotBlank()) {
                viewModel.sendMessage(messageText)
                messageText = ""
            }
        },
        enabled = messageText.isNotBlank() && !isLoading,
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
        }
    }
}
```

### 4.5 Welcome Message

**Implementation**: OllamaScreen.kt empty state

```kotlin
if (messages.isEmpty()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🔍",
                fontSize = 64.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Индексация документов с Ollama",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = """
                    Ваши сообщения будут автоматически обрабатываться:
                    • Создание эмбеддингов через Ollama (nomic-embed-text)
                    • Сохранение в базу данных на сервере
                    • Возможность поиска похожих документов

                    Отправьте первое сообщение, чтобы начать индексацию!
                """.trimIndent(),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

---

## 5. Technical Details

### 5.1 MCP Tools Used

The OllamaScreen integrates with three MCP tools:

1. **save_document** - Save text with embedding
   - Called when user sends text message
   - Proxied to remote MCP server
   - See: [SAVE_DOCUMENT.md](../server/tools/SAVE_DOCUMENT.md)

2. **process_text_chunks** - Process extracted PDF text
   - Called after PDF text extraction
   - Server-side chunking with overlap
   - 300-second timeout for large documents
   - See: [PROCESS_TEXT_CHUNKS.md](../server/tools/PROCESS_TEXT_CHUNKS.md)

3. **search_similar** - Get document count
   - Called to update documents count badge
   - Uses high limit (1000) to get approximate total
   - See: [SEARCH_SIMILAR.md](../server/tools/SEARCH_SIMILAR.md)

### 5.2 Ollama Integration

**Embedding Model**: nomic-embed-text
- **Dimensions**: 768
- **Context Length**: 8192 tokens
- **API Endpoint**: http://localhost:11434/api/embeddings

**Ollama API Request**:
```json
{
  "model": "nomic-embed-text",
  "prompt": "Your document text here"
}
```

**Response**:
```json
{
  "embedding": [0.123, -0.456, 0.789, ...]  // 768 dimensions
}
```

### 5.3 Database Schema

**Database**: `embeddings.db` on remote MCP server

```sql
CREATE TABLE documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content TEXT NOT NULL,
    embedding BLOB NOT NULL,  -- 768-dimensional vector
    metadata TEXT,            -- JSON metadata (filename, chunk info, etc.)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_created_at ON documents(created_at DESC);
```

### 5.4 Architecture

```
┌─────────────────────────────┐
│      OllamaScreen (UI)      │
│  - PDF selection            │
│  - Text input               │
│  - Progress display         │
└──────────────┬──────────────┘
               ↓
┌─────────────────────────────┐
│   OllamaViewModel (Logic)   │
│  - PDF extraction (PDFBox)  │
│  - MCP tool calls           │
│  - State management         │
└──────────────┬──────────────┘
               ↓
┌─────────────────────────────┐
│   McpClient (HTTP Client)   │
│  - JSON-RPC requests        │
│  - http://10.0.2.2:8080     │
└──────────────┬──────────────┘
               ↓
┌─────────────────────────────┐
│  Local MCP Server (Proxy)   │
│  - Tool routing             │
│  - Request forwarding       │
└──────────────┬──────────────┘
               ↓
┌─────────────────────────────┐
│  Remote MCP Server          │
│  (148.253.209.151:22)     │
│  - Ollama integration       │
│  - Text chunking            │
│  - Embedding generation     │
│  - Database storage         │
└─────────────────────────────┘
```

### 5.5 Typewriter Animation

**Implementation**: OllamaViewModel.kt

```kotlin
private suspend fun displayTypewriterMessage(message: String) {
    val fullText = message
    val delay = 10L // 10ms delay per character

    addMessage(Message(
        content = "",
        isFromUser = false,
        timestamp = System.currentTimeMillis()
    ))

    val messageIndex = _messages.value.size - 1

    for (char in fullText) {
        val currentMessage = _messages.value[messageIndex]
        val updatedMessage = currentMessage.copy(
            content = currentMessage.content + char
        )

        val updatedMessages = _messages.value.toMutableList()
        updatedMessages[messageIndex] = updatedMessage
        _messages.value = updatedMessages

        delay(delay)
    }
}
```

**Speed**: 1 character every 10ms = 100 characters per second

### 5.6 Error Handling

```kotlin
try {
    // MCP tool call
    val result = mcpClient.callTool(toolName, arguments)

    // Success handling
    displayTypewriterMessage("✅ Success message")

} catch (e: IOException) {
    // Network errors
    displayTypewriterMessage("❌ Ошибка сети: ${e.message}")

} catch (e: JsonException) {
    // JSON parsing errors
    displayTypewriterMessage("❌ Ошибка парсинга ответа: ${e.message}")

} catch (e: Exception) {
    // Generic errors
    displayTypewriterMessage("❌ Ошибка: ${e.message}")

} finally {
    _isLoading.value = false
    _pdfUploadState.value = PdfUploadState.Idle
}
```

---

## Troubleshooting

### PDF Extraction Failed

**Symptom**: "Ошибка извлечения текста из PDF"

**Solutions**:
1. Verify PDF is not password-protected
2. Check if PDF contains actual text (not just images)
3. Verify PDFBox dependency is installed
4. Try with a different PDF file
5. Check Android storage permissions

### Documents Not Saved

**Symptom**: Document count doesn't increase after sending message

**Solutions**:
1. Check if MCP server is running
2. Verify remote server is accessible (148.253.209.151:8080)
3. Check server logs for errors
4. Verify Ollama is running on remote server
5. Check network connectivity

### PDF Processing Timeout

**Symptom**: "Timeout after 300 seconds"

**Solutions**:
1. Reduce PDF file size (split large PDFs)
2. Check remote server performance
3. Verify Ollama is responding
4. Check server logs for resource issues
5. Increase timeout in tool configuration

### Empty Text Extracted from PDF

**Symptom**: "PDF не содержит текста"

**Solutions**:
1. Verify PDF contains text (not scanned images)
2. Try OCR preprocessing if PDF is image-based
3. Check PDF file integrity
4. Try a different PDF extraction tool

### Embeddings Not Generated

**Symptom**: Document saved but search doesn't find it

**Solutions**:
1. Verify Ollama service is running on remote server
2. Check if nomic-embed-text model is downloaded
3. Verify embeddings.db has write permissions
4. Check remote server logs for Ollama errors
5. Test Ollama directly: `curl http://localhost:11434/api/embeddings`

---

## Related Documentation

- [SAVE_DOCUMENT.md](../server/tools/SAVE_DOCUMENT.md) - Document saving tool
- [PROCESS_TEXT_CHUNKS.md](../server/tools/PROCESS_TEXT_CHUNKS.md) - Text chunking tool
- [SEARCH_SIMILAR.md](../server/tools/SEARCH_SIMILAR.md) - Similarity search tool
- [CREATE_EMBEDDING.md](../server/tools/CREATE_EMBEDDING.md) - Embedding generation
- [SEMANTIC_SEARCH.md](../server/SEMANTIC_SEARCH.md) - Semantic search with threshold filtering
- [CHAT_SCREEN.md](CHAT_SCREEN.md) - Main chat interface
- [SERVER_README.md](../server/SERVER_README.md) - MCP server overview

---

## Usage Examples

### Example 1: Index Text Message

1. Open OllamaScreen
2. Type: "REST API это архитектурный стиль для веб-сервисов"
3. Press Send
4. System creates embedding and saves to database
5. Document count increases by 1

### Example 2: Index PDF Document

1. Open OllamaScreen
2. Click PDF attachment button
3. Select PDF file (e.g., "REST_API_Guide.pdf")
4. System extracts text: 5000 characters
5. System creates chunks: 8 chunks with 200-char overlap
6. Each chunk gets embedding via Ollama
7. All chunks saved to database
8. Document count increases by 8

### Example 3: Search Indexed Documents

1. Index documents in OllamaScreen (see examples above)
2. Switch to ChatScreen
3. Ask: "Что такое REST API?"
4. System triggers semantic_search
5. Finds relevant chunks from indexed documents
6. AI generates answer using retrieved context

---

## Version

**Day 18**: Реранкинг и фильтрация (Reranking and Filtering)

**Last Updated**: 2026-02-04
