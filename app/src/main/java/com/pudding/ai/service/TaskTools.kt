package com.pudding.ai.service

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * 工具定义，用于 Function Call
 */
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction
)

data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolProperty>,
    val required: List<String> = emptyList()
)

data class ToolProperty(
    val type: String,
    val description: String,
    val `enum`: List<String>? = null
)

/**
 * 工具调用请求
 */
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

data class ToolCallFunction(
    val name: String,
    @SerializedName("arguments")
    val argumentsJson: String
) {
    fun getArgumentsAsJsonObject(): JsonObject {
        return gson.fromJson(argumentsJson, JsonObject::class.java)
    }

    companion object {
        private val gson = com.google.gson.Gson()
    }
}

/**
 * 工具执行结果
 */
data class ToolResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

/**
 * 任务相关的工具定义
 */
object TaskTools {

    /**
     * 创建任务工具
     */
    val CREATE_TASK = ToolDefinition(
        function = ToolFunction(
            name = "create_task",
            description = """
                创建一个提醒任务。支持两种类型：
                1. 定时任务（SCHEDULED）：使用 cron 表达式定义重复规则
                2. 一次性任务（ONE_TIME）：指定具体日期时间执行一次

                Cron 表达式格式：分 时 日 月 周
                - "*" 表示任意值
                - "1-5" 表示范围
                - "*/5" 表示每5单位

                示例：
                - 每分钟: "* * * * *"
                - 每天8点: "0 8 * * *"
                - 工作日9点: "0 9 * * 1-5"
                - 每周一14:30: "30 14 * * 1"
            """.trimIndent(),
            parameters = ToolParameters(
                properties = mapOf(
                    "title" to ToolProperty(
                        type = "string",
                        description = "任务标题，简短描述任务内容"
                    ),
                    "prompt" to ToolProperty(
                        type = "string",
                        description = "任务执行时的提示内容，AI将根据此内容生成回复"
                    ),
                    "taskType" to ToolProperty(
                        type = "string",
                        description = "任务类型：SCHEDULED（定时任务）或 ONE_TIME（一次性任务）",
                        enum = listOf("SCHEDULED", "ONE_TIME")
                    ),
                    "cronExpression" to ToolProperty(
                        type = "string",
                        description = "Cron表达式，仅定时任务需要。格式：分 时 日 月 周"
                    ),
                    "scheduledDateTime" to ToolProperty(
                        type = "string",
                        description = "执行时间，仅一次性任务需要。格式：yyyy-MM-dd HH:mm"
                    )
                ),
                required = listOf("title", "prompt", "taskType")
            )
        )
    )

    /**
     * 删除任务工具
     */
    val DELETE_TASK = ToolDefinition(
        function = ToolFunction(
            name = "delete_task",
            description = "删除指定的任务。可以通过任务ID或任务标题（模糊匹配）来指定任务。",
            parameters = ToolParameters(
                properties = mapOf(
                    "taskId" to ToolProperty(
                        type = "integer",
                        description = "任务ID（如果知道具体ID）"
                    ),
                    "taskTitle" to ToolProperty(
                        type = "string",
                        description = "任务标题（模糊匹配，用于查找要删除的任务）"
                    )
                ),
                required = emptyList()
            )
        )
    )

    /**
     * 查询任务工具
     */
    val LIST_TASKS = ToolDefinition(
        function = ToolFunction(
            name = "list_tasks",
            description = "查询当前所有任务列表，可选按状态筛选。",
            parameters = ToolParameters(
                properties = mapOf(
                    "status" to ToolProperty(
                        type = "string",
                        description = "筛选状态：ACTIVE（启用）、PAUSED（暂停）、COMPLETED（已完成）、DISABLED（禁用）",
                        enum = listOf("ACTIVE", "PAUSED", "COMPLETED", "DISABLED")
                    )
                ),
                required = emptyList()
            )
        )
    )

    /**
     * 修改任务状态工具
     */
    val UPDATE_TASK_STATUS = ToolDefinition(
        function = ToolFunction(
            name = "update_task_status",
            description = "修改任务状态：启用、暂停、禁用。可以通过任务ID或任务标题（模糊匹配）来指定任务。",
            parameters = ToolParameters(
                properties = mapOf(
                    "taskId" to ToolProperty(
                        type = "integer",
                        description = "任务ID（如果知道具体ID）"
                    ),
                    "taskTitle" to ToolProperty(
                        type = "string",
                        description = "任务标题（模糊匹配，用于查找要修改的任务）"
                    ),
                    "status" to ToolProperty(
                        type = "string",
                        description = "新状态：ACTIVE（启用）、PAUSED（暂停）、DISABLED（禁用）",
                        enum = listOf("ACTIVE", "PAUSED", "DISABLED")
                    )
                ),
                required = listOf("status")
            )
        )
    )

