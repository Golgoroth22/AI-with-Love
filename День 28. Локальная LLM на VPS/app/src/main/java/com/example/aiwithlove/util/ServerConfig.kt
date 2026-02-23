package com.example.aiwithlove.util

object ServerConfig {
    val OLLAMA_SERVER_URL: String
        get() = SecureData.OLLAMA_SERVER_URL

    val SERVER_IP: String
        get() = SecureData.REMOTE_SERVER_IP

    val SERVER_PORT: Int
        get() = SecureData.SERVER_PORT
}
