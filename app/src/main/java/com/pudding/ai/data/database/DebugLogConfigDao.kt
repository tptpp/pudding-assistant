package com.pudding.ai.data.database

import androidx.room.*
import com.pudding.ai.data.model.DebugLogConfig
import com.pudding.ai.data.model.DebugLogType

/**
 * 调试日志配置数据访问对象
 */
@Dao
interface DebugLogConfigDao {

    /**
     * 插入或更新配置
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: DebugLogConfig)

    /**
     * 获取某类型的配置
     */
    @Query("SELECT * FROM debug_log_configs WHERE type = :type")
    suspend fun getConfig(type: DebugLogType): DebugLogConfig?

    /**
     * 获取所有配置
     */
    @Query("SELECT * FROM debug_log_configs")
    suspend fun getAllConfigs(): List<DebugLogConfig>

    /**
     * 删除某类型的配置
     */
    @Delete
    suspend fun deleteConfig(config: DebugLogConfig)

    /**
     * 重置为默认配置
     */
    @Query("DELETE FROM debug_log_configs WHERE type = :type")
    suspend fun resetConfig(type: DebugLogType)
}