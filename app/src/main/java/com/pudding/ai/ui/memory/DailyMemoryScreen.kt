package com.pudding.ai.ui.memory

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pudding.ai.data.model.DailyMemory
import kotlinx.coroutines.flow.Flow

/**
 * 每日记忆页面
 *
 * 显示每日记忆列表，支持：
 * - 查看每日记忆的 Markdown 内容
 * - 搜索记忆
 * - 手动生成记忆
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMemoryScreen(
    memories: Flow<List<DailyMemory>>,
    onBack: () -> Unit,
    onGenerateMemory: (String) -> Unit
) {
    val memoriesList by memories.collectAsState(initial = emptyList())
    var searchText by remember { mutableStateOf("") }
    var showMemoryDetail by remember { mutableStateOf<DailyMemory?>(null) }

    // 搜索过滤
    val filteredMemories = remember(memoriesList, searchText) {
        if (searchText.isBlank()) memoriesList
        else memoriesList.filter {
            it.title.contains(searchText, ignoreCase = true) ||
            it.summary.contains(searchText, ignoreCase = true) ||
            it.content.contains(searchText, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("每日记忆") },
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
        ) {
            // 搜索栏
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("搜索记忆...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true
            )

            // 记忆列表
            if (filteredMemories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无记忆记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "记忆会在每天自动整理生成",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn {
                    items(filteredMemories) { memory ->
                        MemoryItem(
                            memory = memory,
                            onClick = { showMemoryDetail = memory }
                        )
                        Divider()
                    }
                }
            }
        }

        // 记忆详情对话框
        showMemoryDetail?.let { memory ->
            MemoryDetailDialog(
                memory = memory,
                onDismiss = { showMemoryDetail = null }
            )
        }
    }
}

/**
 * 记忆列表项
 */
@Composable
fun MemoryItem(
    memory: DailyMemory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Article,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = memory.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = memory.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = memory.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = "查看详情",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 记忆详情对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailDialog(
    memory: DailyMemory,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(memory.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Text(
                    text = "日期: ${memory.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Divider()

                Spacer(modifier = Modifier.height(8.dp))

                // 显示 Markdown 内容
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}