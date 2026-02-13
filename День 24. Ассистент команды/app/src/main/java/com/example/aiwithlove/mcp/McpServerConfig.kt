package com.example.aiwithlove.mcp

import com.example.aiwithlove.util.ServerConfig

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
                id = "rag",
                name = "RAG Server",
                url = ServerConfig.MCP_SERVER_URL,
                description = "MCP сервер для семантического поиска в документах с использованием RAG (Retrieval-Augmented Generation).",
                tools =
                    listOf(
                        McpToolInfo(
                            name = "semantic_search",
                            emoji = "🌐",
                            description =
                                "Семантический поиск релевантных фрагментов в проиндексированных документах. " +
                                    "Документы обрабатываются локально через Ollama, векторные представления хранятся на сервере. " +
                                    "Используется для поиска контекста для ответа на вопросы.",
                            triggerWords =
                                listOf(
                                    "найди в документах",
                                    "поиск в базе",
                                    "что говорится в документах",
                                    "информация о",
                                    "расскажи о",
                                    "что такое",
                                    "как работает",
                                    "объясни"
                                )
                        )
                    )
            ),
            McpServerConfig(
                id = "github",
                name = "GitHub Assistant",
                url = if (ServerConfig.GITHUB_MCP_USE_LOCAL) ServerConfig.GITHUB_MCP_LOCAL_URL else ServerConfig.GITHUB_MCP_SERVER_URL,
                description = "GitHub операции: репозитории, issues, PRs, коммиты, поиск кода, code review",
                tools =
                    listOf(
                        McpToolInfo(
                            name = "get_repo",
                            emoji = "📦",
                            description = "Получить информацию о репозитории",
                            triggerWords = listOf("GitWithLove")
                        ),
                        McpToolInfo(
                            name = "search_code",
                            emoji = "🔍",
                            description = "Поиск кода в репозиториях",
                            triggerWords = listOf("GitWithLove")
                        ),
                        McpToolInfo(
                            name = "create_issue",
                            emoji = "🐛",
                            description = "Создать issue в GitHub",
                            triggerWords = listOf("GitWithLove")
                        ),
                        McpToolInfo(
                            name = "list_issues",
                            emoji = "📋",
                            description = "Список issues",
                            triggerWords = listOf("GitWithLove")
                        ),
                        McpToolInfo(
                            name = "list_commits",
                            emoji = "📝",
                            description = "История коммитов",
                            triggerWords = listOf("GitWithLove")
                        ),
                        McpToolInfo(
                            name = "get_repo_content",
                            emoji = "📄",
                            description = "Получить содержимое файла из репозитория",
                            triggerWords = listOf("GitWithLove")
                        ),
                        McpToolInfo(
                            name = "get_pull_request",
                            emoji = "🔍",
                            description = "Получить информацию о pull request (для code review)",
                            triggerWords = listOf("ReviewPR", "код-ревью", "code review")
                        ),
                        McpToolInfo(
                            name = "get_pr_files",
                            emoji = "📋",
                            description = "Получить список изменённых файлов в PR с diff",
                            triggerWords = listOf("ReviewPR", "код-ревью", "code review")
                        )
                    ),
                isEnabled = false
            ),
            McpServerConfig(
                id = "local_git",
                name = "Local Git",
                url = ServerConfig.LOCAL_GIT_SERVER_URL,
                description = "Локальные git операции: статус, ветки, diff, PR статус",
                tools =
                    listOf(
                        McpToolInfo(
                            name = "git_status",
                            emoji = "📊",
                            description = "Git статус: измененные файлы, текущая ветка",
                            triggerWords = listOf("GitLocal", "git status", "статус репозитория")
                        ),
                        McpToolInfo(
                            name = "git_branch",
                            emoji = "🌿",
                            description = "Список веток",
                            triggerWords = listOf("GitLocal", "ветки", "branches")
                        ),
                        McpToolInfo(
                            name = "git_diff",
                            emoji = "📝",
                            description = "Изменения в файлах (diff)",
                            triggerWords = listOf("GitLocal", "diff", "изменения")
                        ),
                        McpToolInfo(
                            name = "git_pr_status",
                            emoji = "🔀",
                            description = "Статус pull request",
                            triggerWords = listOf("GitLocal", "pr status", "статус pr")
                        )
                    ),
                isEnabled = false
            ),
            McpServerConfig(
                id = "support",
                name = "Support Assistant",
                url = ServerConfig.MCP_SERVER_URL,
                description = "Управление тикетами поддержки: просмотр, создание, обновление статусов, поиск решений в FAQ",
                tools =
                    listOf(
                        McpToolInfo(
                            name = "get_ticket",
                            emoji = "🎫",
                            description = "Получить информацию о тикете по ID",
                            triggerWords = listOf("Support", "тикет", "ticket", "обращение")
                        ),
                        McpToolInfo(
                            name = "list_user_tickets",
                            emoji = "📋",
                            description = "Список тикетов пользователя",
                            triggerWords = listOf("Support", "тикеты пользователя", "обращения клиента")
                        ),
                        McpToolInfo(
                            name = "create_ticket",
                            emoji = "➕",
                            description = "Создать новый тикет",
                            triggerWords = listOf("Support", "создать тикет", "новое обращение")
                        ),
                        McpToolInfo(
                            name = "update_ticket",
                            emoji = "✏️",
                            description = "Обновить статус тикета или добавить комментарий",
                            triggerWords = listOf("Support", "обновить тикет", "изменить статус")
                        )
                    ),
                isEnabled = false
            )
        )
}
