package com.pudding.ai.ui.settings

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pudding.ai.data.model.DebugLog
import com.pudding.ai.data.model.DebugLogType
import com.pudding.ai.data.model.MemoryGenerationInput
import com.pudding.ai.data.repository.DebugLogRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 记忆生成状态
 */
sealed class MemoryGenerationState {
    object Idle : MemoryGenerationState()
    object Loading : MemoryGenerationState()
    data class Success(val logId: Long) : MemoryGenerationState()
    data class Error(val message: String) : MemoryGenerationState()
}

/**
 * 记忆调试页面
 *
 * 功能：
 * - 手动触发每日记忆生成
 * - 查看历史生成记录
 * - 查看详细调试信息
 * - 导出日志
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDebugScreen(
    debugLogRepository: DebugLogRepository,
    onGenerateMemory: suspend (String) -> Pair<Boolean, Long?>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状态
    var selectedDate by remember { mutableStateOf(LocalDate.now().minusDays(1)) }
    var generationState by remember { mutableStateOf<MemoryGenerationState>(MemoryGenerationState.Idle) }
    var showDetailDialog by remember { mutableStateOf<DebugLog?>(null) }
    var maxRecords by remember { mutableStateOf(5) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    // 历史日志
    val logs by debugLogRepository.getLogsByType(DebugLogType.MEMORY_GENERATION)
        .collectAsState(initial = emptyList())

    // 加载配置
    LaunchedEffect(Unit) {
        maxRecords = debugLogRepository.getMaxRecords(DebugLogType.MEMORY_GENERATION)
    }

    // 日期选择器
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).apply {
            // 限制可选日期范围（最近7天）
            datePicker.minDate = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            datePicker.maxDate = System.currentTimeMillis() - 24 * 60 * 60 * 1000L // 昨天为止
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆调试") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "导出")
                    }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 手动生成区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "手动生成记忆",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "选择日期: ${selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(onClick = { datePickerDialog.show() }) {
                            Text("选择")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                generationState = MemoryGenerationState.Loading
                                val dateStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                val (success, logId) = onGenerateMemory(dateStr)
                                if (success && logId != null) {
                                    generationState = MemoryGenerationState.Success(logId)
                                } else {
                                    generationState = MemoryGenerationState.Error("生成失败")
                                }
                            }
                        },
                        enabled = generationState !is MemoryGenerationState.Loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (generationState is MemoryGenerationState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("生成中...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("生成记忆")
                        }
                    }
                }
            }

            // 历史记录区域
            Text(
                text = "历史生成记录 (保留最近 $maxRecords 条)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(logs) { log ->
                    MemoryLogItem(
                        log = log,
                        onClick = { showDetailDialog = log }
                    )
                    Divider()
                }

                if (logs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // 详情对话框
    showDetailDialog?.let { log ->
        MemoryLogDetailDialog(
            log = log,
            debugLogRepository = debugLogRepository,
            onDismiss = { showDetailDialog = null }
        )
    }

    // 配置对话框
    if (showConfigDialog) {
        MaxRecordsConfigDialog(
            currentValue = maxRecords,
            onDismiss = { showConfigDialog = false },
            onSave = { newValue ->
                scope.launch {
                    debugLogRepository.updateMaxRecords(DebugLogType.MEMORY_GENERATION, newValue)
                    maxRecords = newValue
                }
                showConfigDialog = false
            }
        )
    }

    // 导出对话框
    if (showExportDialog) {
        ExportLogDialog(
            logs = logs,
            onDismiss = { showExportDialog = false },
            onExport = { format ->
                scope.launch {
                    // TODO: 实现实际的导出逻辑（保存到文件或分享）
                    // 这里暂时只关闭对话框
                    showExportDialog = false
                }
            }
        )
    }

    // 清理确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清理日志") },
            text = { Text("确定要清理所有记忆生成日志吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            debugLogRepository.deleteLogsByType(DebugLogType.MEMORY_GENERATION)
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
}

/**
 * 记忆日志列表项
 */
@Composable
fun MemoryLogItem(
    log: DebugLog,
    onClick: () -> Unit
) {
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
                text = log.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (log.success) {
                    "耗时 ${log.durationMs}ms"
                } else {
                    log.errorMessage ?: "未知错误"
                },
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
 * 记忆日志详情对话框
 */
@Composable
fun MemoryLogDetailDialog(
    log: DebugLog,
    debugLogRepository: DebugLogRepository,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf<MemoryGenerationInput?>(null) }
    var expandedPrompt by remember { mutableStateOf(false) }
    var expandedOutput by remember { mutableStateOf(false) }

    LaunchedEffect(log) {
        input = debugLogRepository.parseMemoryInput(log.inputData)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(log.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
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

                Spacer(modifier = Modifier.height(16.dp))

                // 消息统计
                input?.let { data ->
                    Text(
                        text = "消息统计",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("总数: ${data.messageCount} 条")
                    Text("用户: ${data.userMessages} 条，助手: ${data.assistantMessages} 条")
                    Text("时间范围: ${data.timeRange}")

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 发送的提示词
                log.promptSent?.let { prompt ->
                    Text(
                        text = "发送的提示词",
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
                        Text(
                            text = prompt.take(200) + if (prompt.length > 200) "..." else "",
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

                // AI 原始响应
                Text(
                    text = "AI 原始响应",
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
                    Text(
                        text = log.rawOutput.take(300) + if (log.rawOutput.length > 300) "..." else "",
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
 * 最大记录数配置对话框
 */
@Composable
fun MaxRecordsConfigDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var value by remember { mutableStateOf(currentValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置保留记录数") },
        text = {
            Column {
                Text("设置记忆调试日志的最大保留数量")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { c -> c.isDigit() } },
                    label = { Text("最大记录数") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intValue = value.toIntOrNull() ?: 5
                    onSave(intValue.coerceIn(1, 50))
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 导出日志对话框
 */
@Composable
fun ExportLogDialog(
    logs: List<DebugLog>,
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    var selectedFormat by remember { mutableStateOf("json") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出日志") },
        text = {
            Column {
                Text("选择导出格式：")
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedFormat == "json",
                        onClick = { selectedFormat = "json" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("JSON 格式")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedFormat == "text",
                        onClick = { selectedFormat = "text" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("纯文本格式")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "共 ${logs.size} 条记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(selectedFormat) }
            ) {
                Text("导出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}