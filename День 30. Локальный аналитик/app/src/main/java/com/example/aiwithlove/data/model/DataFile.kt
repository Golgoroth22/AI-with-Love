package com.example.aiwithlove.data.model

enum class DataFileType { CSV, JSON, TEXT }

data class DataFile(
    val name: String,
    val type: DataFileType,
    val rawContent: String,
    val isTruncated: Boolean,
    val rowCount: Int,
    val totalRowCount: Int
)
