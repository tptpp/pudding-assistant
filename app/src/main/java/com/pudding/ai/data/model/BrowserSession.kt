package com.pudding.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cookie 实体
 * 用于持久化保存浏览器会话的 Cookie，实现登录状态保持
 *
 * @property id 唯一标识
 * @property sessionName 会话名称，用于区分不同的网站/服务
 * @property url Cookie 所属的 URL
 * @property cookieJson 序列化的 Cookie JSON 字符串
 * @property createdAt 创建时间
 * @property updatedAt 更新时间
 */
@Entity(tableName = "cookies")
data class CookieEntity(
    @PrimaryKey
    val id: String,                    // sessionName + cookieName 作为主键
    val sessionName: String,           // 会话名称，如 "github", "ticket_site"
    val url: String,                   // Cookie 所属 URL
    val cookieJson: String,            // 序列化的 Cookie JSON
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 浏览器会话状态
 */
enum class BrowserState {
    IDLE,       // 空闲
    LOADING,    // 加载中
    READY,      // 就绪
    ERROR       // 错误
}

/**
 * 浏览器会话
 * 表示一个浏览器会话的状态
 *
 * @property id 会话 ID
 * @property url 当前 URL
 * @property title 页面标题
 * @property state 会话状态
 * @property createdAt 创建时间
 */
data class BrowserSession(
    val id: String,
    val url: String = "",
    val title: String = "",
    val state: BrowserState = BrowserState.IDLE,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 浏览器操作结果
 *
 * @property success 是否成功
 * @property message 结果消息
 * @property data 附加数据（截图、页面内容等）
 */
data class BrowserActionResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)