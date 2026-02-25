package com.example.aiwithlove.data.repository

import android.content.Context
import android.util.Log
import com.example.aiwithlove.util.GGUFModel
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Download progress state
 */
sealed class DownloadProgress {
    data object Idle : DownloadProgress()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long, val percent: Int) : DownloadProgress()
    data class Completed(val file: File) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}

/**
 * Repository for managing GGUF model downloads
 */
class GGUFModelRepository(private val context: Context, private val httpClient: HttpClient) {

    private val modelsDir: File by lazy {
        File(context.filesDir, "models").apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "Created models directory: $absolutePath")
            }
        }
    }

    /**
     * Download a GGUF model with progress tracking
     */
    fun downloadModel(model: GGUFModel): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Idle)

        try {
            val targetFile = File(modelsDir, model.filename)

            // Check if file already exists and is complete
            if (targetFile.exists() && targetFile.length() == model.sizeBytes) {
                Log.d(TAG, "Model already downloaded: ${targetFile.absolutePath}")
                emit(DownloadProgress.Completed(targetFile))
                return@flow
            }

            // Delete partial file if exists
            if (targetFile.exists()) {
                targetFile.delete()
                Log.d(TAG, "Deleted partial file: ${targetFile.absolutePath}")
            }

            Log.d(TAG, "Starting download: ${model.name} from ${model.downloadUrl}")

            // Download with progress
            httpClient.prepareGet(model.downloadUrl) {
                timeout {
                    requestTimeoutMillis = null // No timeout for large files
                    connectTimeoutMillis = 60_000
                    socketTimeoutMillis = 60_000
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw Exception("HTTP ${response.status.value}: ${response.status.description}")
                }

                val contentLength = response.contentLength() ?: model.sizeBytes
                var bytesDownloaded = 0L

                // Read and write in chunks
                targetFile.outputStream().use { outputStream ->
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    val buffer = ByteArray(8192) // 8KB chunks

                    while (!channel.isClosedForRead) {
                        val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                        if (bytesRead <= 0) break

                        outputStream.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        // Emit progress every 100KB
                        if (bytesDownloaded % (100 * 1024) < buffer.size) {
                            val percent = ((bytesDownloaded * 100) / contentLength).toInt()
                            emit(DownloadProgress.Downloading(bytesDownloaded, contentLength, percent))
                        }
                    }
                }

                // Emit final progress
                val percent = ((bytesDownloaded * 100) / contentLength).toInt()
                emit(DownloadProgress.Downloading(bytesDownloaded, contentLength, percent))

                Log.d(TAG, "Download completed: ${targetFile.absolutePath} (${bytesDownloaded} bytes)")

                // Verify file size
                if (targetFile.length() != contentLength) {
                    throw Exception("Download incomplete: expected $contentLength bytes, got ${targetFile.length()}")
                }

                emit(DownloadProgress.Completed(targetFile))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            emit(DownloadProgress.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Check if model is downloaded
     */
    suspend fun isModelDownloaded(model: GGUFModel): Boolean = withContext(Dispatchers.IO) {
        val file = File(modelsDir, model.filename)
        file.exists() && file.length() == model.sizeBytes
    }

    /**
     * Get model file if it exists
     */
    suspend fun getModelFile(model: GGUFModel): File? = withContext(Dispatchers.IO) {
        val file = File(modelsDir, model.filename)
        if (file.exists()) file else null
    }

    /**
     * Get absolute path to model file
     */
    suspend fun getModelPath(model: GGUFModel): String? {
        return getModelFile(model)?.absolutePath
    }

    /**
     * Delete a downloaded model
     */
    suspend fun deleteModel(model: GGUFModel): Boolean = withContext(Dispatchers.IO) {
        val file = File(modelsDir, model.filename)
        if (file.exists()) {
            val deleted = file.delete()
            Log.d(TAG, "Deleted model: ${model.name}, success: $deleted")
            deleted
        } else {
            false
        }
    }

    /**
     * Get all downloaded model files
     */
    suspend fun getDownloadedModels(): List<File> = withContext(Dispatchers.IO) {
        modelsDir.listFiles()?.filter { it.extension == "gguf" } ?: emptyList()
    }

    companion object {
        private const val TAG = "GGUFModelRepository"
    }
}
