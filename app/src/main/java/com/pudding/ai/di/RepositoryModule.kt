package com.pudding.ai.di

import android.content.Context
import com.pudding.ai.data.database.CookieDao
import com.pudding.ai.data.database.DebugLogConfigDao
import com.pudding.ai.data.database.DebugLogDao
import com.pudding.ai.data.database.MemoryDao
import com.pudding.ai.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 模块：提供 Repository 依赖
 *
 * 包括：
 * - SettingsRepository
 * - ChatRepository
 * - SearchRepository
 * - DebugLogRepository
 * - DailyMemoryRepository
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideChatRepository(): ChatRepository {
        return ChatRepository()
    }

    @Provides
    @Singleton
    fun provideSearchRepository(
        settingsRepository: SettingsRepository
    ): SearchRepository {
        return SearchRepository(settingsRepository)
    }

    @Provides
    @Singleton
    fun provideDebugLogRepository(
        debugLogDao: DebugLogDao,
        configDao: DebugLogConfigDao
    ): DebugLogRepository {
        return DebugLogRepository(debugLogDao, configDao)
    }

    @Provides
    @Singleton
    fun provideDailyMemoryRepository(
        memoryDao: MemoryDao
    ): DailyMemoryRepository {
        return DailyMemoryRepository(memoryDao)
    }

    @Provides
    @Singleton
    fun provideCookieRepository(
        cookieDao: CookieDao
    ): CookieRepository {
        return CookieRepository(cookieDao)
    }
}