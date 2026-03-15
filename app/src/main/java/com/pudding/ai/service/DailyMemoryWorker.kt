package com.pudding.ai.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pudding.ai.data.database.MemoryDao
import com.pudding.ai.data.repository.ChatRepository
import com.pudding.ai.data.repository.DebugLogRepository
import com.pudding.ai.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 每日记忆生成 Worker
 *
 * 使用 WorkManager 定期生成每日记忆，相比 AlarmManager 优势：
 * - 自动处理 Doze 模式
 * - 设备重启后自动恢复
 * - 更可靠的后台执行
 */
@HiltWorker
class DailyMemoryWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val dailyMemoryService: DailyMemoryService,
    private val memoryDao: MemoryDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "DailyMemoryWorker"
        const val WORK_NAME = "daily_memory_generation"
        const val GENERATION_HOUR = 2  // 凌晨 2:00 生成

        /**
         * 调度每日记忆生成
         */
        fun scheduleDailyMemory(context: Context) {
            // 计算到凌晨 2:00 的初始延迟
            val now = ZonedDateTime.now()
            val todayTarget = now.with(LocalTime.of(GENERATION_HOUR, 0))
            val nextTarget = if (now.isBefore(todayTarget)) todayTarget else todayTarget.plusDays(1)
            val initialDelay = nextTarget.toInstant().toEpochMilli() - System.currentTimeMillis()

            // 使用 24 小时周期的周期性任务
            val workRequest = PeriodicWorkRequestBuilder<DailyMemoryWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.i(TAG, "Daily memory generation scheduled for $nextTarget")
        }

        /**
         * 取消每日记忆生成
         */
        fun cancelDailyMemory(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Daily memory generation cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting daily memory generation")

        return try {
            // 生成前一天的记忆
            val yesterday = LocalDate.now().minusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            val memory = dailyMemoryService.generateDailyMemory(yesterday)

            if (memory != null) {
                Log.i(TAG, "Daily memory generated successfully for $yesterday")
                Result.success()
            } else {
                Log.i(TAG, "No messages found for $yesterday, skipping")
                Result.success()  // 不是错误，只是没有消息
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily memory", e)
            Result.retry()
        }
    }
}