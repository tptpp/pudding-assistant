package com.pudding.ai.service

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import com.google.gson.JsonObject
import com.pudding.ai.data.model.BrowserActionResult
import com.pudding.ai.data.model.BrowserSession
import com.pudding.ai.data.model.BrowserState
import com.pudding.ai.data.model.SavedCookie
import com.pudding.ai.data.repository.CookieRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 浏览器显示状态
 */
enum class BrowserDisplayState {
    HIDDEN,      // 完全隐藏（后台运行或无任务）
    OVERLAY,     // 浮层模式（全屏显示）
    BUBBLE       // 气泡模式（收起状态）
}

/**
 * 浏览器管理器
 *
 * 负责管理 WebView 实例，执行浏览器自动化操作
 * 支持可视化和后台两种运行模式
 */
class BrowserManager(
    private val context: Context,
    private val cookieRepository: CookieRepository
) {
    companion object {
        private const val TAG = "BrowserManager"
        private const val DEFAULT_TIMEOUT = 30000L  // 默认超时时间 30 秒
        private const val PAGE_LOAD_TIMEOUT = 60000L  // 页面加载超时 60 秒
    }

    private var webView: WebView? = null
    private var currentSession: BrowserSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // WebView 是否已初始化
    private var isInitialized = false

    // 显示状态
    private val _displayState = MutableStateFlow(BrowserDisplayState.HIDDEN)
    val displayState: StateFlow<BrowserDisplayState> = _displayState

    // 是否有正在进行的浏览器任务
    private val _isTaskActive = MutableStateFlow(false)
    val isTaskActive: StateFlow<Boolean> = _isTaskActive

    /**
     * 设置显示状态
     */
    fun setDisplayState(state: BrowserDisplayState) {
        _displayState.value = state
    }

    /**
     * 显示浮层
     */
    fun showOverlay() {
        _displayState.value = BrowserDisplayState.OVERLAY
    }

    /**
     * 显示气泡（收起浮层）
     */
    fun showBubble() {
        _displayState.value = BrowserDisplayState.BUBBLE
    }

    /**
     * 隐藏（完全隐藏）
     */
    fun hide() {
        _displayState.value = BrowserDisplayState.HIDDEN
    }

    /**
     * 设置任务活跃状态
     */
    fun setTaskActive(active: Boolean) {
        _isTaskActive.value = active
    }

    /**
     * 初始化 WebView
     * 在主线程上创建和配置 WebView
     */
    @Synchronized
    fun initialize(): WebView {
        if (webView != null && isInitialized) {
            return webView!!
        }

        webView = WebView(context).apply {
            // 设置默认尺寸，确保 WebView 可以正常渲染
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // 设置最小尺寸，用于后台模式截图
            setMinimumWidth(1080)
            setMinimumHeight(1920)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowFileAccess = true
                javaScriptCanOpenWindowsAutomatically = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                // 设置 User-Agent
                userAgentString = userAgentString.replace("Mobile", "")
            }

            // 启用 Cookie
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
            }
            // 对 WebView 启用第三方 Cookie
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            // 设置 WebViewClient
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d(TAG, "Page started loading: $url")
                    currentSession = currentSession?.copy(
                        url = url ?: "",
                        state = BrowserState.LOADING
                    )
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page finished loading: $url")
                    currentSession = currentSession?.copy(
                        url = url ?: "",
                        state = BrowserState.READY
                    )
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Log.e(TAG, "WebView error: ${error?.description}")
                    currentSession = currentSession?.copy(state = BrowserState.ERROR)
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    // 对于自签名证书，继续加载（仅用于开发/测试）
                    Log.w(TAG, "SSL error, proceeding: ${error?.primaryError}")
                    handler?.proceed()
                }
            }

            // 设置 WebChromeClient
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    currentSession = currentSession?.copy(title = title ?: "")
                }
            }
        }

        isInitialized = true
        return webView!!
    }

    /**
     * 执行浏览器操作
     *
     * @param action 操作类型
     * @param params 操作参数
     * @return 操作结果
     */
    suspend fun executeAction(
        action: String,
        params: JsonObject
    ): BrowserActionResult = withContext(Dispatchers.Main) {
        // 确保 WebView 已初始化
        if (webView == null) {
            initialize()
        }

        val sessionName = params.get("sessionName")?.asString ?: "default"

        when (action) {
            "navigate" -> navigate(params)
            "click" -> click(params)
            "type" -> type(params)
            "wait" -> waitForElement(params)
            "screenshot" -> screenshot()
            "getContent" -> getContent(params)
            "executeJs" -> executeJs(params)
            "waitForNavigation" -> waitForNavigation(params)
            "fillForm" -> fillForm(params)
            "submit" -> submit(params)
            "saveCookies" -> saveCurrentCookies(sessionName)
            "loadCookies" -> loadCookiesForSession(sessionName, params)
            "clearCookies" -> clearCookies(sessionName)
            else -> BrowserActionResult(false, "未知操作: $action")
        }
    }

    /**
     * 导航到指定 URL
     */
    private suspend fun navigate(params: JsonObject): BrowserActionResult {
        val url = params.get("url")?.asString
        if (url.isNullOrBlank()) {
            return BrowserActionResult(false, "URL 不能为空")
        }

        return suspendCancellableCoroutine { cont ->
            currentSession = BrowserSession(
                id = java.util.UUID.randomUUID().toString(),
                url = url,
                state = BrowserState.LOADING
            )

            var hasResumed = false

            val client = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page finished loading: $url")
                    currentSession = currentSession?.copy(
                        url = url ?: "",
                        state = BrowserState.READY
                    )
                    if (!hasResumed) {
                        hasResumed = true
                        cont.resume(BrowserActionResult(true, "已成功加载: $url"))
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Log.e(TAG, "WebView error: ${error?.description}")
                    currentSession = currentSession?.copy(state = BrowserState.ERROR)
                    if (!hasResumed) {
                        hasResumed = true
                        cont.resume(BrowserActionResult(false, "加载失败: ${error?.description}"))
                    }
                }
            }

            webView?.webViewClient = client
            webView?.loadUrl(url)

            // 设置超时
            scope.launch {
                delay(PAGE_LOAD_TIMEOUT)
                if (!hasResumed) {
                    hasResumed = true
                    cont.resume(BrowserActionResult(false, "页面加载超时"))
                }
            }
        }
    }

    /**
     * 点击元素
     */
    private suspend fun click(params: JsonObject): BrowserActionResult {
        val selector = params.get("selector")?.asString
        if (selector.isNullOrBlank()) {
            return BrowserActionResult(false, "选择器不能为空")
        }

        return executeJsImpl("""
            (function() {
                var element = document.querySelector('$selector');
                if (element) {
                    element.click();
                    return { success: true, message: '已点击元素' };
                } else {
                    return { success: false, message: '未找到元素: $selector' };
                }
            })()
        """.trimIndent())
    }

    /**
     * 输入文本
     */
    private suspend fun type(params: JsonObject): BrowserActionResult {
        val selector = params.get("selector")?.asString
        val text = params.get("text")?.asString

        if (selector.isNullOrBlank()) {
            return BrowserActionResult(false, "选择器不能为空")
        }
        if (text.isNullOrBlank()) {
            return BrowserActionResult(false, "文本不能为空")
        }

        // 转义文本中的引号
        val escapedText = text.replace("'", "\\'").replace("\n", "\\n")

        return executeJsImpl("""
            (function() {
                var element = document.querySelector('$selector');
                if (element) {
                    element.focus();
                    element.value = '$escapedText';
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                    return { success: true, message: '已输入文本' };
                } else {
                    return { success: false, message: '未找到元素: $selector' };
                }
            })()
        """.trimIndent())
    }

    /**
     * 等待元素出现
     */
    private suspend fun waitForElement(params: JsonObject): BrowserActionResult {
        val selector = params.get("selector")?.asString
        val timeout = params.get("timeout")?.asLong ?: DEFAULT_TIMEOUT

        if (selector.isNullOrBlank()) {
            return BrowserActionResult(false, "选择器不能为空")
        }

        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeout) {
            val result = executeJsImpl("""
                (function() {
                    var element = document.querySelector('$selector');
                    return {
                        success: true,
                        message: element ? '元素已找到' : '元素未找到',
                        found: element !== null
                    };
                })()
            """.trimIndent())

            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any>
            if (data?.get("found") == true) {
                return BrowserActionResult(true, "元素已找到: $selector")
            }

            delay(500)  // 每 500ms 检查一次
        }

        return BrowserActionResult(false, "等待元素超时: $selector")
    }

    /**
     * 截图
     */
    private suspend fun screenshot(): BrowserActionResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserActionResult(false, "WebView 未初始化")

        try {
            // 使用固定尺寸进行截图（后台模式）
            val width = if (wv.width > 0) wv.width else 1080
            val height = if (wv.height > 0) wv.height else wv.contentHeight * 2

            wv.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
            )
            wv.layout(0, 0, wv.measuredWidth, wv.measuredHeight)

            // 确保尺寸有效
            val bitmapWidth = maxOf(1, wv.measuredWidth)
            val bitmapHeight = maxOf(1, wv.measuredHeight)

            val bitmap = Bitmap.createBitmap(
                bitmapWidth,
                bitmapHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            wv.draw(canvas)

            // 转换为 Base64
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)

            BrowserActionResult(
                success = true,
                message = "截图成功",
                data = mapOf(
                    "base64" to base64,
                    "width" to bitmap.width,
                    "height" to bitmap.height
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot failed", e)
            BrowserActionResult(false, "截图失败: ${e.message}")
        }
    }

    /**
     * 获取页面内容
     */
    private suspend fun getContent(params: JsonObject): BrowserActionResult {
        val selector = params.get("selector")?.asString

        val jsCode = if (selector.isNullOrBlank()) {
            """
                (function() {
                    return {
                        success: true,
                        message: '获取页面内容成功',
                        content: document.body.innerText,
                        html: document.body.innerHTML
                    };
                })()
            """.trimIndent()
        } else {
            """
                (function() {
                    var element = document.querySelector('$selector');
                    if (element) {
                        return {
                            success: true,
                            message: '获取元素内容成功',
                            content: element.innerText,
                            html: element.innerHTML
                        };
                    } else {
                        return { success: false, message: '未找到元素: $selector' };
                    }
                })()
            """.trimIndent()
        }

        return executeJsImpl(jsCode)
    }

    /**
     * 执行 JavaScript
     */
    private suspend fun executeJs(params: JsonObject): BrowserActionResult {
        val script = params.get("script")?.asString
        if (script.isNullOrBlank()) {
            return BrowserActionResult(false, "脚本不能为空")
        }
        return executeJsImpl(script)
    }

    /**
     * 执行 JavaScript 的内部实现
     */
    private suspend fun executeJsImpl(script: String): BrowserActionResult = suspendCancellableCoroutine { cont ->
        webView?.evaluateJavascript(script) { result ->
            try {
                // 尝试解析 JSON 结果
                val gson = com.google.gson.Gson()
                @Suppress("UNCHECKED_CAST")
                val resultMap = gson.fromJson(result, Map::class.java) as? Map<String, Any>

                if (resultMap != null) {
                    val success = resultMap["success"] as? Boolean ?: false
                    val message = resultMap["message"] as? String ?: "执行完成"
                    val data = resultMap.filterKeys { it != "success" && it != "message" }
                    cont.resume(BrowserActionResult(success, message, data))
                } else {
                    cont.resume(BrowserActionResult(true, "执行完成", mapOf("result" to result)))
                }
            } catch (e: Exception) {
                // 如果不是 JSON，直接返回结果
                if (result == "null") {
                    cont.resume(BrowserActionResult(true, "执行完成"))
                } else {
                    cont.resume(BrowserActionResult(true, "执行完成", mapOf("result" to result)))
                }
            }
        } ?: cont.resume(BrowserActionResult(false, "WebView 未初始化"))
    }

    /**
     * 等待页面导航完成
     */
    private suspend fun waitForNavigation(params: JsonObject): BrowserActionResult {
        val timeout = params.get("timeout")?.asLong ?: PAGE_LOAD_TIMEOUT
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeout) {
            val state = currentSession?.state
            if (state == BrowserState.READY) {
                return BrowserActionResult(true, "页面加载完成")
            }
            delay(200)
        }

        return BrowserActionResult(false, "等待页面加载超时")
    }

    /**
     * 填充表单
     */
    private suspend fun fillForm(params: JsonObject): BrowserActionResult {
        val fieldsObj = params.get("fields")?.asJsonObject
        if (fieldsObj == null || fieldsObj.entrySet().isEmpty()) {
            return BrowserActionResult(false, "表单字段不能为空")
        }

        val results = mutableListOf<String>()

        for ((selector, valueElement) in fieldsObj.entrySet()) {
            val value = valueElement.asString
            val escapedValue = value.replace("'", "\\'")

            val result = executeJsImpl("""
                (function() {
                    var element = document.querySelector('$selector');
                    if (element) {
                        element.focus();
                        element.value = '$escapedValue';
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        return { success: true, message: '已填充字段' };
                    } else {
                        return { success: false, message: '未找到元素: $selector' };
                    }
                })()
            """.trimIndent())

            if (!result.success) {
                results.add("$selector: 失败")
            }
        }

        return BrowserActionResult(
            success = true,
            message = "表单填充完成: ${results.size} 个字段"
        )
    }

    /**
     * 提交表单
     */
    private suspend fun submit(params: JsonObject): BrowserActionResult {
        val selector = params.get("selector")?.asString

        val jsCode = if (selector.isNullOrBlank()) {
            """
                (function() {
                    var forms = document.forms;
                    if (forms.length > 0) {
                        forms[0].submit();
                        return { success: true, message: '已提交第一个表单' };
                    } else {
                        return { success: false, message: '页面上没有表单' };
                    }
                })()
            """.trimIndent()
        } else {
            """
                (function() {
                    var form = document.querySelector('$selector');
                    if (form && form.tagName === 'FORM') {
                        form.submit();
                        return { success: true, message: '已提交表单' };
                    } else {
                        return { success: false, message: '未找到表单: $selector' };
                    }
                })()
            """.trimIndent()
        }

        return executeJsImpl(jsCode)
    }

    /**
     * 保存当前会话的 Cookie
     */
    private suspend fun saveCurrentCookies(sessionName: String): BrowserActionResult {
        return try {
            val url = currentSession?.url ?: return BrowserActionResult(false, "没有当前会话")
            val cookieManager = CookieManager.getInstance()
            val cookieString = cookieManager.getCookie(url)

            if (cookieString.isNullOrBlank()) {
                return BrowserActionResult(false, "没有 Cookie 可保存")
            }

            // 解析 Cookie 字符串
            val cookies = parseCookieString(cookieString, url)

            // 保存到仓库
            cookieRepository.saveCookies(sessionName, url, cookies)

            BrowserActionResult(true, "已保存 ${cookies.size} 个 Cookie 到会话: $sessionName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cookies", e)
            BrowserActionResult(false, "保存 Cookie 失败: ${e.message}")
        }
    }

    /**
     * 加载指定会话的 Cookie
     */
    private suspend fun loadCookiesForSession(sessionName: String, params: JsonObject): BrowserActionResult {
        return try {
            val url = params.get("url")?.asString ?: currentSession?.url
            ?: return BrowserActionResult(false, "需要提供 URL")

            val cookies = cookieRepository.loadCookies(sessionName)

            if (cookies.isEmpty()) {
                return BrowserActionResult(false, "会话 $sessionName 没有保存的 Cookie")
            }

            val cookieManager = CookieManager.getInstance()

            for (cookie in cookies) {
                val cookieString = "${cookie.name}=${cookie.value}"
                cookieManager.setCookie(url, cookieString)
            }

            // 同步 Cookie
            cookieManager.flush()

            BrowserActionResult(true, "已加载 ${cookies.size} 个 Cookie 从会话: $sessionName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cookies", e)
            BrowserActionResult(false, "加载 Cookie 失败: ${e.message}")
        }
    }

    /**
     * 清除会话 Cookie
     */
    private suspend fun clearCookies(sessionName: String): BrowserActionResult {
        return try {
            cookieRepository.clearCookies(sessionName)
            BrowserActionResult(true, "已清除会话 $sessionName 的 Cookie")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cookies", e)
            BrowserActionResult(false, "清除 Cookie 失败: ${e.message}")
        }
    }

    /**
     * 解析 Cookie 字符串
     */
    private fun parseCookieString(cookieString: String, url: String): List<SavedCookie> {
        return cookieString.split(";").mapNotNull { cookie ->
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2) {
                SavedCookie(
                    name = parts[0].trim(),
                    value = parts[1].trim(),
                    domain = cookieRepository.extractDomain(url)
                )
            } else {
                null
            }
        }
    }

    /**
     * 获取 WebView 实例（用于 UI 显示）
     */
    fun getWebView(): WebView? {
        return webView
    }

    /**
     * 获取当前会话
     */
    fun getCurrentSession(): BrowserSession? {
        return currentSession
    }

    /**
     * 清理资源
     */
    fun destroy() {
        webView?.apply {
            stopLoading()
            settings.javaScriptEnabled = false
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
            onPause()
            removeAllViews()
            destroy()
        }
        webView = null
        currentSession = null
        isInitialized = false
        _displayState.value = BrowserDisplayState.HIDDEN
        _isTaskActive.value = false
        scope.cancel()
        Log.d(TAG, "BrowserManager destroyed")
    }
}