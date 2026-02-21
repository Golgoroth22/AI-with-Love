package com.example.aiwithlove.llm

/**
 * Interface for LLM (Large Language Model) clients
 * Allows swapping between different implementations (MediaPipe, Ollama, etc.)
 */
interface LLMClient {
    /**
     * Send a chat message and get AI response
     * @param messages Conversation history
     * @return AI-generated response text
     */
    suspend fun chat(messages: List<ChatMessage>): String

    /**
     * Check if the model is ready for inference
     * @return true if model is loaded and ready
     */
    suspend fun isModelReady(): Boolean

    /**
     * Get information about the current model
     * @return Model metadata
     */
    suspend fun getModelInfo(): ModelInfo

    /**
     * Clean up resources (close model, free memory)
     */
    fun close()
}

/**
 * Represents a single chat message
 */
data class ChatMessage(
    val role: String,  // "user" or "assistant"
    val content: String
)

/**
 * Model metadata
 */
data class ModelInfo(
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val isDownloaded: Boolean
)
