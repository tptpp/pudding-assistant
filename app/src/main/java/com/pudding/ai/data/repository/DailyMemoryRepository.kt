package com.pudding.ai.data.repository

import com.pudding.ai.data.database.MemoryDao
import com.pudding.ai.data.model.DailyMemory
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日记忆仓库
 *
 * 封装每日记忆的数据访问操作。
 */
@Singleton
class DailyMemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao
) {
    /**
     * 获取所有每日记忆
     */
    fun getAllDailyMemories(): Flow<List<DailyMemory>> {
        return memoryDao.getAllDailyMemories()
    }

    /**
     * 根据日期获取每日记忆
     */
    suspend fun getDailyMemoryByDate(date: String): DailyMemory? {
        return memoryDao.getDailyMemoryByDate(date)
    }

    /**
     * 搜索每日记忆
     */
    fun searchDailyMemories(keyword: String): Flow<List<DailyMemory>> {
        return memoryDao.searchDailyMemories(keyword)
    }
}