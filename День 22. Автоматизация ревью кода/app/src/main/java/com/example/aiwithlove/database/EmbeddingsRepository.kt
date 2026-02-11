package com.example.aiwithlove.database

import com.example.aiwithlove.util.ILoggable
import kotlin.math.sqrt

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

/**
 * Repository for managing document chunks with embeddings
 */
class EmbeddingsRepository(
    private val dao: DocumentChunkDao
) : ILoggable {

    /**
     * Save a document chunk with embedding
     */
    suspend fun saveChunk(
        content: String,
        embedding: List<Double>,
        sourceFile: String,
        sourceType: String,
        chunkIndex: Int,
        totalChunks: Int
    ): Long {
        val chunk =
            DocumentChunkEntity(
                content = content,
                embedding = embedding,
                sourceFile = sourceFile,
                sourceType = sourceType,
                chunkIndex = chunkIndex,
                totalChunks = totalChunks
            )

        return dao.insertChunk(chunk)
    }

    /**
     * Get all chunks
     */
    suspend fun getAllChunks(): List<DocumentChunkEntity> = dao.getAllChunks()

    /**
     * Get total chunks count
     */
    suspend fun getChunksCount(): Int = dao.getChunksCount()

    /**
     * Get chunks by file name
     */
    suspend fun getChunksByFile(fileName: String): List<DocumentChunkEntity> = dao.getChunksByFile(fileName)

    /**
     * Delete all chunks for a specific file
     */
    suspend fun deleteChunksByFile(fileName: String) {
        dao.deleteChunksByFile(fileName)
    }

    /**
     * Delete all chunks
     */
    suspend fun deleteAllChunks() {
        dao.deleteAllChunks()
    }

    /**
     * Get all indexed file names
     */
    suspend fun getAllFileNames(): List<String> = dao.getAllFileNames()

    /**
     * Search for similar chunks using cosine similarity
     */
    suspend fun searchSimilar(
        queryEmbedding: List<Double>,
        limit: Int = 5,
        threshold: Double = 0.6
    ): List<SimilarChunk> {
        // Get all chunks (in production, consider pagination or indexing)
        val allChunks = dao.getChunksForSearch(limit = 10000)

        logD("🔍 Searching through ${allChunks.size} chunks with threshold $threshold")

        // Calculate similarity for each chunk
        val results =
            allChunks.map { chunk ->
                val similarity = cosineSimilarity(queryEmbedding, chunk.embedding)

                SimilarChunk(
                    chunk = chunk,
                    similarity = similarity
                )
            }

        // Log top 5 results for debugging (even below threshold)
        val top5 = results.sortedByDescending { it.similarity }.take(5)
        logD("📊 Top 5 similarity scores:")
        top5.forEachIndexed { index, result ->
            logD("  ${index + 1}. ${result.similarity.format(4)} - ${result.chunk.sourceFile} (chunk ${result.chunk.chunkIndex})")
        }

        // Filter by threshold and sort by similarity
        val filtered =
            results
                .filter { it.similarity >= threshold }
                .sortedByDescending { it.similarity }
                .take(limit)

        logD("✅ Found ${filtered.size} chunks above threshold $threshold")

        return filtered
    }

    /**
     * Calculate cosine similarity between two vectors
     */
    private fun cosineSimilarity(
        vec1: List<Double>,
        vec2: List<Double>
    ): Double {
        if (vec1.size != vec2.size) {
            logE("Vector size mismatch: ${vec1.size} vs ${vec2.size}")
            return 0.0
        }

        var dotProduct = 0.0
        var magnitude1 = 0.0
        var magnitude2 = 0.0

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            magnitude1 += vec1[i] * vec1[i]
            magnitude2 += vec2[i] * vec2[i]
        }

        magnitude1 = sqrt(magnitude1)
        magnitude2 = sqrt(magnitude2)

        return if (magnitude1 > 0 && magnitude2 > 0) {
            dotProduct / (magnitude1 * magnitude2)
        } else {
            0.0
        }
    }
}

/**
 * Data class for search results
 */
data class SimilarChunk(
    val chunk: DocumentChunkEntity,
    val similarity: Double
)
