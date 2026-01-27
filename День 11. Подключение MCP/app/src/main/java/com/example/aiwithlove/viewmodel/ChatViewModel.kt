package com.example.aiwithlove.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.data.PerplexityApiService
import com.example.aiwithlove.database.ChatRepository
import com.example.aiwithlove.mcp.McpClient
import com.example.aiwithlove.mcp.McpServerConfig
import com.example.aiwithlove.mcp.McpServers
import com.example.aiwithlove.mcp.McpTool
import com.example.aiwithlove.util.ILoggable
import com.example.aiwithlove.util.runAndCatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.aiwithlove.data.ChatMessage as ApiChatMessage

class ChatViewModel(
    private val perplexityService: PerplexityApiService,
    private val chatRepository: ChatRepository,
    private val mcpClient: McpClient
) : ViewModel(),
    ILoggable {

    private val _messages =
        MutableStateFlow(listOf(Message(text = CONGRATS_MESSAGE, isFromUser = false)))
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _mcpServers = MutableStateFlow(McpServers.availableServers)
    val mcpServers: StateFlow<List<McpServerConfig>> = _mcpServers.asStateFlow()

    private val _mcpTools = MutableStateFlow<List<McpTool>>(emptyList())
    val mcpTools: StateFlow<List<McpTool>> = _mcpTools.asStateFlow()

    private val _showMcpDialog = MutableStateFlow(false)
    val showMcpDialog: StateFlow<Boolean> = _showMcpDialog.asStateFlow()

    private var storedSummary: Message? = null
    private var userMessagesCountSinceAppLaunch = 0

    init {
        loadChatHistory()
        loadMcpTools()
    }

    fun toggleMcpDialog() {
        _showMcpDialog.value = !_showMcpDialog.value
    }

    fun toggleMcpServer(serverId: String) {
        _mcpServers.value =
            _mcpServers.value.map { server ->
                if (server.id == serverId) {
                    server.copy(isEnabled = !server.isEnabled)
                } else {
                    server
                }
            }
        if (_mcpServers.value.any { it.isEnabled }) {
            loadMcpTools()
        }
    }

    private fun loadMcpTools() {
        val enabledServers = _mcpServers.value.filter { it.isEnabled }
        if (enabledServers.isEmpty()) {
            _mcpTools.value = emptyList()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                mcpClient.initialize()
                val tools = mcpClient.listTools()
                _mcpTools.value = tools
                logD("Загружено ${tools.size} MCP инструментов")
            }.onFailure { error ->
                logE("Ошибка загрузки MCP инструментов", error)
                _mcpTools.value = emptyList()
            }
        }
    }

    private fun loadChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                val summary = chatRepository.getSummary()
                storedSummary = summary

                val savedMessages = chatRepository.getAllMessages()
                if (savedMessages.isNotEmpty()) {
                    logD("Загружено ${savedMessages.size} сообщений из БД")
                    val welcomeMessage = _messages.value.first()
                    _messages.value = listOf(welcomeMessage) + savedMessages

                    if (summary != null) {
                        logD("Сводка загружена и будет использоваться для контекста API")
                    }
                } else {
                    logD("История сообщений пуста")
                }
            }.onFailure { error ->
                logE("Ошибка при загрузке истории сообщений", error)
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                chatRepository.clearAllMessages()
                chatRepository.clearSummary()
                storedSummary = null
                userMessagesCountSinceAppLaunch = 0
                _messages.value = listOf(Message(text = CONGRATS_MESSAGE, isFromUser = false))
                logD("Чат очищен, контекст сброшен, БД очищена")
            }.onFailure { error ->
                logE("Ошибка при очистке БД", error)
            }
        }
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || _isLoading.value) return

        val userMsg =
            Message(
                text = userMessage,
                isFromUser = true
            )
        _messages.value = _messages.value + userMsg
        _isLoading.value = true
        userMessagesCountSinceAppLaunch++

        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                chatRepository.saveUserMessage(userMsg)
            }.onFailure { error ->
                logE("Ошибка при сохранении пользовательского сообщения", error)
            }
        }

        val thinkingMessage =
            Message(
                text = "Думаю...",
                isFromUser = false
            )
        _messages.value = _messages.value + thinkingMessage
        val thinkingMessageIndex = _messages.value.size - 1

        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                val allMessages = _messages.value.drop(1)
                val lastSummaryIndex = allMessages.indexOfLast { it.isSummary }

                val messagesToSend =
                    if (lastSummaryIndex >= 0) {
                        val summary = allMessages[lastSummaryIndex]
                        val messagesAfterSummary =
                            allMessages.subList(lastSummaryIndex + 1, allMessages.size)
                                .filterNot { it.isCompressed }
                        listOf(summary) + messagesAfterSummary
                    } else {
                        allMessages.filterNot { it.isCompressed }
                    }

                val userMessages =
                    buildList {
                        if (storedSummary != null) {
                            add(
                                ApiChatMessage(
                                    role = "system",
                                    content = storedSummary!!.text
                                )
                            )
                            logD("Добавлена сводка из БД в контекст API")
                        }

                        addAll(
                            messagesToSend
                                .filterNot { it.text == "Думаю..." || it.isCompressionNotice }
                                .map { msg ->
                                    val role =
                                        when {
                                            msg.isSystemMessage -> "system"
                                            msg.isFromUser -> "user"
                                            else -> "assistant"
                                        }
                                    ApiChatMessage(
                                        role = role,
                                        content = msg.text
                                    )
                                }
                        )
                    }

                logD("Отправка ${userMessages.size} сообщений в API")
                perplexityService.sendMessage(
                    messages = userMessages,
                    model = "sonar",
                    maxTokens = MAX_TOKENS
                )
            }.onSuccess { result ->
                result
                    .onSuccess { response ->
                        val rawResponse =
                            response.choices
                                .firstOrNull()
                                ?.message
                                ?.content
                                ?: "Извините, не удалось получить ответ."

                        val fullResponse = rawResponse.trim()

                        val usage = response.usage
                        val promptTokens = usage?.prompt_tokens ?: 0
                        val completionTokens = usage?.completion_tokens ?: 0

                        logD("Успешно получен ответ от Perplexity API")

                        val currentMessages = _messages.value.toMutableList()
                        if (thinkingMessageIndex < currentMessages.size) {
                            currentMessages[thinkingMessageIndex] =
                                Message(
                                    text = "",
                                    isFromUser = false,
                                    promptTokens = promptTokens,
                                    completionTokens = completionTokens
                                )
                            _messages.value = currentMessages
                        }

                        typewriterEffect(
                            fullResponse,
                            thinkingMessageIndex,
                            promptTokens,
                            completionTokens
                        )

                        if (shouldCompressDialog()) {
                            compressDialogWithNotification()
                        }
                    }.onFailure { error ->
                        logE("Ошибка при получении ответа от Perplexity API", error)
                        val currentMessages = _messages.value.toMutableList()
                        if (thinkingMessageIndex < currentMessages.size) {
                            currentMessages[thinkingMessageIndex] =
                                Message(
                                    text = "Ошибка: ${error.message ?: "Неизвестная ошибка"}",
                                    isFromUser = false
                                )
                            _messages.value = currentMessages
                        }
                        _isLoading.value = false
                    }
            }.onFailure { error ->
                logE("Исключение при отправке запроса к Perplexity API", error)
                val currentMessages = _messages.value.toMutableList()
                if (thinkingMessageIndex < currentMessages.size) {
                    currentMessages[thinkingMessageIndex] =
                        Message(
                            text = "Ошибка при отправке запроса: ${error.message}",
                            isFromUser = false
                        )
                    _messages.value = currentMessages
                }
                _isLoading.value = false
            }
        }
    }

    data class Message(
        val text: String,
        val isFromUser: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val promptTokens: Int? = null,
        val completionTokens: Int? = null,
        val isSystemMessage: Boolean = false,
        val isSummary: Boolean = false,
        val isCompressionNotice: Boolean = false,
        val isCompressed: Boolean = false
    )

    private suspend fun typewriterEffect(
        fullText: String,
        messageIndex: Int,
        promptTokens: Int,
        completionTokens: Int
    ) {
        val charsPerDelay = 3
        val delayMs = 30L

        runAndCatch {
            for (i in 0..fullText.length step charsPerDelay) {
                val currentText = fullText.substring(0, minOf(i, fullText.length))
                val currentMessages = _messages.value.toMutableList()
                if (messageIndex < currentMessages.size) {
                    currentMessages[messageIndex] =
                        currentMessages[messageIndex].copy(text = currentText)
                    _messages.value = currentMessages
                }
                delay(delayMs)
            }

            val finalMessages = _messages.value.toMutableList()
            if (messageIndex < finalMessages.size) {
                val assistantMessage =
                    finalMessages[messageIndex].copy(
                        text = fullText,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens
                    )
                finalMessages[messageIndex] = assistantMessage
                _messages.value = finalMessages

                viewModelScope.launch(Dispatchers.IO) {
                    runAndCatch {
                        chatRepository.saveAssistantMessage(assistantMessage)
                    }.onFailure { error ->
                        logE("Ошибка при сохранении ответа ассистента", error)
                    }
                }
            }

            _isLoading.value = false
        }.onFailure {
            _isLoading.value = false
        }
    }

    private fun shouldCompressDialog(): Boolean {
        val allMessages = _messages.value.drop(1)
        val lastSummaryIndex = allMessages.indexOfLast { it.isSummary }

        val messagesAfterSummary =
            if (lastSummaryIndex >= 0) {
                allMessages.subList(lastSummaryIndex + 1, allMessages.size)
            } else {
                allMessages
            }

        val userMessagesCount =
            messagesAfterSummary
                .filterNot { it.isCompressionNotice || it.text == "Думаю..." || it.isCompressed }
                .count { it.isFromUser }

        val canCompress = userMessagesCount >= COMPRESSION_THRESHOLD && userMessagesCountSinceAppLaunch >= COMPRESSION_THRESHOLD

        logD(
            "Проверка сжатия: $userMessagesCount сообщений с последнего сжатия, " +
                "$userMessagesCountSinceAppLaunch с запуска приложения. Можно сжать: $canCompress"
        )

        return canCompress
    }

    private suspend fun compressDialogWithNotification() {
        val compressionNotice =
            Message(
                text = "🗜️ Сжимаю историю диалога...",
                isFromUser = false,
                isCompressionNotice = true
            )
        _messages.value = _messages.value + compressionNotice

        compressDialog()

        val completionNotice =
            Message(
                text = "✅ История диалога сжата",
                isFromUser = false,
                isCompressionNotice = true
            )
        _messages.value = _messages.value.filterNot {
            it.text.contains("Сжимаю историю диалога")
        } + completionNotice
    }

    private suspend fun compressDialog() {
        logD("Начало сжатия диалога...")

        val allMessages = _messages.value.drop(1).toList()
        val lastSummaryIndex = allMessages.indexOfLast { it.isSummary }

        val messagesToCompress =
            if (lastSummaryIndex >= 0) {
                allMessages.subList(lastSummaryIndex + 1, allMessages.size)
                    .filterNot { it.text.contains("Сжимаю историю диалога") || it.isCompressionNotice }
            } else {
                allMessages.filterNot { it.text.contains("Сжимаю историю диалога") || it.isCompressionNotice }
            }

        if (messagesToCompress.isEmpty()) {
            logD("Нет сообщений для сжатия")
            return
        }

        logD("Сжимаем ${messagesToCompress.size} сообщений")

        val conversationText =
            messagesToCompress.joinToString("\n") { msg ->
                val role = if (msg.isFromUser) "Пользователь" else "Ассистент"
                "$role: ${msg.text}"
            }

        val summaryPrompt =
            """Создай краткое резюме следующего диалога. Сохрани ключевые темы, факты и контекст. Будь лаконичен, но информативен.

Диалог:
$conversationText

Краткое резюме:"""

        runAndCatch {
            val summaryMessages =
                listOf(
                    ApiChatMessage(
                        role = "user",
                        content = summaryPrompt
                    )
                )

            perplexityService.sendMessage(
                messages = summaryMessages,
                model = "sonar",
                maxTokens = MAX_TOKENS,
                temperature = 0.3
            )
        }.onSuccess { result ->
            result.onSuccess { response ->
                val summary = response.choices.firstOrNull()?.message?.content ?: ""

                if (summary.isNotEmpty()) {
                    logD("Получено резюме: ${summary.take(100)}...")

                    val summaryMessage =
                        Message(
                            text = summary,
                            isFromUser = false,
                            isSystemMessage = true,
                            isSummary = true
                        )

                    val welcomeMessage = _messages.value.first()
                    val visibleMessages =
                        _messages.value.drop(1)
                            .filterNot { it.text.contains("Сжимаю историю диалога") || it.isSummary }
                            .map { msg ->
                                if (messagesToCompress.contains(msg)) {
                                    msg.copy(isCompressed = true)
                                } else {
                                    msg
                                }
                            }

                    _messages.value = listOf(welcomeMessage, summaryMessage) + visibleMessages

                    storedSummary = summaryMessage

                    viewModelScope.launch(Dispatchers.IO) {
                        runAndCatch {
                            val totalMessagesInDb = chatRepository.getAllMessages().size
                            chatRepository.saveSummary(summary, totalMessagesInDb)
                            logD("Сводка сохранена в БД")
                        }.onFailure { error ->
                            logE("Ошибка при сохранении сводки в БД", error)
                        }
                    }

                    logD(
                        "Диалог успешно сжат. Сжато ${messagesToCompress.size} сообщений в резюме, ${visibleMessages.size} сообщений остаются видимыми"
                    )
                } else {
                    logD("Не удалось получить резюме")
                }
            }.onFailure { error ->
                logE("Ошибка при создании резюме", error)
            }
        }.onFailure { error ->
            logE("Исключение при сжатии диалога", error)
        }
    }

    companion object {
        private const val MAX_TOKENS = 1000
        private const val COMPRESSION_THRESHOLD = 5
        private const val CONGRATS_MESSAGE =
            "Привет! Я ваш ИИ-помощник на базе Perplexity API " +
                "(модель: sonar).\n\nЛимит токенов для ответа: $MAX_TOKENS токенов\n\n🗜️ Включено" +
                " автоматическое сжатие диалога каждые $COMPRESSION_THRESHOLD ваших сообщений для" +
                " оптимизации токенов!\n\nЧем могу помочь?"
    }
}
