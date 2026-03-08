package com.pudding.ai.data.database

import androidx.room.*
import com.pudding.ai.data.model.Conversation
import kotlinx.coroutines.flow.Flow

/**
 * 对话数据访问对象
 *
 * 提供对话的增删改查操作。
 */
@Dao
interface ConversationDao {
    /**
     * 获取所有对话
     *
     * @return 对话列表 Flow，按更新时间降序排列
     */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    /**
     * 根据ID获取对话
     *
     * @param id 对话ID
     * @return 对话实体，不存在则返回 null
     */
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): Conversation?

    /**
     * 插入对话
     *
     * @param conversation 对话实体
     * @return 插入的对话ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation): Long

    /**
     * 更新对话
     *
     * @param conversation 对话实体
     */
    @Update
    suspend fun updateConversation(conversation: Conversation)

    /**
     * 删除对话
     *
     * @param conversation 对话实体
     */
    @Delete
    suspend fun deleteConversation(conversation: Conversation)

    /**
     * 根据ID删除对话
     *
     * @param id 对话ID
     */
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)
}