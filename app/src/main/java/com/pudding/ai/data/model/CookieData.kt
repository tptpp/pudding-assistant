package com.pudding.ai.data.model

/**
 * 保存的 Cookie 数据结构
 * 用于在 WebView 和数据库之间传递 Cookie 信息
 *
 * @property name Cookie 名称
 * @property value Cookie 值
 * @property domain Cookie 域名
 * @property path Cookie 路径
 * @property expires 过期时间（时间戳，秒）
 * @property secure 是否仅 HTTPS
 * @property httpOnly 是否仅 HTTP
 */
data class SavedCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expires: Long = 0,
    val secure: Boolean = false,
    val httpOnly: Boolean = false
)