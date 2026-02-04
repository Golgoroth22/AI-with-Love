package com.example.aiwithlove.data.model

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
    val mcpToolInfo: List<McpToolInfo>? = null,
    val attachedLogFile: String? = null
)

data class McpToolInfo(
    val toolName: String,
    val requestBody: String,
    val responseBody: String
)
