package com.example.aiwithlove.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assistant_messages")
data class AssistantMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "request_tokens")
    val requestTokens: Int?,
    @ColumnInfo(name = "response_tokens")
    val responseTokens: Int?,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
