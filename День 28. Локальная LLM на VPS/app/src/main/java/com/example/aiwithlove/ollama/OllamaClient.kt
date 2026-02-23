package com.example.aiwithlove.ollama

import com.example.aiwithlove.util.ILoggable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString

class OllamaClient(
    private val serverUrl: String,
    private val modelName: String = "gemma:2b"
) : ILoggable {

    private val httpClient =
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 300000
                connectTimeoutMillis = 30000
                socketTimeoutMillis = 300000
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                    }
                )
            }

            install(Logging) {
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            logD("Ktor [Ollama]: $message")
                        }
                    }
                level = LogLevel.INFO
            }
        }

    suspend fun chat(
        messages: List<OllamaMessage>,
        keepAlive: String = "5m"
    ): String {
        logD("Sending chat request with ${messages.size} messages")

        val request = OllamaChatRequest(
            model = modelName,
            messages = messages,
            stream = false,
            keep_alive = keepAlive
        )

        return try {
            val response: HttpResponse = httpClient.post("$serverUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = try {
                    response.body<OllamaError>()
                } catch (e: Exception) {
                    OllamaError("HTTP ${response.status.value}: ${response.status.description}")
                }

                when (response.status.value) {
                    404 -> throw OllamaClientException(
                        "Model '$modelName' not found. Pull it with: ollama pull $modelName",
                        errorBody.error
                    )
                    else -> throw OllamaClientException(
                        "Ollama request failed: ${response.status.value}",
                        errorBody.error
                    )
                }
            }

            val responseText = response.bodyAsText()
            logD("Response content type: ${response.contentType()}")
            logD("Response body length: ${responseText.length}")

            val chatResponse = parseOllamaResponse(responseText)

            val tokenCount = chatResponse.eval_count ?: 0
            val durationMs = (chatResponse.eval_duration ?: 0) / 1_000_000
            logD("Received response: ${chatResponse.message.content.take(50)}... " +
                "($tokenCount tokens in ${durationMs}ms)")

            chatResponse.message.content
        } catch (e: Exception) {
            logE("Chat request failed", e)
            throw OllamaClientException("Failed to connect to Ollama server", e.message ?: "Unknown error")
        }
    }

    private fun parseOllamaResponse(responseText: String): OllamaChatResponse {
        val json = Json { ignoreUnknownKeys = true }

        return if (responseText.contains('\n')) {
            logD("Parsing NDJSON response (streaming format)")
            val lines = responseText.trim().split('\n').filter { it.isNotBlank() }

            val fullContent = StringBuilder()
            var lastResponse: OllamaChatResponse? = null

            for (line in lines) {
                try {
                    val response = json.decodeFromString<OllamaChatResponse>(line)

                    fullContent.append(response.message.content)

                    lastResponse = response
                } catch (e: Exception) {
                    logE("Failed to parse line: $line", e)
                }
            }

            lastResponse?.copy(
                message = lastResponse.message.copy(
                    content = fullContent.toString().trim()
                )
            ) ?: throw IllegalStateException("No valid response found in NDJSON")
        } else {
            logD("Parsing JSON response (non-streaming format)")
            json.decodeFromString<OllamaChatResponse>(responseText)
        }
    }

    class OllamaClientException(
        message: String,
        val details: String? = null
    ) : Exception(message + (details?.let { "\nDetails: $it" } ?: ""))

}
