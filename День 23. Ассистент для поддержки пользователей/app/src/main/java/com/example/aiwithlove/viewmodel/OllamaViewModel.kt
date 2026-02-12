package com.example.aiwithlove.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.data.model.Message
import com.example.aiwithlove.mcp.McpClient
import com.example.aiwithlove.util.ILoggable
import com.example.aiwithlove.util.runAndCatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OllamaViewModel(
    private val ollamaClient: com.example.aiwithlove.ollama.OllamaClient,
    private val embeddingsRepository: com.example.aiwithlove.database.EmbeddingsRepository
) : ViewModel(),
    ILoggable {

    private val _messages = MutableStateFlow(listOf(Message(text = WELCOME_MESSAGE, isFromUser = false)))
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _documentsCount = MutableStateFlow(0)
    val documentsCount: StateFlow<Int> = _documentsCount.asStateFlow()

    private val _documentUploadState = MutableStateFlow<DocumentUploadState>(DocumentUploadState.Idle)
    val documentUploadState: StateFlow<DocumentUploadState> = _documentUploadState.asStateFlow()

    init {
        // Get initial documents count
        viewModelScope.launch {
            updateDocumentsCount()
        }
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                // Add user message
                _messages.value = _messages.value + Message(text = userMessage, isFromUser = true)
                _isLoading.value = true

                logD("Saving message as document to LOCAL database: $userMessage")

                // Generate embedding with LOCAL Ollama
                val embedding = ollamaClient.generateEmbedding(userMessage)
                logD("✅ Embedding generated: ${embedding.size} dimensions")

                // Save to LOCAL database
                val docId =
                    embeddingsRepository.saveChunk(
                        content = userMessage,
                        embedding = embedding,
                        sourceFile = "manual_entry",
                        sourceType = "manual",
                        chunkIndex = 0,
                        totalChunks = 1
                    )

                logD("💾 Document saved to LOCAL database with ID: $docId")

                // Update documents count
                updateDocumentsCount()

                // Create assistant response
                val assistantMessage =
                    buildString {
                        append("✅ Документ сохранен!\n\n")
                        append("📄 ID документа: $docId\n")
                        append("🔮 Эмбеддинг создан локально через Ollama\n")
                        append("💾 Сохранено в локальной базе данных\n\n")
                        append("Теперь вы можете найти похожие документы по смысловому содержанию!")
                    }

                // Display assistant response with typewriter effect
                displayTypewriterMessage(assistantMessage)
            }.onFailure { error ->
                val errorMessage = "❌ Произошла ошибка: ${error.message}"
                _messages.value = _messages.value + Message(text = errorMessage, isFromUser = false)
                logE("Error in sendMessage", error)
            }

            _isLoading.value = false
        }
    }

    fun uploadPdf(
        uri: Uri,
        fileName: String,
        context: Context
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                // 1. Update state: Reading
                _documentUploadState.value = DocumentUploadState.Reading(fileName)
                _isLoading.value = true

                logD("Extracting text from PDF: $fileName")

                // 2. Extract text from PDF locally using PDFBox
                val extractedText =
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        try {
                            // Initialize PDFBox for Android
                            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

                            // Load PDF document
                            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
                            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()

                            // Extract text from all pages
                            val text = stripper.getText(document)
                            document.close()

                            text
                        } catch (e: Exception) {
                            logE("Error extracting PDF text", e)
                            throw Exception("Failed to extract text from PDF: ${e.message}")
                        }
                    } ?: throw Exception("Failed to open PDF file")

                val textLength = extractedText.length
                logD("Extracted $textLength characters from PDF")

                // 3. Validate text length
                if (extractedText.isBlank()) {
                    throw Exception("No text could be extracted from PDF")
                }

                // 4. Process locally with local Ollama
                processTextLocally(extractedText, fileName, sourceType = "pdf")
            }.onFailure { error ->
                val errorMessage = "❌ Ошибка загрузки PDF: ${error.message}"
                _messages.value = _messages.value + Message(text = errorMessage, isFromUser = false)
                _documentUploadState.value = DocumentUploadState.Error(error.message ?: "Unknown error")
                logE("Error in uploadPdf", error)
            }

            _isLoading.value = false

            // Reset state after 3 seconds
            delay(3000)
            _documentUploadState.value = DocumentUploadState.Idle
        }
    }

    fun uploadTxtFile(
        uri: Uri,
        fileName: String,
        context: Context
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                // 1. Update state: Reading
                _documentUploadState.value = DocumentUploadState.Reading(fileName)
                _isLoading.value = true

                logD("Reading text from .txt file: $fileName")

                // 2. Read .txt file content
                val extractedText =
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { reader ->
                            reader.readText()
                        }
                    } ?: throw Exception("Failed to read .txt file")

                val textLength = extractedText.length
                logD("Read $textLength characters from .txt file")

                // 3. Validate text length
                if (extractedText.isBlank()) {
                    throw Exception(".txt file is empty")
                }

                // 4. Process locally with local Ollama
                processTextLocally(extractedText, fileName, sourceType = "txt")
            }.onFailure { error ->
                val errorMessage = "❌ Ошибка загрузки TXT: ${error.message}"
                _messages.value = _messages.value + Message(text = errorMessage, isFromUser = false)
                _documentUploadState.value = DocumentUploadState.Error(error.message ?: "Unknown error")
                logE("Error in uploadTxtFile", error)
            }

            _isLoading.value = false

            // Reset state after 3 seconds
            delay(3000)
            _documentUploadState.value = DocumentUploadState.Idle
        }
    }

    fun uploadMarkdownFile(
        uri: Uri,
        fileName: String,
        context: Context
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                // 1. Update state: Reading
                _documentUploadState.value = DocumentUploadState.Reading(fileName)
                _isLoading.value = true

                logD("Reading text from .md file: $fileName")

                // 2. Read .md file content
                val extractedText =
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { reader ->
                            reader.readText()
                        }
                    } ?: throw Exception("Failed to read .md file")

                val textLength = extractedText.length
                logD("Read $textLength characters from .md file")

                // 3. Validate text length
                if (extractedText.isBlank()) {
                    throw Exception(".md file is empty")
                }

                // 4. Process locally with local Ollama
                processTextLocally(extractedText, fileName, sourceType = "markdown")
            }.onFailure { error ->
                val errorMessage = "❌ Ошибка загрузки Markdown: ${error.message}"
                _messages.value = _messages.value + Message(text = errorMessage, isFromUser = false)
                _documentUploadState.value = DocumentUploadState.Error(error.message ?: "Unknown error")
                logE("Error in uploadMarkdownFile", error)
            }

            _isLoading.value = false

            // Reset state after 3 seconds
            delay(3000)
            _documentUploadState.value = DocumentUploadState.Idle
        }
    }

    fun uploadDocument(
        uri: Uri,
        fileName: String,
        context: Context
    ) {
        when {
            fileName.endsWith(".pdf", ignoreCase = true) -> {
                uploadPdf(uri, fileName, context)
            }

            fileName.endsWith(".txt", ignoreCase = true) -> {
                uploadTxtFile(uri, fileName, context)
            }

            fileName.endsWith(".md", ignoreCase = true) -> {
                uploadMarkdownFile(uri, fileName, context)
            }

            else -> {
                viewModelScope.launch {
                    _documentUploadState.value =
                        DocumentUploadState.Error(
                            "Unsupported file type. Please select .pdf, .txt, or .md file."
                        )
                    delay(3000)
                    _documentUploadState.value = DocumentUploadState.Idle
                }
            }
        }
    }

    private suspend fun updateDocumentsCount() {
        runAndCatch {
            // Get count from LOCAL database
            val count = embeddingsRepository.getChunksCount()

            _documentsCount.value = count
            logD("Documents count updated from LOCAL database: $count")
        }.onFailure { error ->
            logE("Error updating documents count", error)
        }
    }

    /**
     * Process text locally: chunk it, generate embeddings with local Ollama, then save to LOCAL database
     */
    private suspend fun processTextLocally(
        text: String,
        fileName: String,
        sourceType: String,
        chunkSize: Int = 1000,
        chunkOverlap: Int = 200
    ) {
        logD("🔧 Starting LOCAL processing for: $fileName")
        logD("📊 Text length: ${text.length} characters")

        val startTime = System.currentTimeMillis()

        // 1. Chunk text locally
        _documentUploadState.value = DocumentUploadState.Uploading(fileName, "Chunking text locally...")

        val chunks =
            com.example.aiwithlove.util.TextChunker.chunkTextWithMetadata(
                text = text,
                chunkSize = chunkSize,
                chunkOverlap = chunkOverlap
            )

        val totalChunks = chunks.size
        logD("✂️ Created $totalChunks chunks locally")

        // 2. Process each chunk: generate embedding locally and save to LOCAL database
        var savedCount = 0
        var failedCount = 0

        for (chunk in chunks) {
            try {
                // Update progress
                val progress = "Processing chunk ${chunk.chunkIndex + 1}/$totalChunks..."
                _documentUploadState.value = DocumentUploadState.Uploading(fileName, progress)
                logD("📦 $progress")

                // Generate embedding with LOCAL Ollama
                val embedding = ollamaClient.generateEmbedding(chunk.content)
                logD("✅ Embedding generated: ${embedding.size} dimensions")

                // Save to LOCAL database
                val chunkId =
                    embeddingsRepository.saveChunk(
                        content = "[$fileName - Chunk ${chunk.chunkIndex + 1}/$totalChunks]\n\n${chunk.content}",
                        embedding = embedding,
                        sourceFile = fileName,
                        sourceType = sourceType,
                        chunkIndex = chunk.chunkIndex,
                        totalChunks = totalChunks
                    )

                logD("💾 Chunk ${chunk.chunkIndex + 1} saved to LOCAL database with ID: $chunkId")
                savedCount++
            } catch (e: Exception) {
                logE("❌ Failed to process chunk ${chunk.chunkIndex + 1}", e)
                failedCount++
            }
        }

        val processingTime = (System.currentTimeMillis() - startTime) / 1000.0

        logD("🎉 LOCAL processing complete: $savedCount saved, $failedCount failed in ${processingTime}s")

        // 3. Update documents count
        updateDocumentsCount()

        // 4. Show success message
        val successMessage =
            buildString {
                append("✅ $fileName обработан локально!\n\n")
                append("📄 Файл: $fileName\n")
                append("📊 Создано фрагментов: $savedCount/$totalChunks\n")
                append("📝 Всего символов: ${text.length}\n")
                append("⏱️ Время обработки: ${processingTime.toInt()}s\n")
                if (failedCount > 0) {
                    append("⚠️ Не удалось обработать: $failedCount\n")
                }
                append("\n🔮 Эмбеддинги созданы локально через Ollama!\n")
                append("💾 Все фрагменты сохранены в ЛОКАЛЬНОЙ базе данных!")
            }

        displayTypewriterMessage(successMessage)

        // 5. Update state: Success
        _documentUploadState.value = DocumentUploadState.Success(fileName, savedCount)
    }

    private suspend fun displayTypewriterMessage(message: String) {
        val assistantMessage = Message(text = "", isFromUser = false)
        _messages.value = _messages.value + assistantMessage

        var currentText = ""
        message.forEach { char ->
            currentText += char
            _messages.value = _messages.value.dropLast(1) + assistantMessage.copy(text = currentText)
            delay(10)
        }
    }

    companion object {
        private val WELCOME_MESSAGE =
            """
            🔍 Индексация документов с Ollama

            Ваши сообщения будут автоматически обрабатываться:
            • Создание эмбеддингов через Ollama (nomic-embed-text)
            • Сохранение в базу данных на сервере
            • Возможность поиска похожих документов

            Отправьте первое сообщение, чтобы начать индексацию!
            """.trimIndent()
    }
}

sealed class DocumentUploadState {
    object Idle : DocumentUploadState()
    data class Reading(
        val fileName: String
    ) : DocumentUploadState()
    data class Uploading(
        val fileName: String,
        val progress: String
    ) : DocumentUploadState()
    data class Success(
        val fileName: String,
        val chunksCount: Int
    ) : DocumentUploadState()
    data class Error(
        val message: String
    ) : DocumentUploadState()
}
