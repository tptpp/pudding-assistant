package com.pudding.ai.util

/**
 * 应用错误基类
 *
 * 统一的错误类型定义，用于区分不同类型的错误并提供用户友好的错误消息。
 */
sealed class AppError : Exception() {

    /**
     * 网络错误
     *
     * @param message 错误消息
     */
    data class NetworkError(override val message: String) : AppError() {
        override fun toString(): String = "NetworkError: $message"
    }

    /**
     * API 错误
     *
     * @param code HTTP 状态码
     * @param message 错误消息
     */
    data class ApiError(val code: Int, override val message: String) : AppError() {
        override fun toString(): String = "ApiError($code): $message"
    }

    /**
     * 数据库错误
     *
     * @param message 错误消息
     */
    data class DatabaseError(override val message: String) : AppError() {
        override fun toString(): String = "DatabaseError: $message"
    }

    /**
     * 验证错误
     *
     * @param message 错误消息
     */
    data class ValidationError(override val message: String) : AppError() {
        override fun toString(): String = "ValidationError: $message"
    }

    /**
     * 未授权错误
     *
     * @param message 错误消息
     */
    data class UnauthorizedError(override val message: String = "未授权，请检查API密钥") : AppError() {
        override fun toString(): String = "UnauthorizedError: $message"
    }

    /**
     * 超时错误
     *
     * @param message 错误消息
     */
    data class TimeoutError(override val message: String = "请求超时") : AppError() {
        override fun toString(): String = "TimeoutError: $message"
    }

    /**
     * 服务不可用错误
     *
     * @param message 错误消息
     */
    data class ServiceUnavailableError(override val message: String = "服务暂时不可用") : AppError() {
        override fun toString(): String = "ServiceUnavailableError: $message"
    }

    /**
     * 未知错误
     *
     * @param message 错误消息
     */
    data class UnknownError(override val message: String) : AppError() {
        override fun toString(): String = "UnknownError: $message"
    }

    /**
     * 获取用户友好的错误消息
     */
    fun getUserFriendlyMessage(): String {
        return when (this) {
            is NetworkError -> "网络连接失败，请检查网络设置"
            is ApiError -> when (code) {
                400 -> "请求参数错误"
                401 -> "API 密钥无效或未授权"
                403 -> "访问被拒绝"
                404 -> "请求的资源不存在"
                429 -> "请求过于频繁，请稍后重试"
                500 -> "服务器内部错误"
                502 -> "网关错误"
                503 -> "服务暂时不可用"
                else -> "API 错误 ($code): $message"
            }
            is DatabaseError -> "数据操作失败: $message"
            is ValidationError -> "输入验证失败: $message"
            is UnauthorizedError -> "未授权，请检查 API 密钥设置"
            is TimeoutError -> "请求超时，请稍后重试"
            is ServiceUnavailableError -> "服务暂时不可用，请稍后重试"
            is UnknownError -> "发生未知错误: $message"
        }
    }

    companion object {
        /**
         * 从异常创建 AppError
         */
        fun fromException(e: Exception): AppError {
            return when (e) {
                is AppError -> e
                is java.net.UnknownHostException -> NetworkError("无法连接到服务器")
                is java.net.SocketTimeoutException -> TimeoutError()
                is java.net.ConnectException -> NetworkError("连接失败")
                else -> UnknownError(e.message ?: "未知错误")
            }
        }

        /**
         * 从 HTTP 状态码创建 AppError
         */
        fun fromHttpCode(code: Int, message: String = ""): AppError {
            return when (code) {
                400 -> ValidationError(message.ifEmpty { "请求参数错误" })
                401 -> UnauthorizedError()
                403 -> UnauthorizedError("访问被拒绝")
                404 -> ApiError(code, message.ifEmpty { "资源不存在" })
                429 -> ApiError(code, "请求过于频繁")
                in 500..599 -> ServiceUnavailableError()
                else -> ApiError(code, message)
            }
        }
    }
}