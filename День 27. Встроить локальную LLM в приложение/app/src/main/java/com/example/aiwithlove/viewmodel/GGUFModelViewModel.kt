package com.example.aiwithlove.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwithlove.data.repository.DownloadProgress
import com.example.aiwithlove.data.repository.GGUFModelRepository
import com.example.aiwithlove.util.GGUFModel
import com.example.aiwithlove.util.ModelCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for GGUF model selection and download
 */
class GGUFModelViewModel(
    private val repository: GGUFModelRepository
) : ViewModel() {

    private val _selectedModel = MutableStateFlow<GGUFModel>(ModelCatalog.DEFAULT_MODEL)
    val selectedModel: StateFlow<GGUFModel> = _selectedModel.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    init {
        checkForDownloadedModels()
    }

    /**
     * Check if ANY model is already downloaded
     * If yes, set it as selected and mark as ready
     */
    private fun checkForDownloadedModels() {
        viewModelScope.launch {
            // Get all downloaded models
            val downloadedFiles = repository.getDownloadedModels()

            if (downloadedFiles.isNotEmpty()) {
                // Find which model catalog entry matches the downloaded file
                val downloadedFile = downloadedFiles.first()
                val matchingModel = ModelCatalog.RECOMMENDED_MODELS.find { model ->
                    downloadedFile.name == model.filename
                }

                if (matchingModel != null) {
                    _selectedModel.value = matchingModel
                    _isModelReady.value = true
                    _downloadProgress.value = DownloadProgress.Completed(downloadedFile)
                    android.util.Log.d("GGUFModelViewModel", "Found downloaded model: ${matchingModel.name}")
                } else {
                    // Unknown GGUF file, but still mark as ready
                    _isModelReady.value = true
                    _downloadProgress.value = DownloadProgress.Completed(downloadedFile)
                    android.util.Log.d("GGUFModelViewModel", "Found unknown GGUF model: ${downloadedFile.name}")
                }
            } else {
                // No models downloaded
                _isModelReady.value = false
                android.util.Log.d("GGUFModelViewModel", "No models downloaded")
            }
        }
    }

    /**
     * Check if the selected model is already downloaded
     */
    private fun checkModelStatus() {
        viewModelScope.launch {
            val isDownloaded = repository.isModelDownloaded(_selectedModel.value)
            _isModelReady.value = isDownloaded

            if (isDownloaded) {
                val file = repository.getModelFile(_selectedModel.value)
                _downloadProgress.value = DownloadProgress.Completed(file!!)
            }
        }
    }

    /**
     * Select a model from the catalog
     */
    fun selectModel(model: GGUFModel) {
        _selectedModel.value = model
        checkModelStatus()
    }

    /**
     * Start downloading the selected model
     */
    fun startDownload() {
        viewModelScope.launch {
            repository.downloadModel(_selectedModel.value).collect { progress ->
                _downloadProgress.value = progress

                // Update ready status when download completes
                if (progress is DownloadProgress.Completed) {
                    _isModelReady.value = true
                }
            }
        }
    }

    /**
     * Delete the current model
     */
    fun deleteModel() {
        viewModelScope.launch {
            repository.deleteModel(_selectedModel.value)
            _isModelReady.value = false
            _downloadProgress.value = DownloadProgress.Idle
        }
    }

    /**
     * Get all available models
     */
    fun getAvailableModels(): List<GGUFModel> {
        return ModelCatalog.RECOMMENDED_MODELS
    }
}
