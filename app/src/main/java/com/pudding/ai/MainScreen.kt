package com.pudding.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pudding.ai.data.model.ModelConfig
import com.pudding.ai.ui.chat.*
import com.pudding.ai.ui.settings.SettingsScreen
import com.pudding.ai.ui.tasks.*

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
    val conversationViewModel: ConversationViewModel = viewModel()
    val taskViewModel: TaskViewModel = viewModel()

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

    // 任务状态
    val tasks by taskViewModel.tasks.collectAsState(initial = emptyList())

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
                    streamError = streamError
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

            // 设置页面
            composable(Screen.Settings.route) {
                SettingsScreen(
                    currentConfig = modelConfig,
                    onSave = { config ->
                        conversationViewModel.saveModelConfig(config)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}