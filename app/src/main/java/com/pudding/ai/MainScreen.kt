package com.pudding.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pudding.ai.data.model.ModelConfig
import com.pudding.ai.data.model.SearchConfig
import com.pudding.ai.service.BrowserDisplayState
import com.pudding.ai.ui.browser.*
import com.pudding.ai.ui.chat.*
import com.pudding.ai.ui.settings.DebugScreen
import com.pudding.ai.ui.settings.MemoryDebugScreen
import com.pudding.ai.ui.settings.ModelConfigScreen
import com.pudding.ai.ui.settings.SearchConfigScreen
import com.pudding.ai.ui.settings.SettingsScreen
import com.pudding.ai.ui.settings.TaskExecutionDebugScreen
import com.pudding.ai.ui.settings.ToolCallDebugScreen
import com.pudding.ai.ui.tasks.*
import com.pudding.ai.ui.memory.DailyMemoryScreen
import com.pudding.ai.ui.memory.EntityManagementScreen

/**
 * 导航目的地定义
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Chat : Screen("chat", "对话", Icons.Default.Chat)
    object Tasks : Screen("tasks", "任务", Icons.Default.TaskAlt)
    object Settings : Screen("settings", "设置", Icons.Default.Settings)
}

/**
 * 主界面 Composable
 *
 * 包含底部导航和页面切换逻辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val conversationViewModel: ConversationViewModel = hiltViewModel()
    val taskViewModel: TaskViewModel = hiltViewModel()

    // 对话状态
    val messages by conversationViewModel.messages.collectAsState(initial = emptyList())
    val conversations by conversationViewModel.conversations.collectAsState(initial = emptyList())
    val currentConversationId by conversationViewModel.currentConversationId.collectAsState()
    val inputText by conversationViewModel.inputText.collectAsState()
    val isLoading by conversationViewModel.isLoading.collectAsState()
    val modelConfig by conversationViewModel.modelConfig.collectAsState(initial = ModelConfig())
    val streamingMessage by conversationViewModel.streamingMessage.collectAsState()
    val isStreaming by conversationViewModel.isStreaming.collectAsState()
    val streamError by conversationViewModel.streamError.collectAsState()
    val currentStatus by conversationViewModel.currentStatus.collectAsState()

    // 任务状态
    val tasks by taskViewModel.tasks.collectAsState(initial = emptyList())

    // 搜索配置状态
    val searchConfig by conversationViewModel.searchConfig.collectAsState(initial = SearchConfig())

    // 浏览器状态
    val browserDisplayState by conversationViewModel.browserDisplayState.collectAsState()
    val browserUrl by conversationViewModel.browserUrl.collectAsState()
    val isBrowserLoading by conversationViewModel.isBrowserLoading.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val screens = listOf(Screen.Chat, Screen.Tasks, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationRoute ?: screen.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 主内容
            NavHost(
                navController = navController,
                startDestination = Screen.Chat.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            // 对话页面
            composable(Screen.Chat.route) {
                ChatScreen(
                    messages = messages,
                    isLoading = isLoading,
                    currentInput = inputText,
                    onInputChange = { conversationViewModel.setInputText(it) },
                    onSend = { conversationViewModel.sendMessage() },
                    conversations = conversations,
                    currentConversationId = currentConversationId,
                    onSelectConversation = { conversationViewModel.selectConversation(it) },
                    onNewConversation = { conversationViewModel.createNewConversation() },
                    onDeleteConversation = { conversationViewModel.deleteConversation(it) },
                    onClearConversation = { conversationViewModel.clearCurrentConversation() },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    streamingMessage = streamingMessage,
                    isStreaming = isStreaming,
                    streamError = streamError,
                    currentStatus = currentStatus
                )
            }

            // 任务页面
            composable(Screen.Tasks.route) {
                TasksScreen(
                    tasks = tasks,
                    onAddTask = { navController.navigate("task_edit") },
                    onEditTask = { navController.navigate("task_edit/${it.id}") },
                    onToggleTask = { taskViewModel.toggleTask(it) },
                    onDeleteTask = { taskViewModel.deleteTask(it) },
                    onQuickCreate = { taskViewModel.quickCreateTask(it) }
                )
            }

            // 任务编辑页面
            composable("task_edit") {
                TaskEditScreen(
                    task = null,
                    onSave = {
                        taskViewModel.saveTask(it)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("task_edit/{taskId}") { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId")?.toLongOrNull()
                val task = tasks.find { it.id == taskId }
                TaskEditScreen(
                    task = task,
                    onSave = {
                        taskViewModel.saveTask(it)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // 设置主页面
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToModelConfig = { navController.navigate("model_config") },
                    onNavigateToEntityManagement = { navController.navigate("entity_management") },
                    onNavigateToDailyMemory = { navController.navigate("daily_memory") },
                    onNavigateToSearchConfig = { navController.navigate("search_config") },
                    onNavigateToDebug = { navController.navigate("debug") },
                    onBack = { navController.popBackStack() }
                )
            }

            // 模型配置页面
            composable("model_config") {
                ModelConfigScreen(
                    currentConfig = modelConfig,
                    onSave = { config ->
                        conversationViewModel.saveModelConfig(config)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // 搜索配置页面
            composable("search_config") {
                SearchConfigScreen(
                    currentConfig = searchConfig,
                    onSave = { config ->
                        conversationViewModel.saveSearchConfig(config)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // 实体管理页面
            composable("entity_management") {
                EntityManagementScreen(
                    entityTypes = conversationViewModel.entityTypes,
                    entities = conversationViewModel.getAllEntities(),
                    entityAttributes = { entityId -> conversationViewModel.getEntityAttributes(entityId) },
                    onBack = { navController.popBackStack() },
                    onAddEntityType = { name, description, prompt ->
                        conversationViewModel.addEntityType(name, description, prompt)
                    },
                    onDeleteEntityType = { entityType ->
                        conversationViewModel.deleteEntityType(entityType)
                    },
                    onDeleteEntity = { entity ->
                        conversationViewModel.deleteEntity(entity)
                    }
                )
            }

            // 每日记忆页面
            composable("daily_memory") {
                val memoryViewModel: MemoryViewModel = hiltViewModel()

                DailyMemoryScreen(
                    memories = memoryViewModel.getAllDailyMemories(),
                    onBack = { navController.popBackStack() },
                    onGenerateMemory = { /* TODO: 实现手动生成记忆 */ }
                )
            }

            // 调试主页面
            composable("debug") {
                DebugScreen(
                    onNavigateToMemoryDebug = { navController.navigate("memory_debug") },
                    onNavigateToToolCallDebug = { navController.navigate("tool_call_debug") },
                    onNavigateToTaskExecutionDebug = { navController.navigate("task_execution_debug") },
                    onBack = { navController.popBackStack() }
                )
            }

            // 记忆调试页面
            composable("memory_debug") {
                val debugViewModel: DebugViewModel = hiltViewModel()

                MemoryDebugScreen(
                    debugLogRepository = debugViewModel.getDebugLogRepository(),
                    onGenerateMemory = { date ->
                        debugViewModel.generateDailyMemoryWithDebug(date)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // 工具调用调试页面
            composable("tool_call_debug") {
                val debugViewModel: DebugViewModel = hiltViewModel()

                ToolCallDebugScreen(
                    debugLogRepository = debugViewModel.getDebugLogRepository(),
                    onBack = { navController.popBackStack() }
                )
            }

            // 定时任务调试页面
            composable("task_execution_debug") {
                val debugViewModel: DebugViewModel = hiltViewModel()

                TaskExecutionDebugScreen(
                    debugLogRepository = debugViewModel.getDebugLogRepository(),
                    onBack = { navController.popBackStack() }
                )
            }
        }

            // 浏览器浮层（条件显示）
            AnimatedVisibility(
                visible = browserDisplayState == BrowserDisplayState.OVERLAY,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                BrowserOverlay(
                    browserManager = conversationViewModel.browserManager,
                    onCollapse = { conversationViewModel.collapseBrowser() },
                    onClose = { conversationViewModel.closeBrowser() }
                )
            }

            // 浏览器气泡（条件显示）
            if (browserDisplayState == BrowserDisplayState.BUBBLE) {
                BrowserBubbleContainer(
                    url = browserUrl,
                    isLoading = isBrowserLoading,
                    onExpand = { conversationViewModel.expandBrowser() },
                    onClose = { conversationViewModel.closeBrowser() }
                )
            }
        }
    }
}