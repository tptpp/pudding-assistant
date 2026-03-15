package com.pudding.ai.ui.browser

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pudding.ai.data.model.BrowserSession
import com.pudding.ai.data.model.BrowserState
import com.pudding.ai.service.BrowserManager

/**
 * 浏览器自动化界面
 *
 * 显示 WebView 内容，支持用户观察和干预自动化操作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    browserManager: BrowserManager?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }

    val session = browserManager?.getCurrentSession()

    LaunchedEffect(session) {
        session?.let {
            currentUrl = it.url
            pageTitle = it.title
            isLoading = it.state == BrowserState.LOADING
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部工具栏
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = pageTitle.ifEmpty { "浏览器自动化" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (currentUrl.isNotEmpty()) {
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            },
            actions = {
                // 刷新按钮
                IconButton(onClick = {
                    browserManager?.getWebView()?.reload()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
                // 后退按钮
                IconButton(onClick = {
                    browserManager?.getWebView()?.goBack()
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "后退")
                }
                // 前进按钮
                IconButton(onClick = {
                    browserManager?.getWebView()?.goForward()
                }) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "前进")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // URL 输入栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入网址") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        if (inputUrl.isNotEmpty()) {
                            val url = if (!inputUrl.startsWith("http")) {
                                "https://$inputUrl"
                            } else {
                                inputUrl
                            }
                            browserManager?.getWebView()?.loadUrl(url)
                        }
                    }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "前往")
                    }
                }
            )
        }

        // 加载进度指示器
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        // WebView 内容区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            val webView = browserManager?.getWebView()

            if (webView != null) {
                AndroidView(
                    factory = {
                        webView.apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "浏览器未初始化",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 底部状态栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态指示
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (session?.state) {
                                    BrowserState.READY -> Color.Green
                                    BrowserState.LOADING -> Color.Yellow
                                    BrowserState.ERROR -> Color.Red
                                    else -> Color.Gray
                                }
                            )
                    )
                    Text(
                        text = when (session?.state) {
                            BrowserState.READY -> "就绪"
                            BrowserState.LOADING -> "加载中..."
                            BrowserState.ERROR -> "错误"
                            BrowserState.IDLE -> "空闲"
                            else -> "未知"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // 会话名称
                session?.id?.let { id ->
                    Text(
                        text = "会话: ${id.take(8)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 浏览器操作面板
 *
 * 显示当前正在执行的自动化操作
 */
@Composable
fun BrowserActionPanel(
    action: String,
    params: Map<String, Any>,
    result: String?,
    isExecuting: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (result != null) Icons.Default.Check else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (result != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "操作: $action",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // 参数显示
            if (params.isNotEmpty()) {
                Text(
                    text = "参数:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                params.forEach { (key, value) ->
                    Text(
                        text = "  $key: $value",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 结果显示
            if (result != null) {
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "结果: $result",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}