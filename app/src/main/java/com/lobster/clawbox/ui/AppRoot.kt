package com.lobster.clawbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lobster.clawbox.data.AppPrefs
import com.lobster.clawbox.ui.screens.BootSetupScreen
import com.lobster.clawbox.ui.screens.ChatScreen
import com.lobster.clawbox.ui.screens.HomeScreen
import com.lobster.clawbox.ui.screens.ManageScreen
import com.lobster.clawbox.ui.screens.SettingsScreen
import com.lobster.clawbox.ui.screens.TerminalScreen
import com.lobster.clawbox.ui.theme.Bg
import com.lobster.clawbox.ui.theme.Bg3
import com.lobster.clawbox.ui.theme.Purple
import com.lobster.clawbox.ui.theme.TextMuted

private data class TabItem(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("状态", Icons.Filled.Home),
    TabItem("聊天", Icons.Filled.ChatBubble),
    TabItem("终端", Icons.Filled.Terminal),
    TabItem("管理", Icons.Filled.Widgets),
    TabItem("设置", Icons.Filled.Settings),
)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }

    if (!prefs.setupDone) {
        BootSetupScreen(
            onDone = { prefs.initialized = true },
        )
        return
    }

    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F121B),
                contentColor = TextMuted,
            ) {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = { Icon(tab.icon, null, Modifier.padding(top = 3.dp)) },
                        label = { Text(tab.label, fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Purple,
                            selectedTextColor = Purple,
                            indicatorColor = Bg3,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad),
        ) {
            when (selected) {
                0 -> HomeScreen(onOpenChat = { selected = 1 })
                1 -> ChatScreen()
                2 -> TerminalScreen()
                3 -> ManageScreen()
                4 -> SettingsScreen()
            }
        }
    }
}
