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
import com.pudding.ai.data.model.ToolCallInput
import com.pudding.ai.data.model.ToolCallOutput
import com.pudding.ai.data.model.ToolCallMeta
import com.pudding.ai.data.repository.DebugLogRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 工具调用调试页面
 *
 * 功能：
 * - 查看工具调用历史记录
 * - 查看详细的输入输出参数
 * - 清理日志
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCallDebugScreen(
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
    val logs by debugLogRepository.getLogsByType(DebugLogType.TOOL_CALL)
        .collectAsState(initial = emptyList())

    // 加载配置
    LaunchedEffect(Unit) {
        maxRecords = debugLogRepository.getMaxRecords(DebugLogType.TOOL_CALL)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工具调用调试") },
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
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无工具调用记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "在对话中使用工具后将在此显示",
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
                    ToolCallLogItem(
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
        ToolCallLogDetailDialog(
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
            text = { Text("确定要清理所有工具调用日志吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            debugLogRepository.deleteLogsByType(DebugLogType.TOOL_CALL)
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
                    debugLogRepository.updateMaxRecords(DebugLogType.TOOL_CALL, newValue)
                    maxRecords = newValue
                }
                showConfigDialog = false
            }
        )
    }
}

/**
 * 工具调用日志列表项
 */
@Composable
fun ToolCallLogItem(
    log: DebugLog,
    debugLogRepository: DebugLogRepository,
    onClick: () -> Unit
) {
    var input by remember { mutableStateOf<ToolCallInput?>(null) }

    LaunchedEffect(log) {
        input = debugLogRepository.parseToolCallInput(log.inputData)
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
                text = input?.toolName ?: "未知工具",
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
                text = formatTimestamp(log.createdAt),
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
 * 工具调用日志详情对话框
 */
@Composable
fun ToolCallLogDetailDialog(
    log: DebugLog,
    debugLogRepository: DebugLogRepository,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf<ToolCallInput?>(null) }
    var output by remember { mutableStateOf<ToolCallOutput?>(null) }
    var meta by remember { mutableStateOf<ToolCallMeta?>(null) }
    var expandedParams by remember { mutableStateOf(false) }
    var expandedOutput by remember { mutableStateOf(false) }

    LaunchedEffect(log) {
        input = debugLogRepository.parseToolCallInput(log.inputData)
        output = log.parsedOutput?.let { debugLogRepository.parseToolCallOutput(it) }
        meta = log.metadata?.let { debugLogRepository.parseToolCallMeta(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(input?.toolName ?: "工具调用") },
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

                // 时间信息
                meta?.let { m ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "执行时间: ${formatTimestamp(log.createdAt)}",
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

                // 输入参数
                Text(
                    text = "输入参数",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                input?.parametersJson?.let { paramsJson ->
                    if (expandedParams) {
                        Text(
                            text = formatJson(paramsJson),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = Int.MAX_VALUE
                        )
                        TextButton(onClick = { expandedParams = false }) {
                            Text("收起")
                        }
                    } else {
                        val preview = paramsJson.take(200) + if (paramsJson.length > 200) "..." else ""
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3
                        )
                        if (paramsJson.length > 200) {
                            TextButton(onClick = { expandedParams = true }) {
                                Text("展开全部")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 输出结果
                Text(
                    text = "输出结果",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                output?.let { out ->
                    Text(
                        text = out.message,
                        style = MaterialTheme.typography.bodySmall
                    )
                    out.dataPreview?.let { preview ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "数据预览: $preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 原始输出
                Text(
                    text = "原始响应",
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
private fun formatTimestamp(timestamp: Long): String {
    val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    return format.format(Date(timestamp))
}

/**
 * 简单格式化 JSON（添加缩进）
 */
private fun formatJson(json: String): String {
    return try {
        val indent = 2
        val sb = StringBuilder()
        var indentLevel = 0
        var inString = false

        for (char in json) {
            when {
                char == '"' && (sb.isEmpty() || sb.last() != '\\') -> {
                    inString = !inString
                    sb.append(char)
                }
                inString -> sb.append(char)
                char == '{' || char == '[' -> {
                    sb.append(char).append('\n')
                    indentLevel++
                    repeat(indentLevel * indent) { sb.append(' ') }
                }
                char == '}' || char == ']' -> {
                    sb.append('\n')
                    indentLevel--
                    repeat(indentLevel * indent) { sb.append(' ') }
                    sb.append(char)
                }
                char == ',' -> {
                    sb.append(char).append('\n')
                    repeat(indentLevel * indent) { sb.append(' ') }
                }
                char == ':' -> sb.append(char).append(' ')
                !char.isWhitespace() -> sb.append(char)
            }
        }
        sb.toString()
    } catch (e: Exception) {
        json
    }
}