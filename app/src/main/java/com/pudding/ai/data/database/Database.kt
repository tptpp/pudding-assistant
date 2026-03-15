package com.pudding.ai.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pudding.ai.data.model.*

/**
 * 应用数据库
 *
 * Room 数据库的入口类，定义了以下实体：
 * - [Message]: 对话消息
 * - [Conversation]: 对话
 * - [Task]: 定时任务
 * - [TaskExecution]: 任务执行记录
 * - [EntityType]: 实体类型
 * - [Entity]: 实体实例
 * - [EntityAttribute]: 实体属性
 * - [DailyMemory]: 每日记忆
 * - [DebugLog]: 调试日志
 * - [DebugLogConfig]: 调试日志配置
 * - [CookieEntity]: Cookie 存储（用于浏览器自动化）
 *
 * 当前数据库版本：8
 *
 * 注意：schema 文件导出到 app/schemas 目录，用于版本管理。
 * 如需修改数据库结构，请增加版本号并添加相应的 Migration。
 * 当前使用 fallbackToDestructiveMigration 作为开发阶段方案。
 */
@Database(
    entities = [
        Message::class,
        Conversation::class,
        Task::class,
        TaskExecution::class,
        EntityType::class,
        TrackedEntity::class,
        EntityAttribute::class,
        DailyMemory::class,
        DebugLog::class,
        DebugLogConfig::class,
        CookieEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    /** 消息数据访问对象 */
    abstract fun messageDao(): MessageDao

    /** 对话数据访问对象 */
    abstract fun conversationDao(): ConversationDao

    /** 任务数据访问对象 */
    abstract fun taskDao(): TaskDao

    /** 任务执行记录数据访问对象 */
    abstract fun taskExecutionDao(): TaskExecutionDao

    /** 记忆系统数据访问对象 */
    abstract fun memoryDao(): MemoryDao

    /** 调试日志数据访问对象 */
    abstract fun debugLogDao(): DebugLogDao

    /** 调试日志配置数据访问对象 */
    abstract fun debugLogConfigDao(): DebugLogConfigDao

    /** Cookie 数据访问对象（用于浏览器自动化） */
    abstract fun cookieDao(): CookieDao
}