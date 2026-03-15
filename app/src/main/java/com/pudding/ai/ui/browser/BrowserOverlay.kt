package com.pudding.ai.ui.browser

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.pudding.ai.data.model.BrowserState
import com.pudding.ai.service.BrowserManager

/**
 * 浏览器浮层组件
 *
 * 从底部滑出的全屏浮层，显示 WebView 内容
 * 用户可以观察、手动操作、或收起到气泡
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserOverlay(
    browserManager: BrowserManager,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val session = browserManager.getCurrentSession()

    LaunchedEffect(session) {
        session?.let {
            currentUrl = it.url
            pageTitle = it.title
            isLoading = it.state == BrowserState.LOADING
        }
    }

    // 定时更新 URL 和标题
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            browserManager.getCurrentSession()?.let {
                currentUrl = it.url
                pageTitle = it.title
                isLoading = it.state == BrowserState.LOADING
            }
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
                        text = pageTitle.ifEmpty { "浏览器" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    if (currentUrl.isNotEmpty()) {
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起")
                }
            },
            actions = {
                // 刷新按钮
                IconButton(onClick = {
                    browserManager.getWebView()?.reload()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
                // 后退按钮
                IconButton(onClick = {
                    browserManager.getWebView()?.goBack()
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "后退")
                }
                // 前进按钮
                IconButton(onClick = {
                    browserManager.getWebView()?.goForward()
                }) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "前进")
                }
                // 关闭按钮
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

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
            val webView = browserManager.getWebView()

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

                // 提示信息
                Text(
                    text = "点击收起按钮可最小化到气泡",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}