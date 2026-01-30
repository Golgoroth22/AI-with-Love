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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any>
    ): String {
        logD("Calling tool: $toolName")

        val params =
            buildJsonObject {
                put("name", toolName)
                put(
                    "arguments",
                    buildJsonObject {
                        arguments.forEach { (key, value) ->
                            when (value) {
                                is String -> put(key, value)
                                is Int -> put(key, value)
                                is Boolean -> put(key, value)
                                is Double -> put(key, value)
                                else -> put(key, value.toString())
                            }
                        }
                    }
                )
            }

        val request =
            McpRequest(
                id = getNextRequestId(),
                method = "tools/call",
                params = params
            )

        val response = sendRequest(request)

        return if (response.result != null) {
            response.result.toString()
        } else {
            throw Exception("Tool call failed: ${response.error?.message}")
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
}
