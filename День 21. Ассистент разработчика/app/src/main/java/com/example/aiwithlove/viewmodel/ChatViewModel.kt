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
import com.example.aiwithlove.database.SimilarChunk
import com.example.aiwithlove.mcp.McpClientManager
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
import kotlinx.coroutines.withContext
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
    private val mcpClientManager: McpClientManager,
    private val ollamaClient: com.example.aiwithlove.ollama.OllamaClient,
    private val embeddingsRepository: com.example.aiwithlove.database.EmbeddingsRepository
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

    private val _searchThreshold = MutableStateFlow(0.6f) // Lowered for better results
    val searchThreshold: StateFlow<Float> = _searchThreshold.asStateFlow()

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

    fun updateSearchThreshold(threshold: Float) {
        _searchThreshold.value = threshold.coerceIn(0.3f, 0.95f)
        logD("🎚️ Search threshold updated to ${_searchThreshold.value}")
    }

    private fun isJokeServerEnabled(): Boolean = _mcpServers.value.any { it.id == "jokes" && it.isEnabled }

    private fun userMentionsGitHub(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("gitwithlove")
    }

    private fun isGitHubServerEnabled(): Boolean = _mcpServers.value.any { it.id == "github" && it.isEnabled }

    private fun userMentionsLocalGit(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("gitlocal")
    }

    private fun isLocalGitServerEnabled(): Boolean = _mcpServers.value.any { it.id == "local_git" && it.isEnabled }

    private fun buildGetRepoTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("owner") {
                        put("type", "string")
                        put("description", "Repository owner (username or organization)")
                    }
                    putJsonObject("repo") {
                        put("type", "string")
                        put("description", "Repository name")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("owner"))
                    add(JsonPrimitive("repo"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "get_repo",
            description = "Get detailed information about a GitHub repository including description, stars, forks, language, and topics",
            parameters = parameters
        )
    }

    private fun buildSearchCodeTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put(
                            "description",
                            "Search query using GitHub code search syntax (e.g., 'language:kotlin android', 'class:MyClass repo:owner/repo')"
                        )
                    }
                    putJsonObject("max_results") {
                        put("type", "integer")
                        put("description", "Maximum number of results to return")
                        put("default", 5)
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("query"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "search_code",
            description = "Search for code across GitHub repositories using GitHub's code search syntax",
            parameters = parameters
        )
    }

    private fun buildCreateIssueTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("owner") {
                        put("type", "string")
                        put("description", "Repository owner")
                    }
                    putJsonObject("repo") {
                        put("type", "string")
                        put("description", "Repository name")
                    }
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Issue title")
                    }
                    putJsonObject("body") {
                        put("type", "string")
                        put("description", "Issue description (supports markdown)")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("owner"))
                    add(JsonPrimitive("repo"))
                    add(JsonPrimitive("title"))
                    add(JsonPrimitive("body"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "create_issue",
            description = "Create a new issue in a GitHub repository",
            parameters = parameters
        )
    }

    private fun buildListIssuesTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("owner") {
                        put("type", "string")
                        put("description", "Repository owner")
                    }
                    putJsonObject("repo") {
                        put("type", "string")
                        put("description", "Repository name")
                    }
                    putJsonObject("state") {
                        put("type", "string")
                        put("description", "Filter by state: open, closed, or all")
                        put("default", "open")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("owner"))
                    add(JsonPrimitive("repo"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "list_issues",
            description = "List issues from a GitHub repository with optional filtering by state",
            parameters = parameters
        )
    }

    private fun buildListCommitsTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("owner") {
                        put("type", "string")
                        put("description", "Repository owner")
                    }
                    putJsonObject("repo") {
                        put("type", "string")
                        put("description", "Repository name")
                    }
                    putJsonObject("max_results") {
                        put("type", "integer")
                        put("description", "Maximum number of commits to return")
                        put("default", 10)
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("owner"))
                    add(JsonPrimitive("repo"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "list_commits",
            description = "List commit history for a repository",
            parameters = parameters
        )
    }

    private fun buildGetRepoContentTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("owner") {
                        put("type", "string")
                        put("description", "Repository owner")
                    }
                    putJsonObject("repo") {
                        put("type", "string")
                        put("description", "Repository name")
                    }
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "File or directory path in the repository")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("owner"))
                    add(JsonPrimitive("repo"))
                    add(JsonPrimitive("path"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "get_repo_content",
            description = "Get contents of a file or directory from a GitHub repository",
            parameters = parameters
        )
    }

    private fun buildGitStatusTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("repo_path") {
                        put("type", "string")
                        put("description", "Path to git repository")
                        put("default", "/Users/falin/AndroidStudioProjects/AI-with-Love")
                    }
                }
                putJsonArray("required") {
                    // No required parameters - uses default repo_path
                }
            }

        return AgenticTool(
            type = "function",
            name = "git_status",
            description =
                "Get current git repository status: modified files, staged files, untracked files, " +
                    "current branch, ahead/behind remote",
            parameters = parameters
        )
    }

    private fun buildGitBranchTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("repo_path") {
                        put("type", "string")
                        put("description", "Path to git repository")
                        put("default", "/Users/falin/AndroidStudioProjects/AI-with-Love")
                    }
                    putJsonObject("include_remote") {
                        put("type", "boolean")
                        put("description", "Include remote branches")
                        put("default", true)
                    }
                }
                putJsonArray("required") {
                    // No required parameters
                }
            }

        return AgenticTool(
            type = "function",
            name = "git_branch",
            description = "List all local and remote branches, showing which is currently active",
            parameters = parameters
        )
    }

    private fun buildGitDiffTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("repo_path") {
                        put("type", "string")
                        put("description", "Path to git repository")
                        put("default", "/Users/falin/AndroidStudioProjects/AI-with-Love")
                    }
                    putJsonObject("filepath") {
                        put("type", "string")
                        put("description", "Optional: specific file path to diff (omit for all changes)")
                    }
                    putJsonObject("staged") {
                        put("type", "boolean")
                        put("description", "If true, show staged changes; if false, show unstaged")
                        put("default", false)
                    }
                    putJsonObject("max_lines") {
                        put("type", "integer")
                        put("description", "Maximum lines of diff output")
                        put("default", 500)
                    }
                }
                putJsonArray("required") {
                    // No required parameters
                }
            }

        return AgenticTool(
            type = "function",
            name = "git_diff",
            description = "Get diff for files (unstaged or staged changes)",
            parameters = parameters
        )
    }

    private fun buildGitPrStatusTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("repo_path") {
                        put("type", "string")
                        put("description", "Path to git repository")
                        put("default", "/Users/falin/AndroidStudioProjects/AI-with-Love")
                    }
                }
                putJsonArray("required") {
                    // No required parameters
                }
            }

        return AgenticTool(
            type = "function",
            name = "git_pr_status",
            description =
                "Check pull request status for current branch by combining local git info with GitHub API. " +
                    "Returns current branch, related PRs, and whether PRs are open.",
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

                    val enabledServers = _mcpServers.value.filter { it.isEnabled }.map { it.id }
                    val mcpResult = mcpClientManager.callTool("get_joke", args, enabledServers)
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

                    val enabledServers = _mcpServers.value.filter { it.isEnabled }.map { it.id }
                    val mcpResult = mcpClientManager.callTool("save_joke", args, enabledServers)
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

                    val enabledServers = _mcpServers.value.filter { it.isEnabled }.map { it.id }
                    val mcpResult = mcpClientManager.callTool("get_saved_jokes", args, enabledServers)
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

                    val enabledServers = _mcpServers.value.filter { it.isEnabled }.map { it.id }
                    val mcpResult = mcpClientManager.callTool("run_tests", args, enabledServers)
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

            // GitHub tools
            "get_repo", "search_code", "create_issue", "list_issues",
            "list_commits", "get_repo_content" -> {
                runAndCatch {
                    val args = parseToolArguments(arguments)
                    val requestBody = arguments ?: "{}"

                    logD("🐙 Calling GitHub MCP: $toolName with args: $args")

                    val enabledServers =
                        _mcpServers.value
                            .filter { it.isEnabled }
                            .map { it.id }

                    val mcpResult =
                        mcpClientManager.callTool(
                            toolName = toolName,
                            arguments = args,
                            enabledServers = enabledServers
                        )

                    logD("🐙 GitHub MCP result: ${mcpResult.take(200)}...")

                    val parsedResult = parseJokeFromMcpResult(mcpResult)

                    ToolExecutionResult(
                        result = parsedResult,
                        mcpToolInfo =
                            McpToolInfo(
                                toolName = toolName,
                                requestBody = requestBody,
                                responseBody = parsedResult
                            )
                    )
                }.getOrElse { error ->
                    logE("🐙 GitHub tool execution failed", error)
                    ToolExecutionResult(
                        result = """{"error": true, "message": "${error.message}"}""",
                        mcpToolInfo = null
                    )
                }
            }

            // Local Git tools
            "git_status", "git_branch", "git_diff", "git_pr_status" -> {
                runAndCatch {
                    val args = parseToolArguments(arguments)
                    val requestBody = arguments ?: "{}"

                    logD("🌿 Calling Local Git MCP: $toolName")

                    val enabledServers =
                        _mcpServers.value
                            .filter { it.isEnabled }
                            .map { it.id }

                    val mcpResult =
                        mcpClientManager.callTool(
                            toolName = toolName,
                            arguments = args,
                            enabledServers = enabledServers
                        )

                    val parsedResult = parseJokeFromMcpResult(mcpResult)

                    ToolExecutionResult(
                        result = parsedResult,
                        mcpToolInfo =
                            McpToolInfo(
                                toolName = toolName,
                                requestBody = requestBody,
                                responseBody = parsedResult
                            )
                    )
                }.getOrElse { error ->
                    logE("🌿 Local Git tool failed", error)
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

    private fun buildInstructions(
        useJokeTools: Boolean,
        useSemanticSearch: Boolean,
        useGitHubTools: Boolean,
        useLocalGitTools: Boolean
    ): String {
        val baseInstruction = "You are a helpful assistant. Respond in Russian."
        if (!useJokeTools && !useSemanticSearch && !useGitHubTools && !useLocalGitTools) return baseInstruction

        val instructions = mutableListOf<String>()
        instructions.add(baseInstruction)

        if (useJokeTools) {
            instructions.add(
                """
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
- Do NOT add explanations, jokes, or additional content
                """.trimIndent()
            )
        }

        if (useGitHubTools) {
            instructions.add(
                """
GitHub Operations:
- Use get_repo for repository information (description, stats, languages)
- Use search_code to find code patterns across repositories
- Use create_issue to create new issues with title, body, and optional labels
- Use list_issues to view issues (filter by state: open/closed/all)
- Use list_commits for commit history
- Use get_repo_content to read files from repositories

IMPORTANT: Repository owner defaults to "Golgoroth22" if not specified.
When user mentions just a repository name (e.g., "AI-with-Love"), assume owner is "Golgoroth22".
Only ask for owner if user explicitly mentions a different owner/organization.

Summarize results clearly in Russian with code blocks for code snippets.
Include links when available.
                """.trimIndent()
            )
        }

        if (useLocalGitTools) {
            instructions.add(
                """
Local Git Operations:
- Use git_status to check repository status (modified files, current branch, ahead/behind remote)
- Use git_branch to list all local and remote branches
- Use git_diff to see file changes (unstaged or staged)
- Use git_pr_status to check if there are open PRs for the current branch

Repository path: /Users/falin/AndroidStudioProjects/AI-with-Love

Example questions:
- "какая текущая ветка?" → use git_status or git_branch
- "какие файлы изменены?" → use git_status
- "покажи diff для файла X" → use git_diff with filepath parameter
- "есть ли открытые PR?" → use git_pr_status

Respond in Russian with clear formatting. Use code blocks for diffs and file lists.
                """.trimIndent()
            )
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
                } else {
                    null
                }
            } else {
                null
            }
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

        // Intercept /help command
        if (userMessage.trimStart().startsWith("/help")) {
            viewModelScope.launch(Dispatchers.IO) {
                handleHelpCommand(userMessage)
            }
            return
        }

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
            val useGitHubTools = isGitHubServerEnabled() && userMentionsGitHub(userMessage)
            val useLocalGitTools = isLocalGitServerEnabled() && userMentionsLocalGit(userMessage)
            logD("🌐 GitHub: $useGitHubTools, LocalGit: $useLocalGitTools")
            sendWithAgenticApi(userMessage, thinkingMessageIndex, false)
        }
    }

    private suspend fun sendWithAgenticApi(
        userMessage: String,
        thinkingMessageIndex: Int,
        useJokeTools: Boolean = false
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

            // Detect GitHub and Local Git mentions
            val useGitHubTools = isGitHubServerEnabled() && userMentionsGitHub(userMessage)
            val useLocalGitTools = isLocalGitServerEnabled() && userMentionsLocalGit(userMessage)

            val tools =
                buildList {
                    if (useGitHubTools) {
                        // Add GitHub tools
                        add(buildGetRepoTool())
                        add(buildSearchCodeTool())
                        add(buildCreateIssueTool())
                        add(buildListIssuesTool())
                        add(buildListCommitsTool())
                        add(buildGetRepoContentTool())
                    }
                    if (useLocalGitTools) {
                        // Add Local Git tools
                        add(buildGitStatusTool())
                        add(buildGitBranchTool())
                        add(buildGitDiffTool())
                        add(buildGitPrStatusTool())
                    }
                }.takeIf { it.isNotEmpty() }

            val instructions = buildInstructions(useJokeTools, false, useGitHubTools, useLocalGitTools)

            logD("📤 Sending Agentic request with ${tools?.size ?: 0} tools (GitHub: $useGitHubTools, LocalGit: $useLocalGitTools)")

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
                    }
                }

                val instruction =
                    if (containsJokeTool) {
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

    /**
     * Parse /help command and extract query
     * Returns null if no valid query
     */
    private fun parseHelpCommand(message: String): String? {
        val trimmed = message.trim()
        if (!trimmed.startsWith("/help")) return null

        val query = trimmed.removePrefix("/help").trim()
        return if (query.isNotEmpty()) query else null
    }

    /**
     * Handle /help slash command
     * Searches local embeddings and sends augmented message to AI
     */
    private suspend fun handleHelpCommand(message: String) {
        val query =
            parseHelpCommand(message) ?: run {
                addCommandErrorMessage("Usage: /help <your question>")
                return
            }

        // Show user message
        val userMsg =
            Message(
                text = "Help: $query",
                isFromUser = true,
                isHelpCommand = true
            )
        withContext(Dispatchers.Main) {
            _messages.value = _messages.value + userMsg
            _isLoading.value = true
        }
        userMessagesCountSinceAppLaunch++

        runAndCatch {
            // 1. Check if database has documents
            val totalChunks = embeddingsRepository.getChunksCount()
            if (totalChunks == 0) {
                addCommandErrorMessage(
                    "No documents indexed yet. Please go to Ollama screen to index documents first."
                )
                return@runAndCatch
            }

            logD("🔍 /help command: searching for '$query' in $totalChunks chunks")

            // 2. Generate embedding for query
            val queryEmbedding =
                try {
                    ollamaClient.generateEmbedding(query)
                } catch (e: Exception) {
                    addCommandErrorMessage(
                        "Cannot generate embedding. Is Ollama running on your machine?"
                    )
                    logE("Ollama error during /help command", e)
                    return@runAndCatch
                }

            // 3. Search local database
            val threshold = _searchThreshold.value.toDouble()
            val results =
                embeddingsRepository.searchSimilar(
                    queryEmbedding = queryEmbedding,
                    limit = 5,
                    threshold = threshold
                )

            logD("✅ Found ${results.size} results (threshold: $threshold)")

            // 4. Update user message with docs count
            val updatedUserMsg = userMsg.copy(helpDocsFound = results.size)
            withContext(Dispatchers.Main) {
                val currentMessages = _messages.value.toMutableList()
                val userMsgIndex = currentMessages.lastIndexOf(userMsg)
                if (userMsgIndex >= 0) {
                    currentMessages[userMsgIndex] = updatedUserMsg
                    _messages.value = currentMessages
                }
            }

            // 5. Save user message to database
            chatRepository.saveUserMessage(updatedUserMsg)

            // 6. Format context and send to AI
            val contextMessage = formatHelpContext(query, results, threshold)
            sendToAIWithHelpContext(contextMessage, query, results.size)
        }.onFailure { error ->
            addCommandErrorMessage("Search failed: ${error.message}")
            logE("Help command failed", error)
            withContext(Dispatchers.Main) {
                _isLoading.value = false
            }
        }
    }

    /**
     * Format search results as context for AI
     */
    private fun formatHelpContext(
        query: String,
        results: List<SimilarChunk>,
        threshold: Double
    ): String {
        val sb = StringBuilder()

        sb.appendLine("User question: $query")
        sb.appendLine()

        if (results.isEmpty()) {
            sb.appendLine("No relevant documents found in the local database (threshold: ${(threshold * 100).toInt()}%).")
            sb.appendLine()
            sb.appendLine(
                "Please inform the user that you don't have documents on this topic. You may answer from general knowledge if appropriate, but clarify that it's not based on the indexed documents."
            )
        } else {
            sb.appendLine("Relevant documents found (${results.size} results, threshold: ${(threshold * 100).toInt()}%):")
            sb.appendLine()

            results.forEachIndexed { index, similarChunk ->
                val doc = similarChunk.chunk
                val similarity = (similarChunk.similarity * 100).toInt()

                sb.appendLine("Document ${index + 1} (similarity: $similarity%):")
                sb.appendLine("Source: [${doc.sourceFile}, chunk ${doc.chunkIndex + 1}/${doc.totalChunks}]")
                sb.appendLine(doc.content)
                sb.appendLine()
            }

            sb.appendLine(
                "Please answer the user's question based on these documents. Include citations in your response using the format [filename, chunk X/Y]. If the documents don't fully answer the question, say so clearly."
            )
        }

        return sb.toString()
    }

    /**
     * Send help context to AI
     */
    private suspend fun sendToAIWithHelpContext(
        contextMessage: String,
        originalQuery: String,
        docsFound: Int
    ) {
        // Add thinking message
        val thinkingMessage =
            Message(
                text = "Analyzing documents...",
                isFromUser = false
            )
        withContext(Dispatchers.Main) {
            _messages.value = _messages.value + thinkingMessage
        }
        val thinkingMessageIndex = _messages.value.size - 1

        // Send to AI (without tools)
        runAndCatch {
            val conversationContext = buildConversationContext()
            val input =
                if (conversationContext.isNotEmpty()) {
                    "$conversationContext\n\n$contextMessage"
                } else {
                    contextMessage
                }

            val instructions = "You are a helpful assistant. The user asked a question and you have been provided with relevant documents from their indexed knowledge base. Answer based on these documents and cite sources. Respond in Russian."

            logD("📤 Sending help query to AI with $docsFound documents")

            val response =
                perplexityService.sendAgenticRequest(
                    input = input,
                    model = AGENTIC_MODEL,
                    instructions = instructions,
                    tools = null // NO TOOLS for /help command
                ).getOrThrow()

            val fullResponse = extractTextFromResponse(response)
            val usage = response.usage
            val promptTokens = usage?.inputTokens ?: 0
            val completionTokens = usage?.outputTokens ?: 0

            logD("✅ Received AI response for /help command")

            // Replace thinking message with real response
            withContext(Dispatchers.Main) {
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
            }

            // Typewriter effect
            typewriterEffect(
                fullResponse,
                thinkingMessageIndex,
                promptTokens,
                completionTokens,
                mcpToolInfo = null
            )

            // Check compression
            if (shouldCompressDialog()) {
                compressDialogWithNotification()
            }
        }.onFailure { error ->
            logE("Error sending help query to AI", error)
            withContext(Dispatchers.Main) {
                val currentMessages = _messages.value.toMutableList()
                if (thinkingMessageIndex < currentMessages.size) {
                    currentMessages[thinkingMessageIndex] =
                        Message(
                            text = "Error: ${error.message ?: "Unknown error"}",
                            isFromUser = false
                        )
                    _messages.value = currentMessages
                }
                _isLoading.value = false
            }
        }
    }

    /**
     * Add error message for command failures
     */
    private fun addCommandErrorMessage(errorText: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val errorMsg =
                Message(
                    text = errorText,
                    isFromUser = false,
                    isSystemMessage = true
                )
            _messages.value = _messages.value + errorMsg
            _isLoading.value = false
        }
    }

    companion object {
        private const val COMPRESSION_THRESHOLD = 5
        private const val AGENTIC_MODEL = "openai/gpt-5-mini"
        private const val CONGRATS_MESSAGE =
            "Привет! Я ваш ИИ-помощник на базе Perplexity Agentic API " +
                "(модель: $AGENTIC_MODEL).\n\n🗜️ Включено" +
                " автоматическое сжатие диалога каждые $COMPRESSION_THRESHOLD ваших сообщений для" +
                " оптимизации токенов!\n\n" +
                "📚 Работа с документами (экран Ollama):\n" +
                "• Загружайте PDF, TXT или MD файлы для индексации\n" +
                "• Локальная обработка через Ollama (nomic-embed-text)\n" +
                "• Настройте порог релевантности (0.3-0.95) с помощью слайдера\n\n" +
                "🔍 Команда /help для поиска в документах:\n" +
                "• Используйте: /help <ваш вопрос>\n" +
                "• Пример: /help что такое архитектура проекта?\n" +
                "• Быстрый поиск по индексированным документам с цитатами\n\n" +
                "Чем могу помочь?"
    }
}
