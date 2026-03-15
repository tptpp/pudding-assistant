package com.pudding.ai.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每日记忆实体
 *
 * 存储每天对话的整理摘要，以 Markdown 格式保存。
 * 每天凌晨自动生成前一天的记忆文档。
 *
 * @property id 主键
 * @property date 日期 "2024-03-08"
 * @property title 标题
 * @property content MD 格式内容
 * @property summary 简短摘要
 * @property entityCount 包含的实体数量
 * @property createdAt 创建时间戳
 */
@Entity(
    tableName = "daily_memories",
    indices = [Index(value = ["date"], unique = true)]
)
data class DailyMemory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,                 // 日期 "2024-03-08"
    val title: String,                // 标题
    val content: String,              // MD 格式内容
    val summary: String,              // 简短摘要
    val entityCount: Int,             // 包含的实体数量
    val createdAt: Long = System.currentTimeMillis()
)