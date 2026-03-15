package com.pudding.ai.data.database

import androidx.room.*
import com.pudding.ai.data.model.Message
import com.pudding.ai.data.model.MessageType
import kotlinx.coroutines.flow.Flow

/**
 * 消息数据访问对象
 *
 * 提供消息的增删改查操作，包括检查点消息的管理。
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
     * 获取指定对话的最新检查点消息
     *
     * @param conversationId 对话ID
     * @return 最新的检查点消息，如果没有则返回 null
     */
    @Query("""
        SELECT * FROM messages
        WHERE conversationId = :conversationId AND type = 'CHECKPOINT'
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLatestCheckpoint(conversationId: Long): Message?

    /**
     * 获取检查点之后的所有消息
     *
     * @param conversationId 对话ID
     * @param checkpointTimestamp 检查点时间戳
     * @return 检查点之后的消息列表
     */
    @Query("""
        SELECT * FROM messages
        WHERE conversationId = :conversationId AND timestamp > :checkpointTimestamp
        ORDER BY timestamp ASC
    """)
    fun getMessagesAfterCheckpoint(conversationId: Long, checkpointTimestamp: Long): Flow<List<Message>>

    /**
     * 获取指定对话的普通消息（不含检查点）
     *
     * @param conversationId 对话ID
     * @return 普通消息列表 Flow
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND type = 'NORMAL' ORDER BY timestamp ASC")
    fun getNormalMessagesByConversation(conversationId: Long): Flow<List<Message>>

    /**
     * 获取指定对话的消息数量
     *
     * @param conversationId 对话ID
     * @return 消息数量
     */
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: Long): Int

    /**
     * 获取指定对话的普通消息数量
     *
     * @param conversationId 对话ID
     * @return 普通消息数量
     */
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND type = 'NORMAL'")
    suspend fun getNormalMessageCount(conversationId: Long): Int

    /**
     * 插入消息
     *
     * @param message 消息实体
     * @return 插入的消息ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    /**
     * 更新消息
     *
     * @param message 消息实体
     */
    @Update
    suspend fun updateMessage(message: Message)

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

    /**
     * 删除指定对话的所有检查点消息
     *
     * @param conversationId 对话ID
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND type = 'CHECKPOINT'")
    suspend fun deleteCheckpointsByConversation(conversationId: Long)

    /**
     * 获取指定时间范围内的所有消息（所有对话）
     *
     * @param startTimestamp 开始时间戳（包含）
     * @param endTimestamp 结束时间戳（不包含）
     * @return 消息列表，按时间戳升序排列
     */
    @Query("""
        SELECT * FROM messages
        WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp
        AND type = 'NORMAL'
        ORDER BY timestamp ASC
    """)
    suspend fun getMessagesByTimeRange(startTimestamp: Long, endTimestamp: Long): List<Message>

    /**
     * 获取指定时间范围内的消息数量
     *
     * @param startTimestamp 开始时间戳（包含）
     * @param endTimestamp 结束时间戳（不包含）
     * @return 消息数量
     */
    @Query("""
        SELECT COUNT(*) FROM messages
        WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp
        AND type = 'NORMAL'
    """)
    suspend fun getMessageCountByTimeRange(startTimestamp: Long, endTimestamp: Long): Int
}