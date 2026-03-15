package com.pudding.ai.data.repository

import android.util.Log
import com.google.gson.Gson
import com.pudding.ai.data.database.DebugLogConfigDao
import com.pudding.ai.data.database.DebugLogDao
import com.pudding.ai.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 调试日志仓库
 *
 * 封装调试日志的增删改查操作，实现自动清理逻辑。
 * 只负责存储和查询，日志构建逻辑由各 DebugLogBuilder 实现。
 */
class DebugLogRepository(
    private val debugLogDao: DebugLogDao,
    private val configDao: DebugLogConfigDao,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "DebugLogRepository"
        private const val DEFAULT_MAX_RECORDS = 5
    }

    // ========== 查询操作 ==========

    /**
     * 获取所有日志
     */
    fun getAllLogs(): Flow<List<DebugLog>> = debugLogDao.getAllLogs()

    /**
     * 按类型获取日志
     */
    fun getLogsByType(type: DebugLogType): Flow<List<DebugLog>> = debugLogDao.getLogsByType(type)

    /**
     * 根据 ID 获取日志
     */
    suspend fun getLogById(id: Long): DebugLog? = debugLogDao.getLogById(id)

    /**
     * 获取某类型的最近日志
     */
    suspend fun getRecentLogs(type: DebugLogType, limit: Int = 10): List<DebugLog> =
        debugLogDao.getRecentLogsByType(type, limit)

    // ========== 写入操作 ==========

    /**
     * 插入日志并自动清理旧记录
     */
    suspend fun saveLog(log: DebugLog): Long {
        val id = debugLogDao.insertLog(log)
        Log.d(TAG, "Saved debug log: type=${log.type}, title=${log.title}, id=$id")

        // 自动清理超出限制的旧记录
        val maxRecords = getMaxRecords(log.type)
        debugLogDao.deleteOldRecords(log.type, maxRecords)

        return id
    }

    /**
     * 更新日志
     */
    suspend fun updateLog(log: DebugLog) {
        debugLogDao.updateLog(log)
    }

    /**
     * 删除日志
     */
    suspend fun deleteLog(log: DebugLog) {
        debugLogDao.deleteLog(log)
    }

    /**
     * 删除某类型的所有日志
     */
    suspend fun deleteLogsByType(type: DebugLogType) {
        debugLogDao.deleteLogsByType(type)
        Log.d(TAG, "Deleted all logs of type: $type")
    }

    /**
     * 清空所有日志
     */
    suspend fun clearAllLogs() {
        debugLogDao.deleteAllLogs()
        Log.d(TAG, "Cleared all debug logs")
    }

    // ========== 配置操作 ==========

    /**
     * 获取某类型的最大记录数
     */
    suspend fun getMaxRecords(type: DebugLogType): Int {
        return configDao.getConfig(type)?.maxRecords ?: DEFAULT_MAX_RECORDS
    }

    /**
     * 更新某类型的最大记录数
     */
    suspend fun updateMaxRecords(type: DebugLogType, maxRecords: Int) {
        configDao.upsertConfig(DebugLogConfig(type, maxRecords))
        Log.d(TAG, "Updated max records for $type: $maxRecords")

        // 立即清理超出新限制的记录
        debugLogDao.deleteOldRecords(type, maxRecords)
    }

    /**
     * 重置为默认配置
     */
    suspend fun resetConfig(type: DebugLogType) {
        configDao.resetConfig(type)
    }

    // ========== JSON 解析工具（供 UI 使用）==========

    /**
     * 解析记忆生成输入数据
     */
    fun parseMemoryInput(json: String): MemoryGenerationInput? {
        return try {
            gson.fromJson(json, MemoryGenerationInput::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MemoryGenerationInput", e)
            null
        }
    }

    /**
     * 解析记忆生成输出数据
     */
    fun parseMemoryOutput(json: String): MemoryGenerationOutput? {
        return try {
            gson.fromJson(json, MemoryGenerationOutput::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MemoryGenerationOutput", e)
            null
        }
    }

    /**
     * 解析工具调用输入数据
     */
    fun parseToolCallInput(json: String): ToolCallInput? {
        return try {
            gson.fromJson(json, ToolCallInput::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ToolCallInput", e)
            null
        }
    }

    /**
     * 解析工具调用输出数据
     */
    fun parseToolCallOutput(json: String): ToolCallOutput? {
        return try {
            gson.fromJson(json, ToolCallOutput::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ToolCallOutput", e)
            null
        }
    }

    /**
     * 解析工具调用元数据
     */
    fun parseToolCallMeta(json: String): ToolCallMeta? {
        return try {
            gson.fromJson(json, ToolCallMeta::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ToolCallMeta", e)
            null
        }
    }

    /**
     * 解析任务执行输入数据
     */
    fun parseTaskExecutionInput(json: String): TaskExecutionInput? {
        return try {
            gson.fromJson(json, TaskExecutionInput::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TaskExecutionInput", e)
            null
        }
    }

    /**
     * 解析任务执行输出数据
     */
    fun parseTaskExecutionOutput(json: String): TaskExecutionOutput? {
        return try {
            gson.fromJson(json, TaskExecutionOutput::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TaskExecutionOutput", e)
            null
        }
    }

    /**
     * 解析任务执行元数据
     */
    fun parseTaskExecutionMeta(json: String): TaskExecutionMeta? {
        return try {
            gson.fromJson(json, TaskExecutionMeta::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TaskExecutionMeta", e)
            null
        }
    }

    /**
     * 通用 JSON 解析方法
     */
    fun <T> parseJson(json: String, clazz: Class<T>): T? {
        return try {
            gson.fromJson(json, clazz)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON to ${clazz.simpleName}", e)
            null
        }
    }
}