package com.example.aiwithlove.mcp

data class McpToolInfo(
    val name: String,
    val emoji: String,
    val description: String,
    val triggerWords: List<String>
)

data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val description: String,
    val tools: List<McpToolInfo>,
    val isEnabled: Boolean = false
)

object McpServers {
    val availableServers =
        listOf(
            McpServerConfig(
                id = "jokes",
                name = "JokeAPI Server",
                url = "http://10.0.2.2:8080",
                description = "MCP сервер для работы с шутками: получение из JokeAPI, сохранение в базу данных и просмотр избранного.",
                tools =
                    listOf(
                        McpToolInfo(
                            name = "get_joke",
                            emoji = "🎭",
                            description = "Получение случайной шутки из JokeAPI. Поддерживает категории: Any, Programming, Misc, Dark, Pun, Spooky, Christmas.",
                            triggerWords = listOf("шутка", "анекдот", "jokeapi", "пошути", "рассмеши")
                        ),
                        McpToolInfo(
                            name = "save_joke",
                            emoji = "💾",
                            description = "Сохранение шутки (переведённой на русский) в локальную базу данных на сервере. Шутка будет доступна в избранном.",
                            triggerWords = listOf("сохрани шутку", "сохрани эту шутку", "запомни шутку", "добавь в избранное")
                        ),
                        McpToolInfo(
                            name = "get_saved_jokes",
                            emoji = "📖",
                            description = "Просмотр всех сохранённых шуток из базы данных. Показывает избранные шутки с датой сохранения.",
                            triggerWords = listOf("мои шутки", "избранные шутки", "сохранённые шутки", "покажи сохранённые")
                        )
                    )
            )
        )
}
