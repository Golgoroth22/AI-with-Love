package com.example.aiwithlove.data.model

data class Message(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val webpageUrl: String? = null
)
