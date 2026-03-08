package com.pudding.ai.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import com.pudding.ai.R
import com.pudding.ai.data.database.MessageDao
import com.pudding.ai.data.database.TaskDao
import com.pudding.ai.data.database.TaskExecutionDao
import com.pudding.ai.data.model.*
import com.pudding.ai.data.repository.ChatRepository
import com.pudding.ai.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

/**
 * 任务调度器
 *
 * 负责任务的调度和执行：
 * - 使用 AlarmManager 定时触发任务
 * - 支持 Cron 表达式的定时任务
 * - 支持一次性任务
 * - 执行任务时调用 AI API 并处理结果
 * - 发送任务执行通知
 *
 * @property context 应用上下文
 * @property taskDao 任务数据访问对象
 * @property taskExecutionDao 任务执行记录数据访问对象
 * @property messageDao 消息数据访问对象
 * @property chatRepository 聊天仓库
 * @property settingsRepository 设置仓库
 * @property scope 协程作用域
 */
class TaskScheduler(
    private val context: Context,
    private val taskDao: TaskDao,
    private val taskExecutionDao: TaskExecutionDao,
    private val messageDao: MessageDao,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Cron 解析器
    private val cronParser = CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    )

    companion object {
        const val ACTION_EXECUTE_TASK = "com.pudding.ai.EXECUTE_TASK"
        const val EXTRA_TASK_ID = "task_id"
        const val CHANNEL_ID = "task_notifications"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "任务通知", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleAllTasks() {
        scope.launch {
            taskDao.getTasksByStatus(TaskStatus.ACTIVE).first().forEach { scheduleTask(it) }
        }
    }

    fun scheduleTask(task: Task) {
        if (task.status != TaskStatus.ACTIVE) {
            cancelTask(task)
            return
        }

        val intent = Intent(context, TaskExecutionReceiver::class.java).apply {
            action = ACTION_EXECUTE_TASK
            putExtra(EXTRA_TASK_ID, task.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, task.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextRun = calculateNextRunTime(task)

        if (nextRun == Long.MAX_VALUE) {
            Log.w("TaskScheduler", "Task ${task.title} has no valid next run time")
            return
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRun, pendingIntent)
            scope.launch { taskDao.updateTask(task.copy(nextRunAt = nextRun)) }
            Log.d("TaskScheduler", "Task ${task.title} scheduled for $nextRun")
        } catch (e: Exception) {
            Log.e("TaskScheduler", "Failed to schedule task", e)
        }
    }

    /**
     * 使用 Cron 表达式计算下次执行时间
     */
    private fun calculateNextRunTime(task: Task): Long {
        return when (task.type) {
            TaskType.ONE_TIME -> {
                // 一次性任务：使用 scheduledTime
                val scheduledTime = task.scheduledTime
                if (scheduledTime > System.currentTimeMillis()) {
                    scheduledTime
                } else {
                    // 已过期的任务
                    Long.MAX_VALUE
                }
            }
            TaskType.SCHEDULED -> {
                // 定时任务：使用 Cron 表达式
                val cronExpression = task.cronExpression
                if (cronExpression.isNullOrBlank()) {
                    Long.MAX_VALUE
                } else {
                    try {
                        val cron: Cron = cronParser.parse(cronExpression)
                        val executionTime = ExecutionTime.forCron(cron)
                        val now = ZonedDateTime.now()
                        val nextExecution = executionTime.nextExecution(now).orElse(null)
                        nextExecution?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
                    } catch (e: Exception) {
                        Log.e("TaskScheduler", "Invalid cron expression: $cronExpression", e)
                        Long.MAX_VALUE
                    }
                }
            }
        }
    }

    fun cancelTask(task: Task) {
        val intent = Intent(context, TaskExecutionReceiver::class.java).apply { action = ACTION_EXECUTE_TASK }
        val pendingIntent = PendingIntent.getBroadcast(
            context, task.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun executeTask(taskId: Long) {
        scope.launch {
            val task = taskDao.getTaskById(taskId) ?: return@launch

            try {
                val config = settingsRepository.modelConfig.first()

                // 构建包含当前时间上下文的系统消息
                val currentTime = java.text.SimpleDateFormat(
                    "yyyy年MM月dd日 EEEE HH:mm:ss",
                    java.util.Locale.CHINA
                ).format(java.util.Date())

                val systemPrompt = """
                    你是一个智能助手，正在执行定时任务。
                    当前时间：$currentTime

                    如果需要提醒用户，请使用 send_notification 工具发送通知。
                    你可以根据任务内容智能决定是否发送通知以及通知的内容。
                """.trimIndent()

                val messages = listOf(
                    Message(conversationId = 0, role = MessageRole.SYSTEM, content = systemPrompt),
                    Message(conversationId = 0, role = MessageRole.USER, content = task.prompt)
                )

                val result = chatRepository.sendMessageWithTools(
                    messages = messages,
                    config = config,
                    tools = TaskTools.TASK_EXECUTION_TOOLS,
                    onToolCall = { toolName, params ->
                        when (toolName) {
                            "send_notification" -> {
                                val title = params.get("title")?.asString ?: task.title
                                val message = params.get("message")?.asString ?: ""
                                if (message.isNotBlank()) {
                                    showNotification(title, message)
                                    ToolResult(true, "通知已发送")
                                } else {
                                    ToolResult(false, "通知内容不能为空")
                                }
                            }
                            else -> ToolResult(false, "未知工具: $toolName")
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    result.onSuccess { response ->
                        // 保存执行记录
                        val execution = TaskExecution(
                            taskId = task.id,
                            taskTitle = task.title,
                            prompt = task.prompt,
                            response = response,
                            success = true
                        )
                        scope.launch {
                            taskExecutionDao.insertExecution(execution)
                        }

                        // 如果有 conversationId，保存消息到对话
                        task.conversationId?.let { convId ->
                            scope.launch {
                                // 保存用户提示消息
                                messageDao.insertMessage(
                                    Message(
                                        conversationId = convId,
                                        role = MessageRole.USER,
                                        content = "[定时任务] ${task.prompt}"
                                    )
                                )
                                // 保存 AI 响应消息
                                messageDao.insertMessage(
                                    Message(
                                        conversationId = convId,
                                        role = MessageRole.ASSISTANT,
                                        content = response
                                    )
                                )
                                Log.d("TaskScheduler", "Messages saved to conversation $convId")
                            }
                        }

                        // 注意：通知由 AI 通过 send_notification 工具主动发送
                        // 这里不再自动发送通知

                        // 更新任务状态
                        if (task.type == TaskType.ONE_TIME) {
                            taskDao.updateTask(task.copy(status = TaskStatus.COMPLETED))
                        } else {
                            val updated = task.copy(lastRunAt = System.currentTimeMillis())
                            taskDao.updateTask(updated)
                            scheduleTask(updated)
                        }
                    }.onFailure { error ->
                        // 执行失败时发送通知
                        showNotification(task.title, "执行失败: ${error.message}")

                        // 保存失败记录
                        val execution = TaskExecution(
                            taskId = task.id,
                            taskTitle = task.title,
                            prompt = task.prompt,
                            response = "",
                            success = false,
                            errorMessage = error.message
                        )
                        scope.launch {
                            taskExecutionDao.insertExecution(execution)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskScheduler", "Task execution failed", e)
                withContext(Dispatchers.Main) {
                    showNotification(task.title, "执行出错: ${e.message}")
                }
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}