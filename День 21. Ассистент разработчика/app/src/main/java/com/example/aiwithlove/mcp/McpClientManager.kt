package com.example.aiwithlove.mcp

import com.example.aiwithlove.util.ILoggable

class McpClientManager(
    private val serverConfigs: List<McpServerConfig>
) : ILoggable {

    private val clients = mutableMapOf<String, McpClient>()

    init {
        serverConfigs.forEach { config ->
            clients[config.id] =
                McpClient(
                    serverUrl = config.url,
                    serverId = config.id,
                    requiresAuth = false // No auth needed - token configured server-side
                )
        }
    }

    /**
     * Route tool call to appropriate server based on tool name
     */
    suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any>,
        enabledServers: List<String>
    ): String {
        // Find which server provides this tool (only check runtime enabledServers, not static config.isEnabled)
        val serverConfig =
            serverConfigs.find { config ->
                enabledServers.contains(config.id) &&
                    config.tools.any { it.name == toolName }
            } ?: throw Exception("No enabled server found for tool: $toolName")

        val client =
            clients[serverConfig.id]
                ?: throw Exception("Client not found for server: ${serverConfig.id}")

        logD("🔧 Routing $toolName to ${serverConfig.name} (${serverConfig.id})")

        return client.callTool(toolName, arguments)
    }
}
