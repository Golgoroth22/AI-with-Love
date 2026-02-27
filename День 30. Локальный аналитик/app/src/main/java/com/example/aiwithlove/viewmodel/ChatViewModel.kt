package com.example.aiwithlove.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.data.model.Message
import com.example.aiwithlove.ollama.OllamaClient
import com.example.aiwithlove.ollama.OllamaMessage
import com.example.aiwithlove.util.TranslationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val YUKI_GREETING_RU =
    "Konnichiwa! Я Юки, твой личный гид по Японии. Спрашивай о направлениях, еде, транспорте, культурных советах или скрытых жемчужинах Японии!"

private const val YUKI_GREETING_EN =
    "Konnichiwa! I'm Yuki, your personal guide to Japan. Ask me about destinations, food, transportation, cultural tips, or hidden gems of Japan!"

class ChatViewModel(
    private val ollamaClient: OllamaClient,
    private val translationService: TranslationService
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(
        listOf(Message(text = YUKI_GREETING_RU, isFromUser = false))
    )
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val conversationHistory = mutableListOf(
        OllamaMessage(role = "assistant", content = YUKI_GREETING_EN)
    )

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return

        val userMsg = Message(text = userText, isFromUser = true)
        _messages.value = _messages.value + userMsg

        _isLoading.value = true

        val thinkingMsg = Message(text = "Думаю...", isFromUser = false)
        _messages.value = _messages.value + thinkingMsg
        val thinkingIndex = _messages.value.size - 1

        viewModelScope.launch {
            try {
                val englishInput = translationService.toEnglish(userText)
                conversationHistory.add(OllamaMessage(role = "user", content = englishInput))

                val englishResponse = ollamaClient.chat(conversationHistory)
                conversationHistory.add(OllamaMessage(role = "assistant", content = englishResponse))

                val russianResponse = translationService.toRussian(englishResponse)

                val responseMsg = Message(text = russianResponse, isFromUser = false)
                val currentMessages = _messages.value.toMutableList()
                currentMessages[thinkingIndex] = responseMsg
                _messages.value = currentMessages

            } catch (e: Exception) {
                val errorMsg = Message(
                    text = "❌ Ошибка: ${e.message}\n\nПроверьте подключение к Ollama серверу.",
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
        conversationHistory.clear()
        conversationHistory.add(OllamaMessage(role = "assistant", content = YUKI_GREETING_EN))

        _messages.value = listOf(Message(text = YUKI_GREETING_RU, isFromUser = false))
    }
}
