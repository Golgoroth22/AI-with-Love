package com.example.aiwithlove.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.data.model.DataFile
import com.example.aiwithlove.data.model.Message
import com.example.aiwithlove.ollama.OllamaClient
import com.example.aiwithlove.ollama.OllamaMessage
import com.example.aiwithlove.ollama.OllamaOptions
import com.example.aiwithlove.util.DataFileParser
import com.example.aiwithlove.util.ErrorLogRepository
import com.example.aiwithlove.util.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ANALYST_OPTIONS = OllamaOptions(
    temperature = 0.2,
    num_predict = 512,
    num_ctx = 4096,
    top_p = 0.9,
    repeat_penalty = 1.1
)

private val SYSTEM_PROMPT_TEMPLATE = """
You are a precise data analyst assistant. Answer questions only based on the data provided.
For counts and statistics, compute exact numbers. Format lists as bullet points.

DATA FILE CONTENTS:
---
{DATA}
---
""".trimIndent()

class AnalystViewModel(
    private val ollamaClient: OllamaClient,
    private val translationService: TranslationService,
    private val context: Context,
    private val errorLogRepository: ErrorLogRepository
) : ViewModel() {

    private val _loadedFile = MutableStateFlow<DataFile?>(null)
    val loadedFile: StateFlow<DataFile?> = _loadedFile.asStateFlow()

    private val _parseError = MutableStateFlow<String?>(null)
    val parseError: StateFlow<String?> = _parseError.asStateFlow()

    private val _isParsingFile = MutableStateFlow(false)
    val isParsingFile: StateFlow<Boolean> = _isParsingFile.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val conversationHistory = mutableListOf<OllamaMessage>()

    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            _isParsingFile.value = true
            _parseError.value = null
            try {
                val file = withContext(Dispatchers.IO) {
                    DataFileParser.parse(context, uri)
                }
                _loadedFile.value = file
                clearChatHistory()
            } catch (e: Exception) {
                _parseError.value = e.message ?: "Не удалось прочитать файл"
            } finally {
                _isParsingFile.value = false
            }
        }
    }

    fun loadSampleFile() {
        viewModelScope.launch {
            _isParsingFile.value = true
            _parseError.value = null
            try {
                val raw = withContext(Dispatchers.IO) { errorLogRepository.readContent() }
                if (errorLogRepository.rowCount == 0) {
                    _parseError.value = "Файл пуст. Сгенерируйте ошибки в эмуляторе."
                    return@launch
                }
                val file = DataFileParser.parseCsvText("generated_errors.csv", raw)
                _loadedFile.value = file
                clearChatHistory()
            } catch (e: Exception) {
                _parseError.value = e.message ?: "Не удалось загрузить данные"
            } finally {
                _isParsingFile.value = false
            }
        }
    }

    fun sendMessage(text: String) {
        val file = _loadedFile.value ?: return
        if (text.isBlank() || _isLoading.value) return

        val userMsg = Message(text = text, isFromUser = true)
        _messages.value = _messages.value + userMsg

        _isLoading.value = true

        val thinkingMsg = Message(text = "Анализирую данные...", isFromUser = false)
        _messages.value = _messages.value + thinkingMsg
        val thinkingIndex = _messages.value.size - 1

        viewModelScope.launch {
            try {
                val englishInput = translationService.toEnglish(text)
                conversationHistory.add(OllamaMessage(role = "user", content = englishInput))

                val systemContent = SYSTEM_PROMPT_TEMPLATE.replace("{DATA}", file.rawContent)

                // Prepend system message into the messages array so Ollama always
                // processes it fresh — the keep_alive cached Yuki context ignores
                // the separate `system` request field, but messages are never skipped.
                val messagesToSend = listOf(
                    OllamaMessage(role = "system", content = systemContent)
                ) + conversationHistory

                val englishResponse = ollamaClient.chat(
                    messages = messagesToSend,
                    systemPromptOverride = systemContent,
                    optionsOverride = ANALYST_OPTIONS
                )
                conversationHistory.add(OllamaMessage(role = "assistant", content = englishResponse))

                val russianResponse = translationService.toRussian(englishResponse)

                val responseMsg = Message(text = russianResponse, isFromUser = false)
                val current = _messages.value.toMutableList()
                current[thinkingIndex] = responseMsg
                _messages.value = current

            } catch (e: Exception) {
                val errorMsg = Message(
                    text = "Ошибка: ${e.message}",
                    isFromUser = false
                )
                val current = _messages.value.toMutableList()
                if (thinkingIndex < current.size) {
                    current[thinkingIndex] = errorMsg
                    _messages.value = current
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        clearChatHistory()
    }

    fun clearFile() {
        _loadedFile.value = null
        _parseError.value = null
        clearChatHistory()
    }

    private fun clearChatHistory() {
        conversationHistory.clear()
        _messages.value = emptyList()
    }
}
