package com.example.aiwithlove.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM user_messages ORDER BY timestamp ASC")
    suspend fun getAllUserMessages(): List<UserMessageEntity>

    @Query("SELECT * FROM assistant_messages ORDER BY timestamp ASC")
    suspend fun getAllAssistantMessages(): List<AssistantMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserMessage(message: UserMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssistantMessage(message: AssistantMessageEntity)

    @Query("DELETE FROM user_messages")
    suspend fun deleteAllUserMessages()

    @Query("DELETE FROM assistant_messages")
    suspend fun deleteAllAssistantMessages()

    @Query("SELECT * FROM chat_summary WHERE id = 1 LIMIT 1")
    suspend fun getChatSummary(): ChatSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatSummary(summary: ChatSummaryEntity)

    @Query("DELETE FROM chat_summary")
    suspend fun deleteChatSummary()
}
