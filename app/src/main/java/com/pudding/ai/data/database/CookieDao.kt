package com.pudding.ai.data.database

import androidx.room.*
import com.pudding.ai.data.model.CookieEntity
import kotlinx.coroutines.flow.Flow

/**
 * Cookie 数据访问对象
 *
 * 提供浏览器会话 Cookie 的持久化存储功能
 */
@Dao
interface CookieDao {

    /**
     * 获取指定会话的所有 Cookie
     *
     * @param sessionName 会话名称
     * @return Cookie 列表
     */
    @Query("SELECT * FROM cookies WHERE sessionName = :sessionName")
    suspend fun getCookiesBySession(sessionName: String): List<CookieEntity>

    /**
     * 获取所有会话名称
     *
     * @return 会话名称列表
     */
    @Query("SELECT DISTINCT sessionName FROM cookies")
    suspend fun getAllSessionNames(): List<String>

    /**
     * 观察所有会话名称
     *
     * @return 会话名称 Flow
     */
    @Query("SELECT DISTINCT sessionName FROM cookies")
    fun observeAllSessionNames(): Flow<List<String>>

    /**
     * 保存 Cookie（插入或更新）
     *
     * @param cookie Cookie 实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCookie(cookie: CookieEntity)

    /**
     * 批量保存 Cookie
     *
     * @param cookies Cookie 列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCookies(cookies: List<CookieEntity>)

    /**
     * 删除指定会话的所有 Cookie
     *
     * @param sessionName 会话名称
     */
    @Query("DELETE FROM cookies WHERE sessionName = :sessionName")
    suspend fun clearSessionCookies(sessionName: String)

    /**
     * 删除单个 Cookie
     *
     * @param id Cookie ID
     */
    @Query("DELETE FROM cookies WHERE id = :id")
    suspend fun deleteCookie(id: String)

    /**
     * 删除所有 Cookie
     */
    @Query("DELETE FROM cookies")
    suspend fun deleteAllCookies()

    /**
     * 获取 Cookie 数量
     *
     * @param sessionName 会话名称（可选）
     * @return Cookie 数量
     */
    @Query("SELECT COUNT(*) FROM cookies WHERE sessionName = :sessionName")
    suspend fun getCookieCount(sessionName: String): Int

    /**
     * 获取所有 Cookie
     *
     * @return 所有 Cookie 列表
     */
    @Query("SELECT * FROM cookies ORDER BY sessionName, updatedAt DESC")
    suspend fun getAllCookies(): List<CookieEntity>
}