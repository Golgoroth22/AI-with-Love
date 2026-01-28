package com.example.aiwithlove.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_summary")
data class ChatSummaryEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "summary_text")
    val summaryText: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "message_count_at_compression")
    val messageCountAtCompression: Int
)