    /**
     * 发送通知工具
     */
    val SEND_NOTIFICATION = ToolDefinition(
        function = ToolFunction(
            name = "send_notification",
            description = "向用户发送系统通知。用于提醒用户重要信息或任务结果。AI可以根据情况决定是否发送通知以及通知的内容。",
            parameters = ToolParameters(
                properties = mapOf(
                    "title" to ToolProperty(
                        type = "string",
                        description = "通知标题，简短描述通知主题"
                    ),
                    "message" to ToolProperty(
                        type = "string",
                        description = "通知内容，详细描述要通知用户的信息"
                    )
                ),
                required = listOf("title", "message")
            )
        )
    )

    /**
     * 网络搜索工具
     */
    val WEB_SEARCH = ToolDefinition(
        function = ToolFunction(
            name = "web_search",
            description = "搜索网络获取实时信息。当用户询问时事、新闻、天气、具体数据等需要最新信息时使用此工具。",
            parameters = ToolParameters(
                properties = mapOf(
                    "query" to ToolProperty(
                        type = "string",
                        description = "搜索关键词，简洁明确的搜索词效果更好"
                    ),
                    "search_type" to ToolProperty(
                        type = "string",
                        description = "搜索类型：general(通用搜索)、news(新闻搜索)",
                        enum = listOf("general", "news")
                    )
                ),
                required = listOf("query")
            )
        )
    )

    /**
     * 浏览器自动化工具
     */
    val BROWSER_ACTION = ToolDefinition(
        function = ToolFunction(
            name = "browser_action",
            description = """
                执行浏览器自动化操作，支持自动登录、填表、抢票等功能。

                支持的操作：
                - navigate: 打开网页
                - click: 点击元素
                - type: 输入文本
                - wait: 等待元素出现
                - screenshot: 截图
                - getContent: 获取页面内容
                - executeJs: 执行 JavaScript
                - waitForNavigation: 等待页面加载
                - fillForm: 填充表单
                - submit: 提交表单
                - saveCookies: 保存当前 Cookie
                - loadCookies: 加载保存的 Cookie
                - clearCookies: 清除 Cookie

                使用 CSS 选择器定位元素，例如：
                - #username (ID 选择器)
                - .btn (类选择器)
                - input[name="email"] (属性选择器)

                Cookie 会话用于保存登录状态，例如：
                - 首次登录时使用 saveCookies 保存登录状态
                - 后续操作时使用 loadCookies 恢复登录状态

                可视化模式：
                - 设置 visible=true 可以显示浏览器窗口，让用户看到操作过程
                - 适用于需要用户观察、手动输入密码等场景
            """.trimIndent(),
            parameters = ToolParameters(
                properties = mapOf(
                    "action" to ToolProperty(
                        type = "string",
                        description = "操作类型",
                        enum = listOf(
                            "navigate", "click", "type", "wait", "screenshot",
                            "getContent", "executeJs", "waitForNavigation",
                            "fillForm", "submit", "saveCookies", "loadCookies", "clearCookies"
                        )
                    ),
                    "visible" to ToolProperty(
                        type = "boolean",
                        description = "是否显示浏览器窗口让用户可见。当用户说'让我看看'、'我要看'、'打开给我看'等时设置为 true"
                    ),
                    "url" to ToolProperty(
                        type = "string",
                        description = "URL（navigate 操作必需）"
                    ),
                    "selector" to ToolProperty(
                        type = "string",
                        description = "CSS 选择器（click/type/wait/submit 操作必需）"
                    ),
                    "text" to ToolProperty(
                        type = "string",
                        description = "输入的文本（type 操作必需）"
                    ),
                    "timeout" to ToolProperty(
                        type = "integer",
                        description = "超时时间（毫秒），默认 30000"
                    ),
                    "script" to ToolProperty(
                        type = "string",
                        description = "JavaScript 脚本（executeJs 操作必需）"
                    ),
                    "fields" to ToolProperty(
                        type = "object",
                        description = "表单字段，格式：{\"选择器\": \"值\", ...}"
                    ),
                    "sessionName" to ToolProperty(
                        type = "string",
                        description = "Cookie 会话名称，用于区分不同网站（默认 'default'）"
                    )
                ),
                required = listOf("action")
            )
        )
    )

    /**
     * 任务执行时使用的工具
     * 用于定时任务执行时 AI 可用的工具集
     */
    val TASK_EXECUTION_TOOLS = listOf(SEND_NOTIFICATION)

    /**
     * 对话时使用的工具
     * 用于用户与 AI 对话时可用的工具集
     */
    val CONVERSATION_TOOLS = listOf(CREATE_TASK, DELETE_TASK, LIST_TASKS, UPDATE_TASK_STATUS, WEB_SEARCH, BROWSER_ACTION)

    /**
     * 所有工具列表
     */
    val ALL_TOOLS = CONVERSATION_TOOLS + TASK_EXECUTION_TOOLS
}