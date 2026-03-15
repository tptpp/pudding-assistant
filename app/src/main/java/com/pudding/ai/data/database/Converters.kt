package com.pudding.ai.data.database

import androidx.room.TypeConverter
import com.pudding.ai.data.model.*

/**
 * Room 数据库类型转换器
 *
 * 提供 enum 类型与数据库类型之间的转换。
 */
class Converters {

    // ========== MessageType 转换 ==========
    @TypeConverter
    fun fromMessageType(value: MessageType): String {
        return value.name
    }

    @TypeConverter
    fun toMessageType(value: String): MessageType {
        return try {
            MessageType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            MessageType.NORMAL
        }
    }

    // ========== MessageRole 转换 ==========
    @TypeConverter
    fun fromMessageRole(value: MessageRole): String {
        return value.name
    }

    @TypeConverter
    fun toMessageRole(value: String): MessageRole {
        return try {
            MessageRole.valueOf(value)
        } catch (e: IllegalArgumentException) {
            MessageRole.USER
        }
    }

    // ========== ExtractionStatus 转换 ==========
    @TypeConverter
    fun fromExtractionStatus(value: ExtractionStatus): String {
        return value.name
    }

    @TypeConverter
    fun toExtractionStatus(value: String): ExtractionStatus {
        return try {
            ExtractionStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ExtractionStatus.PENDING
        }
    }

    // ========== TaskType 转换 ==========
    @TypeConverter
    fun fromTaskType(value: TaskType): String {
        return value.name
    }

    @TypeConverter
    fun toTaskType(value: String): TaskType {
        return try {
            TaskType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            TaskType.SCHEDULED
        }
    }

    // ========== TaskStatus 转换 ==========
    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String {
        return value.name
    }

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus {
        return try {
            TaskStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            TaskStatus.ACTIVE
        }
    }

    // ========== DebugLogType 转换 ==========
    @TypeConverter
    fun fromDebugLogType(value: DebugLogType): String {
        return value.name
    }

    @TypeConverter
    fun toDebugLogType(value: String): DebugLogType {
        return try {
            DebugLogType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            DebugLogType.MEMORY_GENERATION
        }
    }
}