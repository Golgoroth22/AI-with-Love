package com.example.aiwithlove.di

import com.example.aiwithlove.mcp.McpClient
import com.example.aiwithlove.util.ServerConfig
import com.example.aiwithlove.viewmodel.ChatViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        McpClient(
            serverUrl = ServerConfig.MCP_SERVER_URL,
            serverId = "webpage_creator",
            requiresAuth = false
        )
    }

    viewModel {
        ChatViewModel(mcpClient = get())
    }
}
