package com.example.aiwithlove.llm

import android.content.Context
import android.util.Log
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LlamaCpp implementation using Llamatik library
 * Provides on-device GGUF model inference
 */
class LlamaCppClient(
    private val context: Context,
    private val modelPath: String,
    private val contextSize: Int = 2048
) : LLMClient {

    private var isInitialized = false

    /**
     * Initialize the model (lazy loading on first use)
     */
    private suspend fun initializeModel() {
        if (isInitialized) return

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing llama.cpp model from: $modelPath")

                // Check if model file exists
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    throw Exception("Model file not found at: $modelPath")
                }

                // Initialize model with Llamatik
                LlamaBridge.initGenerateModel(modelPath)

                Log.d(TAG, "Model initialized successfully")
                isInitialized = true

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize model", e)
                throw Exception("Failed to load GGUF model: ${e.message}", e)
            }
        }
    }

    override suspend fun chat(messages: List<ChatMessage>): String {
        // Initialize model on first call
        if (!isInitialized) {
            initializeModel()
        }

        return withContext(Dispatchers.IO) {
            try {
                // Format messages into prompt (ChatML format)
                val prompt = formatPrompt(messages)
                Log.d(TAG, "Sending prompt (${prompt.length} chars)")

                // Generate response using Llamatik
                val response = LlamaBridge.generate(prompt)

                // Clean response
                val cleanedResponse = cleanResponse(response)
                Log.d(TAG, "Received response (${cleanedResponse.length} chars)")

                cleanedResponse

            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                throw Exception("Inference failed: ${e.message}", e)
            }
        }
    }

    /**
     * Format conversation history into ChatML prompt
     * Uses the same format as MediaPipeLLMClient for consistency
     */
    private fun formatPrompt(messages: List<ChatMessage>): String {
        val builder = StringBuilder()

        // Add each message with ChatML formatting
        for (message in messages) {
            builder.append("<|im_start|>${message.role}\n")
            builder.append(message.content)
            builder.append("<|im_end|>\n")
        }

        // Add assistant start tag to prompt for response
        builder.append("<|im_start|>assistant\n")

        return builder.toString()
    }

    /**
     * Clean up response by removing special tokens
     * Mirrors MediaPipeLLMClient's cleaning logic
     */
    private fun cleanResponse(rawResponse: String): String {
        var cleaned = rawResponse
            .replace("<|im_start|>assistant", "")
            .replace("<|im_start|>user", "")
            .replace("<|im_start|>system", "")
            .replace("<|im_end|>", "")
            .replace("<|im_start|>", "")
            .trim()

        // Remove multiple consecutive newlines
        cleaned = cleaned.replace(Regex("\\n{3,}"), "\n\n")

        // If response is empty or too short, return default message
        if (cleaned.isBlank() || cleaned.length < 10) {
            return "Извините, не могу ответить на этот вопрос."
        }

        return cleaned.trim()
    }

    override suspend fun isModelReady(): Boolean {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.w(TAG, "Model file not found at: $modelPath")
            return false
        }

        if (!isInitialized) {
            try {
                initializeModel()
            } catch (e: Exception) {
                Log.e(TAG, "Model initialization check failed", e)
                return false
            }
        }

        return isInitialized
    }

    override suspend fun getModelInfo(): ModelInfo {
        val modelFile = File(modelPath)
        return ModelInfo(
            name = modelFile.nameWithoutExtension,
            version = "GGUF (llama.cpp via Llamatik)",
            sizeBytes = if (modelFile.exists()) modelFile.length() else 0L,
            isDownloaded = modelFile.exists()
        )
    }

    override fun close() {
        Log.d(TAG, "Releasing llama.cpp resources")
        try {
            LlamaBridge.shutdown()
            isInitialized = false
            Log.d(TAG, "Resources released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }

    companion object {
        private const val TAG = "LlamaCppClient"
    }
}
