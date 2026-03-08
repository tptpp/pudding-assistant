package com.pudding.ai.data.database

import androidx.room.*
import com.pudding.ai.data.model.Task
import com.pudding.ai.data.model.TaskStatus
import com.pudding.ai.data.model.TaskType
import kotlinx.coroutines.flow.Flow

/**
 * 任务数据访问对象
 *
 * 提供任务的增删改查操作。
 */
@Dao
interface TaskDao {
    /**
     * 获取所有任务
     *
     * @return 任务列表 Flow，按下次执行时间升序排列
     */
    @Query("SELECT * FROM tasks ORDER BY nextRunAt ASC")
    fun getAllTasks(): Flow<List<Task>>

    /**
     * 根据类型获取任务
     *
     * @param type 任务类型
     * @return 任务列表 Flow，按下次执行时间升序排列
     */
    @Query("SELECT * FROM tasks WHERE type = :type ORDER BY nextRunAt ASC")
    fun getTasksByType(type: TaskType): Flow<List<Task>>

    /**
     * 根据状态获取任务
     *
     * @param status 任务状态
     * @return 任务列表 Flow，按下次执行时间升序排列
     */
    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY nextRunAt ASC")
    fun getTasksByStatus(status: TaskStatus): Flow<List<Task>>

    /**
     * 根据ID获取任务
     *
     * @param id 任务ID
     * @return 任务实体，不存在则返回 null
     */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    /**
     * 插入任务
     *
     * @param task 任务实体
     * @return 插入的任务ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    /**
     * 更新任务
     *
     * @param task 任务实体
     */
    @Update
    suspend fun updateTask(task: Task)

    /**
     * 删除任务
     *
     * @param task 任务实体
     */
    @Delete
    suspend fun deleteTask(task: Task)

    /**
     * 更新任务状态
     *
     * @param id 任务ID
     * @param status 新状态
     */
    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateTaskStatus(id: Long, status: TaskStatus)
}