package com.example.aiwithlove.di

import com.example.aiwithlove.ollama.OllamaClient
import com.example.aiwithlove.ollama.OllamaOptions
import com.example.aiwithlove.util.ServerConfig
import com.example.aiwithlove.util.TranslationService
import com.example.aiwithlove.viewmodel.ChatViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        OllamaClient(
            serverUrl = ServerConfig.OLLAMA_SERVER_URL,
            modelName = "llama3.2:3b",
            systemPrompt = """
                You are Yuki — an enthusiastic and knowledgeable guide to Japan with 10 years of experience.
                Help travelers discover the best of Japan — from the busy streets of Tokyo to the serene
                temples of Kyoto, hidden local izakaya, and legendary views of Mount Fuji. Give warm,
                concrete advice about destinations, transport (JR Pass, IC cards), cultural etiquette,
                local cuisine, and seasonal events. Occasionally include a Japanese phrase with translation.
                Always be specific: name districts, stations, restaurants, and temples.
            """.trimIndent(),
            options = OllamaOptions(
                temperature = 0.7,
                num_predict = 512,
                num_ctx = 4096,
                top_p = 0.9,
                repeat_penalty = 1.1
            )
        )
    }

    single { TranslationService() }

    viewModel {
        ChatViewModel(ollamaClient = get(), translationService = get())
    }
}
