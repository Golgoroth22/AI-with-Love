package com.example.aiwithlove.data

import android.util.Log
import com.example.aiwithlove.util.ILoggable
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
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
        }

    private val jsonForRequest =
        Json {
            encodeDefaults = true
            explicitNulls = false
        }

    private val client =
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(
                    Json {
                        encodeDefaults = true
                        explicitNulls = false
                        ignoreUnknownKeys = true
                    }
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 180000 // 3 minutes for code review analysis
                connectTimeoutMillis = 30000
                socketTimeoutMillis = 180000 // 3 minutes for code review analysis
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }

    override suspend fun sendAgenticRequest(
        input: String,
        model: String,
        instructions: String?,
        tools: List<AgenticTool>?
    ): Result<AgenticResponse> {
        return try {
            val request =
                AgenticRequest(
                    model = model,
                    input = input,
                    instructions = instructions,
                    tools = tools
                )

            val requestBody = jsonForRequest.encodeToString(AgenticRequest.serializer(), request)
            logD("Agentic Request: $requestBody")

            val httpResponse: HttpResponse =
                client.post("$BASE_URL$AGENTIC_ENDPOINT") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    setBody(request)
                }

            val statusCode = httpResponse.status.value
            val responseBody = httpResponse.bodyAsText()

            logD("HTTP статус: $statusCode")
            logD("Agentic Response: $responseBody")

            if (statusCode !in 200..299) {
                val errorMessage =
                    try {
                        val errorResponse = jsonForResponse.decodeFromString<AgenticResponse>(responseBody)
                        errorResponse.error?.message ?: "Неизвестная ошибка API"
                    } catch (e: Exception) {
                        logE("Не удалось распарсить ошибку Agentic API", e)
                        "HTTP $statusCode: $responseBody"
                    }
                Log.e(TAG, "Agentic API вернул ошибку: $errorMessage")
                return Result.failure(Exception("Agentic API Error: $errorMessage"))
            }

            val response: AgenticResponse = jsonForResponse.decodeFromString(responseBody)
            Result.success(response)
        } catch (e: Exception) {
            logE("Ошибка при отправке Agentic запроса: ${e.javaClass.simpleName}: ${e.message}", e)
            logE("URL: $BASE_URL$AGENTIC_ENDPOINT")
            logE("Модель: $model")
            e.cause?.let { cause ->
                logE("Причина: ${cause.javaClass.simpleName}: ${cause.message}", cause)
            }
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "PerplexityApiService"
        private const val BASE_URL = "https://api.perplexity.ai"
        private const val AGENTIC_ENDPOINT = "/v1/responses"
    }
}
