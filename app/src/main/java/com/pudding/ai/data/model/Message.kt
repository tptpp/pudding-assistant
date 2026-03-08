package com.pudding.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 消息角色枚举
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * 对话消息实体
 *
 * @property id 消息ID
 * @property conversationId 所属对话ID
 * @property role 消息角色
 * @property content 消息内容
 * @property timestamp 时间戳
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)