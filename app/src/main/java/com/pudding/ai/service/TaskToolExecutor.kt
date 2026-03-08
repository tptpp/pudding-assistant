package com.pudding.ai.service

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import com.google.gson.JsonObject
import com.pudding.ai.R
import com.pudding.ai.data.database.TaskDao
import com.pudding.ai.data.model.Task
import com.pudding.ai.data.model.TaskStatus
import com.pudding.ai.data.model.TaskType
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 任务工具执行器
 *
 * 处理 AI 对话中的 Function Call 工具调用，支持以下工具：
 * - create_task: 创建定时或一次性任务
 * - delete_task: 删除任务
 * - list_tasks: 查询任务列表
 * - update_task_status: 修改任务状态
 * - send_notification: 发送通知
 *
 * @property taskDao 任务数据访问对象
 * @property taskScheduler 任务调度器
 * @property context 应用上下文（用于发送通知）
 */
class TaskToolExecutor(
    private val taskDao: TaskDao,
    private val taskScheduler: TaskScheduler,
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "TaskToolExecutor"
        const val CHANNEL_ID = "task_notifications"
    }

    // 通知管理器（延迟初始化）
    private val notificationManager: NotificationManager? by lazy {
        context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    // Cron 解析器（使用 Unix cron 格式：分 时 日 月 周）
    private val cronParser = CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    )

    /**
     * 执行工具调用
     */
    suspend fun executeToolCall(
        conversationId: Long,
        toolName: String,
        params: JsonObject
    ): ToolResult = when (toolName) {
        "create_task" -> executeCreateTask(conversationId, params)
        "delete_task" -> executeDeleteTask(params)
        "list_tasks" -> executeListTasks(params)
        "update_task_status" -> executeUpdateTaskStatus(params)
        "send_notification" -> executeSendNotification(params)
        else -> ToolResult(false, "未知工具: $toolName")
    }

    /**
     * 创建任务
     */
    private suspend fun executeCreateTask(conversationId: Long, params: JsonObject): ToolResult {
        return try {
            val title = params.get("title")?.asString
            val prompt = params.get("prompt")?.asString
            val taskTypeStr = params.get("taskType")?.asString ?: "SCHEDULED"

            if (title.isNullOrBlank() || prompt.isNullOrBlank()) {
                return ToolResult(false, "任务标题和执行内容不能为空")
            }

            val taskType = try {
                TaskType.valueOf(taskTypeStr)
            } catch (e: IllegalArgumentException) {
                TaskType.SCHEDULED
            }

            val task = when (taskType) {
                TaskType.SCHEDULED -> {
                    val cronExpression = params.get("cronExpression")?.asString
                    if (cronExpression.isNullOrBlank()) {
                        return ToolResult(false, "定时任务需要提供 cronExpression 参数")
                    }

                    // 验证 cron 表达式
                    try {
                        cronParser.parse(cronExpression)
                    } catch (e: Exception) {
                        return ToolResult(false, "无效的 Cron 表达式: $cronExpression")
                    }

                    Task(
                        title = title,
                        prompt = prompt,
                        type = TaskType.SCHEDULED,
                        cronExpression = cronExpression,
                        scheduledTime = System.currentTimeMillis(),
                        conversationId = conversationId,
                        source = "dialog"
                    )
                }
                TaskType.ONE_TIME -> {
                    val scheduledDateTime = params.get("scheduledDateTime")?.asString
                    if (scheduledDateTime.isNullOrBlank()) {
                        return ToolResult(false, "一次性任务需要提供 scheduledDateTime 参数")
                    }

                    val scheduledTime = parseDateTime(scheduledDateTime)
                    if (scheduledTime <= System.currentTimeMillis()) {
                        return ToolResult(false, "执行时间必须是将来的时间")
                    }

                    Task(
                        title = title,
                        prompt = prompt,
                        type = TaskType.ONE_TIME,
                        scheduledTime = scheduledTime,
                        conversationId = conversationId,
                        source = "dialog"
                    )
                }
            }

            val taskId = taskDao.insertTask(task)
            taskScheduler.scheduleTask(task.copy(id = taskId))

            Log.d(TAG, "Task created: $title, id=$taskId, type=$taskType")
            ToolResult(
                success = true,
                message = "已成功创建任务「$title」，任务ID: $taskId",
                data = mapOf("taskId" to taskId, "title" to title)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create task", e)
            ToolResult(false, "创建任务失败: ${e.message}")
        }
    }

    /**
     * 删除任务
     */
    private suspend fun executeDeleteTask(params: JsonObject): ToolResult {
        return try {
            val taskId = params.get("taskId")?.asLong
            val taskTitle = params.get("taskTitle")?.asString

            val task = when {
                taskId != null && taskId > 0 -> taskDao.getTaskById(taskId)
                !taskTitle.isNullOrBlank() -> {
                    // 模糊匹配标题
                    taskDao.getAllTasks().first().find {
                        it.title.contains(taskTitle, ignoreCase = true)
                    }
                }
                else -> null
            }

            if (task == null) {
                return ToolResult(false, "未找到要删除的任务")
            }

            taskScheduler.cancelTask(task)
            taskDao.deleteTask(task)

            Log.d(TAG, "Task deleted: ${task.title}")
            ToolResult(true, "已成功删除任务「${task.title}」")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete task", e)
            ToolResult(false, "删除任务失败: ${e.message}")
        }
    }

    /**
     * 查询任务列表
     */
    private suspend fun executeListTasks(params: JsonObject): ToolResult {
        return try {
            val statusStr = params.get("status")?.asString
            val tasks = if (!statusStr.isNullOrBlank()) {
                val status = try {
                    TaskStatus.valueOf(statusStr)
                } catch (e: IllegalArgumentException) {
                    null
                }
                if (status != null) {
                    taskDao.getTasksByStatus(status).first()
                } else {
                    taskDao.getAllTasks().first()
                }
            } else {
                taskDao.getAllTasks().first()
            }

            if (tasks.isEmpty()) {
                return ToolResult(true, "当前没有任务", data = emptyList<Any>())
            }

            val taskList = tasks.map { task ->
                mapOf(
                    "id" to task.id,
                    "title" to task.title,
                    "type" to task.type.name,
                    "cronExpression" to (task.cronExpression ?: ""),
                    "status" to task.status.name,
                    "nextRunAt" to (task.nextRunAt ?: 0),
                    "source" to task.source
                )
            }

            val message = buildString {
                append("当前共有 ${tasks.size} 个任务：\n")
                tasks.forEachIndexed { index, task ->
                    append("${index + 1}. ${task.title}")
                    append(" [${task.status.name}]")
                    if (task.type == TaskType.SCHEDULED && !task.cronExpression.isNullOrBlank()) {
                        append(" (Cron: ${task.cronExpression})")
                    }
                    append("\n")
                }
            }

            ToolResult(true, message, data = taskList)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list tasks", e)
            ToolResult(false, "查询任务失败: ${e.message}")
        }
    }

    /**
     * 修改任务状态
     */
    private suspend fun executeUpdateTaskStatus(params: JsonObject): ToolResult {
        return try {
            val statusStr = params.get("status")?.asString
            if (statusStr.isNullOrBlank()) {
                return ToolResult(false, "需要提供新状态")
            }

            val newStatus = try {
                TaskStatus.valueOf(statusStr)
            } catch (e: IllegalArgumentException) {
                return ToolResult(false, "无效的状态: $statusStr")
            }

            val taskId = params.get("taskId")?.asLong
            val taskTitle = params.get("taskTitle")?.asString

            val task = when {
                taskId != null && taskId > 0 -> taskDao.getTaskById(taskId)
                !taskTitle.isNullOrBlank() -> {
                    taskDao.getAllTasks().first().find {
                        it.title.contains(taskTitle, ignoreCase = true)
                    }
                }
                else -> null
            }

            if (task == null) {
                return ToolResult(false, "未找到要修改的任务")
            }

            val updatedTask = task.copy(status = newStatus)
            taskDao.updateTask(updatedTask)

            // 根据状态决定是否调度任务
            when (newStatus) {
                TaskStatus.ACTIVE -> taskScheduler.scheduleTask(updatedTask)
                TaskStatus.PAUSED, TaskStatus.DISABLED -> taskScheduler.cancelTask(updatedTask)
                TaskStatus.COMPLETED -> { /* 一次性任务完成后不做额外操作 */ }
            }

            Log.d(TAG, "Task status updated: ${task.title} -> $newStatus")
            ToolResult(true, "已将任务「${task.title}」状态修改为 $newStatus")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update task status", e)
            ToolResult(false, "修改任务状态失败: ${e.message}")
        }
    }

    /**
     * 发送通知
     */
    private fun executeSendNotification(params: JsonObject): ToolResult {
        return try {
            val title = params.get("title")?.asString ?: "通知"
            val message = params.get("message")?.asString ?: ""

            if (message.isBlank()) {
                return ToolResult(false, "通知内容不能为空")
            }

            val ctx = context
            if (ctx == null) {
                Log.w(TAG, "Context not available")
                return ToolResult(false, "通知服务不可用")
            }

            val manager = notificationManager
            if (manager == null) {
                Log.w(TAG, "NotificationManager not available")
                return ToolResult(false, "通知服务不可用")
            }

            val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            manager.notify(System.currentTimeMillis().toInt(), notification)

            Log.d(TAG, "Notification sent: $title - $message")
            ToolResult(true, "通知已发送")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification", e)
            ToolResult(false, "发送通知失败: ${e.message}")
        }
    }

    /**
     * 解析日期时间字符串
     * 支持格式：yyyy-MM-dd HH:mm
     */
    private fun parseDateTime(dateTimeStr: String): Long {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return format.parse(dateTimeStr)?.time ?: System.currentTimeMillis()
    }

    /**
     * 计算 Cron 表达式的下次执行时间
     */
    fun calculateNextRunTime(cronExpression: String): Long {
        return try {
            val cron: Cron = cronParser.parse(cronExpression)
            val executionTime = ExecutionTime.forCron(cron)
            val now = ZonedDateTime.now()
            val nextExecution = executionTime.nextExecution(now).orElse(null)
            nextExecution?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate next run time for: $cronExpression", e)
            Long.MAX_VALUE
        }
    }
}