package com.pudding.ai

import androidx.lifecycle.ViewModel
import com.pudding.ai.data.repository.DebugLogRepository
import com.pudding.ai.service.DailyMemoryService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 调试 ViewModel
 *
 * 负责调试相关的操作，包括：
 * - 记忆生成调试
 * - 工具调用调试
 * - 任务执行调试
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val debugLogRepository: DebugLogRepository,
    private val dailyMemoryService: DailyMemoryService
) : ViewModel() {

    /**
     * 获取调试日志仓库
     */
    fun getDebugLogRepository(): DebugLogRepository = debugLogRepository

    /**
     * 生成每日记忆（带调试）
     */
    suspend fun generateDailyMemoryWithDebug(date: String): Pair<Boolean, Long?> {
        val result = dailyMemoryService.generateDailyMemoryWithDebug(date)
        return Pair(result.first != null, result.second)
    }
}