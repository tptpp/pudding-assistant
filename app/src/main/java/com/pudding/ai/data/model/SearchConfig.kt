package com.pudding.ai.data.model

/**
 * 搜索服务提供商枚举
 */
enum class SearchProvider {
    BING_CN,      // 必应中国（无需 API Key）
    BAIDU,        // 百度搜索（需要 API Key）
    CUSTOM        // 自定义搜索 API
}

/**
 * 搜索配置
 *
 * @param enabled 是否启用搜索功能
 * @param provider 搜索服务提供商
 * @param apiKey API 密钥（百度/自定义 API 需要）
 * @param customUrl 自定义 API URL
 * @param maxResults 最大返回结果数
 */
data class SearchConfig(
    val enabled: Boolean = false,
    val provider: SearchProvider = SearchProvider.BING_CN,
    val apiKey: String = "",
    val customUrl: String = "",
    val maxResults: Int = 5
)