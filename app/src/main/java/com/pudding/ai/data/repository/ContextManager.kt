package com.pudding.ai.data.repository

import android.util.Log
import com.pudding.ai.data.database.MessageDao
import com.pudding.ai.data.model.Message
import com.pudding.ai.data.model.MessageRole
import com.pudding.ai.data.model.MessageType
import com.pudding.ai.util.TokenEstimator
import kotlinx.coroutines.flow.first

/**
 * 上下文管理器
 *
 * 负责管理对话的上下文窗口，包括：
 * - 从消息列表中提取适合模型上下文窗口的消息
 * - 检查点机制的自动触发和创建
 * - Token 计数和限制管理
 */
class ContextManager(
    private val messageDao: MessageDao,
    private val chatRepository: ChatRepository
) {
    companion object {
        private const val TAG = "ContextManager"

        // 默认的上下文 token 限制
        const val DEFAULT_CONTEXT_LIMIT = 8192

        // 触发检查点创建的阈值比例（75%）
        const val CHECKPOINT_THRESHOLD = 0.75

        // 预留给模型响应的 token 数量
        const val RESERVED_TOKENS = 1024

        // 检查点摘要的最大 token 数
        const val MAX_SUMMARY_TOKENS = 500
    }

    /**
     * 获取发送给模型的消息列表
     *
     * 从最新消息向前遍历，直到：
     * 1. 遇到检查点消息（从检查点开始，检查点消息包含摘要）
     * 2. 或者超过 token 限制
     *
     * @param conversationId 对话 ID
     * @param maxTokens 最大 token 限制
     * @return 适合发送给模型的消息列表
     */
    suspend fun getMessagesForModel(
        conversationId: Long,
        maxTokens: Int = DEFAULT_CONTEXT_LIMIT
    ): List<Message> {
        val allMessages = messageDao.getMessagesByConversation(conversationId).first()

        if (allMessages.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<Message>()
        var totalTokens = 0
        val limit = TokenEstimator.getAvailableContextTokens(maxTokens, RESERVED_TOKENS)

        // 从后往前遍历
        for (msg in allMessages.reversed()) {
            val msgTokens = TokenEstimator.estimateTokens(msg.content) + 4

            // 遇到检查点，加入并停止
            if (msg.type == MessageType.CHECKPOINT) {
                result.add(0, msg)
                break
            }

            // 超过限制，停止
            if (totalTokens + msgTokens > limit) {
                Log.d(TAG, "Token limit reached: $totalTokens + $msgTokens > $limit")
                break
            }

            result.add(0, msg)
            totalTokens += msgTokens
        }

        Log.d(TAG, "Selected ${result.size} messages with ~$totalTokens tokens")
        return result
    }

    /**
     * 检查是否需要创建检查点
     *
     * 如果当前消息数量接近 token 限制，生成摘要并插入检查点。
     *
     * @param conversationId 对话 ID
     * @param messages 当前消息列表
     * @param maxTokens 最大 token 限制
     * @return 是否创建了检查点
     */
    suspend fun checkAndCreateCheckpoint(
        conversationId: Long,
        messages: List<Message>,
        maxTokens: Int = DEFAULT_CONTEXT_LIMIT
    ): Boolean {
        val tokens = TokenEstimator.estimateMessagesTokens(messages)
        val threshold = (maxTokens * CHECKPOINT_THRESHOLD).toInt()

        Log.d(TAG, "Checking checkpoint: tokens=$tokens, threshold=$threshold")

        if (tokens >= threshold) {
            // 生成摘要
            val summary = generateSummary(messages)

            // 插入检查点消息
            val checkpoint = Message(
                conversationId = conversationId,
                role = MessageRole.SYSTEM,
                content = "【对话摘要】\n$summary",
                type = MessageType.CHECKPOINT
            )
            messageDao.insertMessage(checkpoint)
            Log.i(TAG, "Created checkpoint with ${tokens} tokens")
            return true
        }

        return false
    }

    /**
     * 生成对话摘要
     *
     * @param messages 要摘要的消息列表
     * @return 摘要文本
     */
    private suspend fun generateSummary(messages: List<Message>): String {
        // 过滤掉检查点消息，只摘要普通消息
        val normalMessages = messages.filter { it.type == MessageType.NORMAL }

        if (normalMessages.isEmpty()) {
            return "暂无对话内容"
        }

        // 构建摘要请求
        val summaryPrompt = buildSummaryPrompt(normalMessages)

        // 调用 AI 生成摘要
        return try {
            val result = chatRepository.sendMessageSimple(
                messages = listOf(
                    Message(
                        conversationId = 0,
                        role = MessageRole.SYSTEM,
                        content = summaryPrompt
                    )
                ),
                config = com.pudding.ai.data.model.ModelConfig() // 使用默认配置
            )

            result.getOrDefault("摘要生成失败，请稍后重试。")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate summary", e)
            // 如果 AI 生成失败，使用简单摘要
            generateSimpleSummary(normalMessages)
        }
    }

    /**
     * 构建摘要请求提示词
     */
    private fun buildSummaryPrompt(messages: List<Message>): String {
        val conversationText = messages.joinToString("\n") { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
                MessageRole.SYSTEM -> "系统"
            }
            "[$role]: ${msg.content}"
        }

        return """
            请将以下对话内容整理成一个简洁的摘要，保留关键信息和重要细节。
            摘要应该包含：
            1. 讨论的主要话题
            2. 重要的决定或结论
            3. 关键的任务或待办事项
            4. 涉及的人物或实体

            对话内容：
            $conversationText

            请用简洁的中文写出摘要（不超过 300 字）：
        """.trimIndent()
    }

    /**
     * 生成简单摘要（当 AI 生成失败时使用）
     */
    private fun generateSimpleSummary(messages: List<Message>): String {
        val userMessages = messages.count { it.role == MessageRole.USER }
        val assistantMessages = messages.count { it.role == MessageRole.ASSISTANT }

        // 获取第一条用户消息作为主题参考
        val firstUserMessage = messages.firstOrNull { it.role == MessageRole.USER }?.content
        val topic = firstUserMessage?.take(50)?.let { "主题：$it..." } ?: "主题：未知"

        return "$topic\n共 $userMessages 条用户消息，$assistantMessages 条助手回复。"
    }

    /**
     * 获取当前对话的 token 统计
     *
     * @param conversationId 对话 ID
     * @return Token 统计信息
     */
    suspend fun getTokenStats(conversationId: Long): TokenStats {
        val messages = messageDao.getMessagesByConversation(conversationId).first()
        val normalMessages = messages.filter { it.type == MessageType.NORMAL }
        val checkpointMessages = messages.filter { it.type == MessageType.CHECKPOINT }

        val estimatedTokens = TokenEstimator.estimateMessagesTokens(normalMessages)
        val actualPromptTokens = normalMessages.sumOf { it.promptTokens }
        val actualCompletionTokens = normalMessages.sumOf { it.completionTokens }

        return TokenStats(
            totalMessages = messages.size,
            normalMessages = normalMessages.size,
            checkpointMessages = checkpointMessages.size,
            estimatedTokens = estimatedTokens,
            actualPromptTokens = actualPromptTokens,
            actualCompletionTokens = actualCompletionTokens,
            totalActualTokens = actualPromptTokens + actualCompletionTokens
        )
    }

    /**
     * Token 统计信息
     */
    data class TokenStats(
        val totalMessages: Int,
        val normalMessages: Int,
        val checkpointMessages: Int,
        val estimatedTokens: Int,
        val actualPromptTokens: Int,
        val actualCompletionTokens: Int,
        val totalActualTokens: Int
    )
}