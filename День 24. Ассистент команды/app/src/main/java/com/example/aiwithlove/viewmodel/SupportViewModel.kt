package com.example.aiwithlove.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.data.AgenticResponse
import com.example.aiwithlove.data.AgenticTool
import com.example.aiwithlove.data.PerplexityApiService
import com.example.aiwithlove.data.model.Message
import com.example.aiwithlove.database.ChatRepository
import com.example.aiwithlove.mcp.McpClientManager
import com.example.aiwithlove.util.ILoggable
import com.example.aiwithlove.util.runAndCatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class SupportViewModel(
    private val perplexityService: PerplexityApiService,
    private val chatRepository: ChatRepository,
    private val mcpClientManager: McpClientManager
) : ViewModel(),
    ILoggable {

    // UI State
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentTicketId = MutableStateFlow<Int?>(null)
    val currentTicketId: StateFlow<Int?> = _currentTicketId.asStateFlow()

    private val _taskContext = MutableStateFlow<TaskContext?>(null)
    val taskContext: StateFlow<TaskContext?> = _taskContext.asStateFlow()

    // Task creation dialog state
    private val _showCreateTaskDialog = MutableStateFlow(false)
    val showCreateTaskDialog: StateFlow<Boolean> = _showCreateTaskDialog.asStateFlow()

    private val _taskFormState = MutableStateFlow(TaskFormState())
    val taskFormState: StateFlow<TaskFormState> = _taskFormState.asStateFlow()

    private val _availableUsers = MutableStateFlow<List<TeamMember>>(emptyList())
    val availableUsers: StateFlow<List<TeamMember>> = _availableUsers.asStateFlow()

    private val _selectedUser = MutableStateFlow<TeamMember?>(null)
    val selectedUser: StateFlow<TeamMember?> = _selectedUser.asStateFlow()

    init {
        // Load welcome message
        _messages.value =
            listOf(
                Message(
                    text = WELCOME_MESSAGE,
                    isFromUser = false
                )
            )

        // Load available users
        viewModelScope.launch(Dispatchers.IO) {
            loadUsers()
        }
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || _isLoading.value) return

        // Check if user wants to create task via dialog (on Main thread for immediate UI update)
        if (detectTaskCreationKeywords(userMessage)) {
            val extractedTitle = extractTitleFromMessage(userMessage)
            _taskFormState.value = TaskFormState(title = extractedTitle)
            _showCreateTaskDialog.value = true
            logD("🎯 Task creation dialog triggered with title: '$extractedTitle'")
            return // Don't send to AI
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            try {
                // Add user message
                val userMsg = Message(text = userMessage, isFromUser = true)
                _messages.value = _messages.value + userMsg

                // Step 3: Add thinking indicator
                val thinkingMsg = Message(text = "Думаю...", isFromUser = false)
                _messages.value = _messages.value + thinkingMsg
                val thinkingIndex = _messages.value.size - 1

                // Step 4: Send to AI with support tools
                val response = sendWithSupportTools(userMessage, thinkingIndex)

                // Update thinking message with real response
                if (response.isNotEmpty()) {
                    _messages.value =
                        _messages.value.toMutableList().apply {
                            if (thinkingIndex < size) {
                                set(thinkingIndex, Message(text = response, isFromUser = false))
                            }
                        }
                }
            } catch (e: Exception) {
                logE("Error sending message", e)
                val errorMsg =
                    Message(
                        text = "Произошла ошибка: ${e.message}",
                        isFromUser = false
                    )
                _messages.value = _messages.value + errorMsg
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun createTicketForSession(firstMessage: String) {
        try {
            val title = extractTitle(firstMessage)
            val category = detectCategory(firstMessage)

            // Use selected user's ID, fallback to 1 if no user selected
            val userId = _selectedUser.value?.id ?: 1

            val args =
                mapOf(
                    "user_id" to userId,
                    "title" to title,
                    "description" to firstMessage,
                    "priority" to "medium",
                    "category" to category
                )

            logD("🎫 Creating ticket: title='$title', category='$category'")

            val result =
                mcpClientManager.callTool(
                    toolName = "create_ticket",
                    arguments = args,
                    enabledServers = listOf("support")
                )

            // Parse ticket ID from result
            logD("🎫 Create ticket response: $result")
            val ticketId = parseTicketIdFromResponse(result)
            _currentTicketId.value = ticketId

            if (ticketId != null) {
                logD("🎫 Created ticket #$ticketId for new support session")
            } else {
                logE("🎫 Failed to parse ticket ID from response", null)
            }
        } catch (e: Exception) {
            logE("Failed to create ticket", e)
            // Continue without ticket - AI can still help
        }
    }

    private suspend fun sendWithSupportTools(
        userMessage: String,
        thinkingIndex: Int
    ): String {
        val ticketId = _currentTicketId.value
        val intent = detectQueryIntent(userMessage)

        // Build instructions based on intent
        val instructions = buildSupportInstructions(ticketId, intent)

        // Build tools based on intent
        val tools =
            buildList {
                // Always include semantic search
                add(buildSemanticSearchTool())

                when (intent) {
                    QueryIntent.SUPPORT_TICKET -> {
                        add(buildCreateTicketTool())
                        add(buildGetTicketTool())
                        add(buildUpdateTicketTool())
                    }

                    QueryIntent.TASK_MANAGEMENT -> {
                        add(buildCreateTaskTool())
                        add(buildListTasksTool())
                        add(buildUpdateTaskTool())
                        add(buildGetTaskTool())
                        add(buildGetTeamWorkloadTool())
                        add(buildSearchSimilarTasksTool())
                    }

                    QueryIntent.HYBRID, QueryIntent.UNCLEAR -> {
                        // Include all tools
                        add(buildCreateTicketTool())
                        add(buildGetTicketTool())
                        add(buildUpdateTicketTool())
                        add(buildCreateTaskTool())
                        add(buildListTasksTool())
                        add(buildUpdateTaskTool())
                        add(buildGetTaskTool())
                        add(buildGetTeamWorkloadTool())
                        add(buildSearchSimilarTasksTool())
                    }
                }
            }

        logD("🎫 Sending request with intent: $intent, ${tools.size} tools")

        // Agentic loop
        return runAndCatch {
            var response =
                perplexityService.sendAgenticRequest(
                    input = userMessage,
                    model = AGENTIC_MODEL,
                    instructions = instructions,
                    tools = tools
                ).getOrThrow()

            var iterations = 0
            val maxIterations = 5
            var currentInput = userMessage

            while (hasToolCalls(response) && iterations < maxIterations) {
                iterations++
                logD("🔧 Tool calls detected, iteration $iterations")

                val toolResults = mutableListOf<String>()
                response.output?.filter { it.type == "function_call" }?.forEach { toolCall ->
                    val toolName = toolCall.name ?: return@forEach
                    val arguments = toolCall.arguments

                    logD("🔧 Executing tool: $toolName")
                    val result = executeToolCall(toolName, arguments)
                    toolResults.add("Tool $toolName result: $result")
                }

                currentInput =
                    "$currentInput\n\nTool results:\n${toolResults.joinToString(
                        "\n"
                    )}\n\nPlease use these results to complete your response."

                response =
                    perplexityService.sendAgenticRequest(
                        input = currentInput,
                        model = AGENTIC_MODEL,
                        instructions = instructions,
                        tools = tools
                    ).getOrThrow()
            }

            // Refresh task context if task tools were used
            if (intent in listOf(QueryIntent.TASK_MANAGEMENT, QueryIntent.HYBRID)) {
                refreshTaskContext()
            }

            // Extract final text
            extractTextFromResponse(response)
        }.getOrElse { error ->
            logE("Error in agentic loop", error)
            "Произошла ошибка при обработке запроса: ${error.message}"
        }
    }

    private suspend fun executeToolCall(
        toolName: String,
        arguments: String?
    ): String =
        when (toolName) {
            "create_ticket" -> {
                runAndCatch {
                    val args = parseToolArguments(arguments)
                    logD("🎫 Creating ticket with args: $args")

                    val result =
                        mcpClientManager.callTool(
                            toolName = toolName,
                            arguments = args,
                            enabledServers = listOf("support")
                        )

                    // Parse ticket ID from result and update state
                    val ticketId = parseTicketIdFromResponse(result)
                    if (ticketId != null) {
                        _currentTicketId.value = ticketId
                        logD("✅ Ticket created: #$ticketId")
                    }

                    result
                }.getOrElse { error ->
                    logE("Tool $toolName failed", error)
                    """{"error": true, "message": "${error.message}"}"""
                }
            }

            "get_ticket", "update_ticket" -> {
                runAndCatch {
                    val args = parseToolArguments(arguments)
                    logD("🎫 Calling $toolName with args: $args")

                    mcpClientManager.callTool(
                        toolName = toolName,
                        arguments = args,
                        enabledServers = listOf("support")
                    )
                }.getOrElse { error ->
                    logE("Tool $toolName failed", error)
                    """{"error": true, "message": "${error.message}"}"""
                }
            }

            "semantic_search" -> {
                runAndCatch {
                    val args = parseToolArguments(arguments)
                    logD("🔍 Searching FAQ: $args")

                    mcpClientManager.callTool(
                        toolName = toolName,
                        arguments = args,
                        enabledServers = listOf("rag")
                    )
                }.getOrElse { error ->
                    logE("Semantic search failed", error)
                    """{"error": true, "message": "${error.message}"}"""
                }
            }

            "create_task", "list_tasks", "update_task", "get_task",
            "get_team_workload", "search_similar_tasks" -> {
                runAndCatch {
                    val args = parseToolArguments(arguments)
                    logD("✅ Calling task tool $toolName with args: $args")

                    mcpClientManager.callTool(
                        toolName = toolName,
                        arguments = args,
                        enabledServers = listOf("support")
                    )
                }.getOrElse { error ->
                    logE("Task tool $toolName failed", error)
                    """{"error": true, "message": "${error.message}"}"""
                }
            }

            else -> {
                logE("Unknown tool: $toolName", null)
                """{"error": true, "message": "Unknown tool: $toolName"}"""
            }
        }

    fun clearSupportSession() {
        _messages.value =
            listOf(
                Message(text = WELCOME_MESSAGE, isFromUser = false)
            )
        _currentTicketId.value = null
        logD("🎫 Cleared support session, ready for new ticket")
    }

    fun toggleCreateTaskDialog() {
        _showCreateTaskDialog.value = !_showCreateTaskDialog.value
    }

    fun createTaskFromDialog(formState: TaskFormState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentUserId = _selectedUser.value?.id ?: 1

                val args =
                    mapOf(
                        "title" to formState.title,
                        "description" to formState.description,
                        "priority" to formState.priority,
                        "assignee" to currentUserId.toString(),
                        "tags" to emptyList<String>()
                    )

                logD("✅ Creating task directly: title='${formState.title}', priority='${formState.priority}'")

                val result =
                    mcpClientManager.callTool(
                        toolName = "create_task",
                        arguments = args,
                        enabledServers = listOf("support")
                    )

                // Parse task ID from response
                val taskId = parseTaskIdFromResponse(result)

                if (taskId != null) {
                    logD("✅ Task created successfully: #$taskId")

                    // Add success message to chat
                    val successMsg =
                        Message(
                            text =
                                "✅ Задача #$taskId успешно создана!\n\n" +
                                    "**Название:** ${formState.title}\n" +
                                    "**Приоритет:** ${formState.priority}\n" +
                                    "**Описание:** ${formState.description}",
                            isFromUser = false
                        )

                    withContext(Dispatchers.Main) {
                        _messages.value = _messages.value + successMsg
                    }

                    // Refresh task context
                    refreshTaskContext()
                } else {
                    logE("Failed to parse task ID from response", null)

                    val errorMsg =
                        Message(
                            text = "❌ Задача создана, но не удалось получить ID",
                            isFromUser = false
                        )

                    withContext(Dispatchers.Main) {
                        _messages.value = _messages.value + errorMsg
                    }
                }
            } catch (e: Exception) {
                logE("Failed to create task from dialog", e)

                val errorMsg =
                    Message(
                        text = "❌ Ошибка при создании задачи: ${e.message}",
                        isFromUser = false
                    )

                withContext(Dispatchers.Main) {
                    _messages.value = _messages.value + errorMsg
                }
            }
        }
    }

    private fun parseTaskIdFromResponse(response: String): Int? {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val outerJson = json.parseToJsonElement(response).jsonObject

            // Try to parse from MCP content array (primary)
            val contentArray = outerJson["content"]?.jsonArray
            if (contentArray != null && contentArray.isNotEmpty()) {
                val textContent = contentArray[0].jsonObject["text"]?.jsonPrimitive?.content
                if (textContent != null) {
                    val innerJson = json.parseToJsonElement(textContent).jsonObject
                    val taskId = innerJson["task_id"]?.jsonPrimitive?.content?.toIntOrNull()
                    if (taskId != null) {
                        logD("✅ Parsed task_id from content array: $taskId")
                        return taskId
                    }
                }
            }

            // Fallback: try top-level task_id
            val topLevelTaskId = outerJson["task_id"]?.jsonPrimitive?.content?.toIntOrNull()
            if (topLevelTaskId != null) {
                logD("✅ Parsed task_id from top level: $topLevelTaskId")
                return topLevelTaskId
            }

            // Fallback: try nested result object
            val resultObj = outerJson["result"]?.jsonObject
            val nestedTaskId = resultObj?.get("task_id")?.jsonPrimitive?.content?.toIntOrNull()
            if (nestedTaskId != null) {
                logD("✅ Parsed task_id from result object: $nestedTaskId")
                return nestedTaskId
            }

            logE("Could not find task_id in response: $response", null)
            null
        } catch (e: Exception) {
            logE("Failed to parse task ID from response", e)
            null
        }
    }

    private suspend fun loadUsers() {
        try {
            logD("👥 Loading team members...")
            val result =
                mcpClientManager.callTool(
                    toolName = "get_team_workload",
                    arguments = emptyMap(),
                    enabledServers = listOf("support")
                )

            logD("👥 Team members response: $result")

            // Parse team members from response
            val json = Json { ignoreUnknownKeys = true }
            val jsonElement = json.parseToJsonElement(result).jsonObject
            val contentArray = jsonElement["content"]?.jsonArray
            val membersText = contentArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

            if (membersText != null) {
                val membersJson = json.parseToJsonElement(membersText).jsonObject
                val membersArray = membersJson["members"]?.jsonArray

                if (membersArray != null) {
                    val members =
                        membersArray.map { memberElement ->
                            val memberObj = memberElement.jsonObject
                            TeamMember(
                                id = memberObj["id"]?.jsonPrimitive?.content ?: "",
                                name = memberObj["name"]?.jsonPrimitive?.content ?: "",
                                role = memberObj["role"]?.jsonPrimitive?.content ?: "",
                                skills =
                                    memberObj["skills"]?.jsonArray?.map {
                                        it.jsonPrimitive.content
                                    } ?: emptyList(),
                                availability = memberObj["availability"]?.jsonPrimitive?.content ?: "available",
                                currentWorkload = memberObj["current_workload"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            )
                        }

                    _availableUsers.value = members

                    // Select first member by default
                    if (members.isNotEmpty()) {
                        _selectedUser.value = members.first()
                        logD("👥 Loaded ${members.size} team members, selected default: ${members.first().name}")
                    }
                }
            }
        } catch (e: Exception) {
            logE("Failed to load team members", e)
            // Fallback to default member if loading fails
            _selectedUser.value =
                TeamMember(
                    id = "1",
                    name = "Борис Шустров",
                    role = "Boss",
                    skills = listOf("Rage", "KPI", "Business courses"),
                    availability = "available",
                    currentWorkload = 0
                )
        }
    }

    fun selectUser(user: TeamMember) {
        _selectedUser.value = user
        logD("👥 Selected team member: ${user.name} (ID: ${user.id})")
    }

    // Tool Builders
    private fun buildGetTicketTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("ticket_id") {
                        put("type", "integer")
                        put("description", "Ticket ID to retrieve")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("ticket_id"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "get_ticket",
            description = "Get ticket details by ID including status, priority, description, and full history",
            parameters = parameters
        )
    }

    private fun buildUpdateTicketTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("ticket_id") {
                        put("type", "integer")
                        put("description", "Ticket ID to update")
                    }
                    putJsonObject("status") {
                        put("type", "string")
                        put("description", "New status: open, in_progress, resolved, or closed")
                    }
                    putJsonObject("note") {
                        put("type", "string")
                        put("description", "Note to add to ticket history")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("ticket_id"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "update_ticket",
            description = "Update ticket status or add notes to ticket history",
            parameters = parameters
        )
    }

    private fun buildCreateTicketTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Brief ticket title (max 100 chars)")
                    }
                    putJsonObject("description") {
                        put("type", "string")
                        put("description", "Detailed problem description")
                    }
                    putJsonObject("category") {
                        put("type", "string")
                        put("description", "Category: authentication, features, troubleshooting, or other")
                    }
                    putJsonObject("priority") {
                        put("type", "string")
                        put("description", "Priority: low, medium, or high")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("title"))
                    add(JsonPrimitive("description"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "create_ticket",
            description = "Create a new support ticket ONLY when user reports a problem or asks for help with an issue",
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
                        put("description", "Search query to find relevant FAQ documents")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Maximum number of results to return")
                        put("default", 5)
                    }
                    putJsonObject("threshold") {
                        put("type", "number")
                        put("description", "Minimum similarity score (0.0-1.0)")
                        put("default", 0.6)
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("query"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "semantic_search",
            description = "Search FAQ documentation for solutions using semantic similarity",
            parameters = parameters
        )
    }

    // Task Management Tool Builders
    private fun buildCreateTaskTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Brief task title (max 100 chars)")
                    }
                    putJsonObject("description") {
                        put("type", "string")
                        put("description", "Detailed task description")
                    }
                    putJsonObject("priority") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add(JsonPrimitive("low"))
                            add(JsonPrimitive("medium"))
                            add(JsonPrimitive("high"))
                        }
                        put("description", "Task priority - REQUIRED, ask user if not specified")
                    }
                    putJsonObject("assignee") {
                        put("type", "string")
                        put("description", "Team member ID (optional)")
                    }
                    putJsonObject("related_ticket_id") {
                        put("type", "integer")
                        put("description", "Link to support ticket (optional)")
                    }
                    putJsonObject("tags") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "string")
                        }
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("title"))
                    add(JsonPrimitive("description"))
                    add(JsonPrimitive("priority"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "create_task",
            description =
                "Create a new task ONLY when user explicitly requests it. " +
                    "ALWAYS ask user for priority (low/medium/high) if not specified in their message.",
            parameters = parameters
        )
    }

    private fun buildListTasksTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("status") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add(JsonPrimitive("todo"))
                            add(JsonPrimitive("in_progress"))
                            add(JsonPrimitive("done"))
                            add(JsonPrimitive("all"))
                        }
                        put("default", "all")
                    }
                    putJsonObject("priority") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add(JsonPrimitive("low"))
                            add(JsonPrimitive("medium"))
                            add(JsonPrimitive("high"))
                            add(JsonPrimitive("all"))
                        }
                        put("default", "all")
                    }
                    putJsonObject("assignee") {
                        put("type", "string")
                        put("description", "Filter by assignee ID")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("default", 10)
                    }
                }
            }

        return AgenticTool(
            type = "function",
            name = "list_tasks",
            description = "List tasks with filtering by status, priority, assignee",
            parameters = parameters
        )
    }

    private fun buildUpdateTaskTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("task_id") {
                        put("type", "integer")
                    }
                    putJsonObject("status") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add(JsonPrimitive("todo"))
                            add(JsonPrimitive("in_progress"))
                            add(JsonPrimitive("done"))
                        }
                    }
                    putJsonObject("priority") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add(JsonPrimitive("low"))
                            add(JsonPrimitive("medium"))
                            add(JsonPrimitive("high"))
                        }
                    }
                    putJsonObject("assignee") {
                        put("type", "string")
                    }
                    putJsonObject("note") {
                        put("type", "string")
                        put("description", "Add note to history")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("task_id"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "update_task",
            description = "Update task status, priority, assignee, or add notes",
            parameters = parameters
        )
    }

    private fun buildGetTaskTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("task_id") {
                        put("type", "integer")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("task_id"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "get_task",
            description = "Get full task details including history and linked ticket",
            parameters = parameters
        )
    }

    private fun buildGetTeamWorkloadTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("role_filter") {
                        put("type", "string")
                        put("description", "Filter by role (optional)")
                    }
                }
            }

        return AgenticTool(
            type = "function",
            name = "get_team_workload",
            description = "Get team members current workload and availability for smart task assignment",
            parameters = parameters
        )
    }

    private fun buildSearchSimilarTasksTool(): AgenticTool {
        val parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Task description or keywords")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("default", 5)
                    }
                    putJsonObject("threshold") {
                        put("type", "number")
                        put("default", 0.6)
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("query"))
                }
            }

        return AgenticTool(
            type = "function",
            name = "search_similar_tasks",
            description = "Find similar tasks using semantic search for context and duplicate detection",
            parameters = parameters
        )
    }

    // Instructions
    private fun buildSupportInstructions(
        ticketId: Int?,
        intent: QueryIntent
    ): String {
        val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

        val currentUserContext =
            _selectedUser.value?.let { user ->
                "\n\n**Текущий пользователь:** ${user.name} (ID: ${user.id}, роль: ${user.role})"
            } ?: ""

        val baseInstructions =
            """
Ты — ассистент команды разработчиков AI with Love.
Текущая дата: $currentDate$currentUserContext

Отвечай на русском языке с использованием Markdown форматирования.
            """.trimIndent()

        val ticketContext =
            ticketId?.let {
                "\n\nТекущий тикет поддержки: #$it (используй get_ticket для получения контекста)"
            } ?: ""

        val taskContextStr =
            _taskContext.value?.let {
                "\n\nКонтекст задач: ${it.activeCount} активных, ${it.highPriorityCount} высокого приоритета"
            } ?: ""

        val intentInstructions =
            when (intent) {
                QueryIntent.SUPPORT_TICKET -> {
                    """

**РЕЖИМ: Поддержка пользователей**

Доступные инструменты:
- semantic_search: поиск решений в FAQ и документации
- create_ticket: создание тикета поддержки
- get_ticket: получение деталей тикета
- update_ticket: обновление статуса тикета

Рабочий процесс:
1. Используй semantic_search для поиска решений в FAQ
2. Создавай тикет ТОЛЬКО если пользователь обращается с проблемой:
   - "Не работает вход"
   - "Помогите с ошибкой"
   - "Проблема с приложением"
   НЕ создавай тикет для общих вопросов о задачах или документации!
3. Отвечай с цитатами из найденных документов
4. Обновляй статус тикета при необходимости

⚠️ ВАЖНО: Создавай тикет только когда пользователь явно обращается за поддержкой с проблемой!
                    """.trimIndent()
                }

                QueryIntent.TASK_MANAGEMENT -> {
                    val currentUserId = _selectedUser.value?.id ?: "1"
                    """

**РЕЖИМ: Управление задачами команды**

Доступные инструменты:
- list_tasks: просмотр задач с фильтрами (status, priority, assignee)
- create_task: создание новой задачи (обязательно: title, description)
- update_task: изменение статуса, приоритета, исполнителя
- get_task: детали задачи с историей
- get_team_workload: загрузка команды для умного назначения
- search_similar_tasks: поиск похожих задач для избежания дубликатов
- semantic_search: поиск связанных документов проекта

⚠️ ВАЖНО ПРИ РАБОТЕ С ЗАДАЧАМИ:
- Когда пользователь говорит "все задачи", "всех задач", "список всех задач" → используй list_tasks БЕЗ assignee фильтра, status="all", priority="all"
- Когда пользователь говорит "мои задачи", "задачи на меня", "показать мне" → используй assignee="$currentUserId"
- При создании задачи БЕЗ указания исполнителя → используй assignee="$currentUserId" (текущий пользователь)
- НЕ используй assignee="me" - это неверно! Всегда используй конкретный ID: "$currentUserId"

Рабочий процесс:
1. Для просмотра задач используй list_tasks с нужными фильтрами
2. ПЕРЕД созданием задачи:
   - Проверь, явно ли пользователь попросил создать задачу
   - ОБЯЗАТЕЛЬНО спроси приоритет (low/medium/high), если не указан
   - Используй search_similar_tasks для проверки дубликатов
3. При назначении проверяй загрузку через get_team_workload
4. Для приоритизации используй semantic_search для поиска связанного контекста
5. Обновляй статус задач через update_task

⚠️ НЕ создавай задачу автоматически! Всегда спрашивай приоритет у пользователя:
"Какой приоритет установить для этой задачи: low, medium или high?"

Примеры запросов:
- "Покажи все задачи" → list_tasks(status="all", priority="all", limit=50)
- "Покажи мои задачи" → list_tasks(assignee="$currentUserId")
- "Покажи задачи с приоритетом high" → list_tasks(priority="high")
- "Создай задачу: исправить баг" → СПРОСИ ПРИОРИТЕТ ПЕРВЫМ
- "Кто сейчас свободен для новой задачи?" → get_team_workload
                    """.trimIndent()
                }

                QueryIntent.HYBRID -> {
                    val currentUserId = _selectedUser.value?.id ?: "1"
                    """

**РЕЖИМ: Гибридный (Поддержка + Задачи)**

Используй инструменты из обоих режимов:
- Поддержка: create_ticket, get_ticket, update_ticket
- Задачи: create_task, list_tasks, update_task, get_team_workload, search_similar_tasks
- Контекст: semantic_search

⚠️ ВАЖНО:
- Создавай ТИКЕТ только когда пользователь обращается с проблемой поддержки!
- При создании ЗАДАЧИ ВСЕГДА спрашивай приоритет (low/medium/high) у пользователя!
- Когда пользователь говорит "все задачи" → используй list_tasks БЕЗ assignee фильтра, status="all", priority="all"
- Когда пользователь говорит "мои задачи", "на меня" → используй assignee="$currentUserId"
- НЕ используй assignee="me" - всегда конкретный ID!

Пример гибридного сценария:
"Создай задачу из этого тикета" → get_ticket → СПРОСИ ПРИОРИТЕТ → create_task (с related_ticket_id) → update_ticket
                    """.trimIndent()
                }

                QueryIntent.UNCLEAR -> {
                    val currentUserId = _selectedUser.value?.id ?: "1"
                    """

**РЕЖИМ: Универсальный**

Проанализируй запрос пользователя и используй подходящие инструменты:
- Для вопросов о проблемах: поддержка (create_ticket ТОЛЬКО при проблеме, semantic_search, get_ticket, update_ticket)
- Для управления работой: задачи (list_tasks, create_task С ПРИОРИТЕТОМ, update_task, workload)
- Для контекста: semantic_search

⚠️ ВАЖНО:
- Тикет создавай ТОЛЬКО при реальной проблеме пользователя
- Задачу создавай ТОЛЬКО после подтверждения приоритета
- Для показа "всех задач" используй list_tasks БЕЗ assignee фильтра, status="all", priority="all"
- Для фильтрации "моих задач" используй assignee="$currentUserId", НЕ "me"!
                    """.trimIndent()
                }
            }

        return baseInstructions + ticketContext + taskContextStr + intentInstructions
    }

    // Helper Functions
    private fun extractTitle(message: String): String {
        // Extract first 50 characters as title, or first sentence
        val firstSentence = message.split(Regex("[.!?]")).firstOrNull()?.trim() ?: message
        return if (firstSentence.length > 50) {
            firstSentence.take(47) + "..."
        } else {
            firstSentence
        }
    }

    private fun detectCategory(message: String): String {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("вход") || lowerMessage.contains("пароль") ||
                lowerMessage.contains("авторизация") || lowerMessage.contains("2fa") ||
                lowerMessage.contains("аккаунт") -> "authentication"

            lowerMessage.contains("ошибка") || lowerMessage.contains("не работает") ||
                lowerMessage.contains("сломал") || lowerMessage.contains("crash") ||
                lowerMessage.contains("медленно") -> "troubleshooting"

            lowerMessage.contains("как") || lowerMessage.contains("использовать") ||
                lowerMessage.contains("функция") || lowerMessage.contains("настроить") -> "features"

            else -> "other"
        }
    }

    private fun detectQueryIntent(message: String): QueryIntent {
        val lowerMessage = message.lowercase()

        val taskKeywords =
            listOf(
                "задач",
                "приоритет",
                "назначить",
                "выполн",
                "статус задач",
                "кто делает",
                "workload",
                "распредели",
                "создай задачу",
                "список задач"
            )

        val supportKeywords =
            listOf(
                "проблема",
                "не работает",
                "ошибка",
                "помощь",
                "тикет",
                "обращение",
                "баг"
            )

        val hasTaskKeywords = taskKeywords.any { lowerMessage.contains(it) }
        val hasSupportKeywords = supportKeywords.any { lowerMessage.contains(it) }

        return when {
            hasTaskKeywords && !hasSupportKeywords -> QueryIntent.TASK_MANAGEMENT
            hasSupportKeywords && !hasTaskKeywords -> QueryIntent.SUPPORT_TICKET
            hasTaskKeywords && hasSupportKeywords -> QueryIntent.HYBRID
            else -> QueryIntent.UNCLEAR
        }
    }

    private fun detectTaskCreationKeywords(message: String): Boolean {
        val lowerMessage = message.lowercase()

        val taskCreationKeywords =
            listOf(
                "создай задачу",
                "создай еще задачу",
                "создай новую задачу",
                "создать задачу",
                "новая задача",
                "новую задачу",
                "добавь задачу",
                "добавить задачу",
                "create task",
                "create a task",
                "create new task",
                "new task",
                "add task"
            )

        return taskCreationKeywords.any { lowerMessage.contains(it) }
    }

    private fun extractTitleFromMessage(message: String): String {
        val lowerMessage = message.lowercase()

        // Patterns: "создай задачу - Title" or "создай задачу: Title"
        val patterns =
            listOf(
                """создай задачу\s*[-:]\s*(.+)""".toRegex(),
                """новая задача\s*[-:]\s*(.+)""".toRegex(),
                """create task\s*[-:]\s*(.+)""".toRegex()
            )

        for (pattern in patterns) {
            val match = pattern.find(lowerMessage)
            if (match != null) {
                return match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            }
        }

        // No title found, return empty
        return ""
    }

    private suspend fun refreshTaskContext() {
        try {
            val result =
                mcpClientManager.callTool(
                    toolName = "list_tasks",
                    arguments = mapOf("status" to "all", "limit" to 100),
                    enabledServers = listOf("support")
                )

            val json = Json { ignoreUnknownKeys = true }
            val jsonElement = json.parseToJsonElement(result).jsonObject
            val tasks = jsonElement["tasks"]?.jsonArray ?: emptyList()

            val activeCount =
                tasks.count { task ->
                    val status = task.jsonObject["status"]?.jsonPrimitive?.content
                    status in listOf("todo", "in_progress")
                }

            val highPriorityCount =
                tasks.count { task ->
                    val priority = task.jsonObject["priority"]?.jsonPrimitive?.content
                    priority == "high"
                }

            _taskContext.value = TaskContext(activeCount, highPriorityCount)
            logD("✅ Task context refreshed: $activeCount active, $highPriorityCount high priority")
        } catch (e: Exception) {
            logE("Failed to refresh task context", e)
        }
    }

    private fun parseTicketIdFromResponse(response: String): Int? {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val outerJson = json.parseToJsonElement(response).jsonObject

            // MCP wraps response in content array with text field
            val contentArray = outerJson["content"]?.jsonArray
            if (contentArray != null && contentArray.isNotEmpty()) {
                val textContent = contentArray[0].jsonObject["text"]?.jsonPrimitive?.content
                if (textContent != null) {
                    // Parse the inner JSON
                    val innerJson = json.parseToJsonElement(textContent).jsonObject
                    val ticketId = innerJson["ticket_id"]?.jsonPrimitive?.content?.toIntOrNull()
                    if (ticketId != null) {
                        logD("🎫 Parsed ticket ID from MCP content: $ticketId")
                        return ticketId
                    }
                }
            }

            // Fallback: try direct ticket_id at top level
            val directTicketId = outerJson["ticket_id"]?.jsonPrimitive?.content?.toIntOrNull()
            if (directTicketId != null) {
                logD("🎫 Parsed ticket ID from top level: $directTicketId")
                return directTicketId
            }

            // Fallback: try nested result object
            val resultObj = outerJson["result"]?.jsonObject
            val resultTicketId = resultObj?.get("ticket_id")?.jsonPrimitive?.content?.toIntOrNull()
            if (resultTicketId != null) {
                logD("🎫 Parsed ticket ID from result object: $resultTicketId")
                return resultTicketId
            }

            logE("🎫 Could not find ticket_id in any expected location", null)
            logE("Response structure: ${outerJson.keys.joinToString()}", null)
            null
        } catch (e: Exception) {
            logE("Failed to parse ticket ID from response: $response", e)
            null
        }
    }

    private fun parseToolArguments(arguments: String?): Map<String, Any> {
        if (arguments == null) return emptyMap()

        return try {
            val json = Json { ignoreUnknownKeys = true }
            val jsonElement = json.parseToJsonElement(arguments)
            val jsonObject = jsonElement.jsonObject

            jsonObject.entries.associate { (key, value) ->
                key to
                    when {
                        value.jsonPrimitive.isString -> value.jsonPrimitive.content
                        else -> value.jsonPrimitive.content.toIntOrNull() ?: value.jsonPrimitive.content
                    }
            }
        } catch (e: Exception) {
            logE("Failed to parse tool arguments", e)
            emptyMap()
        }
    }

    private fun hasToolCalls(response: AgenticResponse): Boolean = response.output?.any { it.type == "function_call" } == true

    private fun extractTextFromResponse(response: AgenticResponse): String {
        // Try outputText first
        response.outputText?.let { return it.trim() }

        // Then try to extract from output items
        response.output?.forEach { outputItem ->
            if (outputItem.type == "message") {
                outputItem.content?.forEach { contentItem ->
                    if (contentItem.type == "output_text" && contentItem.text != null) {
                        return contentItem.text.trim()
                    }
                }
            }
        }

        return ""
    }

    companion object {
        private const val AGENTIC_MODEL = "openai/gpt-5-mini"
        private const val WELCOME_MESSAGE = """👋 Привет! Я ассистент команды AI with Love.

Могу помочь с:
• 🎫 Обработкой тикетов поддержки
• ✅ Управлением задачами команды
• 📊 Анализом загрузки и приоритетов
• 🔍 Поиском информации в документации

**Примеры запросов:**
- "Покажи задачи с приоритетом high"
- "Создай задачу: исправить баг логина, приоритет high"
- "Кто сейчас свободен для новой задачи?"
- "Помощь с ошибкой входа" (создаст тикет)

Просто напиши свой вопрос или задачу!"""
    }

    data class TaskContext(
        val activeCount: Int,
        val highPriorityCount: Int
    )

    data class TaskFormState(
        val title: String = "",
        val description: String = "",
        // default priority
        val priority: String = "medium",
    )

    data class TeamMember(
        val id: String,
        val name: String,
        val role: String,
        val skills: List<String> = emptyList(),
        val availability: String = "available",
        val currentWorkload: Int = 0
    )

    enum class QueryIntent {
        SUPPORT_TICKET,
        TASK_MANAGEMENT,
        HYBRID,
        UNCLEAR
    }
}
