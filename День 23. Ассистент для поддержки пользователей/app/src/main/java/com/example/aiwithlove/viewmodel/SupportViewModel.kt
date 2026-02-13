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
) : ViewModel(), ILoggable {

    // UI State
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentTicketId = MutableStateFlow<Int?>(null)
    val currentTicketId: StateFlow<Int?> = _currentTicketId.asStateFlow()

    init {
        // Load welcome message
        _messages.value = listOf(
            Message(
                text = WELCOME_MESSAGE,
                isFromUser = false
            )
        )
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || _isLoading.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            try {
                // Step 1: If no ticket, create one first
                if (_currentTicketId.value == null) {
                    createTicketForSession(userMessage)
                }

                // Step 2: Add user message
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
                    _messages.value = _messages.value.toMutableList().apply {
                        if (thinkingIndex < size) {
                            set(thinkingIndex, Message(text = response, isFromUser = false))
                        }
                    }
                }
            } catch (e: Exception) {
                logE("Error sending message", e)
                val errorMsg = Message(
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

            val args = mapOf(
                "user_id" to 1,  // Mock user ID
                "title" to title,
                "description" to firstMessage,
                "priority" to "medium",
                "category" to category
            )

            logD("🎫 Creating ticket: title='$title', category='$category'")

            val result = mcpClientManager.callTool(
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

        // Build instructions with current ticket context
        val instructions = buildSupportInstructions(ticketId)

        // Always include support tools
        val tools = listOf(
            buildGetTicketTool(),
            buildUpdateTicketTool(),
            buildSemanticSearchTool()
        )

        logD("🎫 Sending support request for ticket #$ticketId with ${tools.size} tools")

        // Agentic loop
        return runAndCatch {
            var response = perplexityService.sendAgenticRequest(
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

                currentInput = "$currentInput\n\nTool results:\n${toolResults.joinToString("\n")}\n\nPlease use these results to complete your response."

                response = perplexityService.sendAgenticRequest(
                    input = currentInput,
                    model = AGENTIC_MODEL,
                    instructions = instructions,
                    tools = tools
                ).getOrThrow()
            }

            // Extract final text
            extractTextFromResponse(response)
        }.getOrElse { error ->
            logE("Error in agentic loop", error)
            "Произошла ошибка при обработке запроса: ${error.message}"
        }
    }

    private suspend fun executeToolCall(toolName: String, arguments: String?): String {
        return when (toolName) {
            "get_ticket", "update_ticket", "create_ticket" -> {
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
            else -> {
                logE("Unknown tool: $toolName", null)
                """{"error": true, "message": "Unknown tool: $toolName"}"""
            }
        }
    }

    fun clearSupportSession() {
        _messages.value = listOf(
            Message(text = WELCOME_MESSAGE, isFromUser = false)
        )
        _currentTicketId.value = null
        logD("🎫 Cleared support session, ready for new ticket")
    }

    // Tool Builders
    private fun buildGetTicketTool(): AgenticTool {
        val parameters = buildJsonObject {
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
        val parameters = buildJsonObject {
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

    private fun buildSemanticSearchTool(): AgenticTool {
        val parameters = buildJsonObject {
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

    // Instructions
    private fun buildSupportInstructions(ticketId: Int?): String {
        val ticketInfo = ticketId?.let { "Тикет #$it" } ?: "будет создан"

        return """
You are a support assistant for AI with Love application. Current ticket: $ticketInfo.

Available Tools:
- get_ticket: Fetch current ticket details (status, description, history)
- update_ticket: Change ticket status or add notes
- semantic_search: Search FAQ documentation for solutions

Workflow:
1. If ticket ID is provided, use get_ticket to understand the issue context
2. Use semantic_search to find relevant FAQ solutions based on the issue category
3. Combine ticket context with FAQ results in your response
4. If issue is resolved, suggest updating ticket status to "resolved"

Response Format:
- Start with ticket information if available
- Provide clear step-by-step solutions from FAQ
- Include citations/references to FAQ sections
- End with recommended next steps

IMPORTANT:
- Always search FAQ for solutions using semantic_search
- Respond in Russian with markdown formatting
- Be helpful and empathetic
- Suggest ticket status updates when appropriate

Categories:
- authentication: login, password, 2FA, account issues
- features: how to use features, functionality questions
- troubleshooting: errors, crashes, performance issues
        """.trimIndent()
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
                key to when {
                    value.jsonPrimitive.isString -> value.jsonPrimitive.content
                    else -> value.jsonPrimitive.content.toIntOrNull() ?: value.jsonPrimitive.content
                }
            }
        } catch (e: Exception) {
            logE("Failed to parse tool arguments", e)
            emptyMap()
        }
    }

    private fun hasToolCalls(response: AgenticResponse): Boolean {
        return response.output?.any { it.type == "function_call" } == true
    }

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
        private const val WELCOME_MESSAGE = """👋 Добро пожаловать в поддержку!

Опишите вашу проблему, и я помогу вам найти решение.

После отправки вашего первого сообщения будет автоматически создан тикет для отслеживания обращения."""
    }
}
