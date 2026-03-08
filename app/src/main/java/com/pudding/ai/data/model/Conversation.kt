package com.pudding.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 对话实体
 *
 * @property id 对话ID
 * @property title 对话标题
 * @property createdAt 创建时间
 * @property updatedAt 更新时间
 */
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)