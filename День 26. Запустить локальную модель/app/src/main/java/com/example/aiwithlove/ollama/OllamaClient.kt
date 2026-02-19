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
    private val modelName: String = "llama2"
) : ILoggable {

    private val httpClient =
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 300000 // 5 minutes - AI responses can take time
                connectTimeoutMillis = 30000 // 30 seconds
                socketTimeoutMillis = 300000 // 5 minutes
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

    /**
     * Send a chat message to Ollama and get a response.
     *
     * @param messages List of conversation messages (includes history for context)
     * @param keepAlive How long to keep model loaded (default: "5m")
     * @return AI response text
     * @throws OllamaClientException if the request fails
     */
    suspend fun chat(
        messages: List<OllamaMessage>,
        keepAlive: String = "5m"
    ): String {
        logD("Sending chat request with ${messages.size} messages")

        val request = OllamaChatRequest(
            model = modelName,
            messages = messages,
            stream = false,  // Non-streaming for simplicity
            keep_alive = keepAlive  // Keep model loaded to speed up subsequent requests
        )

        return try {
            val response: HttpResponse = httpClient.post("$serverUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            // Check for HTTP error status
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

            // Read response as text first to handle both JSON and NDJSON formats
            val responseText = response.bodyAsText()
            logD("Response content type: ${response.contentType()}")
            logD("Response body length: ${responseText.length}")

            // Parse response - handle both streaming (NDJSON) and non-streaming (JSON)
            val chatResponse = parseOllamaResponse(responseText)

            val tokenCount = chatResponse.eval_count ?: 0
            val durationMs = (chatResponse.eval_duration ?: 0) / 1_000_000
            logD("Received response: ${chatResponse.message.content.take(50)}... " +
                "($tokenCount tokens in ${durationMs}ms)")

            chatResponse.message.content
        } catch (e: OllamaClientException) {
            throw e
        } catch (e: Exception) {
            logE("Chat request failed", e)
            throw OllamaClientException("Failed to connect to Ollama server", e.message ?: "Unknown error")
        }
    }

    /**
     * Parse Ollama response - handles both JSON and NDJSON formats.
     *
     * Some versions of Ollama return NDJSON (newline-delimited JSON) even when
     * stream=false is set. This method handles both formats.
     */
    private fun parseOllamaResponse(responseText: String): OllamaChatResponse {
        val json = Json { ignoreUnknownKeys = true }

        // If response contains newlines, it's NDJSON (streaming format)
        return if (responseText.contains('\n')) {
            logD("Parsing NDJSON response (streaming format)")
            val lines = responseText.trim().split('\n').filter { it.isNotBlank() }

            // In NDJSON format, we need to concatenate message content from all lines
            val fullContent = StringBuilder()
            var lastResponse: OllamaChatResponse? = null

            for (line in lines) {
                try {
                    val response = json.decodeFromString<OllamaChatResponse>(line)

                    // Append content from this chunk
                    fullContent.append(response.message.content)

                    // Keep track of the last response (has metadata)
                    lastResponse = response
                } catch (e: Exception) {
                    logE("Failed to parse line: $line", e)
                }
            }

            // Return the last response but with concatenated content (trimmed)
            lastResponse?.copy(
                message = lastResponse.message.copy(
                    content = fullContent.toString().trim()
                )
            ) ?: throw IllegalStateException("No valid response found in NDJSON")
        } else {
            // Standard JSON response
            logD("Parsing JSON response (non-streaming format)")
            json.decodeFromString<OllamaChatResponse>(responseText)
        }
    }

    /**
     * Custom exception for Ollama client errors
     */
    class OllamaClientException(
        message: String,
        val details: String? = null
    ) : Exception(message + (details?.let { "\nDetails: $it" } ?: ""))

    /**
     * Check if Ollama server is accessible.
     *
     * @return true if server responds, false otherwise
     */
    suspend fun ping(): Boolean {
        return try {
            httpClient.post("$serverUrl/api/version")
            true
        } catch (e: Exception) {
            logE("Ping failed", e)
            false
        }
    }
}
