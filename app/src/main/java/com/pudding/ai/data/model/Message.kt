package com.pudding.ai.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息类型枚举
 */
enum class MessageType {
    NORMAL,       // 普通消息
    CHECKPOINT    // 检查点消息（包含之前对话的摘要）
}

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
 * @property type 消息类型（普通消息或检查点）
 * @property promptTokens 输入 token 数（来自 API 响应）
 * @property completionTokens 输出 token 数（来自 API 响应）
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["timestamp"]),
        Index(value = ["type"])
    ]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.NORMAL,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
)