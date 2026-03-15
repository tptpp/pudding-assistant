package com.pudding.ai.util

import com.pudding.ai.data.model.Message
import com.pudding.ai.data.model.MessageType

/**
 * Token 计数估算工具
 *
 * 提供文本和消息列表的 token 数量估算功能。
 * 由于无法使用实际的 tokenizer，采用简单的估算算法：
 * - 英文约 4 字符 = 1 token
 * - 中文约 1.5 字符 = 1 token
 */
object TokenEstimator {

    /**
     * 估算文本的 token 数量
     *
     * @param text 要估算的文本
     * @return 估算的 token 数量
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0

        // 统计中文字符数量（CJK 统一汉字范围）
        val chineseChars = text.count { it.code in 0x4E00..0x9FFF }
        val otherChars = text.length - chineseChars

        // 中文约 1.5 字符 = 1 token，英文约 4 字符 = 1 token
        val estimatedTokens = (chineseChars * 0.67 + otherChars * 0.25).toInt()

        // 至少返回 1（如果有内容的话）
        return estimatedTokens.coerceAtLeast(1)
    }

    /**
     * 估算消息列表的总 token 数量
     *
     * 优先使用消息中存储的实际 token 数量（来自 API 响应），
     * 如果没有则使用估算值。
     *
     * @param messages 消息列表
     * @return 估算的总 token 数量
     */
    fun estimateMessagesTokens(messages: List<Message>): Int {
        return messages.sumOf { msg ->
            // 跳过检查点消息的 token 计算（它们是摘要）
            if (msg.type == MessageType.CHECKPOINT) {
                estimateTokens(msg.content)
            } else {
                // 优先使用实际的 completion tokens
                if (msg.completionTokens > 0) {
                    msg.completionTokens
                } else if (msg.promptTokens > 0) {
                    // 如果只有 promptTokens，则估算内容
                    estimateTokens(msg.content) + 4 // 4 是消息格式的额外开销
                } else {
                    // 否则估算
                    estimateTokens(msg.content) + 4
                }
            }
        }
    }

    /**
     * 估算消息内容的 token 数量（用于发送给模型前的估算）
     *
     * @param messages 消息列表
     * @param systemPrompt 系统提示词（可选）
     * @return 估算的总 token 数量
     */
    fun estimateContextTokens(
        messages: List<Message>,
        systemPrompt: String? = null
    ): Int {
        var total = 0

        // 系统提示词
        if (!systemPrompt.isNullOrBlank()) {
            total += estimateTokens(systemPrompt) + 4
        }

        // 消息列表
        total += estimateMessagesTokens(messages)

        // 添加消息格式的额外开销（每条消息约 4 tokens）
        total += messages.size * 4

        return total
    }

    /**
     * 检查 token 数量是否超过限制
     *
     * @param currentTokens 当前 token 数量
     * @param maxTokens 最大 token 限制
     * @param reservedTokens 预留给响应的 token 数量
     * @return 是否超过限制
     */
    fun isOverLimit(
        currentTokens: Int,
        maxTokens: Int,
        reservedTokens: Int = 1024
    ): Boolean {
        return currentTokens > (maxTokens - reservedTokens)
    }

    /**
     * 计算可用的上下文 token 数量
     *
     * @param maxTokens 模型的最大 token 限制
     * @param reservedTokens 预留给响应的 token 数量
     * @return 可用的上下文 token 数量
     */
    fun getAvailableContextTokens(
        maxTokens: Int,
        reservedTokens: Int = 1024
    ): Int {
        return (maxTokens - reservedTokens).coerceAtLeast(0)
    }
}