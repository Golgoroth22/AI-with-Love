package com.example.aiwithlove.di

import com.example.aiwithlove.ollama.OllamaClient
import com.example.aiwithlove.util.ServerConfig
import com.example.aiwithlove.viewmodel.ChatViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        OllamaClient(
            serverUrl = ServerConfig.OLLAMA_SERVER_URL,
            modelName = "llama2"
        )
    }

    viewModel {
        ChatViewModel(ollamaClient = get())
    }
}
