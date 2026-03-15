package com.pudding.ai.data.database

import androidx.room.*
import com.pudding.ai.data.model.DebugLog
import com.pudding.ai.data.model.DebugLogType
import kotlinx.coroutines.flow.Flow

/**
 * 调试日志数据访问对象
 */
@Dao
interface DebugLogDao {

    /**
     * 插入调试日志
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DebugLog): Long

    /**
     * 更新调试日志
     */
    @Update
    suspend fun updateLog(log: DebugLog)

    /**
     * 删除调试日志
     */
    @Delete
    suspend fun deleteLog(log: DebugLog)

    /**
     * 根据 ID 获取日志
     */
    @Query("SELECT * FROM debug_logs WHERE id = :id")
    suspend fun getLogById(id: Long): DebugLog?

    /**
     * 获取所有日志
     */
    @Query("SELECT * FROM debug_logs ORDER BY createdAt DESC")
    fun getAllLogs(): Flow<List<DebugLog>>

    /**
     * 按类型获取日志
     */
    @Query("SELECT * FROM debug_logs WHERE type = :type ORDER BY createdAt DESC")
    fun getLogsByType(type: DebugLogType): Flow<List<DebugLog>>

    /**
     * 按类型获取最近的 N 条日志
     */
    @Query("SELECT * FROM debug_logs WHERE type = :type ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentLogsByType(type: DebugLogType, limit: Int): List<DebugLog>

    /**
     * 获取某类型的日志数量
     */
    @Query("SELECT COUNT(*) FROM debug_logs WHERE type = :type")
    suspend fun getLogCountByType(type: DebugLogType): Int

    /**
     * 删除某类型的旧日志（保留最近 keepCount 条）
     */
    @Query("""
        DELETE FROM debug_logs
        WHERE type = :type AND id NOT IN (
            SELECT id FROM debug_logs
            WHERE type = :type
            ORDER BY createdAt DESC
            LIMIT :keepCount
        )
    """)
    suspend fun deleteOldRecords(type: DebugLogType, keepCount: Int)

    /**
     * 删除某类型的所有日志
     */
    @Query("DELETE FROM debug_logs WHERE type = :type")
    suspend fun deleteLogsByType(type: DebugLogType)

    /**
     * 删除所有日志
     */
    @Query("DELETE FROM debug_logs")
    suspend fun deleteAllLogs()
}