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
 * Citation information for a document chunk
 */
@Serializable
data class CitationInfo(
    val source_file: String,
    val source_type: String,
    val chunk_index: Int,
    val page_number: Int? = null,
    val total_chunks: Int,
    val formatted: String
)

/**
 * Represents a single document chunk returned from semantic search
 * Now includes citation fields (Day 19)
 */
@Serializable
data class SemanticSearchDocument(
    val id: Int,
    val content: String,
    val similarity: Double,
    val created_at: String? = null,
    // Citation fields (Day 19)
    val source_file: String? = null,
    val source_type: String? = null,
    val chunk_index: Int? = null,
    val page_number: Int? = null,
    val total_chunks: Int? = null,
    val metadata: String? = null,
    val citation: String? = null,
    val citation_info: CitationInfo? = null
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
    val sources_summary: List<String>? = null,  // Day 19: Citation summary
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
