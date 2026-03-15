package com.pudding.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机广播接收器
 *
 * WorkManager 会自动恢复周期性任务，此接收器主要用于：
 * - 记录设备重启日志
 * - 确保应用在后台正确初始化
 *
 * 注意：WorkManager 的 PeriodicWorkRequest 会在设备重启后自动恢复，
 * 但 OneTimeWorkRequest 可能需要重新调度。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Log.d("BootReceiver", "Device booted, WorkManager will restore periodic tasks automatically")

            // WorkManager 会自动恢复周期性任务
            // 如果有需要立即恢复的一次性任务，可以在这里处理
        }
    }
}