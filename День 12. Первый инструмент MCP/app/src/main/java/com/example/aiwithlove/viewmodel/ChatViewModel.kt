package com.example.aiwithlove.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.data.AgenticResponse
import com.example.aiwithlove.data.AgenticTool
import com.example.aiwithlove.data.PerplexityApiService
import com.example.aiwithlove.database.ChatRepository
import com.example.aiwithlove.mcp.McpClient
import com.example.aiwithlove.mcp.McpServerConfig
import com.example.aiwithlove.mcp.McpServers
import com.example.aiwithlove.util.ILoggable
import com.example.aiwithlove.util.runAndCatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

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

    private val _showMcpDialog = MutableStateFlow(false)
    val showMcpDialog: StateFlow<Boolean> = _showMcpDialog.asStateFlow()

    private var storedSummary: Message? = null
    private var userMessagesCountSinceAppLaunch = 0

    init {
        loadChatHistory()
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
    }

    private fun isJokeServerEnabled(): Boolean = _mcpServers.value.any { it.id == "jokes" && it.isEnabled }

    private fun userMentionsJokeApi(message: String): Boolean {
        val keywords = listOf("jokeapi", "joke api", "джокапи", "жокапи", "joke-api")
        return keywords.any { message.lowercase().contains(it) }
    }

    private fun buildAgenticJokeTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("category") {
                        put("type", "string")
                        put("description", "Joke category: Any, Programming, Misc, Dark, Pun, Spooky, Christmas")
                        put("default", "Any")
                    }
                    putJsonObject("blacklistFlags") {
                        put("type", "string")
                        put("description", "Comma-separated flags to blacklist: nsfw,religious,political,racist,sexist,explicit")
                        put("default", "nsfw,religious,political,racist,sexist,explicit")
                    }
                }
                putJsonArray("required") { }
            }

        return AgenticTool(
            type = "function",
            name = "get_joke",
            description = "Fetches a random joke from JokeAPI. Call this tool to get a joke, then translate it to Russian for the user.",
            parameters = parameters
        )
    }

    data class ToolExecutionResult(
        val result: String,
        val mcpToolInfo: McpToolInfo?
    )

    private suspend fun executeAgenticToolCall(
        toolName: String,
        arguments: String?
    ): ToolExecutionResult {
        logD("🔧 Executing agentic tool call: $toolName")

        return when (toolName) {
            "get_joke" -> {
                runAndCatch {
                    val args =
                        try {
                            if (arguments != null) {
                                val argsJson = Json.parseToJsonElement(arguments)
                                if (argsJson is JsonObject) {
                                    mapOf(
                                        "category" to ((argsJson["category"] as? JsonPrimitive)?.content ?: "Any"),
                                        "blacklistFlags" to
                                            (
                                                (argsJson["blacklistFlags"] as? JsonPrimitive)?.content
                                                    ?: "nsfw,religious,political,racist,sexist,explicit"
                                            )
                                    )
                                } else {
                                    defaultJokeArgs()
                                }
                            } else {
                                defaultJokeArgs()
                            }
                        } catch (e: Exception) {
                            logE("🔧 Failed to parse tool arguments, using defaults", e)
                            defaultJokeArgs()
                        }

                    val requestBody =
                        Json.encodeToString(
                            kotlinx.serialization.serializer<Map<String, String>>(),
                            args
                        )
                    logD("🔧 Calling MCP server with args: $args")

                    val mcpResult = mcpClient.callTool("get_joke", args)
                    logD("🔧 MCP result: $mcpResult")

                    val parsedResult = parseJokeFromMcpResult(mcpResult)

                    ToolExecutionResult(
                        result = parsedResult,
                        mcpToolInfo =
                            McpToolInfo(
                                toolName = "get_joke",
                                requestBody = requestBody,
                                responseBody = parsedResult
                            )
                    )
                }.getOrElse { error ->
                    logE("🔧 Tool execution failed", error)
                    ToolExecutionResult(
                        result = """{"error": true, "message": "${error.message}"}""",
                        mcpToolInfo = null
                    )
                }
            }

            else -> {
                logE("🔧 Unknown tool: $toolName", null)
                ToolExecutionResult(
                    result = """{"error": true, "message": "Unknown tool: $toolName"}""",
                    mcpToolInfo = null
                )
            }
        }
    }

    private fun defaultJokeArgs() =
        mapOf(
            "category" to "Any",
            "blacklistFlags" to "nsfw,religious,political,racist,sexist,explicit"
        )

    private fun parseJokeFromMcpResult(mcpResult: String): String =
        runAndCatch {
            val jokeData = Json.parseToJsonElement(mcpResult)

            if (jokeData is JsonObject) {
                val content = jokeData["content"] as? JsonArray
                val textContent = content?.firstOrNull() as? JsonObject
                val textString = (textContent?.get("text") as? JsonPrimitive)?.content

                if (textString != null) {
                    textString
                } else {
                    mcpResult
                }
            } else {
                mcpResult
            }
        }.getOrElse { mcpResult }

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
            val useTools = isJokeServerEnabled() && userMentionsJokeApi(userMessage)
            logD("🎭 Use Agentic API, tools enabled: $useTools")
            sendWithAgenticApi(userMessage, thinkingMessageIndex, useTools)
        }
    }

    private suspend fun sendWithAgenticApi(
        userMessage: String,
        thinkingMessageIndex: Int,
        useTools: Boolean = false
    ) {
        var capturedMcpToolInfo: McpToolInfo? = null

        runAndCatch {
            val conversationContext = buildConversationContext()
            val input =
                if (conversationContext.isNotEmpty()) {
                    "$conversationContext\n\nUser: $userMessage"
                } else {
                    userMessage
                }

            val tools = if (useTools) listOf(buildAgenticJokeTool()) else null
            val instructions =
                if (useTools) {
                    "You are a helpful assistant. When user asks for a joke using JokeAPI, use the get_joke tool and translate the result to Russian. Respond in Russian."
                } else {
                    "You are a helpful assistant. Respond in Russian."
                }

            logD("📤 Sending Agentic request with ${tools?.size ?: 0} tools")

            var response =
                perplexityService.sendAgenticRequest(
                    input = input,
                    model = AGENTIC_MODEL,
                    instructions = instructions,
                    tools = tools
                ).getOrThrow()

            var iterations = 0
            val maxIterations = 5
            var currentInput = input

            while (hasToolCalls(response) && iterations < maxIterations) {
                iterations++
                logD("🔧 Tool calls detected in Agentic response, iteration $iterations")

                val toolResults = mutableListOf<String>()
                response.output?.filter { it.type == "function_call" }?.forEach { toolCall ->
                    val toolName = toolCall.name ?: return@forEach
                    val arguments = toolCall.arguments
                    logD("🔧 Executing tool: $toolName")
                    val executionResult = executeAgenticToolCall(toolName, arguments)
                    toolResults.add("Tool $toolName result: ${executionResult.result}")

                    if (executionResult.mcpToolInfo != null) {
                        capturedMcpToolInfo = executionResult.mcpToolInfo
                    }
                }

                currentInput =
                    "$currentInput\n\nTool results:\n${toolResults.joinToString(
                        "\n"
                    )}\n\nPlease use these results to complete your response. Translate the joke to Russian."

                response =
                    perplexityService.sendAgenticRequest(
                        input = currentInput,
                        model = AGENTIC_MODEL,
                        instructions = instructions,
                        tools = tools
                    ).getOrThrow()
            }

            response
        }.onSuccess { response ->
            val fullResponse = extractTextFromResponse(response)
            val mcpInfo = capturedMcpToolInfo

            val usage = response.usage
            val promptTokens = usage?.inputTokens ?: 0
            val completionTokens = usage?.outputTokens ?: 0

            logD("✅ Successfully received Agentic response")

            val currentMessages = _messages.value.toMutableList()
            if (thinkingMessageIndex < currentMessages.size) {
                currentMessages[thinkingMessageIndex] =
                    Message(
                        text = "",
                        isFromUser = false,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        mcpToolInfo = mcpInfo
                    )
                _messages.value = currentMessages
            }

            typewriterEffect(
                fullResponse,
                thinkingMessageIndex,
                promptTokens,
                completionTokens,
                mcpInfo
            )

            if (shouldCompressDialog()) {
                compressDialogWithNotification()
            }
        }.onFailure { error ->
            logE("Ошибка при отправке Agentic запроса", error)
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
    }

    private fun hasToolCalls(response: AgenticResponse): Boolean = response.output?.any { it.type == "function_call" } == true

    private fun extractTextFromResponse(response: AgenticResponse): String {
        response.outputText?.let { return it.trim() }

        response.output?.forEach { outputItem ->
            if (outputItem.type == "message") {
                outputItem.content?.forEach { contentItem ->
                    if (contentItem.type == "output_text" && contentItem.text != null) {
                        return contentItem.text.trim()
                    }
                }
            }
        }

        return "Извините, не удалось получить ответ."
    }

    private fun buildConversationContext(): String {
        val dialogMessages =
            _messages.value
                .drop(1)
                .filterNot { it.text == "Думаю..." || it.isCompressionNotice || it.isSystemMessage }
                .takeLast(10)

        if (dialogMessages.isEmpty()) return ""

        return dialogMessages.dropLast(1).joinToString("\n") { msg ->
            val role = if (msg.isFromUser) "User" else "Assistant"
            "$role: ${msg.text}"
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
        val isCompressed: Boolean = false,
        val mcpToolInfo: McpToolInfo? = null
    )

    data class McpToolInfo(
        val toolName: String,
        val requestBody: String,
        val responseBody: String
    )

    private suspend fun typewriterEffect(
        fullText: String,
        messageIndex: Int,
        promptTokens: Int,
        completionTokens: Int,
        mcpToolInfo: McpToolInfo? = null
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
                        completionTokens = completionTokens,
                        mcpToolInfo = mcpToolInfo
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
            perplexityService.sendAgenticRequest(
                input = summaryPrompt,
                model = AGENTIC_MODEL,
                instructions = "Create a brief summary in Russian. Be concise but informative."
            )
        }.onSuccess { result ->
            result.onSuccess { response ->
                val summary = extractTextFromResponse(response)

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
        private const val COMPRESSION_THRESHOLD = 5
        private const val AGENTIC_MODEL = "openai/gpt-5-mini"
        private const val CONGRATS_MESSAGE =
            "Привет! Я ваш ИИ-помощник на базе Perplexity Agentic API " +
                "(модель: $AGENTIC_MODEL).\n\n🗜️ Включено" +
                " автоматическое сжатие диалога каждые $COMPRESSION_THRESHOLD ваших сообщений для" +
                " оптимизации токенов!\n\n🎭 Включите JokeAPI MCP-сервер и напишите 'jokeapi' для получения шуток!\n\nЧем могу помочь?"
    }
}
