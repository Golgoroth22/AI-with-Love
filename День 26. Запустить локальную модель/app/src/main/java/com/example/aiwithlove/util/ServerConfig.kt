package com.example.aiwithlove.util

/**
 * ServerConfig - Public configuration that uses SecureData
 *
 * This file can be safely committed to version control.
 * All sensitive data is stored in SecureData.kt (which is gitignored).
 */
object ServerConfig {
    /**
     * Ollama Server URL - constructed from SecureData
     */
    val OLLAMA_SERVER_URL: String
        get() = SecureData.OLLAMA_SERVER_URL

    /**
     * Server configuration details (for debugging/logging)
     */
    val SERVER_IP: String
        get() = SecureData.SERVER_IP

    val SERVER_PORT: Int
        get() = SecureData.SERVER_PORT
}
