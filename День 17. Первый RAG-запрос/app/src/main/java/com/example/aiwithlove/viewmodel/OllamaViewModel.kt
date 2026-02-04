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
    private val mcpClient: McpClient
) : ViewModel(),
    ILoggable {

    private val _messages = MutableStateFlow(listOf(Message(text = WELCOME_MESSAGE, isFromUser = false)))
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _documentsCount = MutableStateFlow(0)
    val documentsCount: StateFlow<Int> = _documentsCount.asStateFlow()

    private val _pdfUploadState = MutableStateFlow<PdfUploadState>(PdfUploadState.Idle)
    val pdfUploadState: StateFlow<PdfUploadState> = _pdfUploadState.asStateFlow()

    init {
        // Get initial documents count
        viewModelScope.launch {
            updateDocumentsCount()
        }
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        viewModelScope.launch {
            runAndCatch {
                // Add user message
                _messages.value = _messages.value + Message(text = userMessage, isFromUser = true)
                _isLoading.value = true

                logD("Sending message to save as document: $userMessage")

                // Save document with embedding to MCP server
                val saveResult =
                    mcpClient.callTool(
                        toolName = "save_document",
                        arguments = mapOf("content" to userMessage)
                    )

                logD("Save document result: $saveResult")

                // Parse the result to check success
                val resultJson = Json.parseToJsonElement(saveResult).jsonObject
                val contentArray = resultJson["content"]?.jsonArray

                if (contentArray != null && contentArray.isNotEmpty()) {
                    val firstContent = contentArray[0].jsonObject
                    val textContent = firstContent["text"]?.jsonPrimitive?.content ?: ""

                    if (textContent.isNotEmpty()) {
                        val parsedResult = Json.parseToJsonElement(textContent).jsonObject
                        val success = parsedResult["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        val docId = parsedResult["document_id"]?.jsonPrimitive?.content?.toIntOrNull()

                        if (success && docId != null) {
                            // Update documents count
                            updateDocumentsCount()

                            // Create assistant response
                            val assistantMessage =
                                buildString {
                                    append("✅ Документ сохранен!\n\n")
                                    append("📄 ID документа: $docId\n")
                                    append("🔮 Эмбеддинг создан и сохранен в базу данных\n\n")
                                    append("Теперь вы можете найти похожие документы по смысловому содержанию!")
                                }

                            // Display assistant response with typewriter effect
                            displayTypewriterMessage(assistantMessage)
                        } else {
                            val errorMsg = parsedResult["error"]?.jsonPrimitive?.content ?: "Неизвестная ошибка"
                            val assistantMessage = "❌ Ошибка при сохранении документа: $errorMsg"
                            _messages.value = _messages.value + Message(text = assistantMessage, isFromUser = false)
                            logE("Failed to save document: $errorMsg")
                        }
                    } else {
                        val assistantMessage = "❌ Ошибка: получен пустой ответ от сервера"
                        _messages.value = _messages.value + Message(text = assistantMessage, isFromUser = false)
                        logE("Empty text content in save response")
                    }
                } else {
                    val assistantMessage = "❌ Ошибка: некорректный формат ответа от сервера"
                    _messages.value = _messages.value + Message(text = assistantMessage, isFromUser = false)
                    logE("Invalid response format from server")
                }
            }.onFailure { error ->
                val errorMessage = "❌ Произошла ошибка: ${error.message}"
                _messages.value = _messages.value + Message(text = errorMessage, isFromUser = false)
                logE("Error in sendMessage", error)
            }

            _isLoading.value = false
        }
    }

    fun uploadPdf(uri: Uri, fileName: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                // 1. Update state: Reading
                _pdfUploadState.value = PdfUploadState.Reading(fileName)
                _isLoading.value = true

                logD("Extracting text from PDF: $fileName")

                // 2. Extract text from PDF locally using PDFBox
                val extractedText = context.contentResolver.openInputStream(uri)?.use { inputStream ->
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
                logD("Extracted ${textLength} characters from PDF")

                // 3. Validate text length
                if (extractedText.isBlank()) {
                    throw Exception("No text could be extracted from PDF")
                }

                // 4. Update state: Uploading
                _pdfUploadState.value = PdfUploadState.Uploading(fileName, "Sending text to server for chunking...")

                // 5. Call remote MCP server with extracted text
                val result = mcpClient.callTool(
                    toolName = "process_text_chunks",
                    arguments = mapOf(
                        "text" to extractedText,
                        "filename" to fileName
                    )
                )

                logD("PDF processing result: $result")

                // 7. Parse result (MCP response format: result.content[0].text contains JSON)
                val resultJson = Json.parseToJsonElement(result).jsonObject
                val contentArray = resultJson["content"]?.jsonArray

                if (contentArray != null && contentArray.isNotEmpty()) {
                    val firstContent = contentArray[0].jsonObject
                    val textContent = firstContent["text"]?.jsonPrimitive?.content ?: ""

                    if (textContent.isNotEmpty()) {
                        val parsedResult = Json.parseToJsonElement(textContent).jsonObject
                        val success = parsedResult["success"]?.jsonPrimitive?.content?.toBoolean() ?: false

                        if (success) {
                            val chunksCount = parsedResult["chunks_saved"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            val totalChars = parsedResult["total_characters"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                            // 8. Update documents count
                            updateDocumentsCount()

                            // 9. Show success message with typewriter animation
                            val successMessage = buildString {
                                append("✅ PDF обработан успешно!\n\n")
                                append("📄 Файл: $fileName\n")
                                append("📊 Создано фрагментов: $chunksCount\n")
                                append("📝 Всего символов: $totalChars\n\n")
                                append("🔮 Все фрагменты проиндексированы и доступны для поиска!")
                            }

                            displayTypewriterMessage(successMessage)

                            // 10. Update state: Success
                            _pdfUploadState.value = PdfUploadState.Success(fileName, chunksCount)
                        } else {
                            val errorMsg = parsedResult["error"]?.jsonPrimitive?.content ?: "Unknown error"
                            _pdfUploadState.value = PdfUploadState.Error(errorMsg)

                            val errorMessage = "❌ Ошибка при обработке PDF: $errorMsg"
                            _messages.value = _messages.value + Message(text = errorMessage, isFromUser = false)
                        }
                    }
                }
            }.onFailure { error ->
                val errorMessage = "❌ Ошибка загрузки PDF: ${error.message}"
                _messages.value = _messages.value + Message(text = errorMessage, isFromUser = false)
                _pdfUploadState.value = PdfUploadState.Error(error.message ?: "Unknown error")
                logE("Error in uploadPdf", error)
            }

            _isLoading.value = false

            // Reset state after 3 seconds
            delay(3000)
            _pdfUploadState.value = PdfUploadState.Idle
        }
    }

    private suspend fun updateDocumentsCount() {
        runAndCatch {
            // Search with empty query to get all documents
            val searchResult =
                mcpClient.callTool(
                    toolName = "search_similar",
                    arguments =
                        mapOf(
                            "query" to "test",
                            "limit" to 1000
                        )
                )

            logD("Search result for count: $searchResult")

            // Parse the result to get documents count
            val resultJson = Json.parseToJsonElement(searchResult).jsonObject
            val contentArray = resultJson["content"]?.jsonArray

            if (contentArray != null && contentArray.isNotEmpty()) {
                val firstContent = contentArray[0].jsonObject
                val textContent = firstContent["text"]?.jsonPrimitive?.content ?: ""

                if (textContent.isNotEmpty()) {
                    val parsedResult = Json.parseToJsonElement(textContent).jsonObject
                    val count = parsedResult["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                    _documentsCount.value = count
                    logD("Documents count updated: $count")
                } else {
                    logD("Empty text content in response")
                }
            } else {
                logD("Empty content array in response")
            }
        }.onFailure { error ->
            logE("Error updating documents count", error)
        }
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

sealed class PdfUploadState {
    object Idle : PdfUploadState()
    data class Reading(val fileName: String) : PdfUploadState()
    data class Uploading(val fileName: String, val progress: String) : PdfUploadState()
    data class Success(val fileName: String, val chunksCount: Int) : PdfUploadState()
    data class Error(val message: String) : PdfUploadState()
}
