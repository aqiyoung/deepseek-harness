@file:OptIn(ExperimentalMaterial3Api::class)

package ai.deepseek.harness.ui

import ai.deepseek.harness.AppearanceThemeMode
import ai.deepseek.harness.AppLanguage
import ai.deepseek.harness.BuildConfig
import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.appLanguageRowSubtitle
import ai.deepseek.harness.currentSystemLanguageTag
import ai.deepseek.harness.ui.design.DshListItem
import ai.deepseek.harness.ui.design.DshPanel
import ai.deepseek.harness.ui.design.DshTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SettingsRoute {
  Home,
  Appearance,
  Language,
  About,
  Licenses,
}

enum class SettingsTab(val label: String) {
  General("通用"),
  Model("模型"),
  Plugins("插件"),
  Presets("预设"),
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
    SettingsRoute.Language -> LanguageSettingsScreen(viewModel, onNavigateBack)
    SettingsRoute.About -> AboutSettingsScreen(onNavigateBack)
    SettingsRoute.Licenses -> LicensesSettingsScreen(onNavigateBack)
  }
}

@Composable
private fun SettingsHomeScreen(
  viewModel: MainViewModel,
  onNavigateTo: (SettingsRoute) -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val tabList = listOf(SettingsTab.General, SettingsTab.Model, SettingsTab.Plugins, SettingsTab.Presets)

  Column(modifier = Modifier.fillMaxSize()) {
    TabRow(
      selectedTabIndex = selectedTab,
      containerColor = DshTheme.colors.surfaceRaised,
      contentColor = DshTheme.colors.primary,
    ) {
      tabList.forEachIndexed { index, tab ->
        Tab(
          text = { Text(tab.label, style = DshTheme.type.label, color = if (index == selectedTab) DshTheme.colors.primary else DshTheme.colors.textMuted) },
          selected = index == selectedTab,
          onClick = { selectedTab = index },
        )
      }
    }

    HorizontalDivider()

    when (tabList[selectedTab]) {
      SettingsTab.General -> GeneralSettingsTab(viewModel, onNavigateTo)
      SettingsTab.Model -> ModelSettingsTab(viewModel)
      SettingsTab.Plugins -> PluginsSettingsTab(viewModel)
      SettingsTab.Presets -> PresetsSettingsTab(viewModel)
    }
  }
}

