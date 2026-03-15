package com.pudding.ai.service

import android.util.Log
import com.pudding.ai.data.database.MemoryDao
import com.pudding.ai.data.database.MessageDao
import com.pudding.ai.data.debug.MemoryGenerationLogBuilder
import com.pudding.ai.data.model.*
import com.pudding.ai.data.repository.ChatRepository
import com.pudding.ai.data.repository.DebugLogRepository
import com.pudding.ai.data.repository.SettingsRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 每日记忆服务
 *
 * 负责整理每日对话，生成 Markdown 格式的记忆文档。
 * 支持手动触发和定时自动生成。
 */
class DailyMemoryService(
    private val chatRepository: ChatRepository,
    private val memoryDao: MemoryDao,
    private val messageDao: MessageDao,
    private val settingsRepository: SettingsRepository,
    private val debugLogRepository: DebugLogRepository? = null
) {
    companion object {
        private const val TAG = "DailyMemoryService"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private const val MAX_MESSAGES_FOR_AI = 50  // 限制发送给 AI 的消息数量
    }

    private val gson = Gson()

    // 记忆生成日志构建器
    private val logBuilder = MemoryGenerationLogBuilder()

    /**
     * 整理指定日期的对话，生成记忆文档
     *
     * @param date 日期字符串，格式 "2024-03-08"
     * @return 生成的每日记忆，如果当天没有消息则返回 null
     */
    suspend fun generateDailyMemory(date: String): DailyMemory? {
        Log.i(TAG, "Generating daily memory for $date")

        // 计算时间范围
        val dayStart = LocalDate.parse(date, DATE_FORMATTER).atStartOfDay()
        val dayEnd = dayStart.plusDays(1)
        val startTimestamp = dayStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = dayEnd.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 获取当天所有消息
        val messages = messageDao.getMessagesByTimeRange(startTimestamp, endTimestamp)
        Log.i(TAG, "Found ${messages.size} messages for $date")

        if (messages.isEmpty()) {
            Log.i(TAG, "No messages found for $date, skipping memory generation")
            return null
        }

        // 检查是否已存在
        val existing = memoryDao.getDailyMemoryByDate(date)
        if (existing != null) {
            Log.w(TAG, "Daily memory already exists for $date, updating...")
        }

        return try {
            // 生成记忆内容
            val memoryContent = generateMemoryContent(date, messages)
            val summary = generateSummaryFromMessages(messages)

            val memory = DailyMemory(
                id = existing?.id ?: 0,
                date = date,
                title = "记忆 - $date",
                content = memoryContent,
                summary = summary,
                entityCount = 0
            )

            memoryDao.insertDailyMemory(memory)
            Log.i(TAG, "Daily memory saved for $date")
            memory
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily memory for $date", e)
            null
        }
    }

    /**
     * 整理指定日期的对话，生成记忆文档（带调试日志）
     *
     * @param date 日期字符串，格式 "2024-03-08"
     * @return 生成结果，包含记忆和调试日志 ID
     */
    suspend fun generateDailyMemoryWithDebug(date: String): Pair<DailyMemory?, Long?> {
        Log.i(TAG, "Generating daily memory with debug for $date")
        val startTime = System.currentTimeMillis()

        // 计算时间范围
        val dayStart = LocalDate.parse(date, DATE_FORMATTER).atStartOfDay()
        val dayEnd = dayStart.plusDays(1)
        val startTimestamp = dayStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = dayEnd.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 获取当天所有消息
        val messages = messageDao.getMessagesByTimeRange(startTimestamp, endTimestamp)
        Log.i(TAG, "Found ${messages.size} messages for $date")

        if (messages.isEmpty()) {
            Log.i(TAG, "No messages found for $date, skipping memory generation")
            return Pair(null, null)
        }

        // 检查是否已存在
        val existing = memoryDao.getDailyMemoryByDate(date)

        return try {
            // 生成记忆内容
            val limitedMessages = messages.takeLast(MAX_MESSAGES_FOR_AI)
            val (aiResult, prompt, rawResponse) = tryGenerateAISummaryWithDetails(messages)

            val memoryContent = buildMemoryContentFromResult(date, messages, aiResult)
            val summary = generateSummaryFromMessages(messages)

            val memory = DailyMemory(
                id = existing?.id ?: 0,
                date = date,
                title = "记忆 - $date",
                content = memoryContent,
                summary = summary,
                entityCount = 0
            )

            memoryDao.insertDailyMemory(memory)
            Log.i(TAG, "Daily memory saved for $date")

            // 记录调试日志
            val durationMs = System.currentTimeMillis() - startTime
            val debugLog = logBuilder.build(
                date = date,
                messages = limitedMessages,
                prompt = prompt,
                rawResponse = rawResponse,
                result = aiResult,
                durationMs = durationMs,
                error = null
            )
            val debugLogId = debugLogRepository?.saveLog(debugLog)

            Pair(memory, debugLogId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily memory for $date", e)

            // 记录失败的调试日志
            val durationMs = System.currentTimeMillis() - startTime
            val debugLog = logBuilder.build(
                date = date,
                messages = messages.takeLast(MAX_MESSAGES_FOR_AI),
                prompt = "",
                rawResponse = "",
                result = null,
                durationMs = durationMs,
                error = e
            )
            val debugLogId = debugLogRepository?.saveLog(debugLog)

            Pair(null, debugLogId)
        }
    }

    /**
     * 根据消息生成记忆内容
     *
     * @param date 日期
     * @param messages 当天的消息列表
     * @return Markdown 格式的记忆内容
     */
    private suspend fun generateMemoryContent(
        date: String,
        messages: List<Message>
    ): String {
        val sb = StringBuilder()

        sb.appendLine("# $date 记忆")
        sb.appendLine()

        // 添加日期信息
        val localDate = LocalDate.parse(date, DATE_FORMATTER)
        val weekDay = when (localDate.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "星期一"
            java.time.DayOfWeek.TUESDAY -> "星期二"
            java.time.DayOfWeek.WEDNESDAY -> "星期三"
            java.time.DayOfWeek.THURSDAY -> "星期四"
            java.time.DayOfWeek.FRIDAY -> "星期五"
            java.time.DayOfWeek.SATURDAY -> "星期六"
            java.time.DayOfWeek.SUNDAY -> "星期日"
            else -> "星期日"  // 处理潜在的 null 情况
        }
        sb.appendLine("**$weekDay**")
        sb.appendLine()
        sb.appendLine("共 ${messages.size} 条消息记录。")
        sb.appendLine()

        // 尝试调用 AI 生成摘要
        val aiSummary = tryGenerateAISummary(messages)
        if (aiSummary != null) {
            sb.appendLine("## 对话摘要")
            sb.appendLine()
            sb.appendLine(aiSummary.summary)
            sb.appendLine()

            if (aiSummary.keyPoints.isNotEmpty()) {
                sb.appendLine("## 重要事项")
                sb.appendLine()
                aiSummary.keyPoints.forEach { point ->
                    sb.appendLine("- $point")
                }
                sb.appendLine()
            }

            if (aiSummary.topics.isNotEmpty()) {
                sb.appendLine("## 讨论话题")
                sb.appendLine()
                aiSummary.topics.forEach { topic ->
                    sb.appendLine("- $topic")
                }
                sb.appendLine()
            }

            if (aiSummary.tasks.isNotEmpty()) {
                sb.appendLine("## 待办事项")
                sb.appendLine()
                aiSummary.tasks.forEach { task ->
                    sb.appendLine("- [ ] $task")
                }
                sb.appendLine()
            }

            if (aiSummary.entities.isNotEmpty()) {
                sb.appendLine("## 相关实体")
                sb.appendLine()
                aiSummary.entities.forEach { entity ->
                    sb.appendLine("- **${entity.name}** (${entity.type}): ${entity.info}")
                }
                sb.appendLine()
            }
        } else {
            // AI 生成失败，生成基础摘要
            sb.appendLine("## 对话记录")
            sb.appendLine()
            sb.appendLine("共 ${messages.size} 条消息。")
            sb.appendLine()
            sb.appendLine("### 消息概览")
            sb.appendLine()
            // 显示前几条消息作为概览
            messages.take(10).forEach { msg ->
                val role = when (msg.role) {
                    MessageRole.USER -> "用户"
                    MessageRole.ASSISTANT -> "助手"
                    MessageRole.SYSTEM -> "系统"
                }
                val preview = msg.content.take(100) + if (msg.content.length > 100) "..." else ""
                sb.appendLine("- **$role**: $preview")
            }
            if (messages.size > 10) {
                sb.appendLine("- ... 还有 ${messages.size - 10} 条消息")
            }
        }

        return sb.toString()
    }

    /**
     * 从 AI 结果构建记忆内容（用于调试模式）
     */
    private fun buildMemoryContentFromResult(
        date: String,
        messages: List<Message>,
        aiSummary: MemoryResult?
    ): String {
        val sb = StringBuilder()

        sb.appendLine("# $date 记忆")
        sb.appendLine()

        // 添加日期信息
        val localDate = LocalDate.parse(date, DATE_FORMATTER)
        val weekDay = when (localDate.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "星期一"
            java.time.DayOfWeek.TUESDAY -> "星期二"
            java.time.DayOfWeek.WEDNESDAY -> "星期三"
            java.time.DayOfWeek.THURSDAY -> "星期四"
            java.time.DayOfWeek.FRIDAY -> "星期五"
            java.time.DayOfWeek.SATURDAY -> "星期六"
            java.time.DayOfWeek.SUNDAY -> "星期日"
            else -> "星期日"
        }
        sb.appendLine("**$weekDay**")
        sb.appendLine()
        sb.appendLine("共 ${messages.size} 条消息记录。")
        sb.appendLine()

        if (aiSummary != null) {
            sb.appendLine("## 对话摘要")
            sb.appendLine()
            sb.appendLine(aiSummary.summary)
            sb.appendLine()

            if (aiSummary.keyPoints.isNotEmpty()) {
                sb.appendLine("## 重要事项")
                sb.appendLine()
                aiSummary.keyPoints.forEach { point ->
                    sb.appendLine("- $point")
                }
                sb.appendLine()
            }

            if (aiSummary.topics.isNotEmpty()) {
                sb.appendLine("## 讨论话题")
                sb.appendLine()
                aiSummary.topics.forEach { topic ->
                    sb.appendLine("- $topic")
                }
                sb.appendLine()
            }

            if (aiSummary.tasks.isNotEmpty()) {
                sb.appendLine("## 待办事项")
                sb.appendLine()
                aiSummary.tasks.forEach { task ->
                    sb.appendLine("- [ ] $task")
                }
                sb.appendLine()
            }

            if (aiSummary.entities.isNotEmpty()) {
                sb.appendLine("## 相关实体")
                sb.appendLine()
                aiSummary.entities.forEach { entity ->
                    sb.appendLine("- **${entity.name}** (${entity.type}): ${entity.info}")
                }
                sb.appendLine()
            }
        } else {
            // AI 生成失败，生成基础摘要
            sb.appendLine("## 对话记录")
            sb.appendLine()
            sb.appendLine("共 ${messages.size} 条消息。")
            sb.appendLine()
            sb.appendLine("### 消息概览")
            sb.appendLine()
            messages.take(10).forEach { msg ->
                val role = when (msg.role) {
                    MessageRole.USER -> "用户"
                    MessageRole.ASSISTANT -> "助手"
                    MessageRole.SYSTEM -> "系统"
                }
                val preview = msg.content.take(100) + if (msg.content.length > 100) "..." else ""
                sb.appendLine("- **$role**: $preview")
            }
            if (messages.size > 10) {
                sb.appendLine("- ... 还有 ${messages.size - 10} 条消息")
            }
        }

        return sb.toString()
    }

    /**
     * 尝试调用 AI 生成摘要
     */
    private suspend fun tryGenerateAISummary(messages: List<Message>): MemoryResult? {
        if (messages.isEmpty()) return null

        // 限制消息数量，避免 token 过多
        val limitedMessages = messages.takeLast(MAX_MESSAGES_FOR_AI)

        return try {
            val config = settingsRepository.modelConfig.first()
            val prompt = buildDailySummaryPrompt(limitedMessages)

            val result = chatRepository.sendMessageSimple(
                messages = listOf(
                    Message(
                        conversationId = 0,
                        role = MessageRole.SYSTEM,
                        content = prompt
                    )
                ),
                config = config
            )

            result.getOrNull()?.let { parseMemoryResult(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate AI summary", e)
            null
        }
    }

    /**
     * 尝试调用 AI 生成摘要（带详细返回）
     * @return Triple(结果, 提示词, 原始响应)
     */
    suspend fun tryGenerateAISummaryWithDetails(messages: List<Message>): Triple<MemoryResult?, String, String> {
        if (messages.isEmpty()) return Triple(null, "", "")

        // 限制消息数量，避免 token 过多
        val limitedMessages = messages.takeLast(MAX_MESSAGES_FOR_AI)

        return try {
            val config = settingsRepository.modelConfig.first()
            val prompt = buildDailySummaryPrompt(limitedMessages)

            val result = chatRepository.sendMessageSimple(
                messages = listOf(
                    Message(
                        conversationId = 0,
                        role = MessageRole.SYSTEM,
                        content = prompt
                    )
                ),
                config = config
            )

            val rawResponse = result.getOrNull() ?: ""
            Triple(result.getOrNull()?.let { parseMemoryResult(it) }, prompt, rawResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate AI summary", e)
            Triple(null, "", "")
        }
    }

    /**
     * 构建每日摘要提示
     */
    fun buildDailySummaryPrompt(messages: List<Message>): String {
        val conversationText = messages.joinToString("\n") { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
                MessageRole.SYSTEM -> "系统"
            }
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                .format(java.util.Date(msg.timestamp))
            "[$time][$role]: ${msg.content}"
        }

        return """
            请将以下对话整理成一份结构化的记忆摘要。

            对话内容：
            $conversationText

            请以 JSON 格式返回，包含以下字段：
            ```json
            {
              "title": "对话标题（简洁概括）",
              "summary": "简短摘要（100字以内，总结当天的主要对话内容）",
              "topics": ["讨论话题1", "讨论话题2"],
              "keyPoints": ["关键点1", "关键点2"],
              "tasks": ["提到的任务或待办事项"],
              "entities": [
                {"name": "实体名称", "type": "实体类型", "info": "相关信息"}
              ]
            }
            ```

            JSON 结果：
        """.trimIndent()
    }

    /**
     * 解析 AI 返回的记忆结果
     */
    private fun parseMemoryResult(result: String): MemoryResult? {
        return try {
            // 提取 JSON 块
            val jsonStart = result.indexOf("```json")
            val jsonEnd = result.lastIndexOf("```")

            val jsonText = if (jsonStart >= 0 && jsonEnd > jsonStart) {
                result.substring(jsonStart + 7, jsonEnd).trim()
            } else {
                result.trim()
            }

            gson.fromJson(jsonText, MemoryResult::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse memory result", e)
            null
        }
    }

    /**
     * 从消息列表生成简短摘要
     */
    private fun generateSummaryFromMessages(messages: List<Message>): String {
        val userMessages = messages.count { it.role == MessageRole.USER }
        val assistantMessages = messages.count { it.role == MessageRole.ASSISTANT }
        return "共 ${messages.size} 条消息（用户 $userMessages 条，助手 $assistantMessages 条）"
    }

    /**
     * 为对话生成记忆片段
     *
     * @param conversationId 对话 ID
     * @param messages 对话消息列表
     * @return 记忆片段
     */
    suspend fun generateConversationMemory(
        conversationId: Long,
        messages: List<Message>
    ): ConversationMemory? {
        if (messages.isEmpty()) {
            return null
        }

        val normalMessages = messages.filter { it.type == MessageType.NORMAL }
        if (normalMessages.isEmpty()) {
            return null
        }

        // 构建生成提示
        val prompt = buildMemoryPrompt(normalMessages)

        // 调用 AI 生成记忆
        return try {
            val result = chatRepository.sendMessageSimple(
                messages = listOf(
                    Message(
                        conversationId = 0,
                        role = MessageRole.SYSTEM,
                        content = prompt
                    )
                ),
                config = ModelConfig()
            )

            result.getOrNull()?.let { parseConversationMemoryResult(conversationId, it, normalMessages) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate conversation memory", e)
            null
        }
    }

    /**
     * 构建记忆生成提示
     */
    private fun buildMemoryPrompt(messages: List<Message>): String {
        val conversationText = messages.joinToString("\n") { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
                MessageRole.SYSTEM -> "系统"
            }
            "[$role]: ${msg.content}"
        }

        return """
            请将以下对话整理成一份结构化的记忆摘要。

            对话内容：
            $conversationText

            请以 JSON 格式返回，包含以下字段：
            ```json
            {
              "title": "对话标题（简洁概括）",
              "summary": "简短摘要（50字以内）",
              "topics": ["讨论话题1", "讨论话题2"],
              "keyPoints": ["关键点1", "关键点2"],
              "tasks": ["提到的任务或待办事项"],
              "entities": [
                {"name": "实体名称", "type": "实体类型", "info": "相关信息"}
              ]
            }
            ```

            JSON 结果：
        """.trimIndent()
    }

    /**
     * 解析记忆生成结果
     */
    private fun parseConversationMemoryResult(
        conversationId: Long,
        result: String,
        messages: List<Message>
    ): ConversationMemory {
        return try {
            // 提取 JSON 块
            val jsonStart = result.indexOf("```json")
            val jsonEnd = result.lastIndexOf("```")

            val jsonText = if (jsonStart >= 0 && jsonEnd > jsonStart) {
                result.substring(jsonStart + 7, jsonEnd).trim()
            } else {
                result.trim()
            }

            val parsed = gson.fromJson(jsonText, MemoryResult::class.java)

            ConversationMemory(
                conversationId = conversationId,
                title = parsed.title,
                summary = parsed.summary,
                topics = parsed.topics,
                keyPoints = parsed.keyPoints,
                tasks = parsed.tasks,
                entities = parsed.entities,
                messageCount = messages.size,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse memory result", e)
            // 返回基本记忆
            ConversationMemory(
                conversationId = conversationId,
                title = "对话记录",
                summary = "包含 ${messages.size} 条消息的对话",
                topics = emptyList(),
                keyPoints = emptyList(),
                tasks = emptyList(),
                entities = emptyList(),
                messageCount = messages.size,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * 获取最近 N 天的记忆
     */
    suspend fun getRecentMemories(days: Int = 7): List<DailyMemory> {
        val memories = memoryDao.getAllDailyMemories().first()
        val cutoffDate = LocalDate.now().minusDays(days.toLong())
        return memories.filter { memory ->
            LocalDate.parse(memory.date, DATE_FORMATTER) >= cutoffDate
        }
    }

    /**
     * 搜索记忆
     */
    fun searchMemories(keyword: String) = memoryDao.searchDailyMemories(keyword)

    // ========== 数据类 ==========

    /**
     * 记忆生成结果
     */
    data class MemoryResult(
        val title: String,
        val summary: String,
        val topics: List<String>,
        val keyPoints: List<String>,
        val tasks: List<String>,
        val entities: List<EntityInfo>
    )

    /**
     * 实体信息
     */
    data class EntityInfo(
        val name: String,
        val type: String,
        val info: String
    )

    /**
     * 对话记忆
     */
    data class ConversationMemory(
        val conversationId: Long,
        val title: String,
        val summary: String,
        val topics: List<String>,
        val keyPoints: List<String>,
        val tasks: List<String>,
        val entities: List<EntityInfo>,
        val messageCount: Int,
        val timestamp: Long
    )
}