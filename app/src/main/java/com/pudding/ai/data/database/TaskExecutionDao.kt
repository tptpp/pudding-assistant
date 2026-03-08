package com.pudding.ai.data.database

import androidx.room.*
import com.pudding.ai.data.model.TaskExecution
import kotlinx.coroutines.flow.Flow

/**
 * 任务执行记录数据访问对象
 *
 * 提供任务执行记录的增删改查操作。
 */
@Dao
interface TaskExecutionDao {
    /**
     * 获取指定任务的执行记录
     *
     * @param taskId 任务ID
     * @return 执行记录列表 Flow，按执行时间降序排列
     */
    @Query("SELECT * FROM task_executions WHERE taskId = :taskId ORDER BY executedAt DESC")
    fun getExecutionsByTask(taskId: Long): Flow<List<TaskExecution>>

    /**
     * 获取最近的执行记录
     *
     * @param limit 返回数量限制，默认50条
     * @return 执行记录列表 Flow
     */
    @Query("SELECT * FROM task_executions ORDER BY executedAt DESC LIMIT :limit")
    fun getRecentExecutions(limit: Int = 50): Flow<List<TaskExecution>>

    /**
     * 获取所有执行记录
     *
     * @return 执行记录列表 Flow，按执行时间降序排列
     */
    @Query("SELECT * FROM task_executions ORDER BY executedAt DESC")
    fun getAllExecutions(): Flow<List<TaskExecution>>

    /**
     * 插入执行记录
     *
     * @param execution 执行记录实体
     * @return 插入的记录ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(execution: TaskExecution): Long

    /**
     * 删除指定任务的所有执行记录
     *
     * @param taskId 任务ID
     */
    @Query("DELETE FROM task_executions WHERE taskId = :taskId")
    suspend fun deleteExecutionsByTask(taskId: Long)
}