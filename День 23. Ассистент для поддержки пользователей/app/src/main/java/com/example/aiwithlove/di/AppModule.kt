package com.example.aiwithlove.di

import com.example.aiwithlove.data.PerplexityApiService
import com.example.aiwithlove.data.PerplexityApiServiceImpl
import com.example.aiwithlove.database.AppDatabase
import com.example.aiwithlove.database.ChatMessageDao
import com.example.aiwithlove.database.ChatRepository
import com.example.aiwithlove.database.DocumentChunkDao
import com.example.aiwithlove.database.EmbeddingsDatabase
import com.example.aiwithlove.database.EmbeddingsRepository
import com.example.aiwithlove.mcp.McpClientManager
import com.example.aiwithlove.mcp.McpServers
import com.example.aiwithlove.ollama.OllamaClient
import com.example.aiwithlove.util.SecureData
import com.example.aiwithlove.util.ServerConfig
import com.example.aiwithlove.viewmodel.ChatViewModel
import com.example.aiwithlove.viewmodel.OllamaViewModel
import com.example.aiwithlove.viewmodel.SupportViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule =
    module {
        single<PerplexityApiService> {
            PerplexityApiServiceImpl(SecureData.apiKey)
        }

        single<AppDatabase> {
            AppDatabase.getDatabase(androidContext())
        }

        single<ChatMessageDao> {
            get<AppDatabase>().chatMessageDao()
        }

        single<ChatRepository> {
            ChatRepository(get())
        }

        // Embeddings database for local document storage
        single<EmbeddingsDatabase> {
            EmbeddingsDatabase.getDatabase(androidContext())
        }

        single<DocumentChunkDao> {
            get<EmbeddingsDatabase>().documentChunkDao()
        }

        single<EmbeddingsRepository> {
            EmbeddingsRepository(get())
        }

        // Replace single McpClient with McpClientManager for multi-server support
        single {
            McpClientManager(
                serverConfigs = McpServers.availableServers
            )
        }

        single {
            OllamaClient(baseUrl = ServerConfig.OLLAMA_API_URL)
        }

        viewModel { ChatViewModel(get(), get(), get(), get(), get()) }
        viewModel { OllamaViewModel(get(), get()) }
        viewModel { SupportViewModel(get(), get(), get()) }
    }
