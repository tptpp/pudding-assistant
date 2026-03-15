package com.pudding.ai.service

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import com.pudding.ai.R
import com.pudding.ai.data.database.MessageDao
import com.pudding.ai.data.database.TaskDao
import com.pudding.ai.data.database.TaskExecutionDao
import com.pudding.ai.data.debug.TaskExecutionLogBuilder
import com.pudding.ai.data.model.*
import com.pudding.ai.data.repository.ChatRepository
import com.pudding.ai.data.repository.DebugLogRepository
import com.pudding.ai.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 任务执行 Worker
 *
 * 使用 WorkManager 执行定时任务，相比 AlarmManager 优势：
 * - 自动处理 Doze 模式
 * - 支持任务约束（网络、电量等）
 * - 支持任务重试和退避策略
 * - 设备重启后自动恢复
 */
@HiltWorker
class TaskExecutionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskDao: TaskDao,
    private val taskExecutionDao: TaskExecutionDao,
    private val messageDao: MessageDao,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val debugLogRepository: DebugLogRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "TaskExecutionWorker"
        const val KEY_TASK_ID = "task_id"
        const val WORK_NAME_PREFIX = "task_"

        /**
         * 调度任务执行
         */
        fun scheduleTask(
            context: Context,
            task: Task,
            initialDelay: Long = 0
        ) {
            val workRequest = OneTimeWorkRequestBuilder<TaskExecutionWorker>()
                .setInputData(androidx.work.workDataOf(KEY_TASK_ID to task.id))
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_PREFIX + task.id,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Log.d(TAG, "Task ${task.title} scheduled with delay ${initialDelay}ms")
        }

        /**
         * 取消任务调度
         */
        fun cancelTask(context: Context, taskId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PREFIX + taskId)
            Log.d(TAG, "Task $taskId cancelled")
        }
    }

    // Cron 解析器
    private val cronParser = CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    )

    // 任务执行日志构建器
    private val logBuilder = TaskExecutionLogBuilder()

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1)
        if (taskId == -1L) {
            Log.e(TAG, "Invalid task ID")
            return Result.failure()
        }

        val task = taskDao.getTaskById(taskId)
        if (task == null) {
            Log.e(TAG, "Task not found: $taskId")
            return Result.failure()
        }

        Log.i(TAG, "Executing task: ${task.title}")
        val startTime = System.currentTimeMillis()
        var notificationSent = false
        var aiResponse: String? = null

        return try {
            val config = settingsRepository.modelConfig.first()

            // 构建包含当前时间上下文的系统消息
            val currentTime = DateTimeFormatter
                .ofPattern("yyyy年MM月dd日 EEEE HH:mm:ss", Locale.CHINA)
                .format(ZonedDateTime.now())

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
                                notificationSent = true
                                ToolResult(true, "通知已发送")
                            } else {
                                ToolResult(false, "通知内容不能为空")
                            }
                        }
                        else -> ToolResult(false, "未知工具: $toolName")
                    }
                }
            )

            result.onSuccess { response ->
                aiResponse = response

                // 保存执行记录
                val execution = TaskExecution(
                    taskId = task.id,
                    taskTitle = task.title,
                    prompt = task.prompt,
                    response = response,
                    success = true
                )
                taskExecutionDao.insertExecution(execution)

                // 如果有 conversationId，保存消息到对话
                task.conversationId?.let { convId ->
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
                    Log.d(TAG, "Messages saved to conversation $convId")
                }

                // 更新任务状态
                if (task.type == TaskType.ONE_TIME) {
                    taskDao.updateTask(task.copy(status = TaskStatus.COMPLETED))
                } else {
                    val updated = task.copy(lastRunAt = System.currentTimeMillis())
                    taskDao.updateTask(updated)
                    // 调度下次执行
                    scheduleNextExecution(updated)
                }

                // 记录调试日志
                val durationMs = System.currentTimeMillis() - startTime
                val debugLog = logBuilder.build(
                    task = task,
                    aiResponse = aiResponse,
                    notificationSent = notificationSent,
                    durationMs = durationMs,
                    error = null
                )
                debugLogRepository.saveLog(debugLog)
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
                taskExecutionDao.insertExecution(execution)

                // 记录失败的调试日志
                val durationMs = System.currentTimeMillis() - startTime
                val debugLog = logBuilder.build(
                    task = task,
                    aiResponse = null,
                    notificationSent = false,
                    durationMs = durationMs,
                    error = error
                )
                debugLogRepository.saveLog(debugLog)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Task execution failed", e)
            showNotification(task.title, "执行出错: ${e.message}")

            // 记录异常的调试日志
            val durationMs = System.currentTimeMillis() - startTime
            val debugLog = logBuilder.build(
                task = task,
                aiResponse = null,
                notificationSent = false,
                durationMs = durationMs,
                error = e
            )
            debugLogRepository.saveLog(debugLog)

            // 对于周期性任务，使用重试策略
            if (task.type == TaskType.SCHEDULED) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * 调度下次执行（针对周期性任务）
     */
    private suspend fun scheduleNextExecution(task: Task) {
        if (task.type != TaskType.SCHEDULED || task.cronExpression.isNullOrBlank()) {
            return
        }

        try {
            val cron: Cron = cronParser.parse(task.cronExpression)
            val executionTime = ExecutionTime.forCron(cron)
            val now = ZonedDateTime.now()
            val nextExecution = executionTime.nextExecution(now).orElse(null)

            if (nextExecution != null) {
                val delayMs = nextExecution.toInstant().toEpochMilli() - System.currentTimeMillis()
                if (delayMs > 0) {
                    scheduleTask(context, task, delayMs)

                    // 更新任务的 nextRunAt
                    taskDao.updateTask(task.copy(nextRunAt = nextExecution.toInstant().toEpochMilli()))
                    Log.d(TAG, "Next execution scheduled for ${task.title} at $nextExecution")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule next execution", e)
        }
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, TaskScheduler.CHANNEL_ID)
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