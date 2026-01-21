package com.example.aiwithlove.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ResponseFormat(
    val type: String,
    val json_schema: JsonSchema
)

@Serializable
data class JsonSchema(
    val schema: JsonObject
)

@Serializable
data class ChatCompletionRequest(
    val model: String = "sonar",
    val messages: List<ChatMessage>,
    val response_format: ResponseFormat? = null,
    val max_tokens: Int? = 1500,
    val temperature: Double? = null
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val model: String,
    val created: Long,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String? = null
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int,
    val cost: Cost? = null
)

@Serializable
data class Cost(
    val input_tokens_cost: Double = 0.0,
    val output_tokens_cost: Double = 0.0,
    val request_cost: Double = 0.0,
    val total_cost: Double = 0.0
)

@Serializable
data class ErrorResponse(
    val error: ErrorDetail? = null
)

@Serializable
data class ErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
