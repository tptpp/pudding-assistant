package com.pudding.ai

import androidx.lifecycle.ViewModel
import com.pudding.ai.data.database.MemoryDao
import com.pudding.ai.data.model.DailyMemory
import com.pudding.ai.data.repository.DailyMemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 记忆 ViewModel
 *
 * 负责每日记忆的管理，包括：
 * - 获取和展示每日记忆
 * - 搜索记忆
 */
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: DailyMemoryRepository
) : ViewModel() {

    /**
     * 获取所有每日记忆
     */
    fun getAllDailyMemories(): Flow<List<DailyMemory>> {
        return memoryRepository.getAllDailyMemories()
    }

    /**
     * 根据日期获取每日记忆
     */
    suspend fun getDailyMemoryByDate(date: String): DailyMemory? {
        return memoryRepository.getDailyMemoryByDate(date)
    }

    /**
     * 搜索每日记忆
     */
    fun searchDailyMemories(keyword: String): Flow<List<DailyMemory>> {
        return memoryRepository.searchDailyMemories(keyword)
    }
}