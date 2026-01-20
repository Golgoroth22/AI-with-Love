package com.example.aiwithlove.data

import android.util.Log
import com.example.aiwithlove.util.ILoggable
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class PerplexityApiServiceImpl(
    private val apiKey: String,
) : PerplexityApiService,
    ILoggable {

    private val jsonForResponse =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
        }

    private val jsonForRequest =
        Json {
            encodeDefaults = true
        }

    private val client =
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(jsonForRequest)
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        model: String,
        responseFormat: ResponseFormat?,
        maxTokens: Int?,
        temperature: Double?
    ): Result<ChatCompletionResponse> {
        return try {
            val request =
                ChatCompletionRequest(
                    model = model,
                    messages = messages,
                    response_format = responseFormat,
                    max_tokens = maxTokens,
                    temperature = temperature
                )

            val requestBody =
                jsonForRequest.encodeToString(ChatCompletionRequest.serializer(), request)
            logD("Тело запроса: $requestBody")

            val httpResponse: HttpResponse =
                client.post("$BASE_URL$CHAT_ENDPOINT") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    setBody(request)
                }

            val statusCode = httpResponse.status.value
            val responseBody = httpResponse.bodyAsText()

            logD("HTTP статус: $statusCode")
            logD("Тело ответа: $responseBody")

            if (statusCode !in 200..299) {
                val errorMessage =
                    try {
                        val errorResponse =
                            jsonForResponse.decodeFromString<ErrorResponse>(responseBody)
                        errorResponse.error?.message ?: "Неизвестная ошибка API"
                    } catch (e: Exception) {
                        logE("Не удалось распарсить ошибку API", e)
                        "HTTP $statusCode: $responseBody"
                    }
                Log.e(TAG, "API вернул ошибку: $errorMessage")
                return Result.failure(Exception("API Error: $errorMessage"))
            }

            val response: ChatCompletionResponse = jsonForResponse.decodeFromString(responseBody)
            Result.success(response)
        } catch (e: Exception) {
            logE("Ошибка при отправке запроса к Perplexity API", e)
            logE("URL: $BASE_URL$CHAT_ENDPOINT")
            logE("Модель: $model")
            logE("Количество сообщений: ${messages.size}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "PerplexityApiService"
        private const val BASE_URL = "https://api.perplexity.ai"
        private const val CHAT_ENDPOINT = "/chat/completions"
    }
}
