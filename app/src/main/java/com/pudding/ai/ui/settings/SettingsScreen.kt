package com.pudding.ai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 设置项数据类
 */
data class SettingsItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * 设置主页面
 *
 * 作为设置的入口页面，提供列表式的设置选项，
 * 点击各项进入对应的二级设置页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToModelConfig: () -> Unit,
    onNavigateToEntityManagement: () -> Unit,
    onNavigateToDailyMemory: () -> Unit,
    onNavigateToSearchConfig: () -> Unit,
    onNavigateToDebug: () -> Unit,
    onBack: () -> Unit
) {
    val settingsItems = listOf(
        SettingsItem(
            title = "模型配置",
            subtitle = "API 密钥、模型选择等",
            icon = Icons.Default.Settings,
            onClick = onNavigateToModelConfig
        ),
        SettingsItem(
            title = "网络搜索",
            subtitle = "启用 AI 网络搜索功能",
            icon = Icons.Default.Search,
            onClick = onNavigateToSearchConfig
        ),
        SettingsItem(
            title = "实体管理",
            subtitle = "查看和管理提取的实体",
            icon = Icons.Default.Person,
            onClick = onNavigateToEntityManagement
        ),
        SettingsItem(
            title = "每日记忆",
            subtitle = "查看历史对话摘要",
            icon = Icons.Default.History,
            onClick = onNavigateToDailyMemory
        ),
        SettingsItem(
            title = "调试",
            subtitle = "测试和查看功能执行过程",
            icon = Icons.Default.BugReport,
            onClick = onNavigateToDebug
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(settingsItems) { item ->
                SettingsListItem(item = item)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

/**
 * 设置列表项
 */
@Composable
fun SettingsListItem(
    item: SettingsItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { item.onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = "进入",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}