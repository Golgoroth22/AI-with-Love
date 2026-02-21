package com.example.aiwithlove.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.data.model.Message
import com.example.aiwithlove.llm.ChatMessage
import com.example.aiwithlove.llm.LLMClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val llmClient: LLMClient
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(
        listOf(
            Message(
                text = "Привет! Я AI-ассистент на базе Llama 3.2 1B. Работаю полностью офлайн с использованием llama.cpp! Задай мне любой вопрос!",
                isFromUser = false
            )
        )
    )
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Store conversation history for LLM context
    private val conversationHistory = mutableListOf<ChatMessage>()

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return

        // Add user message to UI
        val userMsg = Message(text = userText, isFromUser = true)
        _messages.value = _messages.value + userMsg

        // Add user message to conversation history
        conversationHistory.add(ChatMessage(role = "user", content = userText))

        _isLoading.value = true

        // Add thinking indicator
        val thinkingMsg = Message(text = "Думаю...", isFromUser = false)
        _messages.value = _messages.value + thinkingMsg
        val thinkingIndex = _messages.value.size - 1

        viewModelScope.launch {
            try {
                // Call LLM with full conversation history
                val aiResponse = llmClient.chat(conversationHistory)

                // Add AI response to conversation history
                conversationHistory.add(ChatMessage(role = "assistant", content = aiResponse))

                // Update UI with AI response
                val responseMsg = Message(
                    text = aiResponse,
                    isFromUser = false
                )

                val currentMessages = _messages.value.toMutableList()
                currentMessages[thinkingIndex] = responseMsg
                _messages.value = currentMessages

            } catch (e: Exception) {
                val errorMsg = Message(
                    text = "❌ Ошибка: ${e.message}\n\nПроверьте, что модель загружена и готова к работе.",
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
        // Clear conversation history
        conversationHistory.clear()

        // Reset UI messages
        _messages.value = listOf(
            Message(
                text = "Привет! Я AI-ассистент на базе Llama 3.2 1B. Работаю полностью офлайн с использованием llama.cpp! Задай мне любой вопрос!",
                isFromUser = false
            )
        )
    }
}
