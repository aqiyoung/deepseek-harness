package ai.deepseek.harness.ui

import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.ui.chat.ChatScreen
import ai.deepseek.harness.ui.design.DshBottomNav
import ai.deepseek.harness.ui.design.DshNavItem
import ai.deepseek.harness.ui.design.DshScaffold
import ai.deepseek.harness.ui.design.DshTopBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val tabs = listOf(
  DshNavItem("chat", "Chat", Icons.Default.Chat),
  DshNavItem("sessions", "Sessions", Icons.Default.Home),
  DshNavItem("settings", "Settings", Icons.Default.Settings),
)

/**
 * Main shell with bottom navigation: Chat, Sessions, Settings.
 */
@Composable
fun ShellScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier,
) {
  var selectedTab by rememberSaveable { mutableStateOf("chat") }
  val statusText by viewModel.statusText.collectAsState()

  DshScaffold(
    modifier = modifier,
    contentPadding = PaddingValues(0.dp),
    contentWindowInsets = WindowInsets.systemBars,
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
      // Top bar
      DshTopBar(
        title = "DeepSeek Harness",
        subtitle = statusText,
      )

      // Content area
      Box(modifier = Modifier.fillMaxSize().padding(top = 56.dp, bottom = 64.dp)) {
        when (selectedTab) {
          "chat" -> ChatTab(viewModel)
          "sessions" -> SessionsTab(viewModel)
          "settings" -> SettingsTab(viewModel)
        }
      }

      // Bottom navigation
      Box(modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
        DshBottomNav(
          items = tabs,
          selectedKey = selectedTab,
          onSelect = { selectedTab = it },
        )
      }
    }
  }
}

@Composable
private fun ChatTab(viewModel: MainViewModel) {
  ChatScreen(viewModel = viewModel)
}

@Composable
private fun SessionsTab(viewModel: MainViewModel) {
  SessionsScreen(viewModel = viewModel)
}

@Composable
private fun SettingsTab(viewModel: MainViewModel) {
  SettingsScreens(
    viewModel = viewModel,
    route = SettingsRoute.Home,
    onNavigateBack = {},
    onNavigateTo = {},
  )
}