package com.pudding.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pudding.ai.data.model.ModelConfig
import com.pudding.ai.ui.chat.*
import com.pudding.ai.ui.settings.SettingsScreen
import com.pudding.ai.ui.tasks.*
import com.pudding.ai.ui.theme.AssistantTheme

/**
 * 主 Activity
 *
 * 应用的入口点，负责初始化界面和导航。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                MainScreen()
            }
        }
    }
}