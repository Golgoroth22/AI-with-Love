package com.example.aiwithlove.mcp

import com.example.aiwithlove.util.ILoggable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class McpClient(
    private val serverUrl: String
) : ILoggable {

    private val httpClient =
        HttpClient {
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
                            logD("Ktor: $message")
                        }
                    }
                level = LogLevel.INFO
            }
        }

    private var requestIdCounter = 0

    suspend fun initialize(): McpInitializeResult {
        logD("Initializing MCP connection to $serverUrl")

        val request =
            McpRequest(
                id = getNextRequestId(),
                method = "initialize",
                params =
                    buildJsonObject {
                    }
            )

        val response = sendRequest(request)

        return if (response.result != null) {
            Json.decodeFromJsonElement(McpInitializeResult.serializer(), response.result)
        } else {
            throw Exception("Initialize failed: ${response.error?.message}")
        }
    }

    suspend fun listTools(): List<McpTool> {
        logD("Requesting tools list from MCP server")

        val request =
            McpRequest(
                id = getNextRequestId(),
                method = "tools/list",
                params = null
            )

        val response = sendRequest(request)

        return if (response.result != null) {
            val result = Json.decodeFromJsonElement(McpToolsListResult.serializer(), response.result)
            logD("Received ${result.tools.size} tools")
            result.tools
        } else {
            throw Exception("List tools failed: ${response.error?.message}")
        }
    }

    private suspend fun sendRequest(request: McpRequest): McpResponse =
        try {
            logD("Sending HTTP request: ${request.method}")

            val response =
                httpClient.post(serverUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            response.body<McpResponse>()
        } catch (e: Exception) {
            logE("Request failed: ${request.method}", e)
            throw e
        }

    private fun getNextRequestId(): Int = ++requestIdCounter

    fun close() {
        httpClient.close()
    }
}
