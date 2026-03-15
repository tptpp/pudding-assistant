package com.pudding.ai.di

import android.content.Context
import com.pudding.ai.data.database.*
import com.pudding.ai.data.repository.*
import com.pudding.ai.service.BrowserManager
import com.pudding.ai.service.DailyMemoryService
import com.pudding.ai.service.TaskScheduler
import com.pudding.ai.service.TaskToolExecutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Hilt 模块：提供服务层依赖
 *
 * 包括：
 * - TaskScheduler
 * - TaskToolExecutor
 * - DailyMemoryService
 * - CoroutineScope
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideTaskScheduler(
        @ApplicationContext context: Context,
        taskDao: TaskDao,
        taskExecutionDao: TaskExecutionDao,
        messageDao: MessageDao,
        chatRepository: ChatRepository,
        settingsRepository: SettingsRepository,
        applicationScope: CoroutineScope,
        debugLogRepository: DebugLogRepository
    ): TaskScheduler {
        return TaskScheduler(
            context = context,
            taskDao = taskDao,
            taskExecutionDao = taskExecutionDao,
            messageDao = messageDao,
            chatRepository = chatRepository,
            settingsRepository = settingsRepository,
            scope = applicationScope,
            debugLogRepository = debugLogRepository
        )
    }

    @Provides
    @Singleton
    fun provideTaskToolExecutor(
        taskDao: TaskDao,
        taskScheduler: TaskScheduler,
        @ApplicationContext context: Context,
        searchRepository: SearchRepository,
        debugLogRepository: DebugLogRepository,
        browserManager: BrowserManager
    ): TaskToolExecutor {
        return TaskToolExecutor(
            taskDao = taskDao,
            taskScheduler = taskScheduler,
            context = context,
            searchRepository = searchRepository,
            debugLogRepository = debugLogRepository,
            browserManager = browserManager
        )
    }

    @Provides
    @Singleton
    fun provideBrowserManager(
        @ApplicationContext context: Context,
        cookieRepository: CookieRepository
    ): BrowserManager {
        return BrowserManager(context, cookieRepository)
    }

    @Provides
    @Singleton
    fun provideDailyMemoryService(
        chatRepository: ChatRepository,
        memoryDao: MemoryDao,
        messageDao: MessageDao,
        settingsRepository: SettingsRepository,
        debugLogRepository: DebugLogRepository
    ): DailyMemoryService {
        return DailyMemoryService(
            chatRepository = chatRepository,
            memoryDao = memoryDao,
            messageDao = messageDao,
            settingsRepository = settingsRepository,
            debugLogRepository = debugLogRepository
        )
    }
}