package com.example.aiwithlove.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.data.model.Message
import com.example.aiwithlove.mcp.McpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class ChatViewModel(
    private val mcpClient: McpClient
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(
        listOf(
            Message(
                text = "Привет! Отправь мне любой текст, и я создам веб-страницу с этим текстом.",
                isFromUser = false
            )
        )
    )
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return

        val userMsg = Message(text = userText, isFromUser = true)
        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        val thinkingMsg = Message(text = "Создаю веб-страницу...", isFromUser = false)
        _messages.value = _messages.value + thinkingMsg
        val thinkingIndex = _messages.value.size - 1

        viewModelScope.launch {
            try {
                val result = mcpClient.callTool(
                    toolName = "create_webpage",
                    arguments = mapOf("text" to userText)
                )

                val mcpResult = json.parseToJsonElement(result) as JsonObject
                val content = mcpResult["content"] as? JsonArray
                val textContent = content?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

                if (textContent != null) {
                    val toolResult = json.parseToJsonElement(textContent) as JsonObject
                    val success = toolResult["success"]?.jsonPrimitive?.boolean ?: false

                    if (success) {
                        val url = toolResult["url"]?.jsonPrimitive?.content ?: "Unknown URL"
                        val filename = toolResult["filename"]?.jsonPrimitive?.content ?: ""

                        val successMsg = Message(
                            text = "✅ Веб-страница создана!\n\n🔗 URL: $url\n\n📄 Файл: $filename",
                            isFromUser = false,
                            webpageUrl = url
                        )

                        val currentMessages = _messages.value.toMutableList()
                        currentMessages[thinkingIndex] = successMsg
                        _messages.value = currentMessages
                    } else {
                        val error = toolResult["error"]?.jsonPrimitive?.content ?: "Unknown error"

                        val errorMsg = Message(
                            text = "❌ Ошибка при создании страницы:\n$error",
                            isFromUser = false
                        )

                        val currentMessages = _messages.value.toMutableList()
                        currentMessages[thinkingIndex] = errorMsg
                        _messages.value = currentMessages
                    }
                } else {
                    throw Exception("Invalid response format")
                }

            } catch (e: Exception) {
                val errorMsg = Message(
                    text = "❌ Ошибка: ${e.message}",
                    isFromUser = false
                )

                val currentMessages = _messages.value.toMutableList()
                if (thinkingIndex < currentMessages.size) {
                    currentMessages[thinkingIndex] = errorMsg
                    _messages.value = currentMessages
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        _messages.value = listOf(
            Message(
                text = "Привет! Отправь мне любой текст, и я создам веб-страницу с этим текстом.",
                isFromUser = false
            )
        )
    }
}
