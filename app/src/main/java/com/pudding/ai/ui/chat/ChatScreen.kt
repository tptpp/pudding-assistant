package com.pudding.ai.ui.chat

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pudding.ai.data.model.Conversation
import com.pudding.ai.data.model.Message
import com.pudding.ai.data.model.MessageRole
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j

/**
 * 聊天界面 Composable
 *
 * 显示对话消息列表和输入区域，支持：
 * - 消息列表展示
 * - 流式响应显示
 * - 对话历史抽屉
 * - Markdown 渲染
 * - 代码语法高亮
 *
 * @param messages 消息列表
 * @param isLoading 是否正在加载
 * @param currentInput 当前输入文本
 * @param onInputChange 输入变化回调
 * @param onSend 发送消息回调
 * @param conversations 对话列表
 * @param currentConversationId 当前对话ID
 * @param onSelectConversation 选择对话回调
 * @param onNewConversation 新建对话回调
 * @param onDeleteConversation 删除对话回调
 * @param onClearConversation 清空对话回调
 * @param onNavigateToSettings 导航到设置页面回调
 * @param streamingMessage 流式响应消息
 * @param isStreaming 是否正在流式响应
 * @param streamError 流式响应错误信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<Message>,
    isLoading: Boolean,
    currentInput: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    conversations: List<Conversation>,
    currentConversationId: Long?,
    onSelectConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onClearConversation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    streamingMessage: String = "",
    isStreaming: Boolean = false,
    streamError: String? = null
) {
    val listState = rememberLazyListState()
    var showDrawer by remember { mutableStateOf(false) }

    // 自动滚动到底部（消息变化或流式消息更新时）
    LaunchedEffect(messages.size, streamingMessage) {
        if (messages.isNotEmpty() || streamingMessage.isNotEmpty()) {
            val targetIndex = messages.size + if (streamingMessage.isNotEmpty()) 1 else 0
            if (targetIndex > 0) {
                listState.animateScrollToItem(targetIndex - 1)
            }
        }
    }

    if (showDrawer) {
        ModalDrawerSheet {
            ChatDrawerContent(
                conversations = conversations,
                currentConversationId = currentConversationId,
                onSelectConversation = {
                    onSelectConversation(it)
                    showDrawer = false
                },
                onNewConversation = {
                    onNewConversation()
                    showDrawer = false
                },
                onDeleteConversation = onDeleteConversation,
                onClearConversation = onClearConversation,
                onClose = { showDrawer = false }
            )
        }
    }

    // 不使用 Scaffold，直接用 Column 避免嵌套问题
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部栏
        TopAppBar(
            title = { Text("Assistant") },
            navigationIcon = {
                IconButton(onClick = { showDrawer = true }) {
                    Icon(Icons.Default.Menu, contentDescription = "对话历史")
                }
            },
            actions = {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }
        )

        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty() && streamingMessage.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("开始新对话", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }

                // 显示流式响应
                if (streamingMessage.isNotEmpty()) {
                    item {
                        StreamingMessageBubble(content = streamingMessage)
                    }
                }

                // 显示错误信息
                if (streamError != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.widthIn(max = 300.dp)
                        ) {
                            Text(
                                text = "错误: $streamError",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // 显示加载指示器（仅在没有流式消息时显示）
            if (isLoading && streamingMessage.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "思考中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 输入区域
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()  // 导航栏 padding
                .imePadding(),           // 输入法 padding
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = currentInput,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息...") },
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = onSend,
                    enabled = currentInput.isNotBlank() && !isLoading && !isStreaming
                ) {
                    Icon(Icons.Default.Send, contentDescription = "发送")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            MarkdownText(
                markdown = message.content,
                isUserMessage = isUser,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/**
 * 流式响应消息气泡 - 显示实时流式内容
 */
@Composable
fun StreamingMessageBubble(content: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                MarkdownText(
                    markdown = content,
                    isUserMessage = false,
                    modifier = Modifier
                )
                // 流式指示器
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "生成中...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatDrawerContent(
    conversations: List<Conversation>,
    currentConversationId: Long?,
    onSelectConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onClearConversation: () -> Unit,
    onClose: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxHeight()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "对话历史",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        
        FilledTonalButton(
            onClick = onNewConversation,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("新建对话")
        }
        
        // 清空对话按钮
        OutlinedButton(
            onClick = { showClearDialog = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            enabled = currentConversationId != null
        ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("清空对话")
        }
        
        Divider()
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(conversations) { conversation ->
                ConversationItem(
                    conversation = conversation,
                    isSelected = conversation.id == currentConversationId,
                    onSelect = { onSelectConversation(conversation.id) },
                    onDelete = { onDeleteConversation(conversation.id) }
                )
            }
        }
        
        TextButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text("关闭")
        }
    }
    
    // 清空对话确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空对话") },
            text = { Text("确定要清空当前对话的所有消息吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearConversation()
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除对话") },
            text = { Text("确定要删除这个对话吗？") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

/**
 * Markdown渲染组件 - 使用Markwon渲染Markdown内容
 */
@Composable
fun MarkdownText(
    markdown: String,
    isUserMessage: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 创建Markwon实例（使用remember缓存）
    val markwon = remember {
        val ctx = context as Context
        Markwon.builder(ctx)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(ctx))
            .usePlugin(TaskListPlugin.create(ctx))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(
                SyntaxHighlightPlugin.create(
                    Prism4j(PrismGrammarLocator()),
                    Prism4jThemeDefault.create()
                )
            )
            .build()
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(
                    if (isUserMessage) {
                        android.graphics.Color.WHITE
                    } else {
                        android.graphics.Color.BLACK
                    }
                )
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
        },
        modifier = modifier
    )
}