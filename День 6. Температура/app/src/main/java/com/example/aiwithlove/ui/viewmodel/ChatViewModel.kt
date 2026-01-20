package com.example.aiwithlove.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.data.PerplexityApiService
import com.example.aiwithlove.util.ILoggable
import com.example.aiwithlove.util.runAndCatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.aiwithlove.data.ChatMessage as ApiChatMessage

class ChatViewModel(
    private val perplexityService: PerplexityApiService
) : ViewModel(),
    ILoggable {

    private val _messages =
        MutableStateFlow(
            listOf(
                Message(
                    text = "Привет! Я ваш ИИ-помощник. Чем могу помочь?",
                    isFromUser = false
                )
            )
        )
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedTemperature = MutableStateFlow(0.0)
    val selectedTemperature: StateFlow<Double> = _selectedTemperature.asStateFlow()

    fun setTemperature(temperature: Double) {
        _selectedTemperature.value = temperature
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || _isLoading.value) return

        _messages.value = _messages.value +
            Message(
                text = userMessage,
                isFromUser = true
            )
        _isLoading.value = true

        val thinkingMessage =
            Message(
                text = "Думаю...",
                isFromUser = false
            )
        _messages.value = _messages.value + thinkingMessage
        val thinkingMessageIndex = _messages.value.size - 1

        viewModelScope.launch(Dispatchers.IO) {
            runAndCatch {
                val userMessages =
                    _messages.value
                        .drop(1)
                        .filter { it.isFromUser || it.text != "Думаю..." }
                        .map { msg ->
                            ApiChatMessage(
                                role = if (msg.isFromUser) "user" else "assistant",
                                content = msg.text
                            )
                        }

                logD("Отправка ${userMessages.size} сообщений в API")
                perplexityService.sendMessage(
                    messages = userMessages,
                    maxTokens = 1000,
                    temperature = _selectedTemperature.value
                )
            }.onSuccess { result ->
                result
                    .onSuccess { response ->
                        val rawResponse =
                            response.choices
                                .firstOrNull()
                                ?.message
                                ?.content
                                ?: "Извините, не удалось получить ответ."

                        val fullResponse = rawResponse.trim()

                        logD("Успешно получен ответ от Perplexity API")

                        val currentMessages = _messages.value.toMutableList()
                        if (thinkingMessageIndex < currentMessages.size) {
                            currentMessages[thinkingMessageIndex] =
                                Message(
                                    text = "",
                                    isFromUser = false
                                )
                            _messages.value = currentMessages
                        }

                        typewriterEffect(fullResponse, thinkingMessageIndex)
                    }.onFailure { error ->
                        logE("Ошибка при получении ответа от Perplexity API", error)
                        val currentMessages = _messages.value.toMutableList()
                        if (thinkingMessageIndex < currentMessages.size) {
                            currentMessages[thinkingMessageIndex] =
                                Message(
                                    text = "Ошибка: ${error.message ?: "Неизвестная ошибка"}",
                                    isFromUser = false
                                )
                            _messages.value = currentMessages
                        }
                        _isLoading.value = false
                    }
            }.onFailure { error ->
                logE("Исключение при отправке запроса к Perplexity API", error)
                val currentMessages = _messages.value.toMutableList()
                if (thinkingMessageIndex < currentMessages.size) {
                    currentMessages[thinkingMessageIndex] =
                        Message(
                            text = "Ошибка при отправке запроса: ${error.message}",
                            isFromUser = false
                        )
                    _messages.value = currentMessages
                }
                _isLoading.value = false
            }
        }
    }

    data class Message(
        val text: String,
        val isFromUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private suspend fun typewriterEffect(
        fullText: String,
        messageIndex: Int
    ) {
        val charsPerDelay = 3
        val delayMs = 30L

        runAndCatch {
            for (i in 0..fullText.length step charsPerDelay) {
                val currentText = fullText.substring(0, minOf(i, fullText.length))
                val currentMessages = _messages.value.toMutableList()
                if (messageIndex < currentMessages.size) {
                    currentMessages[messageIndex] =
                        currentMessages[messageIndex].copy(text = currentText)
                    _messages.value = currentMessages
                }
                delay(delayMs)
            }

            val finalMessages = _messages.value.toMutableList()
            if (messageIndex < finalMessages.size) {
                finalMessages[messageIndex] = finalMessages[messageIndex].copy(text = fullText)
                _messages.value = finalMessages
            }

            _isLoading.value = false
        }.onFailure {
            _isLoading.value = false
        }
    }
}

// Переформулируй этот текст 5 раз разными стилями: Наша жизнь есть то, что мы думаем о ней.
