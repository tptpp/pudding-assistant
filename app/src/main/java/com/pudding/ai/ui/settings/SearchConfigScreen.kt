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
import com.pudding.ai.data.model.SearchConfig
import com.pudding.ai.data.model.SearchProvider

/**
 * 搜索配置页面
 *
 * 提供搜索功能的配置界面，包括：
 * - 搜索开关
 * - 搜索服务选择
 * - API Key 配置
 * - 自定义 URL 配置
 * - 结果数量设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchConfigScreen(
    currentConfig: SearchConfig,
    onSave: (SearchConfig) -> Unit,
    onBack: () -> Unit
) {
    var enabled by remember { mutableStateOf(currentConfig.enabled) }
    var provider by remember { mutableStateOf(currentConfig.provider) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var customUrl by remember { mutableStateOf(currentConfig.customUrl) }
    var maxResults by remember { mutableStateOf(currentConfig.maxResults.toString()) }
    var showApiKey by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val config = SearchConfig(
                            enabled = enabled,
                            provider = provider,
                            apiKey = apiKey,
                            customUrl = customUrl,
                            maxResults = maxResults.toIntOrNull() ?: 5
                        )
                        onSave(config)
                    }) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 启用开关
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "启用网络搜索",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "允许 AI 搜索网络获取实时信息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
            }

            if (enabled) {
                // 搜索服务选择
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "搜索服务",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = getProviderDisplayName(provider),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("搜索服务") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                SearchProvider.values().forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(getProviderDisplayName(p)) },
                                        onClick = {
                                            provider = p
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = getProviderDescription(provider),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // API Key（百度和自定义需要）
                if (provider == SearchProvider.BAIDU || provider == SearchProvider.CUSTOM) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "API 密钥",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text("API Key") },
                                singleLine = true,
                                visualTransformation = if (showApiKey) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    TextButton(onClick = { showApiKey = !showApiKey }) {
                                        Text(if (showApiKey) "隐藏" else "显示")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 自定义 URL
                if (provider == SearchProvider.CUSTOM) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "自定义 API URL",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customUrl,
                                onValueChange = { customUrl = it },
                                label = { Text("API URL") },
                                placeholder = { Text("https://api.example.com/search?q={query}&count={count}") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "使用 {query} 作为搜索词占位符，{count} 作为结果数量占位符",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 结果数量
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "搜索结果数量",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = maxResults,
                            onValueChange = {
                                if (it.all { c -> c.isDigit() }) {
                                    maxResults = it
                                }
                            },
                            label = { Text("结果数量") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "每次搜索返回的最大结果数（1-10）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 使用说明
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "使用说明",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = """
                            • 必应中国：无需配置，直接可用
                            • 百度搜索：可能需要配置 API Key
                            • 自定义：支持自定义搜索 API

                            启用后，AI 将在需要实时信息时自动搜索网络。
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 获取提供商显示名称
 */
private fun getProviderDisplayName(provider: SearchProvider): String {
    return when (provider) {
        SearchProvider.BING_CN -> "必应中国"
        SearchProvider.BAIDU -> "百度搜索"
        SearchProvider.CUSTOM -> "自定义 API"
    }
}

/**
 * 获取提供商描述
 */
private fun getProviderDescription(provider: SearchProvider): String {
    return when (provider) {
        SearchProvider.BING_CN -> "必应中国搜索，无需 API Key，适合国内用户"
        SearchProvider.BAIDU -> "百度搜索，可能需要配置 API Key"
        SearchProvider.CUSTOM -> "使用自定义搜索 API，需要配置 URL 和可选的 API Key"
    }
}