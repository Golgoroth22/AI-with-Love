package com.example.aiwithlove.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

@Serializable
data class AgenticRequest(
    val model: String = "openai/gpt-5-mini",
    val input: String,
    val instructions: String? = null,
    val tools: List<AgenticTool>? = null
)

@Serializable
data class AgenticTool(
    val type: String = "function",
    val name: String? = null,
    val description: String? = null,
    val parameters: JsonObject? = null
)

@Serializable
data class AgenticResponse(
    val id: String? = null,
    @SerialName("output_text")
    val outputText: String? = null,
    val output: List<OutputItem>? = null,
    val usage: AgenticUsage? = null,
    val error: ErrorDetail? = null
)

@Serializable
data class OutputItem(
    val type: String,
    val id: String? = null,
    val status: String? = null,
    val role: String? = null,
    val content: List<ContentItem>? = null,
    val name: String? = null,
    val arguments: String? = null,
    @SerialName("call_id")
    val callId: String? = null
)

@Serializable
data class ContentItem(
    val text: String? = null,
    val type: String? = null
)

@Serializable
data class AgenticUsage(
    @SerialName("input_tokens")
    val inputTokens: Int = 0,
    @SerialName("output_tokens")
    val outputTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)
