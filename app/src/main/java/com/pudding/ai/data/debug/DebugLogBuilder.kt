package com.pudding.ai.data.debug

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.pudding.ai.data.model.*
import com.pudding.ai.service.DailyMemoryService
import com.pudding.ai.service.ToolResult

/**
 * 调试日志构建器接口
 *
 * 定义构建特定类型调试日志的通用接口。
 * 各实现类负责构建特定类型的日志数据。
 */
interface DebugLogBuilder {
    val logType: DebugLogType
}

/**
 * 记忆生成日志构建器
 *
 * 负责构建 MEMORY_GENERATION 类型的调试日志。
 */
class MemoryGenerationLogBuilder(
    private val gson: Gson = Gson()
) : DebugLogBuilder {

    override val logType = DebugLogType.MEMORY_GENERATION

    /**
     * 构建记忆生成调试日志
     *
     * @param date 日期字符串
     * @param messages 消息列表
     * @param prompt 发送的提示词
     * @param rawResponse AI 原始响应
     * @param result 记忆生成结果
     * @param durationMs 执行耗时
     * @param error 错误信息
     */
    fun build(
        date: String,
        messages: List<Message>,
        prompt: String,
        rawResponse: String,
        result: DailyMemoryService.MemoryResult?,
        durationMs: Long,
        error: Throwable? = null
    ): DebugLog {
        // 统计消息
        val userMessages = messages.count { it.role == MessageRole.USER }
        val assistantMessages = messages.count { it.role == MessageRole.ASSISTANT }

        // 计算时间范围
        val timeRange = if (messages.isNotEmpty()) {
            val startTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                .format(java.util.Date(messages.minOfOrNull { it.timestamp } ?: 0))
            val endTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                .format(java.util.Date(messages.maxOfOrNull { it.timestamp } ?: 0))
            "$startTime - $endTime"
        } else {
            "无消息"
        }

        // 构建输入数据
        val input = MemoryGenerationInput(
            date = date,
            messageCount = messages.size,
            userMessages = userMessages,
            assistantMessages = assistantMessages,
            timeRange = timeRange,
            messagePreviews = messages.take(5).map { it.content.take(100) }
        )

        // 构建输出数据
        val output = result?.let { r ->
            MemoryGenerationOutput(
                summary = r.summary,
                topics = r.topics,
                keyPoints = r.keyPoints,
                tasks = r.tasks,
                entities = r.entities.map { e ->
                    MemoryEntityInfo(e.name, e.type, e.info)
                }
            )
        }

        return DebugLog(
            type = logType,
            title = "$date 记忆生成",
            success = error == null && result != null,
            errorMessage = error?.message,
            durationMs = durationMs,
            inputData = gson.toJson(input),
            promptSent = prompt,
            rawOutput = rawResponse,
            parsedOutput = output?.let { gson.toJson(it) },
            metadata = null
        )
    }
}

/**
 * 工具调用日志构建器
 *
 * 负责构建 TOOL_CALL 类型的调试日志。
 * 直接使用 JsonObject 作为参数，避免转换丢失复杂类型。
 */
class ToolCallLogBuilder(
    private val gson: Gson = Gson()
) : DebugLogBuilder {

    override val logType = DebugLogType.TOOL_CALL

    /**
     * 构建工具调用调试日志
     *
     * @param toolName 工具名称
     * @param params 工具参数（保留原始 JsonObject）
     * @param result 工具执行结果
     * @param durationMs 执行耗时
     * @param conversationId 关联的对话 ID
     * @param error 错误信息
     */
    fun build(
        toolName: String,
        params: JsonObject,
        result: ToolResult?,
        durationMs: Long,
        conversationId: Long? = null,
        error: Throwable? = null
    ): DebugLog {
        // 构建输入数据 - 保留完整的 JsonObject 结构
        val input = ToolCallInput(
            toolName = toolName,
            parametersJson = params.toString()  // 保留完整 JSON 字符串
        )

        // 构建输出数据（移除冗余的 success 字段）
        val output = result?.let { r ->
            ToolCallOutput(
                message = r.message,
                dataPreview = r.data?.let { data ->
                    // 生成数据预览
                    when (data) {
                        is List<*> -> "[${data.size} items]"
                        is Map<*, *> -> "{${data.size} entries}"
                        else -> data.toString().take(100)
                    }
                }
            )
        }

        // 构建元数据
        val meta = ToolCallMeta(
            conversationId = conversationId
        )

        return DebugLog(
            type = logType,
            title = "工具调用: $toolName",
            success = error == null && result?.success == true,
            errorMessage = error?.message ?: if (result?.success == false) result.message else null,
            durationMs = durationMs,
            inputData = gson.toJson(input),
            promptSent = null,
            rawOutput = result?.message ?: error?.message ?: "",
            parsedOutput = output?.let { gson.toJson(it) },
            metadata = gson.toJson(meta)
        )
    }
}

/**
 * 定时任务执行日志构建器
 *
 * 负责构建 SCHEDULED_TASK 类型的调试日志。
 */
class TaskExecutionLogBuilder(
    private val gson: Gson = Gson()
) : DebugLogBuilder {

    override val logType = DebugLogType.SCHEDULED_TASK

    /**
     * 构建定时任务执行调试日志
     *
     * @param task 任务实体
     * @param aiResponse AI 响应内容
     * @param notificationSent 是否发送了通知
     * @param durationMs 执行耗时
     * @param error 错误信息
     */
    fun build(
        task: Task,
        aiResponse: String?,
        notificationSent: Boolean,
        durationMs: Long,
        error: Throwable? = null
    ): DebugLog {
        // 构建输入数据
        val input = TaskExecutionInput(
            taskId = task.id,
            taskTitle = task.title,
            taskType = task.type.name,
            cronExpression = task.cronExpression,
            scheduledTime = task.scheduledTime
        )

        // 构建输出数据
        val output = TaskExecutionOutput(
            aiResponsePreview = aiResponse?.take(200),
            actionTaken = when {
                error != null -> "failed"
                notificationSent -> "notification_sent"
                else -> "completed"
            }
        )

        // 构建元数据
        val meta = TaskExecutionMeta(
            actualExecutionTime = System.currentTimeMillis(),
            conversationId = task.conversationId
        )

        return DebugLog(
            type = logType,
            title = "任务执行: ${task.title}",
            success = error == null,
            errorMessage = error?.message,
            durationMs = durationMs,
            inputData = gson.toJson(input),
            promptSent = task.prompt,
            rawOutput = aiResponse ?: "",
            parsedOutput = gson.toJson(output),
            metadata = gson.toJson(meta)
        )
    }
}