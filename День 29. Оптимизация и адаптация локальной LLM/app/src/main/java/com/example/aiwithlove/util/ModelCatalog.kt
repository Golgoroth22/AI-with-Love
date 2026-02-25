package com.example.aiwithlove.util

/**
 * Represents a GGUF model available for download
 */
data class GGUFModel(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val sha256: String,
    val quantization: String,
    val parameters: String,
    val filename: String
)

/**
 * Catalog of available GGUF models for download
 */
object ModelCatalog {

    /**
     * List of recommended GGUF models optimized for Android devices
     * Sorted by recommended priority (best balance first)
     */
    val RECOMMENDED_MODELS = listOf(
        GGUFModel(
            id = "llama-3.2-1b-q4",
            name = "Llama 3.2 1B (Q4_K_M)",
            description = "Рекомендуется - отличный баланс качества и скорости. Работает на большинстве устройств.",
            sizeBytes = 869_000_000L, // ~810 MB actual size
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sha256 = "", // TODO: Add actual checksum when available
            quantization = "Q4_K_M",
            parameters = "1B",
            filename = "llama-3.2-1b-q4.gguf"
        ),

        GGUFModel(
            id = "gemma-2-2b-q4",
            name = "Gemma 2 2B (Q4_K_M)",
            description = "Модель от Google - хорошее качество, чуть больше размер.",
            sizeBytes = 1_700_000_000L, // ~1.7GB
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            sha256 = "",
            quantization = "Q4_K_M",
            parameters = "2B",
            filename = "gemma-2-2b-q4.gguf"
        ),

        GGUFModel(
            id = "qwen2.5-1.5b-q4",
            name = "Qwen 2.5 1.5B (Q4_K_M)",
            description = "Отличная модель для рассуждений и программирования.",
            sizeBytes = 1_100_000_000L, // ~1.1GB
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            sha256 = "",
            quantization = "Q4_K_M",
            parameters = "1.5B",
            filename = "qwen2.5-1.5b-q4.gguf"
        ),

        GGUFModel(
            id = "phi-3-mini-q4",
            name = "Phi-3 Mini (Q4_K_M)",
            description = "Компактная модель от Microsoft - хорошо работает на слабых устройствах.",
            sizeBytes = 2_400_000_000L, // ~2.4GB
            downloadUrl = "https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Phi-3-mini-4k-instruct-Q4_K_M.gguf",
            sha256 = "",
            quantization = "Q4_K_M",
            parameters = "3.8B",
            filename = "phi-3-mini-q4.gguf"
        ),

        GGUFModel(
            id = "tinyllama-1.1b-q4",
            name = "TinyLlama 1.1B (Q4_K_M)",
            description = "Самая маленькая модель - для тестирования и слабых устройств.",
            sizeBytes = 669_000_000L, // ~669MB
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            sha256 = "",
            quantization = "Q4_K_M",
            parameters = "1.1B",
            filename = "tinyllama-1.1b-q4.gguf"
        )
    )

    /**
     * Default recommended model for most users
     */
    val DEFAULT_MODEL = RECOMMENDED_MODELS[0] // Llama 3.2 1B Q4_K_M

    /**
     * Get model by ID
     */
    fun getModelById(id: String): GGUFModel? {
        return RECOMMENDED_MODELS.find { it.id == id }
    }

    /**
     * Get model by filename
     */
    fun getModelByFilename(filename: String): GGUFModel? {
        return RECOMMENDED_MODELS.find { it.filename == filename }
    }

    /**
     * Format file size for display
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            else -> String.format("%.1f KB", bytes / 1_000.0)
        }
    }
}