@Composable
private fun GeneralSettingsTab(
  viewModel: MainViewModel,
  onNavigateTo: (SettingsRoute) -> Unit,
) {
  val sessionUser by viewModel.sessionUser.collectAsState()
  val serverUrl by viewModel.dsh.serverUrl.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(DshTheme.spacing.lg),
  ) {
    Spacer(modifier = Modifier.height(8.dp))

    DshPanel {
      DshListItem(
        title = "Appearance",
        subtitle = "Theme",
        leading = { Icon(Icons.Default.Palette, contentDescription = null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        onClick = { onNavigateTo(SettingsRoute.Appearance) },
      )
      DshListItem(
        title = "Language",
        subtitle = "System",
        leading = { Icon(Icons.Default.Translate, contentDescription = null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        onClick = { onNavigateTo(SettingsRoute.Language) },
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    DshPanel {
      DshListItem(
        title = "Server",
        subtitle = serverUrl.ifEmpty { "Not configured" },
        leading = { Icon(Icons.Default.VpnKey, contentDescription = null) },
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    DshPanel {
      DshListItem(
        title = "Logout",
        subtitle = sessionUser.ifBlank { "未登录" },
        leading = { Icon(Icons.Default.Logout, contentDescription = null) },
        onClick = { viewModel.logout() },
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    DshPanel {
      DshListItem(
        title = "About",
        subtitle = "Version ${BuildConfig.VERSION_NAME}",
        leading = { Icon(Icons.Default.Info, contentDescription = null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        onClick = { onNavigateTo(SettingsRoute.About) },
      )
    }
  }
}

@Composable
private fun ModelSettingsTab(viewModel: MainViewModel) {
  val modelGroups by viewModel.dsh.modelGroups.collectAsState()
  val preferredModel by viewModel.preferredModel.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(DshTheme.spacing.lg),
  ) {
    Text(text = "Preferred Model", style = DshTheme.type.section, color = DshTheme.colors.text)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "选择此模型作为新会话的默认模型，输入框上方可切换。",
      style = DshTheme.type.caption,
      color = DshTheme.colors.textMuted,
    )
    Spacer(modifier = Modifier.height(16.dp))

    if (modelGroups.isEmpty()) {
      Text(text = "暂无可用模型", style = DshTheme.type.body, color = DshTheme.colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
    } else {
      modelGroups.forEach { group ->
        Text(text = group.name, style = DshTheme.type.caption, color = DshTheme.colors.primary, modifier = Modifier.padding(vertical = 4.dp))
        group.models.forEach { model ->
          val isSelected = preferredModel == model.name
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(if (isSelected) DshTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(text = model.name, style = DshTheme.type.body, color = if (isSelected) DshTheme.colors.primary else DshTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = DshTheme.colors.primary)
          }
          Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }
}

@Composable
private fun PluginsSettingsTab(viewModel: MainViewModel) {
  val providers by viewModel.dsh.providers.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(DshTheme.spacing.lg),
  ) {
    Text(text = "Plugins & Tools", style = DshTheme.type.section, color = DshTheme.colors.text)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "已注册的插件和工具会在此显示。", style = DshTheme.type.caption, color = DshTheme.colors.textMuted)
    Spacer(modifier = Modifier.height(16.dp))

    if (providers.isEmpty()) {
      Text(text = "暂无插件", style = DshTheme.type.body, color = DshTheme.colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
    } else {
      providers.forEach { p ->
        DshListItem(
          title = p.name,
          subtitle = p.id,
          trailing = {
            Text(
              text = if (p.active) "Active" else "Inactive",
              style = DshTheme.type.caption,
              color = if (p.active) DshTheme.colors.success else DshTheme.colors.textMuted,
            )
          },
        )
      }
    }
  }
}

@Composable
private fun PresetsSettingsTab(viewModel: MainViewModel) {
  val presets by viewModel.dsh.presets.collectAsState()
  val preferredPreset by viewModel.preferredPreset.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(DshTheme.spacing.lg),
  ) {
    Text(text = "Agent Presets", style = DshTheme.type.section, color = DshTheme.colors.text)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "选择此预设为新会话默认 Agent。",
      style = DshTheme.type.caption,
      color = DshTheme.colors.textMuted,
    )
    Spacer(modifier = Modifier.height(16.dp))

    if (presets.isEmpty()) {
      Text(text = "暂无预设", style = DshTheme.type.body, color = DshTheme.colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
    } else {
      presets.forEach { preset ->
        val isSelected = preferredPreset == preset.id
        DshListItem(
          title = preset.name,
          subtitle = preset.id,
          trailing = {
            if (isSelected) Text("✓", color = DshTheme.colors.primary)
            if (preset.isDefault) Text("Default", style = DshTheme.type.caption, color = DshTheme.colors.textSubtle)
          },
          onClick = {
            viewModel.setPreferredPreset(if (isSelected) null else preset.id)
          },
        )
      }
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
private fun LanguageSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val currentLang by viewModel.appLanguage.collectAsState()
  val context = LocalContext.current
  val systemTag = currentSystemLanguageTag(context)

  SettingsScaffold(title = "Language", onBack = onBack) {
    Column(modifier = Modifier.padding(DshTheme.spacing.lg)) {
      Text(text = "App Language", style = DshTheme.type.section, color = DshTheme.colors.text)
      Spacer(modifier = Modifier.height(12.dp))

      DshPanel {
        AppLanguage.entries.forEach { lang ->
          DshListItem(
            title = lang.displayName,
            subtitle = appLanguageRowSubtitle(lang, systemTag),
            trailing = {
              if (currentLang == lang) Text("✓", color = DshTheme.colors.primary)
            },
            onClick = { viewModel.applyAppLanguage(lang) },
          )
        }
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