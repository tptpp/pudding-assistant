package com.pudding.ai.data.repository

import android.util.Log
import com.pudding.ai.BuildConfig
import com.pudding.ai.data.api.*
import com.pudding.ai.data.model.ApiProvider
import com.pudding.ai.data.model.Message
import com.pudding.ai.data.model.MessageRole
import com.pudding.ai.data.model.ModelConfig
import com.pudding.ai.service.ToolDefinition
import com.pudding.ai.service.ToolResult
import com.pudding.ai.util.RetryInterceptor
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * 聊天仓库
 *
 * 负责与 AI API 的通信，支持：
 * - OpenAI 和 Anthropic API
 * - 流式响应（SSE）
 * - Function Call（工具调用）
 * - 自动重试机制
 * - 线程安全的状态管理
 *
 * 使用 Retrofit 和 OkHttp 进行网络请求，
 * 支持动态更新 API 配置。
 */
class ChatRepository {

    companion object {
        private const val MAX_TOOL_ITERATIONS = 15
    }

    @Volatile
    private var currentConfig: ModelConfig? = null

    @Volatile
    private var apiService: ChatApiService? = null

    private val lock = Any()

    private fun createOkHttpClient(config: ModelConfig): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                when (config.provider) {
                    ApiProvider.OPENAI -> {
                        request.addHeader("Authorization", "Bearer ${config.apiKey}")
                    }
                    ApiProvider.ANTHROPIC -> {
                        request.addHeader("x-api-key", config.apiKey)
                        request.addHeader("anthropic-version", "2023-06-01")
                    }
                }
                chain.proceed(request.build())
            })
            // 添加自动重试拦截器
            .addInterceptor(RetryInterceptor(
                maxRetry = 3,
                initialDelayMs = 1000,
                maxDelayMs = 10000
            ))
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun updateConfig(config: ModelConfig) {
        synchronized(lock) {
            // 安全日志：仅在调试模式下显示敏感信息
            val apiKeyPreview = if (BuildConfig.DEBUG) {
                config.apiKey.take(10) + "..."
            } else {
                "***"
            }
            Log.d("ChatRepository", "updateConfig called: baseUrl=${config.baseUrl}, apiKey=$apiKeyPreview")

            // 验证 baseUrl 是否有效
            if (config.baseUrl.isBlank()) {
                Log.w("ChatRepository", "baseUrl is blank, apiService set to null")
                currentConfig = config
                apiService = null  // 清空 apiService，等待后续配置有效 URL
                return
            }

            // 始终重新创建 apiService 以确保配置更新
            currentConfig = config
            apiService = try {
                // Retrofit 要求 baseUrl 必须以 / 结尾
                val baseUrl = if (config.baseUrl.endsWith("/")) config.baseUrl else "${config.baseUrl}/"
                Log.d("ChatRepository", "Creating Retrofit with baseUrl: $baseUrl")
                val service = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(createOkHttpClient(config))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ChatApiService::class.java)
                Log.d("ChatRepository", "Retrofit service created successfully")
                service
            } catch (e: Exception) {
                Log.e("ChatRepository", "Failed to create Retrofit service", e)
                null  // URL 格式无效时，不创建 API 服务
            }
        }
    }

    /**
     * 获取当前配置（线程安全）
     */
    fun getCurrentConfig(): ModelConfig? {
        return synchronized(lock) { currentConfig }
    }

    /**
     * 获取 API 服务（线程安全）
     */
    fun getApiService(): ChatApiService? {
        return synchronized(lock) { apiService }
    }

    suspend fun sendMessageSimple(
        messages: List<Message>,
        config: ModelConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            updateConfig(config)

            val service = getApiService() ?: return@withContext Result.failure(
                Exception("API 未配置或 Base URL 无效，请检查设置")
            )

            when (config.provider) {
                ApiProvider.OPENAI -> sendMessageOpenAI(service, messages, config)
                ApiProvider.ANTHROPIC -> sendMessageAnthropic(service, messages, config)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendMessageOpenAI(
        service: ChatApiService,
        messages: List<Message>,
        config: ModelConfig
    ): Result<String> {
        val apiMessages = messages.map { msg ->
            ChatMessage(
                role = when (msg.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
                },
                content = msg.content
            )
        }

        val request = ChatRequest(
            model = config.model,
            messages = apiMessages,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            stream = false
        )

        val response = service.chat(request)

        if (response.isSuccessful && response.body() != null) {
            val content = response.body()!!.choices.firstOrNull()?.message?.content ?: ""
            return Result.success(content)
        } else {
            return Result.failure(Exception("API error: ${response.code()}"))
        }
    }

    private suspend fun sendMessageAnthropic(
        service: ChatApiService,
        messages: List<Message>,
        config: ModelConfig
    ): Result<String> {
        // Anthropic API: system message goes in a separate field
        val systemMessage = messages.find { it.role == MessageRole.SYSTEM }?.content
        val apiMessages = messages
            .filter { it.role != MessageRole.SYSTEM }
            .map { msg ->
                AnthropicMessage(
                    role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        else -> "user"  // fallback
                    },
                    content = msg.content
                )
            }

        val request = AnthropicRequest(
            model = config.model,
            messages = apiMessages,
            maxTokens = config.maxTokens,
            system = systemMessage,
            temperature = config.temperature,
            stream = false
        )

        val response = service.anthropicChat(request)

        if (response.isSuccessful && response.body() != null) {
            val content = response.body()!!.content.firstOrNull()?.text ?: ""
            return Result.success(content)
        } else {
            return Result.failure(Exception("API error: ${response.code()}"))
        }
    }

    /**
     * 流式发送消息，返回增量文本流
     * 支持 OpenAI 兼容 API 的 SSE (Server-Sent Events) 格式
     */
    fun sendMessageStream(
        messages: List<Message>,
        config: ModelConfig
    ): Flow<String> = flow {
        Log.d("ChatRepository", "sendMessageStream called with config: baseUrl=${config.baseUrl}, model=${config.model}")
        updateConfig(config)

        val service = getApiService() ?: throw Exception("API 未配置或 Base URL 无效，请检查设置")
        Log.d("ChatRepository", "apiService is ready, provider=${config.provider}")

        when (config.provider) {
            ApiProvider.OPENAI -> {
                val apiMessages = messages.map { msg ->
                    ChatMessage(
                        role = when (msg.role) {
                            MessageRole.USER -> "user"
                            MessageRole.ASSISTANT -> "assistant"
                            MessageRole.SYSTEM -> "system"
                        },
                        content = msg.content
                    )
                }

                val request = ChatRequest(
                    model = config.model,
                    messages = apiMessages,
                    temperature = config.temperature,
                    maxTokens = config.maxTokens,
                    stream = true
                )
                Log.d("ChatRepository", "Sending stream request: model=${request.model}, messages=${apiMessages.size}")

                val responseBody = service.chatStream(request)
                parseOpenAISSEStream(responseBody).collect { chunk ->
                    emit(chunk)
                }
            }
            ApiProvider.ANTHROPIC -> {
                // Anthropic 流式 API 暂不支持，回退到非流式
                val result = sendMessageAnthropic(service, messages, config)
                result.getOrNull()?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 解析 OpenAI 兼容 API 的 SSE 流
     */
    private fun parseOpenAISSEStream(responseBody: okhttp3.ResponseBody): Flow<String> = flow {
        val gson = Gson()
        val reader = BufferedReader(responseBody.charStream())

        try {
            var line: String? = reader.readLine()
            var lineCount = 0
            while (line != null) {
                line = line.trim()
                lineCount++

                // SSE 格式: data: {...}
                if (line.startsWith("data: ")) {
                    val jsonData = line.substring(6)

                    // 结束标记
                    if (jsonData == "[DONE]") {
                        Log.d("ChatRepository", "Stream completed, total lines: $lineCount")
                        break
                    }

                    try {
                        val streamResponse = gson.fromJson(jsonData, StreamResponse::class.java)
                        val delta = streamResponse.choices.firstOrNull()?.delta?.content
                        if (!delta.isNullOrEmpty()) {
                            Log.d("ChatRepository", "Received delta: $delta")
                            emit(delta)
                        }
                    } catch (e: Exception) {
                        Log.w("ChatRepository", "Failed to parse line: $jsonData, error: ${e.message}")
                    }
                }

                line = reader.readLine()
            }
            Log.d("ChatRepository", "Stream parsing finished, total lines: $lineCount")
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error parsing stream", e)
        } finally {
            reader.close()
            responseBody.close()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 支持 Function Call 的消息发送
     * 当 AI 返回 tool_calls 时，调用 onToolCall 回调处理工具调用
     * 然后将工具结果追加到消息历史，继续获取 AI 的最终响应
     *
     * @param onStatusUpdate 状态更新回调，用于通知当前正在执行的操作
     */
    suspend fun sendMessageWithTools(
        messages: List<Message>,
        config: ModelConfig,
        tools: List<ToolDefinition>,
        onToolCall: suspend (String, JsonObject) -> ToolResult,
        onStatusUpdate: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            updateConfig(config)

            val service = getApiService() ?: return@withContext Result.failure(
                Exception("API 未配置或 Base URL 无效，请检查设置")
            )

            when (config.provider) {
                ApiProvider.OPENAI -> sendMessageOpenAIWithTools(service, messages, config, tools, onToolCall, onStatusUpdate)
                ApiProvider.ANTHROPIC -> {
                    // Anthropic 暂不支持 Function Call，回退到普通消息
                    sendMessageAnthropic(service, messages, config)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "sendMessageWithTools failed", e)
            Result.failure(e)
        }
    }

    private suspend fun sendMessageOpenAIWithTools(
        service: ChatApiService,
        messages: List<Message>,
        config: ModelConfig,
        tools: List<ToolDefinition>,
        onToolCall: suspend (String, JsonObject) -> ToolResult,
        onStatusUpdate: ((String) -> Unit)? = null
    ): Result<String> {
        val gson = Gson()

        // 将工具定义转换为 API 请求格式
        @Suppress("UNCHECKED_CAST")
        val toolsJson: List<Map<String, Any>> = tools.map { tool ->
            gson.fromJson(gson.toJson(tool), Map::class.java) as Map<String, Any>
        }

        // 构建消息历史
        val apiMessages: MutableList<Map<String, Any>> = messages.map { msg ->
            mapOf(
                "role" to when (msg.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
                },
                "content" to msg.content as Any
            )
        }.toMutableList()

        // 最多处理 MAX_TOOL_ITERATIONS 轮工具调用，防止无限循环（浏览器自动化可能需要多步操作）
        var maxIterations = MAX_TOOL_ITERATIONS
        var currentMessages = apiMessages.toList()

        while (maxIterations-- > 0) {
            val request = ChatRequest(
                model = config.model,
                messages = currentMessages.map { msg ->
                    val role = msg["role"] as String
                    val content = msg["content"] as? String ?: ""
                    ChatMessage(role = role, content = content)
                },
                temperature = config.temperature,
                maxTokens = config.maxTokens,
                stream = false,
                tools = if (toolsJson.isNotEmpty()) toolsJson else null
            )

            Log.d("ChatRepository", "Sending request with tools, messages=${currentMessages.size}")
            val response = service.chat(request)

            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(Exception("API error: ${response.code()}"))
            }

            val choice = response.body()!!.choices.firstOrNull()
                ?: return Result.failure(Exception("No response from API"))

            val message = choice.message

            // 检查是否有工具调用
            if (!message.toolCalls.isNullOrEmpty()) {
                Log.d("ChatRepository", "Received ${message.toolCalls.size} tool calls")

                // 添加 assistant 消息到历史
                val assistantMessage: Map<String, Any> = mutableMapOf(
                    "role" to "assistant" as Any,
                    "content" to (message.content ?: "") as Any
                )
                (assistantMessage as MutableMap)["tool_calls"] = message.toolCalls.map { tc ->
                    mapOf(
                        "id" to tc.id,
                        "type" to tc.type,
                        "function" to mapOf(
                            "name" to tc.function.name,
                            "arguments" to tc.function.arguments
                        )
                    )
                } as Any
                currentMessages = currentMessages + assistantMessage

                // 处理每个工具调用
                for (toolCall in message.toolCalls) {
                    val toolName = toolCall.function.name
                    val argsJson = try {
                        gson.fromJson(toolCall.function.arguments, JsonObject::class.java)
                    } catch (e: Exception) {
                        JsonObject()
                    }

                    Log.d("ChatRepository", "Executing tool: $toolName, args: $argsJson")

                    // 通知状态
                    onStatusUpdate?.invoke("正在执行: ${getToolDisplayName(toolName)}")

                    // 调用工具
                    val result = onToolCall(toolName, argsJson)

                    // 添加工具结果到消息历史
                    currentMessages = currentMessages + mapOf<String, Any>(
                        "role" to "tool",
                        "tool_call_id" to toolCall.id,
                        "content" to result.message
                    )
                }

                // 继续循环，让 AI 处理工具结果
            } else {
                // 没有工具调用，返回最终响应
                val content = message.content ?: ""
                return Result.success(content)
            }
        }

        // 达到工具调用次数限制，发送提示让模型基于现有结果给出最终回答
        Log.d("ChatRepository", "Tool call limit reached ($MAX_TOOL_ITERATIONS), requesting final summary")
        onStatusUpdate?.invoke("已达到工具调用次数上限，正在总结...")

        // 使用 user 角色（而非 system）发送提示，避免多个 system 消息的兼容性问题
        val limitMessage = mapOf(
            "role" to "user",
            "content" to """
                【系统提示】你已达到工具调用次数上限（$MAX_TOOL_ITERATIONS 次）。
                请根据已获取的工具执行结果，直接给出最终回答。
                不要再尝试调用任何工具。
            """.trimIndent()
        )
        currentMessages = currentMessages + limitMessage

        // 发送最后一次请求（不带工具），强制模型直接回复
        val finalRequest = ChatRequest(
            model = config.model,
            messages = currentMessages.map { msg ->
                val role = msg["role"] as String
                val content = msg["content"] as? String ?: ""
                ChatMessage(role = role, content = content)
            },
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            stream = false,
            tools = null  // 不传工具，强制模型直接回复
        )

        val finalResponse = service.chat(finalRequest)
        if (finalResponse.isSuccessful && finalResponse.body() != null) {
            val content = finalResponse.body()!!.choices.firstOrNull()?.message?.content ?: ""
            return Result.success(content)
        }

        return Result.failure(Exception("工具调用次数超过限制，且无法获取最终回复"))
    }

    /**
     * 获取工具的友好显示名称
     */
    private fun getToolDisplayName(toolName: String): String {
        return when (toolName) {
            "browser_action" -> "浏览器操作"
            "web_search" -> "网络搜索"
            "create_task" -> "创建任务"
            "delete_task" -> "删除任务"
            "list_tasks" -> "查询任务"
            "update_task_status" -> "更新任务状态"
            "send_notification" -> "发送通知"
            else -> toolName
        }
    }
}