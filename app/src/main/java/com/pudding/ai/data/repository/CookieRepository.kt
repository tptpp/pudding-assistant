package com.pudding.ai.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pudding.ai.data.database.CookieDao
import com.pudding.ai.data.model.CookieEntity
import com.pudding.ai.data.model.SavedCookie
import java.net.URL
import java.util.UUID

/**
 * Cookie 仓库
 *
 * 负责管理浏览器会话 Cookie 的持久化存储和检索
 */
class CookieRepository(
    private val cookieDao: CookieDao
) {
    companion object {
        private const val TAG = "CookieRepository"
    }

    private val gson = Gson()

    /**
     * 保存 Cookie 列表到指定会话
     *
     * @param sessionName 会话名称
     * @param url Cookie 所属 URL
     * @param cookies Cookie 列表
     */
    suspend fun saveCookies(
        sessionName: String,
        url: String,
        cookies: List<SavedCookie>
    ) {
        try {
            val entities = cookies.map { cookie ->
                val id = "${sessionName}_${cookie.name}_${cookie.domain}"
                CookieEntity(
                    id = id,
                    sessionName = sessionName,
                    url = url,
                    cookieJson = gson.toJson(cookie),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }

            cookieDao.saveCookies(entities)
            Log.d(TAG, "Saved ${cookies.size} cookies for session: $sessionName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cookies for session: $sessionName", e)
            throw e
        }
    }

    /**
     * 加载指定会话的 Cookie 列表
     *
     * @param sessionName 会话名称
     * @return Cookie 列表
     */
    suspend fun loadCookies(sessionName: String): List<SavedCookie> {
        return try {
            val entities = cookieDao.getCookiesBySession(sessionName)
            entities.mapNotNull { entity ->
                try {
                    gson.fromJson(entity.cookieJson, SavedCookie::class.java)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse cookie: ${entity.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cookies for session: $sessionName", e)
            emptyList()
        }
    }

    /**
     * 清除指定会话的所有 Cookie
     *
     * @param sessionName 会话名称
     */
    suspend fun clearCookies(sessionName: String) {
        try {
            cookieDao.clearSessionCookies(sessionName)
            Log.d(TAG, "Cleared cookies for session: $sessionName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cookies for session: $sessionName", e)
            throw e
        }
    }

    /**
     * 获取所有会话名称
     *
     * @return 会话名称列表
     */
    suspend fun getAllSessionNames(): List<String> {
        return try {
            cookieDao.getAllSessionNames()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get session names", e)
            emptyList()
        }
    }

    /**
     * 删除所有 Cookie
     */
    suspend fun deleteAllCookies() {
        try {
            cookieDao.deleteAllCookies()
            Log.d(TAG, "Deleted all cookies")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete all cookies", e)
            throw e
        }
    }

    /**
     * 获取指定会话的 Cookie 数量
     *
     * @param sessionName 会话名称
     * @return Cookie 数量
     */
    suspend fun getCookieCount(sessionName: String): Int {
        return try {
            cookieDao.getCookieCount(sessionName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get cookie count for session: $sessionName", e)
            0
        }
    }

    /**
     * 从 URL 提取域名
     */
    fun extractDomain(url: String): String {
        return try {
            URL(url).host
        } catch (e: Exception) {
            url
        }
    }
}