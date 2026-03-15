package com.pudding.ai.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pudding.ai.data.model.ApiProvider
import com.pudding.ai.data.model.ModelConfig
import com.pudding.ai.data.model.SearchConfig
import com.pudding.ai.data.model.SearchProvider
import com.pudding.ai.util.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置仓库
 *
 * 管理应用配置的持久化存储：
 * - 普通配置使用 DataStore 存储
 * - 敏感信息（API 密钥）使用 EncryptedSharedPreferences 安全存储
 */
class SettingsRepository(private val context: Context) {

    private val secureStorage = SecureStorage(context)

    companion object {
        private val BASE_URL = stringPreferencesKey("base_url")
        private val MODEL = stringPreferencesKey("model")
        private val PROVIDER = stringPreferencesKey("provider")
        private val TEMPERATURE = stringPreferencesKey("temperature")
        private val MAX_TOKENS = stringPreferencesKey("max_tokens")

        // 搜索配置
        private val SEARCH_ENABLED = stringPreferencesKey("search_enabled")
        private val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        private val SEARCH_CUSTOM_URL = stringPreferencesKey("search_custom_url")
        private val SEARCH_MAX_RESULTS = stringPreferencesKey("search_max_results")
    }

    val modelConfig: Flow<ModelConfig> = context.dataStore.data.map { prefs ->
        val apiKey = secureStorage.getSecure(SecureStorage.KEY_API_KEY)
        android.util.Log.d("SettingsRepository", "Loading config: apiKey=${apiKey?.take(10)}..., baseUrl=${prefs[BASE_URL]}, model=${prefs[MODEL]}")
        ModelConfig(
            provider = try {
                ApiProvider.valueOf(prefs[PROVIDER] ?: "OPENAI")
            } catch (e: IllegalArgumentException) {
                ApiProvider.OPENAI
            },
            baseUrl = prefs[BASE_URL] ?: "",
            // API 密钥从安全存储读取
            apiKey = apiKey ?: "",
            model = prefs[MODEL] ?: "",
            temperature = prefs[TEMPERATURE]?.toFloatOrNull() ?: 0.7f,
            maxTokens = prefs[MAX_TOKENS]?.toIntOrNull() ?: 4096
        )
    }

    suspend fun saveModelConfig(config: ModelConfig) {
        // API 密钥使用安全存储
        secureStorage.putSecure(SecureStorage.KEY_API_KEY, config.apiKey)

        // 其他配置使用普通存储
        context.dataStore.edit { prefs ->
            prefs[BASE_URL] = config.baseUrl
            prefs[MODEL] = config.model
            prefs[PROVIDER] = config.provider.name
            prefs[TEMPERATURE] = config.temperature.toString()
            prefs[MAX_TOKENS] = config.maxTokens.toString()
        }
    }

    // 搜索配置
    val searchConfig: Flow<SearchConfig> = context.dataStore.data.map { prefs ->
        SearchConfig(
            enabled = prefs[SEARCH_ENABLED]?.toBoolean() ?: false,
            provider = try {
                SearchProvider.valueOf(prefs[SEARCH_PROVIDER] ?: "BING_CN")
            } catch (e: IllegalArgumentException) {
                SearchProvider.BING_CN
            },
            // 搜索 API 密钥从安全存储读取
            apiKey = secureStorage.getSecure(SecureStorage.KEY_SEARCH_API_KEY),
            customUrl = prefs[SEARCH_CUSTOM_URL] ?: "",
            maxResults = prefs[SEARCH_MAX_RESULTS]?.toIntOrNull() ?: 5
        )
    }

    suspend fun saveSearchConfig(config: SearchConfig) {
        // API 密钥使用安全存储
        secureStorage.putSecure(SecureStorage.KEY_SEARCH_API_KEY, config.apiKey)

        // 其他配置使用普通存储
        context.dataStore.edit { prefs ->
            prefs[SEARCH_ENABLED] = config.enabled.toString()
            prefs[SEARCH_PROVIDER] = config.provider.name
            prefs[SEARCH_CUSTOM_URL] = config.customUrl
            prefs[SEARCH_MAX_RESULTS] = config.maxResults.toString()
        }
    }

    /**
     * 清除所有敏感数据（用于退出登录）
     */
    fun clearSensitiveData() {
        secureStorage.clearAll()
    }
}