package com.example.aiwithlove.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "document_chunks")
data class DocumentChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val embedding: List<Double>,
    val sourceFile: String,
    val sourceType: String, // "pdf", "txt", "markdown"
    val chunkIndex: Int,
    val totalChunks: Int,
    val createdAt: Long = System.currentTimeMillis()
)
