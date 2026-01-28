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
                id = "jokes",
                name = "JokeAPI Server",
                url = "http://10.0.2.2:8080",
                description = "MCP сервер для получения шуток через JokeAPI. Используется как инструмент (tool) в Perplexity Agentic API.",
                benefits =
                    "Напишите 'jokeapi' или 'джокапи' в сообщении, чтобы получить шутку из внешнего API. " +
                        "Шутка будет автоматически переведена на русский язык. " +
                        "Под ответом вы увидите отладочную информацию о MCP запросе.",
                tools =
                    listOf(
                        "🎭 get_joke — получение случайной шутки",
                        "📂 Категории: Any, Programming, Misc, Dark, Pun, Spooky, Christmas",
                        "🛡️ Фильтрация нежелательного контента",
                        "🌐 Источник: https://jokeapi.dev"
                    )
            )
        )
}
