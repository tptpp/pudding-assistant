package com.pudding.ai.data.repository

import android.util.Log
import com.pudding.ai.data.api.*
import com.pudding.ai.data.model.ApiProvider
import com.pudding.ai.data.model.Message
import com.pudding.ai.data.model.MessageRole
import com.pudding.ai.data.model.ModelConfig
import com.pudding.ai.service.ToolDefinition
import com.pudding.ai.service.ToolResult
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
 *
 * 使用 Retrofit 和 OkHttp 进行网络请求，
 * 支持动态更新 API 配置。
 */
class ChatRepository {

    private var currentConfig: ModelConfig? = null
    private var apiService: ChatApiService? = null

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
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun updateConfig(config: ModelConfig) {
        Log.d("ChatRepository", "updateConfig called: baseUrl=${config.baseUrl}, apiKey=${config.apiKey.take(10)}...")
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

    suspend fun sendMessageSimple(
        messages: List<Message>,
        config: ModelConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            updateConfig(config)

            val service = apiService ?: return@withContext Result.failure(
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

        val service = apiService ?: throw Exception("API 未配置或 Base URL 无效，请检查设置")
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
     */
    suspend fun sendMessageWithTools(
        messages: List<Message>,
        config: ModelConfig,
        tools: List<ToolDefinition>,
        onToolCall: suspend (String, JsonObject) -> ToolResult
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            updateConfig(config)

            val service = apiService ?: return@withContext Result.failure(
                Exception("API 未配置或 Base URL 无效，请检查设置")
            )

            when (config.provider) {
                ApiProvider.OPENAI -> sendMessageOpenAIWithTools(service, messages, config, tools, onToolCall)
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
        onToolCall: suspend (String, JsonObject) -> ToolResult
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

        // 最多处理 5 轮工具调用，防止无限循环
        var maxIterations = 5
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

        return Result.failure(Exception("工具调用次数超过限制"))
    }
}