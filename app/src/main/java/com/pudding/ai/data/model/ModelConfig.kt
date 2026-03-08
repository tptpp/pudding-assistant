package com.pudding.ai.data.model

/**
 * API 提供商枚举
 */
enum class ApiProvider {
    OPENAI,
    ANTHROPIC
}

/**
 * 模型配置
 *
 * @property provider API 提供商
 * @property baseUrl API 基础 URL
 * @property apiKey API 密钥
 * @property model 模型名称
 * @property temperature 温度参数
 * @property maxTokens 最大 Token 数
 */
data class ModelConfig(
    val provider: ApiProvider = ApiProvider.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096
)