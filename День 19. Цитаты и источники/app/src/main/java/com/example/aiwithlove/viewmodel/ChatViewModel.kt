package com.example.aiwithlove.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.data.AgenticResponse
import com.example.aiwithlove.data.AgenticTool
import com.example.aiwithlove.data.PerplexityApiService
import com.example.aiwithlove.data.model.McpToolInfo
import com.example.aiwithlove.data.model.Message
import com.example.aiwithlove.data.model.SemanticSearchResult
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class ChatViewModel(
    private val perplexityService: PerplexityApiService,
    private val chatRepository: ChatRepository,
    private val mcpClient: McpClient,
    private val context: android.content.Context
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

    private val _searchThreshold = MutableStateFlow(0.6f)  // Lowered for better results
    val searchThreshold: StateFlow<Float> = _searchThreshold.asStateFlow()

    private var userMessagesCountSinceAppLaunch = 0
    private var lastJokeResult: String? = null

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

    fun updateSearchThreshold(threshold: Float) {
        _searchThreshold.value = threshold.coerceIn(0.3f, 0.95f)
        logD("🎚️ Search threshold updated to ${_searchThreshold.value}")
    }

    private fun isJokeServerEnabled(): Boolean = _mcpServers.value.any { it.id == "jokes" && it.isEnabled }

    private fun userMentionsJokes(message: String): Boolean {
        val lowerMessage = message.lowercase()

        val keywords =
            listOf(
                "jokeapi",
                "joke api",
                "джокапи",
                "жокапи",
                "joke-api",
                "шутк",
                "анекдот",
                "пошути",
                "рассмеши",
                "мои шутки",
                "избранные шутки",
                "сохранённые шутки",
                "сохраненные шутки",
                // Test-related keywords
                "тест",
                "запусти тест",
                "протестируй",
                "проверь работу",
                "проверь сервер",
                "run test",
                "run_test"
            )

        if (keywords.any { lowerMessage.contains(it) }) {
            return true
        }

        val savePatterns =
            listOf(
                Regex("""сохрани\s+(её|ее|его|их)"""),
                Regex("""запомни\s+(её|ее|его|их)"""),
                Regex("""добавь\s+(её|ее|его|их)""")
            )

        return savePatterns.any { it.find(lowerMessage) != null }
    }

    private fun userMentionsSemanticSearch(message: String): Boolean {
        val lowerMessage = message.lowercase()

        val keywords =
            listOf(
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

        return keywords.any { lowerMessage.contains(it) }
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
            description = "Fetches a random joke from JokeAPI. Use this when user asks for a joke.",
            parameters = parameters
        )
    }

    private fun buildSaveJokeTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("joke_api_id") {
                        put("type", "integer")
                        put("description", "Original joke ID from JokeAPI")
                    }
                    putJsonObject("category") {
                        put("type", "string")
                        put("description", "Joke category from JokeAPI")
                    }
                    putJsonObject("type") {
                        put("type", "string")
                        put("description", "Joke type: single or twopart")
                    }
                    putJsonObject("joke_text") {
                        put("type", "string")
                        put("description", "Full joke text for single type jokes (translated Russian version)")
                    }
                    putJsonObject("setup") {
                        put("type", "string")
                        put("description", "Setup part for twopart jokes (translated Russian version)")
                    }
                    putJsonObject("delivery") {
                        put("type", "string")
                        put("description", "Delivery/punchline for twopart jokes (translated Russian version)")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("type"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "save_joke",
            description = "Save a joke to the local database. Use this when user asks to save, remember, or add joke to favorites. Pass the translated Russian joke text.",
            parameters = parameters
        )
    }

    private fun buildGetSavedJokesTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Maximum number of jokes to return (default: 50)")
                        put("default", 50)
                    }
                }
                putJsonArray("required") { }
            }

        return AgenticTool(
            type = "function",
            name = "get_saved_jokes",
            description = "Get all saved jokes from the local database. Use this when user asks to show saved jokes, my jokes, or favorites.",
            parameters = parameters
        )
    }

    private fun buildRunTestsTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") { }
                putJsonArray("required") { }
            }

        return AgenticTool(
            type = "function",
            name = "run_tests",
            description = "Run MCP server tests in an isolated Docker container. Use this when user asks to run tests, test the server, or check if everything works. Returns summary of test results.",
            parameters = parameters
        )
    }

    private fun buildSemanticSearchTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Question or search text to find relevant document chunks")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Maximum number of relevant chunks to return")
                        put("default", 3)
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("query"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "semantic_search",
            description = "Search for relevant document chunks with SOURCE CITATIONS from indexed documents using semantic similarity. Returns documents with 'citation' field containing [filename, page, chunk]. ALWAYS include these citations in your response to the user when presenting information.",
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

            "save_joke" -> {
                runAndCatch {
                    val args = parseToolArguments(arguments)
                    val requestBody = arguments ?: "{}"
                    logD("🔧 Calling MCP server save_joke with args: $args")

                    val mcpResult = mcpClient.callTool("save_joke", args)
                    logD("🔧 MCP result: $mcpResult")

                    val parsedResult = parseJokeFromMcpResult(mcpResult)

                    ToolExecutionResult(
                        result = parsedResult,
                        mcpToolInfo =
                            McpToolInfo(
                                toolName = "save_joke",
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

            "get_saved_jokes" -> {
                runAndCatch {
                    val args = parseToolArguments(arguments)
                    val requestBody = arguments ?: "{}"
                    logD("🔧 Calling MCP server get_saved_jokes with args: $args")

                    val mcpResult = mcpClient.callTool("get_saved_jokes", args)
                    logD("🔧 MCP result: $mcpResult")

                    val parsedResult = parseJokeFromMcpResult(mcpResult)

                    ToolExecutionResult(
                        result = parsedResult,
                        mcpToolInfo =
                            McpToolInfo(
                                toolName = "get_saved_jokes",
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

            "run_tests" -> {
                runAndCatch {
                    val args = parseToolArguments(arguments)
                    val requestBody = arguments ?: "{}"
                    logD("🧪 Calling MCP server run_tests")

                    val mcpResult = mcpClient.callTool("run_tests", args)
                    logD("🧪 MCP result: $mcpResult")

                    val parsedResult = parseJokeFromMcpResult(mcpResult)

                    ToolExecutionResult(
                        result = parsedResult,
                        mcpToolInfo =
                            McpToolInfo(
                                toolName = "run_tests",
                                requestBody = requestBody,
                                responseBody = parsedResult
                            )
                    )
                }.getOrElse { error ->
                    logE("🧪 Tool execution failed", error)
                    ToolExecutionResult(
                        result = """{"error": true, "message": "${error.message}"}""",
                        mcpToolInfo = null
                    )
                }
            }

            "semantic_search" -> {
                runAndCatch {
                    // Get current threshold from UI state
                    val currentThreshold = _searchThreshold.value

                    val args = parseToolArguments(arguments).toMutableMap()
                    // Override threshold with current UI value
                    args["threshold"] = currentThreshold.toDouble()
                    // Always enable comparison mode per user preference
                    args["compare_mode"] = true

                    val requestBody = arguments ?: "{}"
                    logD("🌐 Calling MCP server semantic_search with threshold=$currentThreshold, compare_mode=true")

                    val mcpResult = mcpClient.callTool("semantic_search", args)
                    logD("🌐 MCP result: $mcpResult")

                    val parsedResult = parseJokeFromMcpResult(mcpResult)
                    val semanticResult = parseSemanticSearchResult(mcpResult)

                    // Log parsed results for debugging
                    if (semanticResult != null) {
                        val docCount = semanticResult.count
                            ?: semanticResult.filteredResults?.count
                            ?: semanticResult.unfiltered?.count
                            ?: 0
                        val threshold = semanticResult.threshold ?: "N/A"
                        logD("🌐 Parsed $docCount documents with threshold $threshold")
                    } else {
                        logD("🌐 Could not parse semantic search result")
                    }

                    ToolExecutionResult(
                        result = parsedResult,
                        mcpToolInfo =
                            McpToolInfo(
                                toolName = "semantic_search",
                                requestBody = requestBody,
                                responseBody = parsedResult,
                                semanticSearchResult = semanticResult
                            )
                    )
                }.getOrElse { error ->
                    logE("🌐 Semantic search failed", error)
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

    private fun saveLogsToFile(logsContent: String): String =
        try {
            val timestamp =
                java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
                    .format(java.util.Date())
            val fileName = "test_logs_$timestamp.txt"

            // Delete previous log file (keep only the most recent)
            val logsDir = context.filesDir
            logsDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("test_logs_") && file.name.endsWith(".txt")) {
                    file.delete()
                    logD("Deleted old log file: ${file.name}")
                }
            }

            // Create new log file
            val logFile = java.io.File(logsDir, fileName)
            logFile.writeText(logsContent)
            logD("Saved logs to file: ${logFile.absolutePath}")

            logFile.absolutePath
        } catch (e: Exception) {
            logE("Failed to save logs to file", e)
            ""
        }

    private fun parseToolArguments(arguments: String?): Map<String, Any> {
        if (arguments == null) return emptyMap()
        return try {
            val argsJson = Json.parseToJsonElement(arguments)
            if (argsJson is JsonObject) {
                argsJson.entries.associate { (key, value) ->
                    key to
                        when (value) {
                            is JsonPrimitive -> {
                                when {
                                    value.isString -> value.content
                                    value.intOrNull != null -> value.int
                                    value.booleanOrNull != null -> value.boolean
                                    else -> value.content
                                }
                            }

                            else -> {
                                value.toString()
                            }
                        }
                }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            logE("🔧 Failed to parse tool arguments", e)
            emptyMap()
        }
    }

    private fun defaultJokeArgs() =
        mapOf(
            "category" to "Any",
            "blacklistFlags" to "nsfw,religious,political,racist,sexist,explicit"
        )

    private fun userWantsToSaveJoke(message: String): Boolean {
        val lowerMessage = message.lowercase()
        val savePatterns =
            listOf(
                Regex("""сохрани\s+(её|ее|его|их|эту\s+шутку|шутку)"""),
                Regex("""запомни\s+(её|ее|его|их|эту\s+шутку|шутку)"""),
                Regex("""добавь\s+(её|ее|его|их)\s+(в\s+избранное)?"""),
                Regex("""добавь\s+в\s+избранное""")
            )
        return savePatterns.any { it.find(lowerMessage) != null }
    }

    private fun buildInstructions(useJokeTools: Boolean, useSemanticSearch: Boolean): String {
        val baseInstruction = "You are a helpful assistant. Respond in Russian."
        if (!useJokeTools && !useSemanticSearch) return baseInstruction

        val instructions = mutableListOf<String>()
        instructions.add(baseInstruction)

        if (useJokeTools) {
            instructions.add("""
When user asks for a joke, use the get_joke tool and translate the result to Russian.
When user asks to save a joke, use the save_joke tool with the TRANSLATED Russian version. Extract from the most recent get_joke tool result: joke_api_id (from "id" field), category, type. For the joke text, use your translated Russian version - either joke_text (for type="single") or setup and delivery (for type="twopart").
When user asks for saved/favorite jokes, use the get_saved_jokes tool. Present the jokes in a nice format.
When user asks to run tests or check the server, use ONLY the run_tests tool. IMPORTANT:
- Do NOT call any other tools (like get_joke) after run_tests
- Keep your response EXTREMELY SHORT using this EXACT format:
🧪 run_tests:
[passed] - passed
[failed] - failed
Tests count: [tests_run]
Executing time: [execution_time]

- Use ONLY numbers, no extra text
- Do NOT add explanations, jokes, or additional content""".trimIndent())
        }

        if (useSemanticSearch) {
            instructions.add("""
When user asks a question, FIRST use the semantic_search tool to find relevant documents.

CRITICAL - Understanding the JSON Response:
The tool returns a JSON with these fields:
- "filteredResults": { "count": N, "documents": [...] } - Documents that passed the threshold
- "unfiltered": { "count": N, "documents": [...] } - ALL documents found, regardless of threshold

How to Handle Results:
1. Check filteredResults.count first
2. If filteredResults.count > 0: Use those documents (they're high quality)
3. If filteredResults.count = 0 BUT unfiltered.count > 0:
   - Use the TOP 3 documents from unfiltered.documents
   - Check their "similarity" scores (will be around 0.4-0.6)
   - State clearly that relevance is lower than ideal
   - Still provide helpful answer with citations from these documents
4. ONLY say "Релевантных документов не найдено" if BOTH filteredResults.count = 0 AND unfiltered.count = 0

Citation Requirements:
- Each document has a "citation" field - use it exactly as provided
- Include inline citations for EVERY fact: [filename, page, chunk]
- After your answer, add "Источники:" section listing all unique sources
- Every statement must reference a source

Example with low-similarity results:
"Хотя найденные документы имеют низкую релевантность (similarity 0.48-0.62), вот информация из базы:
Bakemono Archers — юнит Horde [unknown, фрагмент 3]. ...

Источники (низкая релевантность):
- unknown (фрагмент 3)"

DO NOT ignore unfiltered documents when filteredResults is empty. Always check BOTH fields.""".trimIndent())
        }

        return instructions.joinToString("\n\n")
    }

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

    /**
     * Parse semantic search result from MCP response
     * Extracts and deserializes SemanticSearchResult from nested JSON structure
     */
    private fun parseSemanticSearchResult(mcpResult: String): SemanticSearchResult? =
        runAndCatch {
            val jokeData = Json.parseToJsonElement(mcpResult)

            if (jokeData is JsonObject) {
                val content = jokeData["content"] as? JsonArray
                val textContent = content?.firstOrNull() as? JsonObject
                val textString = (textContent?.get("text") as? JsonPrimitive)?.content

                if (textString != null) {
                    // Parse the nested JSON string into SemanticSearchResult
                    Json.decodeFromString<SemanticSearchResult>(textString)
                } else null
            } else null
        }.getOrNull()

    private fun loadChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                val summary = chatRepository.getSummary()
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
            val useJokeTools = isJokeServerEnabled() && userMentionsJokes(userMessage)
            val useSemanticSearch = isJokeServerEnabled() && userMentionsSemanticSearch(userMessage)
            logD("🎭 Use Agentic API with joke tools: $useJokeTools, semantic search: $useSemanticSearch")
            sendWithAgenticApi(userMessage, thinkingMessageIndex, useJokeTools, useSemanticSearch)
        }
    }

    private suspend fun sendWithAgenticApi(
        userMessage: String,
        thinkingMessageIndex: Int,
        useJokeTools: Boolean = false,
        useSemanticSearch: Boolean = false
    ) {
        val capturedMcpToolInfoList = mutableListOf<McpToolInfo>()

        runAndCatch {
            val conversationContext = buildConversationContext()
            var input =
                if (conversationContext.isNotEmpty()) {
                    "$conversationContext\n\nUser: $userMessage"
                } else {
                    userMessage
                }

            if (useJokeTools && userWantsToSaveJoke(userMessage) && lastJokeResult != null) {
                input = "$input\n\nLast joke from JokeAPI: $lastJokeResult"
                logD("🔧 Adding last joke result to context for saving")
            }

            val tools = buildList {
                if (useJokeTools) {
                    add(buildAgenticJokeTool())
                    add(buildSaveJokeTool())
                    add(buildGetSavedJokesTool())
                    add(buildRunTestsTool())
                }
                if (useSemanticSearch) {
                    add(buildSemanticSearchTool())
                }
            }.takeIf { it.isNotEmpty() }

            val instructions = buildInstructions(useJokeTools, useSemanticSearch)

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
                var containsJokeTool = false
                response.output?.filter { it.type == "function_call" }?.forEach { toolCall ->
                    val toolName = toolCall.name ?: return@forEach
                    val arguments = toolCall.arguments
                    logD("🔧 Executing tool: $toolName")
                    val executionResult = executeAgenticToolCall(toolName, arguments)
                    toolResults.add("Tool $toolName result: ${executionResult.result}")

                    if (executionResult.mcpToolInfo != null) {
                        capturedMcpToolInfoList.add(executionResult.mcpToolInfo)
                        if (toolName == "get_joke") {
                            lastJokeResult = executionResult.result
                            containsJokeTool = true
                        }
                    }
                }

                val instruction = if (containsJokeTool) {
                    "Please use these results to complete your response. Translate the joke to Russian."
                } else {
                    "Please use these results to complete your response."
                }

                currentInput =
                    "$currentInput\n\nTool results:\n${toolResults.joinToString(
                        "\n"
                    )}\n\n$instruction"

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
            val mcpInfo = if (capturedMcpToolInfoList.isNotEmpty()) capturedMcpToolInfoList else null

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

    private suspend fun typewriterEffect(
        fullText: String,
        messageIndex: Int,
        promptTokens: Int,
        completionTokens: Int,
        mcpToolInfo: List<McpToolInfo>? = null
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
                // Extract server logs if this was a run_tests call and save to file
                val logFilePath =
                    mcpToolInfo?.firstOrNull { it.toolName == "run_tests" }?.let { toolInfo ->
                        try {
                            val responseJson = Json.parseToJsonElement(toolInfo.responseBody) as? JsonObject
                            val serverLogsValue = responseJson?.get("server_logs") as? JsonPrimitive
                            val outputValue = responseJson?.get("output") as? JsonPrimitive
                            val logsContent =
                                buildString {
                                    serverLogsValue?.content?.let { appendLine(it) }
                                    outputValue?.content?.let {
                                        appendLine("\n--- Test Output ---")
                                        appendLine(it)
                                    }
                                }

                            if (logsContent.isNotBlank()) {
                                saveLogsToFile(logsContent)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            logE("Failed to extract and save server logs", e)
                            null
                        }
                    }

                val assistantMessage =
                    finalMessages[messageIndex].copy(
                        text = fullText,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        mcpToolInfo = mcpToolInfo,
                        attachedLogFile = logFilePath
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
                " оптимизации токенов!\n\n🎭 Включите JokeAPI MCP-сервер:\n" +
                "• 'протестируй сервер' или 'запусти тесты' — проверить состояние сервера\n" +
                "• 'шутка' или 'анекдот' — получить шутку\n" +
                "• 'сохрани шутку' — сохранить в избранное\n" +
                "• 'мои шутки' — показать сохранённые\n\n" +
                "🌐 Семантический поиск в документах:\n" +
                "• 'найди в документах', 'что такое', 'объясни', 'расскажи о' — поиск релевантной информации\n\n" +
                "Чем могу помочь?"
    }
}
