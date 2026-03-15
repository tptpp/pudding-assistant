package com.pudding.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.pudding.ai.BuildConfig
import com.pudding.ai.data.repository.ChatRepository
import com.pudding.ai.data.repository.DebugLogRepository
import com.pudding.ai.data.repository.SearchRepository
import com.pudding.ai.data.repository.SettingsRepository
import com.pudding.ai.service.DailyMemoryService
import com.pudding.ai.service.DailyMemoryWorker
import com.pudding.ai.service.TaskScheduler
import com.pudding.ai.service.TaskToolExecutor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 布丁助手 Application 类
 *
 * 使用 Hilt 进行依赖注入，负责：
 * - 初始化通知渠道
 * - 处理外部配置广播
 * - 启动时调度任务
 *
 * 注意：configReceiver 在 Application 生命周期内保持注册，
 * 因为 Application 的生命周期与应用进程相同，不会造成内存泄漏。
 * 但我们仍在 onTerminate() 中注销作为最佳实践。
 */
@HiltAndroidApp
class AssistantApp : Application() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var searchRepository: SearchRepository
    @Inject lateinit var debugLogRepository: DebugLogRepository
    @Inject lateinit var taskScheduler: TaskScheduler
    @Inject lateinit var taskToolExecutor: TaskToolExecutor
    @Inject lateinit var dailyMemoryService: DailyMemoryService
    @Inject lateinit var applicationScope: CoroutineScope

    /**
     * 配置广播接收器
     *
     * 用于接收外部应用发送的配置更新广播。
     * 声明为 private 成员变量以便在 onTerminate() 中注销。
     */
    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val apiKey = it.getStringExtra("api_key")
                val baseUrl = it.getStringExtra("base_url")
                val provider = it.getStringExtra("provider") ?: "OPENAI"
                val model = it.getStringExtra("model") ?: ""

                // 安全日志：仅在调试模式下显示敏感信息
                val apiKeyPreview = if (BuildConfig.DEBUG) {
                    apiKey?.take(10) + "..."
                } else {
                    "***"
                }
                Log.d("AssistantApp", "Received config: apiKey=$apiKeyPreview, baseUrl=$baseUrl, provider=$provider")

                if (!apiKey.isNullOrEmpty() || !baseUrl.isNullOrEmpty()) {
                    applicationScope.launch {
                        val currentConfig = settingsRepository.modelConfig.first()
                        val config = com.pudding.ai.data.model.ModelConfig(
                            provider = try {
                                com.pudding.ai.data.model.ApiProvider.valueOf(provider.uppercase())
                            } catch (e: IllegalArgumentException) {
                                com.pudding.ai.data.model.ApiProvider.OPENAI
                            },
                            baseUrl = baseUrl ?: currentConfig.baseUrl,
                            apiKey = apiKey ?: currentConfig.apiKey,
                            model = model.ifEmpty { currentConfig.model },
                            temperature = currentConfig.temperature,
                            maxTokens = currentConfig.maxTokens
                        )
                        settingsRepository.saveModelConfig(config)
                        chatRepository.updateConfig(config)
                        Log.d("AssistantApp", "Config saved successfully")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "配置已更新", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * 标记接收器是否已注册
     */
    @Volatile
    private var isReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        Log.d("AssistantApp", "Application starting...")

        // 创建通知渠道
        createNotificationChannels()

        // 注册配置广播接收器
        registerConfigReceiver()

        // 启动时调度所有活跃任务
        applicationScope.launch {
            try {
                val config = settingsRepository.modelConfig.first()
                chatRepository.updateConfig(config)
                taskScheduler.scheduleAllTasks()

                // 调度每日记忆生成任务（使用 WorkManager）
                DailyMemoryWorker.scheduleDailyMemory(this@AssistantApp)
                Log.d("AssistantApp", "Tasks and daily memory scheduled successfully")
            } catch (e: Exception) {
                Log.e("AssistantApp", "Failed to initialize", e)
            }
        }
    }

    /**
     * 注册配置广播接收器
     */
    private fun registerConfigReceiver() {
        if (isReceiverRegistered) {
            Log.w("AssistantApp", "Config receiver already registered")
            return
        }

        try {
            val filter = IntentFilter("com.pudding.ai.SET_CONFIG")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(configReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(configReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d("AssistantApp", "Config receiver registered")
        } catch (e: Exception) {
            Log.e("AssistantApp", "Failed to register config receiver", e)
        }
    }

    /**
     * 注销配置广播接收器
     */
    private fun unregisterConfigReceiver() {
        if (!isReceiverRegistered) {
            return
        }

        try {
            unregisterReceiver(configReceiver)
            isReceiverRegistered = false
            Log.d("AssistantApp", "Config receiver unregistered")
        } catch (e: Exception) {
            Log.e("AssistantApp", "Failed to unregister config receiver", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // 注意：onTerminate() 在真机上可能不会被调用，
        // 但作为最佳实践仍然在这里注销接收器
        unregisterConfigReceiver()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val taskChannel = NotificationChannel(
                TaskScheduler.CHANNEL_ID,
                "定时任务",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "定时任务执行通知"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(taskChannel)
        }
    }
}