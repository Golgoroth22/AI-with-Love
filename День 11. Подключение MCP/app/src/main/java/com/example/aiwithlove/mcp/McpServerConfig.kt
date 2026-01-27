package com.example.aiwithlove.mcp

data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val description: String,
    val benefits: String,
    val tools: List<String>,
    val isEnabled: Boolean = false
)

object McpServers {
    val availableServers =
        listOf(
            McpServerConfig(
                id = "local",
                name = "Local Server",
                url = "http://10.0.2.2:8080",
                description = "Локальный сервер с системными инструментами",
                benefits =
                    "При включении чат получит доступ к системной информации, " +
                        "вычислениям и утилитам вашего компьютера",
                tools =
                    listOf(
                        "📊 Информация о системе (ОС, CPU, память)",
                        "🕐 Текущее время (различные форматы)",
                        "📁 Список файлов в директориях",
                        "🧮 Калькулятор математических выражений",
                        "🌤️ Демо данные о погоде"
                    )
            )
        )
}
