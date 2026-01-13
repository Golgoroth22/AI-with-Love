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
    val content: String,
)

@Serializable
data class ResponseFormat(
    val type: String,
    val json_schema: JsonSchema,
)

@Serializable
data class JsonSchema(
    val schema: JsonObject,
)

@Serializable
data class ChatCompletionRequest(
    val model: String = "sonar-pro",
    val messages: List<ChatMessage>,
    val response_format: ResponseFormat? = null,
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val model: String,
    val created: Long,
    val choices: List<Choice>,
    val usage: Usage? = null,
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String? = null,
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int,
)

@Serializable
data class ErrorResponse(
    val error: ErrorDetail? = null,
)

@Serializable
data class ErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)

object PerplexitySchemaHelper {
    fun createResponseSchema(): JsonObject {
        return buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("question", buildJsonObject {
                    put("type", "string")
                    put("description", "Полный вопрос который пользователь задал")
                })
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "Кратко о чем вопрос")
                })
                put("answer", buildJsonObject {
                    put("type", "string")
                    put("description", "Полный ответ на вопрос")
                })
                put("tags", buildJsonObject {
                    put("type", "array")
                    put("description", "Список из 1-2 тэгов о чем этот вопрос")
                    put("items", buildJsonObject {
                        put("type", "string")
                    })
                })
                put("date_time", buildJsonObject {
                    put("type", "string")
                    put("description", "Текущая дата и время")
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("question"))
                add(JsonPrimitive("title"))
                add(JsonPrimitive("answer"))
                add(JsonPrimitive("tags"))
                add(JsonPrimitive("date_time"))
            })
        }
    }
}
