package com.pudding.ai.data.repository

import android.util.Log
import com.pudding.ai.data.api.SearchResponse
import com.pudding.ai.data.api.SearchResultItem
import com.pudding.ai.data.api.SearchService
import com.pudding.ai.data.api.SearchServiceImpl
import com.pudding.ai.data.model.SearchConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 搜索仓库
 *
 * 封装搜索功能，提供统一的搜索接口。
 */
class SearchRepository(
    private val settingsRepository: SettingsRepository,
    private val searchService: SearchService = SearchServiceImpl()
) {
    companion object {
        private const val TAG = "SearchRepository"
    }

    /**
     * 获取搜索配置
     */
    val searchConfig: Flow<SearchConfig> = settingsRepository.searchConfig

    /**
     * 执行搜索
     *
     * @param query 搜索关键词
     * @return 格式化的搜索结果字符串，供 AI 使用
     */
    suspend fun search(query: String): String {
        val config = settingsRepository.searchConfig.first()

        if (!config.enabled) {
            return "搜索功能未启用。请在设置中开启搜索功能。"
        }

        Log.d(TAG, "Searching for: $query with provider: ${config.provider}")

        val response = searchService.search(query, config)

        if (!response.success) {
            return "搜索失败: ${response.error}"
        }

        if (response.results.isEmpty()) {
            return "未找到相关结果。"
        }

        return formatSearchResults(response.results)
    }

    /**
     * 执行搜索并返回原始结果
     */
    suspend fun searchRaw(query: String): SearchResponse {
        val config = settingsRepository.searchConfig.first()
        return searchService.search(query, config)
    }

    /**
     * 格式化搜索结果为 AI 可读格式
     */
    private fun formatSearchResults(results: List<SearchResultItem>): String {
        return buildString {
            append("找到 ${results.size} 条搜索结果：\n\n")

            results.forEachIndexed { index, result ->
                append("${index + 1}. 【${result.title}】\n")
                append("   ${result.snippet}\n")
                append("   来源: ${result.displayUrl}\n\n")
            }
        }
    }
}