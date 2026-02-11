package com.example.aiwithlove.ollama

import com.example.aiwithlove.util.ILoggable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for local Ollama API
 * Used for generating embeddings locally instead of on remote server
 */
class OllamaClient(
    // Emulator's host machine
    private val baseUrl: String = "http://10.0.2.2:11434"
) : ILoggable {

    private val httpClient =
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 60000 // 1 minute per embedding
                connectTimeoutMillis = 10000 // 10 seconds
                socketTimeoutMillis = 60000 // 1 minute
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = false
                    }
                )
            }

            install(Logging) {
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            logD("Ollama: $message")
                        }
                    }
                level = LogLevel.INFO
            }
        }

    /**
     * Generate embedding for text using local Ollama
     */
    suspend fun generateEmbedding(
        text: String,
        model: String = "nomic-embed-text"
    ): List<Double> {
        try {
            logD("Generating embedding for text: ${text.take(50)}...")

            val request =
                OllamaEmbeddingRequest(
                    model = model,
                    prompt = text
                )

            val response =
                httpClient.post("$baseUrl/api/embeddings") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body<OllamaEmbeddingResponse>()

            logD("✅ Embedding generated: ${response.embedding.size} dimensions")

            return response.embedding
        } catch (e: Exception) {
            logE("❌ Failed to generate embedding", e)
            throw Exception("Failed to generate embedding: ${e.message}")
        }
    }

    /**
     * Check if Ollama service is available
     */
    suspend fun isAvailable(): Boolean =
        try {
            httpClient.post("$baseUrl/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(
                    OllamaEmbeddingRequest(
                        model = "nomic-embed-text",
                        prompt = "test"
                    )
                )
            }
            true
        } catch (e: Exception) {
            logE("Ollama service not available", e)
            false
        }
}

@Serializable
data class OllamaEmbeddingRequest(
    val model: String,
    val prompt: String
)

@Serializable
data class OllamaEmbeddingResponse(
    val embedding: List<Double>
)
