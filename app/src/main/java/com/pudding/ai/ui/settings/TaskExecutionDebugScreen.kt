package com.pudding.ai.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pudding.ai.data.model.DebugLog
import com.pudding.ai.data.model.DebugLogType
import com.pudding.ai.data.model.TaskExecutionInput
import com.pudding.ai.data.model.TaskExecutionOutput
import com.pudding.ai.data.model.TaskExecutionMeta
import com.pudding.ai.data.repository.DebugLogRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 定时任务调试页面
 *
 * 功能：
 * - 查看定时任务执行历史记录
 * - 查看详细的输入输出参数
 * - 清理日志
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskExecutionDebugScreen(
    debugLogRepository: DebugLogRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // 状态
    var showDetailDialog by remember { mutableStateOf<DebugLog?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var maxRecords by remember { mutableStateOf(5) }
    var showConfigDialog by remember { mutableStateOf(false) }

    // 历史日志
    val logs by debugLogRepository.getLogsByType(DebugLogType.SCHEDULED_TASK)
        .collectAsState(initial = emptyList())

    // 加载配置
    LaunchedEffect(Unit) {
        maxRecords = debugLogRepository.getMaxRecords(DebugLogType.SCHEDULED_TASK)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("定时任务调试") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "配置")
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "清理")
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
            if (logs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无定时任务执行记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "定时任务执行后将在此显示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "最近 ${logs.size} 条记录 (最多保留 $maxRecords 条)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(logs) { log ->
                    TaskExecutionLogItem(
                        log = log,
                        debugLogRepository = debugLogRepository,
                        onClick = { showDetailDialog = log }
                    )
                    Divider()
                }
            }
        }
    }

    // 详情对话框
    showDetailDialog?.let { log ->
        TaskExecutionLogDetailDialog(
            log = log,
            debugLogRepository = debugLogRepository,
            onDismiss = { showDetailDialog = null }
        )
    }

    // 清理确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清理日志") },
            text = { Text("确定要清理所有定时任务执行日志吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            debugLogRepository.deleteLogsByType(DebugLogType.SCHEDULED_TASK)
                        }
                        showClearDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 配置对话框
    if (showConfigDialog) {
        MaxRecordsConfigDialog(
            currentValue = maxRecords,
            onDismiss = { showConfigDialog = false },
            onSave = { newValue ->
                scope.launch {
                    debugLogRepository.updateMaxRecords(DebugLogType.SCHEDULED_TASK, newValue)
                    maxRecords = newValue
                }
                showConfigDialog = false
            }
        )
    }
}

/**
 * 定时任务执行日志列表项
 */
@Composable
fun TaskExecutionLogItem(
    log: DebugLog,
    debugLogRepository: DebugLogRepository,
    onClick: () -> Unit
) {
    var input by remember { mutableStateOf<TaskExecutionInput?>(null) }

    LaunchedEffect(log) {
        input = debugLogRepository.parseTaskExecutionInput(log.inputData)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (log.success) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = if (log.success) "成功" else "失败",
            tint = if (log.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = input?.taskTitle ?: "未知任务",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (log.success) {
                    "耗时 ${log.durationMs}ms"
                } else {
                    log.errorMessage ?: "执行失败"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTaskTimestamp(log.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = onClick) {
            Text("详情")
        }
    }
}

/**
 * 定时任务执行日志详情对话框
 */
@Composable
fun TaskExecutionLogDetailDialog(
    log: DebugLog,
    debugLogRepository: DebugLogRepository,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf<TaskExecutionInput?>(null) }
    var output by remember { mutableStateOf<TaskExecutionOutput?>(null) }
    var meta by remember { mutableStateOf<TaskExecutionMeta?>(null) }
    var expandedPrompt by remember { mutableStateOf(false) }
    var expandedOutput by remember { mutableStateOf(false) }

    LaunchedEffect(log) {
        input = debugLogRepository.parseTaskExecutionInput(log.inputData)
        output = log.parsedOutput?.let { debugLogRepository.parseTaskExecutionOutput(it) }
        meta = log.metadata?.let { debugLogRepository.parseTaskExecutionMeta(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(input?.taskTitle ?: "任务执行") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                // 状态信息
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (log.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (log.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (log.success) "成功" else "失败",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "耗时 ${log.durationMs}ms",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // 任务信息
                input?.let { data ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "任务类型: ${data.taskType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    data.cronExpression?.let { cron ->
                        Text(
                            text = "Cron: $cron",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 执行时间信息
                meta?.let { m ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "执行时间: ${formatTaskTimestamp(m.actualExecutionTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    m.conversationId?.let { cid ->
                        Text(
                            text = "对话ID: $cid",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 任务提示词
                log.promptSent?.let { prompt ->
                    Text(
                        text = "任务提示词",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (expandedPrompt) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = Int.MAX_VALUE
                        )
                        TextButton(onClick = { expandedPrompt = false }) {
                            Text("收起")
                        }
                    } else {
                        val preview = prompt.take(200) + if (prompt.length > 200) "..." else ""
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3
                        )
                        if (prompt.length > 200) {
                            TextButton(onClick = { expandedPrompt = true }) {
                                Text("展开全部")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 执行结果
                Text(
                    text = "执行结果",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                output?.let { out ->
                    out.actionTaken.let { action ->
                        val actionText = when (action) {
                            "notification_sent" -> "已发送通知"
                            "completed" -> "已完成"
                            "failed" -> "失败"
                            else -> action
                        }
                        Text(
                            text = "状态: $actionText",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // AI 响应
                Text(
                    text = "AI 响应",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (expandedOutput) {
                    Text(
                        text = log.rawOutput,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = Int.MAX_VALUE
                    )
                    TextButton(onClick = { expandedOutput = false }) {
                        Text("收起")
                    }
                } else {
                    val outputPreview = log.rawOutput.take(300) + if (log.rawOutput.length > 300) "..." else ""
                    Text(
                        text = outputPreview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 5
                    )
                    if (log.rawOutput.length > 300) {
                        TextButton(onClick = { expandedOutput = true }) {
                            Text("展开全部")
                        }
                    }
                }

                // 错误信息
                log.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "错误信息",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 格式化时间戳
 */
private fun formatTaskTimestamp(timestamp: Long): String {
    val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    return format.format(Date(timestamp))
}