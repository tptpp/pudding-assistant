package com.pudding.ai.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pudding.ai.data.model.*
import java.util.Calendar

// Cron 预设选项
enum class CronPreset(val label: String, val description: String) {
    EVERY_MINUTE("每分钟", "每分钟执行一次"),
    HOURLY("每小时", "每小时整点执行"),
    DAILY("每天", "每天指定时间执行"),
    WEEKDAYS("工作日", "周一至周五指定时间执行"),
    WEEKLY("每周一", "每周一指定时间执行")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    task: Task?,
    onSave: (Task) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var prompt by remember { mutableStateOf(task?.prompt ?: "") }
    var taskType by remember { mutableStateOf(task?.type ?: TaskType.SCHEDULED) }
    var cronPreset by remember { mutableStateOf(CronPreset.DAILY) }
    var hour by remember { mutableStateOf(task?.scheduledTime?.let {
        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.HOUR_OF_DAY)
    } ?: 9) }
    var minute by remember { mutableStateOf(task?.scheduledTime?.let {
        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.MINUTE)
    } ?: 0) }
    var oneTimeDate by remember { mutableStateOf(task?.scheduledTime ?: System.currentTimeMillis() + 86400000) }

    val scrollState = rememberScrollState()

    // 根据 task 的 cronExpression 初始化 preset
    LaunchedEffect(task?.cronExpression) {
        task?.cronExpression?.let { cron ->
            cronPreset = when {
                cron == "* * * * *" -> CronPreset.EVERY_MINUTE
                cron.startsWith("0 *") -> CronPreset.HOURLY
                cron.contains("1-5") -> CronPreset.WEEKDAYS
                cron.endsWith("* * 1") -> CronPreset.WEEKLY
                else -> CronPreset.DAILY
            }
        }
    }

    // 生成 Cron 表达式
    fun generateCronExpression(): String {
        return when (cronPreset) {
            CronPreset.EVERY_MINUTE -> "* * * * *"
            CronPreset.HOURLY -> "0 * * * *"
            CronPreset.DAILY -> "$minute $hour * * *"
            CronPreset.WEEKDAYS -> "$minute $hour * * 1-5"
            CronPreset.WEEKLY -> "$minute $hour * * 1"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (task == null) "新建任务" else "编辑任务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val calendar = Calendar.getInstance()
                            if (taskType == TaskType.ONE_TIME) {
                                calendar.timeInMillis = oneTimeDate
                            } else {
                                calendar.set(Calendar.HOUR_OF_DAY, hour)
                                calendar.set(Calendar.MINUTE, minute)
                                calendar.set(Calendar.SECOND, 0)
                            }

                            val newTask = Task(
                                id = task?.id ?: 0,
                                title = title,
                                prompt = prompt,
                                type = taskType,
                                cronExpression = if (taskType == TaskType.SCHEDULED) generateCronExpression() else null,
                                scheduledTime = calendar.timeInMillis,
                                status = task?.status ?: TaskStatus.ACTIVE
                            )
                            onSave(newTask)
                        },
                        enabled = title.isNotBlank() && prompt.isNotBlank()
                    ) {
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
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("任务名称") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("例如：每日新闻摘要") },
                singleLine = true
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("执行内容") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                placeholder = { Text("AI 将执行的内容...") }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("任务类型", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = taskType == TaskType.SCHEDULED,
                    onClick = { taskType = TaskType.SCHEDULED },
                    label = { Text("定时任务") }
                )
                FilterChip(
                    selected = taskType == TaskType.ONE_TIME,
                    onClick = { taskType = TaskType.ONE_TIME },
                    label = { Text("一次性") }
                )
            }

            if (taskType == TaskType.SCHEDULED) {
                Text("执行频率", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CronPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = cronPreset == preset,
                            onClick = { cronPreset = preset },
                            label = { Text("${preset.label} - ${preset.description}") }
                        )
                    }
                }

                if (cronPreset != CronPreset.EVERY_MINUTE && cronPreset != CronPreset.HOURLY) {
                    Text("执行时间", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = hour.toString(),
                            onValueChange = { hour = it.toIntOrNull()?.coerceIn(0, 23) ?: hour },
                            label = { Text("时") },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Text(":", style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField(
                            value = minute.toString().padStart(2, '0'),
                            onValueChange = { minute = it.toIntOrNull()?.coerceIn(0, 59) ?: minute },
                            label = { Text("分") },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                    }
                }

                // 显示当前 Cron 表达式
                Text(
                    "Cron 表达式: ${generateCronExpression()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("快速模板", style = MaterialTheme.typography.titleMedium)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(
                    onClick = {
                        title = "每日新闻摘要"
                        prompt = "请总结今天的科技新闻，列出5条最重要的"
                        taskType = TaskType.SCHEDULED
                        cronPreset = CronPreset.DAILY
                        hour = 9
                        minute = 0
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("每日新闻", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                OutlinedCard(
                    onClick = {
                        title = "提醒事项"
                        prompt = "提醒我完成待办事项"
                        taskType = TaskType.ONE_TIME
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("提醒事项", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}