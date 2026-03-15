package com.pudding.ai.util

import android.content.Context
import com.pudding.ai.ui.chat.PrismGrammarLocator
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
 * Markwon 单例提供者
 *
 * 使用 CompositionLocal 在 Compose 中共享 Markwon 实例，
 * 避免每次重组时创建新的实例，提升性能。
 */
object MarkwonProvider {

    @Volatile
    private var instance: Markwon? = null

    /**
     * 获取 Markwon 实例（线程安全的懒加载）
     */
    fun getInstance(context: Context): Markwon {
        return instance ?: synchronized(this) {
            instance ?: createMarkwon(context).also { instance = it }
        }
    }

    private fun createMarkwon(context: Context): Markwon {
        return Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
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

    /**
     * 重置实例（用于测试或配置变更）
     */
    fun reset() {
        synchronized(this) {
            instance = null
        }
    }
}