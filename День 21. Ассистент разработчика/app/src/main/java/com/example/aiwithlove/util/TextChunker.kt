package com.example.aiwithlove.util

/**
 * Utility for chunking text into smaller pieces with overlap
 * Used for local processing before sending to server
 */
object TextChunker {

    /**
     * Split text into chunks with overlap
     *
     * @param text Text to chunk
     * @param chunkSize Maximum size of each chunk in characters
     * @param chunkOverlap Number of overlapping characters between chunks
     * @return List of text chunks
     */
    fun chunkText(
        text: String,
        chunkSize: Int = 1000,
        chunkOverlap: Int = 200
    ): List<String> {
        if (text.isEmpty()) {
            return emptyList()
        }

        if (text.length <= chunkSize) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        var startIndex = 0

        while (startIndex < text.length) {
            // Calculate end index for this chunk
            val endIndex = minOf(startIndex + chunkSize, text.length)

            // Extract chunk
            val chunk = text.substring(startIndex, endIndex)
            chunks.add(chunk)

            // Move to next chunk with overlap
            // If we're at the end, break to avoid creating a tiny last chunk
            if (endIndex >= text.length) {
                break
            }

            // Move forward by (chunkSize - overlap)
            startIndex += (chunkSize - chunkOverlap)
        }

        return chunks
    }

    /**
     * Get metadata for chunks
     */
    data class ChunkMetadata(
        val chunkIndex: Int,
        val totalChunks: Int,
        val content: String
    )

    /**
     * Create chunks with metadata
     */
    fun chunkTextWithMetadata(
        text: String,
        chunkSize: Int = 1000,
        chunkOverlap: Int = 200
    ): List<ChunkMetadata> {
        val chunks = chunkText(text, chunkSize, chunkOverlap)
        val totalChunks = chunks.size

        return chunks.mapIndexed { index, content ->
            ChunkMetadata(
                chunkIndex = index,
                totalChunks = totalChunks,
                content = content
            )
        }
    }
}
