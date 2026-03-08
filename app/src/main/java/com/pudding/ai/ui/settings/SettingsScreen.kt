package com.pudding.ai.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pudding.ai.data.model.ApiProvider
import com.pudding.ai.data.model.ModelConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentConfig: ModelConfig,
    onSave: (ModelConfig) -> Unit,
    onBack: () -> Unit
) {
    var provider by remember { mutableStateOf(currentConfig.provider) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(currentConfig.baseUrl) }
    var model by remember { mutableStateOf(currentConfig.model) }
    var temperature by remember { mutableStateOf(currentConfig.temperature.toString()) }
    var maxTokens by remember { mutableStateOf(currentConfig.maxTokens.toString()) }
    var showApiKey by remember { mutableStateOf(false) }

    // 当 currentConfig 变化时更新状态
    LaunchedEffect(currentConfig) {
        provider = currentConfig.provider
        apiKey = currentConfig.apiKey
        baseUrl = currentConfig.baseUrl
        model = currentConfig.model
        temperature = currentConfig.temperature.toString()
        maxTokens = currentConfig.maxTokens.toString()
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 协议类型选择
            Text(
                text = "API 协议类型",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = provider == ApiProvider.OPENAI,
                    onClick = { provider = ApiProvider.OPENAI },
                    label = { Text("OpenAI") }
                )
                FilterChip(
                    selected = provider == ApiProvider.ANTHROPIC,
                    onClick = { provider = ApiProvider.ANTHROPIC },
                    label = { Text("Anthropic") }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showApiKey)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showApiKey = !showApiKey }) {
                        Text(if (showApiKey) "隐藏" else "显示")
                    }
                },
                singleLine = true
            )

            // Base URL
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        when (provider) {
                            ApiProvider.OPENAI -> "例如: https://api.openai.com/v1"
                            ApiProvider.ANTHROPIC -> "例如: https://api.anthropic.com/v1"
                        }
                    )
                },
                singleLine = true
            )

            // Model
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型名称") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        when (provider) {
                            ApiProvider.OPENAI -> "例如: gpt-4, gpt-3.5-turbo"
                            ApiProvider.ANTHROPIC -> "例如: claude-3-opus-20240229"
                        }
                    )
                },
                singleLine = true
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // 高级设置
            Text(
                text = "高级设置",
                style = MaterialTheme.typography.titleMedium
            )

            // Temperature
            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it },
                label = { Text("Temperature") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                supportingText = { Text("控制响应的随机性 (0-2)") }
            )

            // Max Tokens
            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                label = { Text("Max Tokens") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                supportingText = { Text("响应的最大 token 数") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 保存按钮
            Button(
                onClick = {
                    onSave(
                        ModelConfig(
                            provider = provider,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = model,
                            temperature = temperature.toFloatOrNull() ?: 0.7f,
                            maxTokens = maxTokens.toIntOrNull() ?: 4096
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存配置")
            }

            // 测试按钮
            OutlinedButton(
                onClick = { /* TODO: 测试连接 */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("测试连接")
            }
        }
    }
}