package com.pudding.ai.ui.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pudding.ai.data.model.TrackedEntity
import com.pudding.ai.data.model.EntityAttribute
import com.pudding.ai.data.model.EntityType
import kotlinx.coroutines.flow.Flow

/**
 * 实体管理页面
 *
 * 显示所有实体类型和实体，支持：
 * - 按类型筛选实体
 * - 查看实体属性历史
 * - 新增/编辑/删除实体类型
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityManagementScreen(
    entityTypes: Flow<List<EntityType>>,
    entities: Flow<List<TrackedEntity>>,
    entityAttributes: (Long) -> Flow<List<EntityAttribute>>,
    onBack: () -> Unit,
    onAddEntityType: (String, String, String) -> Unit,
    onDeleteEntityType: (EntityType) -> Unit,
    onDeleteEntity: (TrackedEntity) -> Unit
) {
    val types by entityTypes.collectAsState(initial = emptyList())
    val allEntities by entities.collectAsState(initial = emptyList())

    var selectedTypeId by remember { mutableStateOf<Long?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEntityDetail by remember { mutableStateOf<TrackedEntity?>(null) }

    // 筛选后的实体列表
    val filteredEntities = remember(selectedTypeId, allEntities) {
        if (selectedTypeId == null) allEntities
        else allEntities.filter { it.typeId == selectedTypeId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("实体管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加实体类型")
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
            // 类型筛选标签
            if (types.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTypeId == null,
                            onClick = { selectedTypeId = null },
                            label = { Text("全部") }
                        )
                    }
                    items(types) { type ->
                        FilterChip(
                            selected = selectedTypeId == type.id,
                            onClick = { selectedTypeId = type.id },
                            label = { Text(type.name) }
                        )
                    }
                }
            }

            // 实体列表
            if (filteredEntities.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PersonOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无实体",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "实体会在对话中自动提取",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn {
                    items(filteredEntities) { entity ->
                        EntityItem(
                            entity = entity,
                            entityTypeName = types.find { it.id == entity.typeId }?.name ?: "",
                            onClick = { showEntityDetail = entity }
                        )
                        Divider()
                    }
                }
            }
        }

        // 添加实体类型对话框
        if (showAddDialog) {
            AddEntityTypeDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, description, prompt ->
                    onAddEntityType(name, description, prompt)
                    showAddDialog = false
                }
            )
        }

        // 实体详情对话框
        showEntityDetail?.let { entity ->
            EntityDetailDialog(
                entity = entity,
                entityTypeName = types.find { it.id == entity.typeId }?.name ?: "",
                attributes = entityAttributes(entity.id),
                onDismiss = { showEntityDetail = null },
                onDelete = {
                    onDeleteEntity(entity)
                    showEntityDetail = null
                }
            )
        }
    }
}

/**
 * 实体列表项
 */
@Composable
fun EntityItem(
    entity: TrackedEntity,
    entityTypeName: String,
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
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = entity.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = entityTypeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = "查看详情",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 添加实体类型对话框
 */
@Composable
fun AddEntityTypeDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, prompt: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加实体类型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("类型名称") },
                    placeholder = { Text("如：人物、公司、项目") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("类型描述") },
                    placeholder = { Text("描述这个实体类型的含义") },
                    singleLine = false,
                    maxLines = 2
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("提取提示") },
                    placeholder = { Text("AI 提取该类型实体时的提示") },
                    singleLine = false,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description, prompt) },
                enabled = name.isNotBlank()
            ) {
                Text("确定")
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
 * 实体详情对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityDetailDialog(
    entity: TrackedEntity,
    entityTypeName: String,
    attributes: Flow<List<EntityAttribute>>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val attrs by attributes.collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entity.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "类型: $entityTypeName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider()

                if (attrs.isEmpty()) {
                    Text(
                        text = "暂无属性记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "当前属性:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    attrs.filter { it.isCurrent }.forEach { attr ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = attr.key,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(text = attr.value)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}