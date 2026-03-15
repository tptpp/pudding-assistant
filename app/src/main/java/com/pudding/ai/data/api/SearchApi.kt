package com.pudding.ai.data.api

import com.google.gson.annotations.SerializedName
import com.pudding.ai.data.model.SearchConfig
import com.pudding.ai.data.model.SearchProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 搜索结果项
 */
data class SearchResultItem(
    val title: String,
    val snippet: String,
    val url: String,
    val displayUrl: String
)

/**
 * 搜索响应
 */
data class SearchResponse(
    val success: Boolean,
    val results: List<SearchResultItem>,
    val error: String? = null
)

/**
 * 搜索服务接口
 */
interface SearchService {
    suspend fun search(query: String, config: SearchConfig): SearchResponse
}

/**
 * 搜索服务实现
 *
 * 支持以下搜索服务：
 * - 必应中国：无需 API Key，通过网页爬取
 * - 百度搜索：需要 API Key（暂未实现完整 API）
 * - 自定义：用户自定义搜索 API
 */
class SearchServiceImpl : SearchService {

    companion object {
        private const val TAG = "SearchServiceImpl"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    override suspend fun search(query: String, config: SearchConfig): SearchResponse {
        if (!config.enabled) {
            return SearchResponse(false, emptyList(), "搜索功能未启用")
        }

        return when (config.provider) {
            SearchProvider.BING_CN -> searchBingCn(query, config.maxResults)
            SearchProvider.BAIDU -> searchBaidu(query, config)
            SearchProvider.CUSTOM -> searchCustom(query, config)
        }
    }

    /**
     * 必应中国搜索（网页爬取方式）
     */
    private suspend fun searchBingCn(query: String, maxResults: Int): SearchResponse = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://cn.bing.com/search?q=$encodedQuery"

            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()

            val results = doc.select("#b_results > li.b_algo").take(maxResults).map { element ->
                val titleElement = element.selectFirst("h2 a")
                val snippetElement = element.selectFirst(".b_caption p")
                val link = titleElement?.attr("href") ?: ""

                SearchResultItem(
                    title = titleElement?.text() ?: "",
                    snippet = snippetElement?.text() ?: "",
                    url = link,
                    displayUrl = extractDisplayUrl(link)
                )
            }

            SearchResponse(true, results)
        } catch (e: Exception) {
            SearchResponse(false, emptyList(), "必应搜索失败: ${e.message}")
        }
    }

    /**
     * 百度搜索（需要 API Key）
     * 注意：完整实现需要百度搜索 API 的接入
     */
    private suspend fun searchBaidu(query: String, config: SearchConfig): SearchResponse = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext SearchResponse(false, emptyList(), "百度搜索需要配置 API Key")
        }

        try {
            // 使用网页爬取作为备选方案
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://www.baidu.com/s?wd=$encodedQuery"

            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()

            val results = doc.select("#content_left .result").take(config.maxResults).map { element ->
                val titleElement = element.selectFirst("h3 a")
                val snippetElement = element.selectFirst(".c-abstract, .c-span9, .c-color-text")
                val link = titleElement?.attr("href") ?: ""

                SearchResultItem(
                    title = titleElement?.text() ?: "",
                    snippet = snippetElement?.text() ?: "",
                    url = link,
                    displayUrl = extractDisplayUrl(link)
                )
            }

            SearchResponse(true, results)
        } catch (e: Exception) {
            SearchResponse(false, emptyList(), "百度搜索失败: ${e.message}")
        }
    }

    /**
     * 自定义搜索 API
     */
    private suspend fun searchCustom(query: String, config: SearchConfig): SearchResponse = withContext(Dispatchers.IO) {
        if (config.customUrl.isBlank()) {
            return@withContext SearchResponse(false, emptyList(), "自定义搜索需要配置 API URL")
        }

        try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = config.customUrl
                .replace("{query}", encodedQuery)
                .replace("{count}", config.maxResults.toString())

            // 如果有 API Key，添加到请求头
            val connection = java.net.URL(url).openConnection()
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (config.apiKey.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.getInputStream().bufferedReader().readText()
            // 解析响应（根据实际 API 格式调整）
            parseCustomResponse(response, config.maxResults)
        } catch (e: Exception) {
            SearchResponse(false, emptyList(), "自定义搜索失败: ${e.message}")
        }
    }

    /**
     * 解析自定义 API 响应
     */
    private fun parseCustomResponse(response: String, maxResults: Int): SearchResponse {
        // 简单的 JSON 解析，假设返回格式为标准搜索结果
        return try {
            val gson = com.google.gson.Gson()
            val jsonElement = gson.fromJson(response, com.google.gson.JsonElement::class.java)

            val results = mutableListOf<SearchResultItem>()

            if (jsonElement.isJsonArray) {
                jsonElement.asJsonArray.take(maxResults).forEach { item ->
                    val obj = item.asJsonObject
                    results.add(
                        SearchResultItem(
                            title = obj.get("title")?.asString ?: "",
                            snippet = obj.get("snippet")?.asString ?: obj.get("description")?.asString ?: "",
                            url = obj.get("url")?.asString ?: obj.get("link")?.asString ?: "",
                            displayUrl = extractDisplayUrl(obj.get("url")?.asString ?: obj.get("link")?.asString ?: "")
                        )
                    )
                }
            } else if (jsonElement.isJsonObject) {
                val obj = jsonElement.asJsonObject
                val itemsArray = obj.get("results")?.asJsonArray
                    ?: obj.get("items")?.asJsonArray
                    ?: obj.get("data")?.asJsonArray

                itemsArray?.take(maxResults)?.forEach { item ->
                    val itemObj = item.asJsonObject
                    results.add(
                        SearchResultItem(
                            title = itemObj.get("title")?.asString ?: "",
                            snippet = itemObj.get("snippet")?.asString ?: itemObj.get("description")?.asString ?: "",
                            url = itemObj.get("url")?.asString ?: itemObj.get("link")?.asString ?: "",
                            displayUrl = extractDisplayUrl(itemObj.get("url")?.asString ?: itemObj.get("link")?.asString ?: "")
                        )
                    )
                }
            }

            SearchResponse(true, results)
        } catch (e: Exception) {
            SearchResponse(false, emptyList(), "解析搜索结果失败: ${e.message}")
        }
    }

    /**
     * 提取显示用的短 URL
     */
    private fun extractDisplayUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            uri.host ?: url
        } catch (e: Exception) {
            url
        }
    }
}