package com.pudding.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pudding.ai.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 任务 ViewModel
 *
 * 负责任务的管理，包括：
 * - 任务的创建、更新、删除
 * - 任务状态的切换
 * - 任务执行记录的管理
 */
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AssistantApp
    private val database = app.database
    private val taskDao = database.taskDao()
    private val taskExecutionDao = database.taskExecutionDao()

    // 任务列表
    val tasks: Flow<List<Task>> = taskDao.getAllTasks()

    // 执行记录
    val executions: Flow<List<TaskExecution>> = taskExecutionDao.getAllExecutions()

    /**
     * 保存任务
     *
     * @param task 任务实体，id为0表示新建任务
     */
    fun saveTask(task: Task) {
        viewModelScope.launch {
            if (task.id == 0L) {
                val id = taskDao.insertTask(task)
                app.taskScheduler?.scheduleTask(task.copy(id = id))
            } else {
                taskDao.updateTask(task)
                app.taskScheduler?.scheduleTask(task)
            }
        }
    }

    /**
     * 切换任务状态（启用/暂停）
     */
    fun toggleTask(task: Task) {
        viewModelScope.launch {
            val newStatus = when (task.status) {
                TaskStatus.ACTIVE -> TaskStatus.PAUSED
                else -> TaskStatus.ACTIVE
            }
            val updated = task.copy(status = newStatus)
            taskDao.updateTask(updated)

            if (newStatus == TaskStatus.ACTIVE) app.taskScheduler?.scheduleTask(updated)
            else app.taskScheduler?.cancelTask(updated)
        }
    }

    /**
     * 删除任务
     */
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            app.taskScheduler?.cancelTask(task)
            taskDao.deleteTask(task)
            taskExecutionDao.deleteExecutionsByTask(task.id)
        }
    }

    /**
     * 快速创建任务（从输入框）
     *
     * @param input 任务描述，默认创建1小时后执行的一次性任务
     */
    fun quickCreateTask(input: String) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR_OF_DAY, 1) // 默认1小时后

            val task = Task(
                title = input.take(30),
                prompt = input,
                type = TaskType.ONE_TIME,
                scheduledTime = calendar.timeInMillis,
                source = "quick"
            )

            val id = taskDao.insertTask(task)
            app.taskScheduler?.scheduleTask(task.copy(id = id))
        }
    }
}