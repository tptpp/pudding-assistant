package com.pudding.ai.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pudding.ai.data.model.*

/**
 * 应用数据库
 *
 * Room 数据库的入口类，定义了以下实体：
 * - [Message]: 对话消息
 * - [Conversation]: 对话
 * - [Task]: 定时任务
 * - [TaskExecution]: 任务执行记录
 *
 * 当前数据库版本：4
 */
@Database(
    entities = [
        Message::class,
        Conversation::class,
        Task::class,
        TaskExecution::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    /** 消息数据访问对象 */
    abstract fun messageDao(): MessageDao

    /** 对话数据访问对象 */
    abstract fun conversationDao(): ConversationDao

    /** 任务数据访问对象 */
    abstract fun taskDao(): TaskDao

    /** 任务执行记录数据访问对象 */
    abstract fun taskExecutionDao(): TaskExecutionDao
}