@file:OptIn(ExperimentalMaterial3Api::class)

package ai.deepseek.harness.ui

import ai.deepseek.harness.AppearanceThemeMode
import ai.deepseek.harness.BuildConfig
import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.ui.design.DshListItem
import ai.deepseek.harness.ui.design.DshPanel
import ai.deepseek.harness.ui.design.DshTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class SettingsRoute {
  Home,
  Appearance,
  About,
  Licenses,
}

@Composable
fun SettingsScreens(
  viewModel: MainViewModel,
  route: SettingsRoute,
  onNavigateBack: () -> Unit,
  onNavigateTo: (SettingsRoute) -> Unit,
) {
  when (route) {
    SettingsRoute.Home -> SettingsHomeScreen(viewModel, onNavigateTo)
    SettingsRoute.Appearance -> AppearanceSettingsScreen(viewModel, onNavigateBack)
    SettingsRoute.About -> AboutSettingsScreen(onNavigateBack)
    SettingsRoute.Licenses -> LicensesSettingsScreen(onNavigateBack)
  }
}

@Composable
private fun SettingsHomeScreen(
  viewModel: MainViewModel,
  onNavigateTo: (SettingsRoute) -> Unit,
) {
  val sessionUser by viewModel.sessionUser.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(DshTheme.spacing.lg),
  ) {
    Text(
      text = "Settings",
      style = DshTheme.type.section,
      color = DshTheme.colors.text,
    )

    Spacer(modifier = Modifier.height(16.dp))

    DshPanel {
      DshListItem(
        title = "Appearance",
        subtitle = "Theme",
        leading = { Icon(Icons.Default.Palette, contentDescription = null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        onClick = { onNavigateTo(SettingsRoute.Appearance) },
      )

      DshListItem(
        title = "About",
        subtitle = "Version ${BuildConfig.VERSION_NAME}",
        leading = { Icon(Icons.Default.Info, contentDescription = null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        onClick = { onNavigateTo(SettingsRoute.About) },
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    DshPanel {
      DshListItem(
        title = "Logout",
        subtitle = sessionUser,
        leading = { Icon(Icons.Default.Logout, contentDescription = null) },
        onClick = { viewModel.logout() },
      )
    }
  }
}

@Composable
private fun AppearanceSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val themeMode by viewModel.appearanceThemeMode.collectAsState()

  SettingsScaffold(title = "Appearance", onBack = onBack) {
    Column(modifier = Modifier.padding(DshTheme.spacing.lg)) {
      Text(text = "Theme", style = DshTheme.type.section, color = DshTheme.colors.text)
      Spacer(modifier = Modifier.height(12.dp))

      DshPanel {
        DshListItem(
          title = "Light",
          trailing = { if (themeMode == AppearanceThemeMode.Light) Text("✓", color = DshTheme.colors.primary) },
          onClick = { viewModel.setAppearanceThemeMode(AppearanceThemeMode.Light) },
        )
        DshListItem(
          title = "Dark",
          trailing = { if (themeMode == AppearanceThemeMode.Dark) Text("✓", color = DshTheme.colors.primary) },
          onClick = { viewModel.setAppearanceThemeMode(AppearanceThemeMode.Dark) },
        )
        DshListItem(
          title = "System",
          trailing = { if (themeMode == AppearanceThemeMode.System) Text("✓", color = DshTheme.colors.primary) },
          onClick = { viewModel.setAppearanceThemeMode(AppearanceThemeMode.System) },
        )
      }
    }
  }
}

@Composable
private fun AboutSettingsScreen(onBack: () -> Unit) {
  SettingsScaffold(title = "About", onBack = onBack) {
    Column(modifier = Modifier.padding(DshTheme.spacing.lg)) {
      DshPanel {
        DshListItem(title = "Version", subtitle = BuildConfig.VERSION_NAME)
        DshListItem(title = "Build", subtitle = "${BuildConfig.VERSION_CODE}")
      }
    }
  }
}

@Composable
private fun LicensesSettingsScreen(onBack: () -> Unit) {
  SettingsScaffold(title = "Licenses", onBack = onBack) {
    Column(modifier = Modifier.padding(DshTheme.spacing.lg)) {
      Text(text = "Open source licenses", style = DshTheme.type.body, color = DshTheme.colors.textMuted)
    }
  }
}

@Composable
private fun SettingsScaffold(
  title: String,
  onBack: () -> Unit,
  content: @Composable () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState()),
    ) { content() }
  }
}