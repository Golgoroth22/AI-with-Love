package com.example.aiwithlove.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DocumentChunkDao {

    @Insert
    suspend fun insertChunk(chunk: DocumentChunkEntity): Long

    @Query("SELECT * FROM document_chunks ORDER BY createdAt DESC")
    suspend fun getAllChunks(): List<DocumentChunkEntity>

    @Query("SELECT COUNT(*) FROM document_chunks")
    suspend fun getChunksCount(): Int

    @Query("SELECT * FROM document_chunks WHERE sourceFile = :fileName ORDER BY chunkIndex ASC")
    suspend fun getChunksByFile(fileName: String): List<DocumentChunkEntity>

    @Query("DELETE FROM document_chunks WHERE sourceFile = :fileName")
    suspend fun deleteChunksByFile(fileName: String)

    @Query("DELETE FROM document_chunks")
    suspend fun deleteAllChunks()

    @Query("SELECT DISTINCT sourceFile FROM document_chunks")
    suspend fun getAllFileNames(): List<String>

    /**
     * Get all chunks for similarity search
     * Note: Actual similarity calculation must be done in Kotlin code
     * since SQLite doesn't support vector operations
     */
    @Query("SELECT * FROM document_chunks LIMIT :limit")
    suspend fun getChunksForSearch(limit: Int = 1000): List<DocumentChunkEntity>
}
