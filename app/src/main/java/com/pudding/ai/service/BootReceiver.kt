package com.pudding.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pudding.ai.AssistantApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Log.d("BootReceiver", "Device booted, rescheduling tasks...")
            
            val app = context.applicationContext as AssistantApp
            app.taskScheduler?.scheduleAllTasks()
        }
    }
}