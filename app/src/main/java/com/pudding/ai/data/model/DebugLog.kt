package com.pudding.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 调试日志系统架构约束
 *
 * 为避免数据冗余，请遵循以下规则：
 *
 * 1. durationMs 字段规则：
 *    - DebugLog.durationMs 已记录执行耗时
 *    - 各 Meta 类（ToolCallMeta, TaskExecutionMeta 等）不应再包含耗时字段
 *    - ❌ 错误示例：ToolCallMeta.responseTimeMs, TaskExecutionMeta.delayMs
 *    - ✅ 正确示例：直接使用 log.durationMs
 *
 * 2. success 字段规则：
 *    - DebugLog.success 已记录执行状态
 *    - 各 Output 类不应再包含 success 字段
 *
 * 3. 新增日志类型检查清单：
 *    - [ ] 创建对应的 XxxInput/XxxOutput/XxxMeta 数据类
 *    - [ ] 创建对应的 XxxLogBuilder
 *    - [ ] 在 DebugLogRepository 添加解析方法
 *    - [ ] 创建对应的调试 UI 屏幕
 *    - [ ] 在 DebugScreen 添加入口
 *    - [ ] 在 MainScreen 添加路由
 */

/**
 * 调试日志类型
 */
enum class DebugLogType {
    MEMORY_GENERATION,      // 每日记忆生成
    SCHEDULED_TASK,         // 定时任务（时间触发）
    AGENT_SUBTASK,          // Agent 子任务（对话触发）
    TOOL_CALL,              // 单次工具调用
    ENTITY_EXTRACTION,      // 实体提取
    CONTEXT_COMPRESSION     // 上下文压缩（预留）
}

/**
 * 统一调试日志实体
 *
 * 支持多种功能的调试日志记录，采用混合方案：
 * - 通用字段：可直接查询
 * - 类型特定数据：JSON 格式存储，灵活扩展
 *
 * @property id 主键
 * @property type 日志类型
 * @property title 标题
 * @property success 是否成功
 * @property errorMessage 错误信息
 * @property durationMs 执行耗时（毫秒）
 * @property createdAt 创建时间戳
 * @property inputData 输入数据（JSON）
 * @property promptSent 发送的提示词（可选）
 * @property rawOutput 原始输出
 * @property parsedOutput 解析后的结果（可选，JSON）
 * @property metadata 额外元数据（可选，JSON）
 */
@Entity(tableName = "debug_logs")
data class DebugLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 通用字段
    val type: DebugLogType,
    val title: String,
    val success: Boolean,
    val errorMessage: String?,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis(),

    // 类型特定数据（JSON 序列化）
    val inputData: String,              // 输入数据
    val promptSent: String?,            // 发送的提示词（可选，非 AI 任务可为空）
    val rawOutput: String,              // 原始输出
    val parsedOutput: String?,          // 解析后的结果（可选）
    val metadata: String?               // 额外元数据
)

/**
 * 调试日志配置实体
 *
 * 定义每种日志类型的最大保留数量
 *
 * @property type 日志类型
 * @property maxRecords 最大保留记录数
 */
@Entity(tableName = "debug_log_configs")
data class DebugLogConfig(
    @PrimaryKey
    val type: DebugLogType,
    val maxRecords: Int = 5            // 默认保留 5 条
)

// ========== 类型特定的数据类 ==========

/**
 * 记忆生成输入数据
 */
data class MemoryGenerationInput(
    val date: String,
    val messageCount: Int,
    val userMessages: Int,
    val assistantMessages: Int,
    val timeRange: String,
    val messagePreviews: List<String>
)

/**
 * 记忆生成输出数据
 */
data class MemoryGenerationOutput(
    val summary: String,
    val topics: List<String>,
    val keyPoints: List<String>,
    val tasks: List<String>,
    val entities: List<MemoryEntityInfo>
)

/**
 * 记忆实体信息
 */
data class MemoryEntityInfo(
    val name: String,
    val type: String,
    val info: String
)

/**
 * 记忆生成元数据
 */
data class MemoryGenerationMeta(
    val tokensUsed: Int?,
    val modelUsed: String?
)

// ========== 工具调用相关数据类 ==========

/**
 * 工具调用输入数据
 */
data class ToolCallInput(
    val toolName: String,              // 工具名称
    val parametersJson: String         // 工具参数 JSON 字符串（保留完整结构）
)

/**
 * 工具调用输出数据
 */
data class ToolCallOutput(
    val message: String,               // 结果消息
    val dataPreview: String? = null    // 数据预览（可选）
)

/**
 * 工具调用元数据
 */
data class ToolCallMeta(
    val conversationId: Long?          // 关联的对话 ID
)

// ========== 定时任务执行数据类 ==========

/**
 * 定时任务执行输入
 */
data class TaskExecutionInput(
    val taskId: Long,
    val taskTitle: String,
    val taskType: String,
    val cronExpression: String?,
    val scheduledTime: Long
)

/**
 * 定时任务执行输出
 */
data class TaskExecutionOutput(
    val aiResponsePreview: String?,
    val actionTaken: String  // "notification_sent" / "completed" / "failed"
)

/**
 * 定时任务执行元数据
 */
data class TaskExecutionMeta(
    val actualExecutionTime: Long,
    val conversationId: Long?
)