package com.pudding.ai.di

import android.content.Context
import androidx.room.Room
import com.pudding.ai.data.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 模块：提供数据库相关依赖
 *
 * 包括：
 * - AppDatabase 实例
 * - 各 DAO 实例
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "code_assistant_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideTaskExecutionDao(database: AppDatabase): TaskExecutionDao {
        return database.taskExecutionDao()
    }

    @Provides
    fun provideMemoryDao(database: AppDatabase): MemoryDao {
        return database.memoryDao()
    }

    @Provides
    fun provideDebugLogDao(database: AppDatabase): DebugLogDao {
        return database.debugLogDao()
    }

    @Provides
    fun provideDebugLogConfigDao(database: AppDatabase): DebugLogConfigDao {
        return database.debugLogConfigDao()
    }

    @Provides
    fun provideCookieDao(database: AppDatabase): CookieDao {
        return database.cookieDao()
    }
}