package com.pudding.ai.data.database

import androidx.room.*
import com.pudding.ai.data.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * 消息数据访问对象
 *
 * 提供消息的增删改查操作。
 */
@Dao
interface MessageDao {
    /**
     * 获取指定对话的所有消息
     *
     * @param conversationId 对话ID
     * @return 消息列表 Flow，按时间戳升序排列
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesByConversation(conversationId: Long): Flow<List<Message>>

    /**
     * 插入消息
     *
     * @param message 消息实体
     * @return 插入的消息ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    /**
     * 删除消息
     *
     * @param message 消息实体
     */
    @Delete
    suspend fun deleteMessage(message: Message)

    /**
     * 删除指定对话的所有消息
     *
     * @param conversationId 对话ID
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: Long)
}