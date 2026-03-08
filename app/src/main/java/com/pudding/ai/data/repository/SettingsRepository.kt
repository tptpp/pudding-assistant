package com.pudding.ai.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pudding.ai.data.model.ApiProvider
import com.pudding.ai.data.model.ModelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val BASE_URL = stringPreferencesKey("base_url")
        private val MODEL = stringPreferencesKey("model")
        private val PROVIDER = stringPreferencesKey("provider")
        private val TEMPERATURE = stringPreferencesKey("temperature")
        private val MAX_TOKENS = stringPreferencesKey("max_tokens")
    }

    val modelConfig: Flow<ModelConfig> = context.dataStore.data.map { prefs ->
        ModelConfig(
            provider = try {
                ApiProvider.valueOf(prefs[PROVIDER] ?: "OPENAI")
            } catch (e: IllegalArgumentException) {
                ApiProvider.OPENAI
            },
            baseUrl = prefs[BASE_URL] ?: "",
            apiKey = prefs[API_KEY] ?: "",
            model = prefs[MODEL] ?: "",
            temperature = prefs[TEMPERATURE]?.toFloatOrNull() ?: 0.7f,
            maxTokens = prefs[MAX_TOKENS]?.toIntOrNull() ?: 4096
        )
    }

    suspend fun saveModelConfig(config: ModelConfig) {
        context.dataStore.edit { prefs ->
            prefs[API_KEY] = config.apiKey
            prefs[BASE_URL] = config.baseUrl
            prefs[MODEL] = config.model
            prefs[PROVIDER] = config.provider.name
            prefs[TEMPERATURE] = config.temperature.toString()
            prefs[MAX_TOKENS] = config.maxTokens.toString()
        }
    }
}