package com.example.aiwithlove.data.model

import kotlinx.serialization.Serializable

data class Message(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val isSystemMessage: Boolean = false,
    val isSummary: Boolean = false,
    val isCompressionNotice: Boolean = false,
    val isCompressed: Boolean = false,
    val mcpToolInfo: List<McpToolInfo>? = null,
    val attachedLogFile: String? = null
)

data class McpToolInfo(
    val toolName: String,
    val requestBody: String,
    val responseBody: String,
    val semanticSearchResult: SemanticSearchResult? = null
)

/**
 * Represents a single document chunk returned from semantic search
 */
@Serializable
data class SemanticSearchDocument(
    val id: Int,
    val content: String,
    val similarity: Double,
    val created_at: String? = null
)

/**
 * Represents the complete semantic search result with optional comparison mode
 */
@Serializable
data class SemanticSearchResult(
    val success: Boolean,
    val threshold: Double? = null,
    val isFiltered: Boolean? = null,
    val count: Int? = null,
    val documents: List<SemanticSearchDocument>? = null,
    // For comparison mode
    val unfiltered: DocumentSet? = null,
    val filteredResults: DocumentSet? = null,
    val source: String? = null,
    val error: String? = null,
    val message: String? = null
)

/**
 * Represents a set of documents with count (used in comparison mode)
 */
@Serializable
data class DocumentSet(
    val count: Int,
    val documents: List<SemanticSearchDocument>
)
