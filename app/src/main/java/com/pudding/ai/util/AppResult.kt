package com.pudding.ai.util

/**
 * 统一的结果封装类
 *
 * 用于表示操作的结果状态，包括成功、错误和加载中。
 * 支持链式操作和错误处理。
 *
 * @param T 成功时的数据类型
 */
sealed class AppResult<out T> {
    /**
     * 成功结果
     * @param data 成功时返回的数据
     */
    data class Success<T>(val data: T) : AppResult<T>()

    /**
     * 错误结果
     * @param error 错误信息
     * @param message 用户友好的错误消息
     */
    data class Error(val error: AppError, val message: String? = null) : AppResult<Nothing>()

    /**
     * 加载中状态
     */
    data object Loading : AppResult<Nothing>()

    /**
     * 是否成功
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * 是否错误
     */
    val isError: Boolean get() = this is Error

    /**
     * 是否加载中
     */
    val isLoading: Boolean get() = this is Loading

    /**
     * 获取数据，如果失败返回 null
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * 获取错误消息
     */
    fun getErrorMessage(): String? = (this as? Error)?.let {
        it.message ?: it.error.message
    }

    /**
     * 获取错误对象
     */
    fun getErrorOrNull(): AppError? = (this as? Error)?.error

    /**
     * 成功时执行操作
     */
    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * 失败时执行操作
     */
    inline fun onError(action: (AppError, String?) -> Unit): AppResult<T> {
        if (this is Error) action(error, message)
        return this
    }

    /**
     * 加载中时执行操作
     */
    inline fun onLoading(action: () -> Unit): AppResult<T> {
        if (this is Loading) action()
        return this
    }

    /**
     * 转换成功时的数据
     */
    inline fun <R> map(transform: (T) -> R): AppResult<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> this
            is Loading -> Loading
        }
    }

    /**
     * 扁平化转换
     */
    inline fun <R> flatMap(transform: (T) -> AppResult<R>): AppResult<R> {
        return when (this) {
            is Success -> transform(data)
            is Error -> this
            is Loading -> Loading
        }
    }

    /**
     * 获取数据或默认值
     */
    @Suppress("UNCHECKED_CAST")
    fun getOrDefault(default: @UnsafeVariance T): T {
        return when (this) {
            is Success -> data
            else -> default
        }
    }

    /**
     * 获取数据或抛出异常
     */
    fun getOrThrow(): T {
        return when (this) {
            is Success -> data
            is Error -> throw error
            is Loading -> throw IllegalStateException("Result is still loading")
        }
    }

    companion object {
        /**
         * 创建成功结果
         */
        fun <T> success(data: T): AppResult<T> = Success(data)

        /**
         * 创建错误结果
         */
        fun error(error: AppError, message: String? = null): AppResult<Nothing> = Error(error, message)

        /**
         * 创建网络错误
         */
        fun networkError(message: String = "网络连接失败"): AppResult<Nothing> =
            Error(AppError.NetworkError(message))

        /**
         * 创建 API 错误
         */
        fun apiError(code: Int, message: String): AppResult<Nothing> =
            Error(AppError.ApiError(code, message))

        /**
         * 创建数据库错误
         */
        fun databaseError(message: String = "数据库操作失败"): AppResult<Nothing> =
            Error(AppError.DatabaseError(message))

        /**
         * 创建未知错误
         */
        fun unknownError(message: String = "未知错误"): AppResult<Nothing> =
            Error(AppError.UnknownError(message))

        /**
         * 创建加载中状态
         */
        fun loading(): AppResult<Nothing> = Loading
    }
}