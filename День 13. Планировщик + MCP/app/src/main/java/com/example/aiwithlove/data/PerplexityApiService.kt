package com.example.aiwithlove.data

interface PerplexityApiService {
    suspend fun sendAgenticRequest(
        input: String,
        model: String = "openai/gpt-5-mini",
        instructions: String? = null,
        tools: List<AgenticTool>? = null
    ): Result<AgenticResponse>
}
