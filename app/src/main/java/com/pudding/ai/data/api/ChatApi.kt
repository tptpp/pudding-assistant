package com.pudding.ai.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.http.*

// OpenAI 兼容的 API 请求/响应格式

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float? = null,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    val stream: Boolean = false,
    val tools: List<Map<String, Any>>? = null  // Function Call 工具定义
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val id: String,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: Usage? = null
)

data class ChatChoice(
    val index: Int,
    val message: ChatMessageResponse,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class ChatMessageResponse(
    val role: String,
    val content: String?,
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCallResponse>? = null
)

// Function Call 响应
data class ToolCallResponse(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunctionResponse
)

data class ToolCallFunctionResponse(
    val name: String,
    val arguments: String
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

// 流式响应
data class StreamResponse(
    val id: String,
    val choices: List<StreamChoice>
)

data class StreamChoice(
    val index: Int,
    val delta: StreamDelta,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class StreamDelta(
    val role: String? = null,
    val content: String? = null
)

// ============ Anthropic API 格式 ============

data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    val system: String? = null,
    val temperature: Float? = null,
    val stream: Boolean = false
)

data class AnthropicMessage(
    val role: String,
    val content: String
)

data class AnthropicResponse(
    val id: String,
    val `type`: String,
    val role: String,
    val content: List<AnthropicContent>,
    val model: String,
    @SerializedName("stop_reason")
    val stopReason: String?,
    val usage: AnthropicUsage? = null
)

data class AnthropicContent(
    val type: String,
    val text: String
)

data class AnthropicUsage(
    @SerializedName("input_tokens")
    val inputTokens: Int,
    @SerializedName("output_tokens")
    val outputTokens: Int
)

// API 接口
interface ChatApiService {
    // OpenAI 兼容 API
    @POST("chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chat(@Body request: ChatRequest): retrofit2.Response<ChatResponse>

    @POST("chat/completions")
    @Headers("Content-Type: application/json")
    @Streaming
    suspend fun chatStream(@Body request: ChatRequest): ResponseBody

    @GET("models")
    suspend fun listModels(): ModelsResponse

    // Anthropic API
    @POST("messages")
    @Headers("Content-Type: application/json")
    suspend fun anthropicChat(@Body request: AnthropicRequest): retrofit2.Response<AnthropicResponse>
}

data class ModelsResponse(
    val data: List<ModelInfo>
)

data class ModelInfo(
    val id: String,
    val owned_by: String
)