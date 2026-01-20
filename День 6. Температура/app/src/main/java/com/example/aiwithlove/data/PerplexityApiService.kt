package com.example.aiwithlove.data

interface PerplexityApiService {
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        model: String = "sonar",
        responseFormat: ResponseFormat? = null,
        maxTokens: Int? = null,
        temperature: Double? = null
    ): Result<ChatCompletionResponse>
}
