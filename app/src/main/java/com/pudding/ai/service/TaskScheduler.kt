package com.pudding.ai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import com.pudding.ai.data.database.MessageDao
import com.pudding.ai.data.database.TaskDao
import com.pudding.ai.data.database.TaskExecutionDao
import com.pudding.ai.data.repository.ChatRepository
import com.pudding.ai.data.repository.DebugLogRepository
import com.pudding.ai.data.repository.SettingsRepository
import com.pudding.ai.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务调度器
 *
 * 使用 WorkManager 进行任务调度，相比 AlarmManager 优势：
 * - 自动处理 Doze 模式
 * - 支持任务约束（网络、电量等）
 * - 支持任务重试和退避策略
 * - 设备重启后自动恢复
 *
 * @property context 应用上下文
 * @property taskDao 任务数据访问对象
 * @property taskExecutionDao 任务执行记录数据访问对象
 * @property messageDao 消息数据访问对象
 * @property chatRepository 聊天仓库
 * @property settingsRepository 设置仓库
 * @property scope 协程作用域
 * @property debugLogRepository 调试日志仓库
 */
@Singleton
class TaskScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val taskExecutionDao: TaskExecutionDao,
    private val messageDao: MessageDao,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val debugLogRepository: DebugLogRepository
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Cron 解析器
    private val cronParser = CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    )

    companion object {
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

    /**
     * 调度所有活跃任务
     */
    fun scheduleAllTasks() {
        scope.launch {
            taskDao.getTasksByStatus(TaskStatus.ACTIVE).first().forEach { scheduleTask(it) }
        }
    }

    /**
     * 调度单个任务
     */
    fun scheduleTask(task: Task) {
        if (task.status != TaskStatus.ACTIVE) {
            cancelTask(task)
            return
        }

        val nextRun = calculateNextRunTime(task)

        if (nextRun == Long.MAX_VALUE) {
            Log.w("TaskScheduler", "Task ${task.title} has no valid next run time")
            return
        }

        val delayMs = nextRun - System.currentTimeMillis()
        if (delayMs < 0) {
            Log.w("TaskScheduler", "Task ${task.title} is already past due")
            return
        }

        // 使用 WorkManager 调度
        TaskExecutionWorker.scheduleTask(context, task, delayMs)

        // 更新任务的 nextRunAt
        scope.launch {
            taskDao.updateTask(task.copy(nextRunAt = nextRun))
        }

        Log.d("TaskScheduler", "Task ${task.title} scheduled for $nextRun")
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

    /**
     * 取消任务调度
     */
    fun cancelTask(task: Task) {
        TaskExecutionWorker.cancelTask(context, task.id)
        Log.d("TaskScheduler", "Task ${task.title} cancelled")
    }

    /**
     * 立即执行任务（用于测试或手动触发）
     */
    fun executeTaskNow(taskId: Long) {
        scope.launch {
            val task = taskDao.getTaskById(taskId) ?: return@launch

            // 立即调度（延迟为 0）
            TaskExecutionWorker.scheduleTask(context, task, 0)
        }
    }
}