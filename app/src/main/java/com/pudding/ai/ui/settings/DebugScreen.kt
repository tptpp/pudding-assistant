package com.pudding.ai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 调试分类项
 */
data class DebugCategoryItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * 调试主页面
 *
 * 提供各功能调试的入口，目前包含记忆调试和工具调用调试。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onNavigateToMemoryDebug: () -> Unit,
    onNavigateToToolCallDebug: () -> Unit,
    onNavigateToTaskExecutionDebug: () -> Unit,
    onBack: () -> Unit
) {
    val debugItems = listOf(
        DebugCategoryItem(
            title = "记忆调试",
            subtitle = "测试和查看记忆生成过程",
            icon = Icons.Default.Psychology,
            onClick = onNavigateToMemoryDebug
        ),
        DebugCategoryItem(
            title = "工具调用调试",
            subtitle = "查看工具执行记录",
            icon = Icons.Default.Build,
            onClick = onNavigateToToolCallDebug
        ),
        DebugCategoryItem(
            title = "定时任务调试",
            subtitle = "查看定时任务执行记录",
            icon = Icons.Default.Schedule,
            onClick = onNavigateToTaskExecutionDebug
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调试") },
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
            items(debugItems) { item ->
                DebugCategoryListItem(item = item)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

/**
 * 调试分类列表项
 */
@Composable
fun DebugCategoryListItem(
    item: DebugCategoryItem,
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