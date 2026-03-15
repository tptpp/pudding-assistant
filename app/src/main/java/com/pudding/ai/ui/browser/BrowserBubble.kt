package com.pudding.ai.ui.browser

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 浏览器气泡组件
 *
 * 收起浮层后显示在右下角的悬浮气泡
 * 点击重新打开浮层，点击 X 结束任务
 */
@Composable
fun BrowserBubble(
    url: String,
    isLoading: Boolean = false,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 气泡主体
    Card(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 浏览器图标 + 加载动画
            Box(
                modifier = Modifier.clickable { onExpand() },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    // 加载中动画
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "浏览器运行中",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // URL 显示（可点击展开）
            Text(
                text = getDisplayUrl(url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(max = 120.dp)
                    .clickable { onExpand() }
            )

            // 关闭按钮
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭浏览器",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 从 URL 中提取显示名称
 */
private fun getDisplayUrl(url: String): String {
    if (url.isBlank()) return "浏览器"

    return try {
        val uri = java.net.URI(url)
        uri.host ?: url.take(20)
    } catch (e: Exception) {
        url.take(20)
    }
}

/**
 * 悬浮气泡容器
 *
 * 用于在屏幕右下角显示气泡，处理位置和动画
 */
@Composable
fun BrowserBubbleContainer(
    url: String,
    isLoading: Boolean,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        BrowserBubble(
            url = url,
            isLoading = isLoading,
            onExpand = onExpand,
            onClose = onClose,
            modifier = Modifier.padding(bottom = 80.dp, end = 16.dp)
        )
    }
}