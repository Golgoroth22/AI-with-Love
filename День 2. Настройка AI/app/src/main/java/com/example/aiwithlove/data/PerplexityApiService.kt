package com.example.aiwithlove.data

interface PerplexityApiService {
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        model: String = "sonar-pro",
        responseFormat: ResponseFormat? = null,
    ): Result<ChatCompletionResponse>
}
