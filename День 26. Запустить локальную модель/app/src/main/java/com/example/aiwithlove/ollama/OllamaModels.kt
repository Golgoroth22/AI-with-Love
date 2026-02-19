package com.example.aiwithlove.ollama

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val keep_alive: String? = "5m",  // Keep model loaded for 5 minutes
    val options: JsonObject? = null  // Runtime options (temperature, num_predict, etc.)
)

@Serializable
data class OllamaChatResponse(
    val model: String,
    val created_at: String,
    val message: OllamaMessage,
    val done: Boolean,
    val done_reason: String? = null,  // "stop", "length", or "load"
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
)

@Serializable
data class OllamaError(
    val error: String
)
