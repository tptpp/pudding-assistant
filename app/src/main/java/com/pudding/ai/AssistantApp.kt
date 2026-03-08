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
import androidx.room.Room
import com.pudding.ai.data.database.AppDatabase
import com.pudding.ai.data.model.ApiProvider
import com.pudding.ai.data.model.ModelConfig
import com.pudding.ai.data.repository.ChatRepository
import com.pudding.ai.data.repository.SettingsRepository
import com.pudding.ai.service.TaskScheduler
import com.pudding.ai.service.TaskToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 布丁助手 Application 类
 *
 * 负责初始化应用的全局组件：
 * - Room 数据库
 * - 设置仓库和聊天仓库
 * - 任务调度器和工具执行器
 * - 通知渠道
 *
 * 同时处理外部配置广播（com.pudding.ai.SET_CONFIG）以支持动态更新 API 配置。
 */
class AssistantApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var chatRepository: ChatRepository
        private set

    var taskScheduler: TaskScheduler? = null
        private set

    var taskToolExecutor: TaskToolExecutor? = null
        private set

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val apiKey = it.getStringExtra("api_key")
                val baseUrl = it.getStringExtra("base_url")
                val provider = it.getStringExtra("provider") ?: "OPENAI"
                val model = it.getStringExtra("model") ?: ""

                Log.d("AssistantApp", "Received config: apiKey=${apiKey?.take(10)}..., baseUrl=$baseUrl, provider=$provider")

                if (!apiKey.isNullOrEmpty() || !baseUrl.isNullOrEmpty()) {
                    applicationScope.launch {
                        val currentConfig = settingsRepository.modelConfig.first()
                        val config = ModelConfig(
                            provider = try {
                                ApiProvider.valueOf(provider.uppercase())
                            } catch (e: IllegalArgumentException) {
                                ApiProvider.OPENAI
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

    override fun onCreate() {
        super.onCreate()
        Log.d("AssistantApp", "Application starting...")

        // 初始化数据库
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "code_assistant_db"
        )
            .fallbackToDestructiveMigration()
            .build()

        // 初始化仓库
        settingsRepository = SettingsRepository(applicationContext)
        chatRepository = ChatRepository()

        // 初始化任务调度器
        taskScheduler = TaskScheduler(
            context = applicationContext,
            taskDao = database.taskDao(),
            taskExecutionDao = database.taskExecutionDao(),
            messageDao = database.messageDao(),
            chatRepository = chatRepository,
            settingsRepository = settingsRepository,
            scope = applicationScope
        )

        // 初始化任务工具执行器
        taskToolExecutor = TaskToolExecutor(
            taskDao = database.taskDao(),
            taskScheduler = taskScheduler!!
        )

        // 创建通知渠道
        createNotificationChannels()

        // 注册配置广播接收器
        val filter = IntentFilter("com.pudding.ai.SET_CONFIG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(configReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(configReceiver, filter)
        }
        Log.d("AssistantApp", "Config receiver registered")

        // 启动时调度所有活跃任务
        applicationScope.launch {
            try {
                val config = settingsRepository.modelConfig.first()
                chatRepository.updateConfig(config)
                taskScheduler?.scheduleAllTasks()
                Log.d("AssistantApp", "Tasks scheduled successfully")
            } catch (e: Exception) {
                Log.e("AssistantApp", "Failed to initialize", e)
            }
        }
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