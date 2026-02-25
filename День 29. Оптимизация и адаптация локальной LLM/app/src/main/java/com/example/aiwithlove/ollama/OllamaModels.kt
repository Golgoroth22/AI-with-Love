package com.example.aiwithlove.ollama

import kotlinx.serialization.Serializable

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    val num_predict: Int? = null,
    val num_ctx: Int? = null,
    val top_p: Double? = null,
    val top_k: Int? = null,
    val repeat_penalty: Double? = null
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val system: String? = null,
    val stream: Boolean = false,
    val keep_alive: String? = "5m",
    val options: OllamaOptions? = null
)

@Serializable
data class OllamaChatResponse(
    val message: OllamaMessage
)

@Serializable
data class OllamaError(
    val error: String
)
