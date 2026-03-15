package com.pudding.ai.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 安全存储工具类
 *
 * 使用 EncryptedSharedPreferences 安全存储敏感信息，如 API 密钥。
 * 基于 Android Keystore 进行加密，即使设备被 root 也难以直接读取。
 */
class SecureStorage(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * 安全保存敏感数据
     */
    fun putSecure(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    /**
     * 获取安全存储的数据
     */
    fun getSecure(key: String, defaultValue: String = ""): String {
        val value = encryptedPrefs.getString(key, defaultValue) ?: defaultValue
        android.util.Log.d("SecureStorage", "getSecure: key=$key, value=${value.take(10)}...")
        return value
    }

    /**
     * 删除安全存储的数据
     */
    fun removeSecure(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    /**
     * 检查是否包含某个键
     */
    fun contains(key: String): Boolean {
        return encryptedPrefs.contains(key)
    }

    /**
     * 清空所有安全存储的数据
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    companion object {
        const val SECURE_PREFS_NAME = "secure_prefs"

        // 敏感数据键名
        const val KEY_API_KEY = "api_key"
        const val KEY_SEARCH_API_KEY = "search_api_key"
    }
}