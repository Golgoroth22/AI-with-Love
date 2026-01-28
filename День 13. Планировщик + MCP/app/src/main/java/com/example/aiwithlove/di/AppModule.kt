package com.example.aiwithlove.di

import com.example.aiwithlove.data.PerplexityApiService
import com.example.aiwithlove.data.PerplexityApiServiceImpl
import com.example.aiwithlove.database.AppDatabase
import com.example.aiwithlove.database.ChatMessageDao
import com.example.aiwithlove.database.ChatRepository
import com.example.aiwithlove.mcp.McpClient
import com.example.aiwithlove.util.SecureData
import com.example.aiwithlove.viewmodel.ChatViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule =
    module {
        single<PerplexityApiService?> {
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

        single {
            McpClient("http://10.0.2.2:8080")
        }

        viewModel { ChatViewModel(get(), get(), get()) }
    }
