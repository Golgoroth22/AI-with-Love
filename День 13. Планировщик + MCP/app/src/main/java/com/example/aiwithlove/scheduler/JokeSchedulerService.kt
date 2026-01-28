package com.example.aiwithlove.scheduler

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.aiwithlove.data.AgenticTool
import com.example.aiwithlove.data.PerplexityApiService
import com.example.aiwithlove.mcp.McpClient
import com.example.aiwithlove.util.ILoggable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.koin.android.ext.android.inject

class JokeSchedulerService : Service(), ILoggable {

    private val perplexityService: PerplexityApiService by inject()
    private val mcpClient: McpClient by inject()
    private lateinit var notificationHelper: NotificationHelper
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var schedulerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        logD("JokeSchedulerService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logD("JokeSchedulerService onStartCommand")

        startForeground(
            NotificationHelper.NOTIFICATION_SERVICE_ID,
            notificationHelper.buildForegroundNotification()
        )

        startScheduler()

        return START_STICKY
    }

    private fun startScheduler() {
        schedulerJob?.cancel()
        schedulerJob = serviceScope.launch {
            while (isActive) {
                logD("Fetching joke...")
                fetchAndShowJoke()
                delay(INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchAndShowJoke() {
        try {
            val jokeResult = fetchJokeFromMcp()
            if (jokeResult == null) {
                notificationHelper.showErrorNotification("MCP сервер недоступен")
                return
            }

            val translatedJoke = translateJokeWithPerplexity(jokeResult)
            if (translatedJoke != null) {
                notificationHelper.showJokeNotification(translatedJoke)
            } else {
                notificationHelper.showErrorNotification("Не удалось перевести шутку")
            }
        } catch (e: Exception) {
            logE("Error fetching joke", e)
            notificationHelper.showErrorNotification("Ошибка: ${e.message}")
        }
    }

    private suspend fun fetchJokeFromMcp(): String? {
        return try {
            val args = mapOf(
                "category" to "Any",
                "blacklistFlags" to "nsfw,religious,political,racist,sexist,explicit"
            )
            val result = mcpClient.callTool("get_joke", args)
            parseJokeFromMcpResult(result)
        } catch (e: Exception) {
            logE("MCP call failed", e)
            null
        }
    }

    private fun parseJokeFromMcpResult(mcpResult: String): String? {
        return try {
            val jokeData = Json.parseToJsonElement(mcpResult)

            if (jokeData is JsonObject) {
                val content = jokeData["content"] as? JsonArray
                val textContent = content?.firstOrNull() as? JsonObject
                val textString = (textContent?.get("text") as? JsonPrimitive)?.content

                if (textString != null) {
                    val jokeJson = Json.parseToJsonElement(textString)
                    if (jokeJson is JsonObject) {
                        val jokeType = (jokeJson["type"] as? JsonPrimitive)?.content
                        return if (jokeType == "single") {
                            (jokeJson["joke"] as? JsonPrimitive)?.content
                        } else {
                            val setup = (jokeJson["setup"] as? JsonPrimitive)?.content
                            val delivery = (jokeJson["delivery"] as? JsonPrimitive)?.content
                            if (setup != null && delivery != null) {
                                "$setup\n\n$delivery"
                            } else null
                        }
                    }
                    textString
                } else {
                    mcpResult
                }
            } else {
                mcpResult
            }
        } catch (e: Exception) {
            logE("Failed to parse joke", e)
            mcpResult
        }
    }

    private suspend fun translateJokeWithPerplexity(joke: String): String? {
        return try {
            val response = perplexityService.sendAgenticRequest(
                input = "Translate this joke to Russian. Only return the translated joke, nothing else:\n\n$joke",
                model = MODEL,
                instructions = "You are a translator. Translate the joke to Russian naturally and keep it funny. Only return the translation."
            )

            response.getOrNull()?.let { agenticResponse ->
                agenticResponse.outputText?.trim()
                    ?: agenticResponse.output?.firstOrNull { it.type == "message" }
                        ?.content?.firstOrNull { it.type == "output_text" }
                        ?.text?.trim()
            }
        } catch (e: Exception) {
            logE("Translation failed", e)
            null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        schedulerJob?.cancel()
        serviceScope.cancel()
        logD("JokeSchedulerService destroyed")
    }

    companion object {
        private const val INTERVAL_MS = 30_000L
        private const val MODEL = "openai/gpt-5-mini"

        fun start(context: Context) {
            val intent = Intent(context, JokeSchedulerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, JokeSchedulerService::class.java)
            context.stopService(intent)
        }
    }
}
