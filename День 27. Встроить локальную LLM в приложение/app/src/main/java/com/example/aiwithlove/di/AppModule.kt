package com.example.aiwithlove.di

import com.example.aiwithlove.data.repository.GGUFModelRepository
import com.example.aiwithlove.llm.LLMClient
import com.example.aiwithlove.llm.LlamaCppClient
import com.example.aiwithlove.util.ModelCatalog
import com.example.aiwithlove.viewmodel.ChatViewModel
import com.example.aiwithlove.viewmodel.GGUFModelViewModel
import io.ktor.client.*
import io.ktor.client.engine.android.*
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // HTTP client for model downloads
    single {
        HttpClient(Android) {
            expectSuccess = true
        }
    }

    // GGUF model repository for downloads
    single { GGUFModelRepository(androidContext(), get()) }

    single<LLMClient> {
        android.util.Log.d("AppModule", "Creating LlamaCpp LLMClient")

        val modelsDir = java.io.File(androidContext().filesDir, "models")

        // Find the first downloaded GGUF model
        val downloadedModel = if (modelsDir.exists()) {
            modelsDir.listFiles()?.firstOrNull { it.extension == "gguf" }
        } else {
            null
        }

        val modelPath = if (downloadedModel != null) {
            android.util.Log.d("AppModule", "Found downloaded model: ${downloadedModel.name}")
            downloadedModel.absolutePath
        } else {
            // Fall back to default model path (will show error if not downloaded)
            val defaultPath = "${androidContext().filesDir}/models/${ModelCatalog.DEFAULT_MODEL.filename}"
            android.util.Log.d("AppModule", "No model found, using default path: $defaultPath")
            defaultPath
        }

        android.util.Log.d("AppModule", "Model path: $modelPath")

        LlamaCppClient(
            context = androidContext(),
            modelPath = modelPath,
            contextSize = 2048
        )
    }

    viewModel { ChatViewModel(llmClient = get()) }
    viewModel { GGUFModelViewModel(repository = get()) }
}

