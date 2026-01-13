package com.example.aiwithlove.di

import com.example.aiwithlove.data.PerplexityApiService
import com.example.aiwithlove.data.PerplexityApiServiceImpl
import com.example.aiwithlove.ui.viewmodel.ChatViewModel
import com.example.aiwithlove.util.SecureData
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val appModule =
    module {
        single<PerplexityApiService?> {
            PerplexityApiServiceImpl(SecureData.apiKey)
        }
        viewModelOf(::ChatViewModel)
    }
