package com.pudding.ai.util

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 网络请求重试拦截器
 *
 * 当网络请求失败时自动重试，支持配置：
 * - 最大重试次数
 * - 重试间隔（指数退避）
 * - 可重试的异常类型
 *
 * @param maxRetry 最大重试次数
 * @param initialDelayMs 初始重试间隔（毫秒）
 * @param maxDelayMs 最大重试间隔（毫秒）
 */
class RetryInterceptor(
    private val maxRetry: Int = 3,
    private val initialDelayMs: Long = 1000,
    private val maxDelayMs: Long = 10000
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: Exception? = null
        var currentDelay = initialDelayMs

        repeat(maxRetry + 1) { attempt ->
            try {
                val response = chain.proceed(request)

                // 如果响应成功或者是客户端错误（4xx），不重试
                if (response.isSuccessful || response.code in 400..499) {
                    return response
                }

                // 服务器错误（5xx）可能需要重试
                if (attempt < maxRetry && response.code >= 500) {
                    response.close()
                    Thread.sleep(calculateDelay(attempt))
                    return@repeat
                }

                return response
            } catch (e: Exception) {
                lastException = e

                // 判断是否应该重试
                if (!shouldRetry(e) || attempt >= maxRetry) {
                    throw e
                }

                // 等待后重试
                Thread.sleep(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
            }
        }

        // 所有重试都失败，抛出最后一个异常
        throw lastException ?: IOException("Unknown error after $maxRetry retries")
    }

    /**
     * 判断异常是否应该重试
     */
    private fun shouldRetry(exception: Exception): Boolean {
        return when (exception) {
            is UnknownHostException -> true  // DNS 解析失败
            is SocketTimeoutException -> true  // 连接超时
            is IOException -> true  // 网络 IO 错误
            else -> false
        }
    }

    /**
     * 计算重试延迟（指数退避）
     */
    private fun calculateDelay(attempt: Int): Long {
        val delay = initialDelayMs * (1L shl attempt)  // 2^attempt
        return delay.coerceAtMost(maxDelayMs)
    }
}