package com.example.aiwithlove.database

import com.example.aiwithlove.data.model.Message
import com.example.aiwithlove.util.ILoggable

class ChatRepository(
    private val chatMessageDao: ChatMessageDao
) : ILoggable {

    suspend fun getAllMessages(): List<Message> {
        val userMessages = chatMessageDao.getAllUserMessages()
        val assistantMessages = chatMessageDao.getAllAssistantMessages()

        val allMessages =
            (userMessages.map { it.toMessage() } + assistantMessages.map { it.toMessage() })
                .sortedBy { it.timestamp }

        logD("Загружено ${allMessages.size} сообщений из БД (${userMessages.size} пользовательских, ${assistantMessages.size} ассистента)")
        return allMessages
    }

    suspend fun saveUserMessage(message: Message) {
        val entity =
            UserMessageEntity(
                text = message.text,
                timestamp = message.timestamp
            )
        chatMessageDao.insertUserMessage(entity)
        logD("Пользовательское сообщение сохранено в БД: ${entity.text.take(50)}")
    }

    suspend fun saveAssistantMessage(message: Message) {
        val entity =
            AssistantMessageEntity(
                text = message.text,
                requestTokens = message.promptTokens,
                responseTokens = message.completionTokens,
                timestamp = message.timestamp
            )
        chatMessageDao.insertAssistantMessage(entity)
        logD("Сообщение ассистента сохранено в БД: ${entity.text.take(50)}")
    }

    suspend fun clearAllMessages() {
        chatMessageDao.deleteAllUserMessages()
        chatMessageDao.deleteAllAssistantMessages()
        logD("Все сообщения удалены из БД")
    }

    suspend fun getSummary(): Message? {
        val summaryEntity = chatMessageDao.getChatSummary()
        return summaryEntity?.let {
            logD("Загружена сводка диалога из БД")
            Message(
                text = it.summaryText,
                isFromUser = false,
                timestamp = it.timestamp,
                isSystemMessage = true,
                isSummary = true
            )
        }
    }

    suspend fun saveSummary(
        summaryText: String,
        messageCount: Int
    ) {
        val entity =
            ChatSummaryEntity(
                summaryText = summaryText,
                timestamp = System.currentTimeMillis(),
                messageCountAtCompression = messageCount
            )
        chatMessageDao.insertChatSummary(entity)
        logD("Сводка диалога сохранена в БД")
    }

    suspend fun clearSummary() {
        chatMessageDao.deleteChatSummary()
        logD("Сводка диалога удалена из БД")
    }

    private fun UserMessageEntity.toMessage(): Message =
        Message(
            text = text,
            isFromUser = true,
            timestamp = timestamp
        )

    private fun AssistantMessageEntity.toMessage(): Message =
        Message(
            text = text,
            isFromUser = false,
            timestamp = timestamp,
            promptTokens = requestTokens,
            completionTokens = responseTokens
        )
}
