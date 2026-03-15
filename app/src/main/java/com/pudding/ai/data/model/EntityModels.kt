package com.pudding.ai.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 实体类型定义（用户可自定义新增）
 * 只预设"任务"类型，其他完全由用户定义
 */
@Entity(tableName = "entity_types")
data class EntityType(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                    // 类型名称，如 "任务"、"人物"、"公司"
    val description: String = "",        // 类型描述
    val icon: String? = null,           // 图标标识（可选）
    val extractionPrompt: String,       // AI 提取该类型实体时的提示词
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 实体实例
 */
@Entity(
    tableName = "entities",
    foreignKeys = [
        ForeignKey(
            entity = EntityType::class,
            parentColumns = ["id"],
            childColumns = ["typeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("typeId")]
)
data class TrackedEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val typeId: Long,                    // 关联的实体类型 ID
    val name: String,                    // 实体名称，如 "小王"
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 实体属性（支持历史追踪）
 * 每次属性变化都创建新记录，形成完整历史
 *
 * 示例：
 * 小王的职位变化历史：
 * - {key: "职位", value: "开发", sourceMessageId: 1}
 * - {key: "职位", value: "开发经理", sourceMessageId: 5, supersedes: 1}
 * - {key: "职位", value: "开发总监", sourceMessageId: 8, supersedes: 2}
 *
 * 小王和我的关系变化：
 * - {key: "关系", value: "同事", sourceMessageId: 1}
 * - {key: "关系", value: "前同事", sourceMessageId: 10, supersedes: 3}  // 我离职了
 * - {key: "关系", value: "同事", sourceMessageId: 15, supersedes: 4}    // 我加入了小王的公司
 */
@Entity(
    tableName = "entity_attributes",
    foreignKeys = [
        ForeignKey(
            entity = TrackedEntity::class,
            parentColumns = ["id"],
            childColumns = ["entityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entityId")]
)
data class EntityAttribute(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityId: Long,                  // 关联的实体 ID
    val key: String,                     // 属性名，如 "职位"、"关系"、"公司"
    val value: String,                   // 属性值，如 "开发经理"、"同事"
    val sourceMessageId: Long? = null,   // 这次变化来自哪个消息
    val supersedesId: Long? = null,      // 取代了哪条旧属性记录（用于追溯历史）
    val isCurrent: Boolean = true,       // 是否为当前有效值（便于查询）
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 实体提取队列任务
 * 用于批量处理实体提取，避免每条消息都调用 API
 */
@Entity(tableName = "entity_extraction_queue")
data class ExtractionTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val fromMessageId: Long,            // 起始消息 ID
    val toMessageId: Long,              // 结束消息 ID
    val status: ExtractionStatus,       // PENDING, PROCESSING, COMPLETED
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 提取任务状态
 */
enum class ExtractionStatus {
    PENDING,    // 待处理
    PROCESSING, // 处理中
    COMPLETED   // 已完成
}