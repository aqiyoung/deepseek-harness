package ai.deepseek.harness.ui

import ai.deepseek.harness.DSHHUB_SKILL_GATEWAY_UNAVAILABLE
import ai.deepseek.harness.GatewayDshHubInstallReview
import ai.deepseek.harness.GatewayDshHubSkillSearchState
import ai.deepseek.harness.GatewayDshHubSkillSummary
import ai.deepseek.harness.GatewaySkillSummary
import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.i18n.nativeString
import ai.deepseek.harness.isDshHubSkillInstalled
import ai.deepseek.harness.isDshHubSkillOperationActive
import ai.deepseek.harness.ui.design.DshDetailRow
import ai.deepseek.harness.ui.design.DshIconButton
import ai.deepseek.harness.ui.design.DshListPanel
import ai.deepseek.harness.ui.design.DshPanel
import ai.deepseek.harness.ui.design.DshPill
import ai.deepseek.harness.ui.design.DshPrimaryButton
import ai.deepseek.harness.ui.design.DshSecondaryButton
import ai.deepseek.harness.ui.design.DshSegmentedControl
import ai.deepseek.harness.ui.design.DshStatus
import ai.deepseek.harness.ui.design.DshStatusPill
import ai.deepseek.harness.ui.design.DshTextBadge
import ai.deepseek.harness.ui.design.DshTextField
import ai.deepseek.harness.ui.design.DshTheme
import ai.deepseek.harness.uppercaseFirstGraphemeOrNull
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private enum class SkillsTab {
  Installed,
  Browse,
}

private enum class InstalledSkillFilter {
  All,
  Ready,
  Setup,
  Off,
}

/** Settings screen for gateway skills and their readiness state. */
@Composable
internal fun SkillsSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val skillsSummary by viewModel.skillsSummary.collectAsState()
  val skillsRefreshing by viewModel.skillsRefreshing.collectAsState()
  val skillsErrorText by viewModel.skillsErrorText.collectAsState()
  val skillMutationKeys by viewModel.skillMutationKeys.collectAsState()
  val dshHubState by viewModel.dshHubSkillSearchState.collectAsState()
  val dshHubMethodsAvailable by viewModel.dshHubSkillMethodsAvailable.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val operatorAdminScopeAvailable by viewModel.operatorAdminScopeAvailable.collectAsState()
  val canManageSkills = isConnected && operatorAdminScopeAvailable
  val skills = skillsSummary.skills
  val readyCount = skills.count { skillReady(it) }
  val needsSetupCount = skills.count { skillNeedsSetup(it) }
  val disabledCount = skills.count { it.disabled }
  var selectedSkillKey by remember { mutableStateOf<String?>(null) }
  var selectedTabName by rememberSaveable { mutableStateOf(SkillsTab.Installed.name) }
  var installedSearch by rememberSaveable { mutableStateOf("") }
  var installedFilterName by rememberSaveable { mutableStateOf(InstalledSkillFilter.All.name) }
  var dshHubQuery by rememberSaveable { mutableStateOf("") }
  val selectedTab = SkillsTab.entries.firstOrNull { it.name == selectedTabName } ?: SkillsTab.Installed
  val installedFilter =
    InstalledSkillFilter.entries.firstOrNull { it.name == installedFilterName }
      ?: InstalledSkillFilter.All
  val visibleSkills =
    remember(skills, installedSearch, installedFilter) {
      filterInstalledSkills(skills, installedSearch, installedFilter)
    }

  LaunchedEffect(isConnected) {
    if (isConnected) {
      viewModel.refreshSkills()
    }
  }

  selectedSkillKey?.let { skillKey ->
    val selectedSkill = skills.firstOrNull { it.skillKey == skillKey }
    SkillDetailSettingsScreen(
      skill = selectedSkill,
      skillKey = skillKey,
      isConnected = isConnected,
      canManageSkills = canManageSkills,
      isMutating = skillKey in skillMutationKeys,
      onSkillEnabledChange = viewModel::setSkillEnabled,
      onBack = { selectedSkillKey = null },
    )
    return
  }

  SettingsDetailFrame(
    title = nativeString("Skills"),
    subtitle = nativeString("Manage installed skills and add trusted releases from DshHub."),
    icon = Icons.Default.Settings,
    onBack = onBack,
  ) {
    SkillsOverviewPanel(
      installedCount = skills.size,
      readyCount = readyCount,
      needsSetupCount = needsSetupCount,
      disabledCount = disabledCount,
      refreshing = skillsRefreshing,
      canRefresh = isConnected,
      onRefresh = viewModel::refreshSkills,
    )
    val installedTabLabel = nativeString("Installed")
    val browseTabLabel = nativeString("Browse")
    DshSegmentedControl(
      options = listOf(installedTabLabel, browseTabLabel),
      selected = if (selectedTab == SkillsTab.Installed) installedTabLabel else browseTabLabel,
      onSelect = { selected ->
        selectedTabName =
          if (selected == installedTabLabel) SkillsTab.Installed.name else SkillsTab.Browse.name
      },
      modifier = Modifier.fillMaxWidth(),
    )
    skillsErrorText?.let { errorText ->
      DshPanel {
        Text(text = errorText, style = DshTheme.type.body, color = DshTheme.colors.warning)
      }
    }
    if (isConnected && !operatorAdminScopeAvailable) {
      DshPanel {
        Text(
          text = nativeString("Skill changes require operator.admin. Reconnect with an admin-capable gateway token."),
          style = DshTheme.type.body,
          color = DshTheme.colors.warning,
        )
      }
    }
    when (selectedTab) {
      SkillsTab.Installed ->
        InstalledSkillsPane(
          skills = skills,
          visibleSkills = visibleSkills,
          query = installedSearch,
          filter = installedFilter,
          isConnected = isConnected,
          canManageSkills = canManageSkills,
          mutatingSkillKeys = skillMutationKeys,
          onQueryChange = { installedSearch = it },
          onFilterChange = { installedFilterName = it.name },
          onSkillClick = { selectedSkillKey = it.skillKey },
          onSkillEnabledChange = viewModel::setSkillEnabled,
        )
      SkillsTab.Browse ->
        DshHubSkillSearchPanel(
          state = dshHubState,
          installedSkills = skills,
          query = dshHubQuery,
          isConnected = isConnected,
          methodsAvailable = dshHubMethodsAvailable,
          canManageSkills = canManageSkills,
          onQueryChange = { dshHubQuery = it },
          onSearch = { viewModel.searchDshHubSkills(dshHubQuery) },
          onReviewInstall = viewModel::reviewDshHubSkillInstall,
          onAcknowledgeInstall = { slug, version ->
            viewModel.installDshHubSkill(slug, acknowledgeDshHubRisk = true, version = version)
          },
          onClearMessage = viewModel::clearDshHubSkillMessage,
        )
    }
  }
  dshHubState.installReview?.let { review ->
    DshHubInstallReviewDialog(
      review = review,
      canInstall = canManageSkills && dshHubMethodsAvailable && review.slug !in dshHubState.installingSlugs,
      onDismiss = viewModel::dismissDshHubSkillInstallReview,
      onInstall = {
        viewModel.dismissDshHubSkillInstallReview()
        viewModel.installDshHubSkill(review.slug, version = review.version)
      },
    )
  }
}

@Composable
private fun SkillDetailSettingsScreen(
  skill: GatewaySkillSummary?,
  skillKey: String,
  isConnected: Boolean,
  canManageSkills: Boolean,
  isMutating: Boolean,
  onSkillEnabledChange: (String, Boolean) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  SettingsDetailFrame(
    title = skill?.name ?: skillKey,
    subtitle = nativeString("Inspect and manage installed skill state."),
    icon = Icons.Default.Settings,
    onBack = onBack,
  ) {
    skill?.let { summary ->
      SettingsMetricPanel(
        rows =
          listOf(
            SettingsMetric(nativeString("Status"), skillStatusText(summary)),
            SettingsMetric(nativeString("Source"), skillSourceLabel(summary)),
            SettingsMetric(nativeString("Missing"), summary.missingCount.toString()),
          ),
      )
      SkillSwitchPanel(
        skill = summary,
        canManageSkills = canManageSkills,
        isMutating = isMutating,
        onSkillEnabledChange = onSkillEnabledChange,
      )
      SkillSetupPanel(summary)
    }
    SkillDetailPanel(skill = skill, isConnected = isConnected)
  }
}

@Composable
private fun SkillsOverviewPanel(
  installedCount: Int,
  readyCount: Int,
  needsSetupCount: Int,
  disabledCount: Int,
  refreshing: Boolean,
  canRefresh: Boolean,
  onRefresh: () -> Unit,
) {
  DshPanel(contentPadding = PaddingValues(14.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
          Text(text = installedCount.toString(), style = DshTheme.type.display, color = DshTheme.colors.text)
          Text(text = nativeString("Installed"), style = DshTheme.type.caption, color = DshTheme.colors.textMuted)
        }
        DshIconButton(
          icon = Icons.Default.Refresh,
          contentDescription = if (refreshing) nativeString("Refreshing") else nativeString("Refresh"),
          onClick = onRefresh,
          enabled = canRefresh && !refreshing,
        )
      }
      SkillDistributionBar(
        readyCount = readyCount,
        needsSetupCount = needsSetupCount,
        disabledCount = disabledCount,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SkillCountLegend(
          label = nativeString("Ready"),
          count = readyCount,
          color = DshTheme.colors.success,
          modifier = Modifier.weight(1f),
        )
        SkillCountLegend(
          label = nativeString("Needs Setup"),
          count = needsSetupCount,
          color = DshTheme.colors.warning,
          modifier = Modifier.weight(1f),
        )
        SkillCountLegend(
          label = nativeString("Off"),
          count = disabledCount,
          color = DshTheme.colors.textSubtle,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun SkillDistributionBar(
  readyCount: Int,
  needsSetupCount: Int,
  disabledCount: Int,
) {
  val total = readyCount + needsSetupCount + disabledCount
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(6.dp)
        .clip(RoundedCornerShape(DshTheme.radii.pill))
        .background(DshTheme.colors.surfacePressed),
  ) {
    if (total > 0) {
      if (readyCount > 0) {
        Box(modifier = Modifier.weight(readyCount.toFloat()).fillMaxHeight().background(DshTheme.colors.success))
      }
      if (needsSetupCount > 0) {
        Box(modifier = Modifier.weight(needsSetupCount.toFloat()).fillMaxHeight().background(DshTheme.colors.warning))
      }
      if (disabledCount > 0) {
        Box(modifier = Modifier.weight(disabledCount.toFloat()).fillMaxHeight().background(DshTheme.colors.textSubtle))
      }
    }
  }
}

@Composable
private fun SkillCountLegend(
  label: String,
  count: Int,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
      Text(text = label, style = DshTheme.type.caption, color = DshTheme.colors.textMuted, maxLines = 1)
    }
    Text(text = count.toString(), style = DshTheme.type.section, color = DshTheme.colors.text)
  }
}

@Composable
private fun InstalledSkillsPane(
  skills: List<GatewaySkillSummary>,
  visibleSkills: List<GatewaySkillSummary>,
  query: String,
  filter: InstalledSkillFilter,
  isConnected: Boolean,
  canManageSkills: Boolean,
  mutatingSkillKeys: Set<String>,
  onQueryChange: (String) -> Unit,
  onFilterChange: (InstalledSkillFilter) -> Unit,
  onSkillClick: (GatewaySkillSummary) -> Unit,
  onSkillEnabledChange: (String, Boolean) -> Unit,
) {
  DshPanel {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      DshTextField(value = query, onValueChange = onQueryChange, placeholder = nativeString("Search installed skills"))
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        InstalledSkillFilter.entries.forEach { option ->
          DshPill(
            text = installedSkillFilterLabel(option),
            selected = option == filter,
            onClick = { onFilterChange(option) },
          )
        }
      }
    }
  }
  when {
    !isConnected ->
      DshPanel {
        Text(text = nativeString("Connect the gateway to load skills."), style = DshTheme.type.body, color = DshTheme.colors.textMuted)
      }
    skills.isEmpty() ->
      DshPanel {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(text = nativeString("No skills installed."), style = DshTheme.type.section, color = DshTheme.colors.text)
          Text(text = nativeString("Skills installed on the gateway will appear here."), style = DshTheme.type.body, color = DshTheme.colors.textMuted)
        }
      }
    visibleSkills.isEmpty() ->
      DshPanel {
        Text(text = nativeString("No installed skills match this search."), style = DshTheme.type.body, color = DshTheme.colors.textMuted)
      }
    else ->
      SkillsPanel(
        skills = visibleSkills,
        canManageSkills = canManageSkills,
        mutatingSkillKeys = mutatingSkillKeys,
        onSkillClick = onSkillClick,
        onSkillEnabledChange = onSkillEnabledChange,
      )
  }
}

@Composable
private fun SkillSwitchPanel(
  skill: GatewaySkillSummary,
  canManageSkills: Boolean,
  isMutating: Boolean,
  onSkillEnabledChange: (String, Boolean) -> Unit,
) {
  DshPanel {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = nativeString("Gateway switch"), style = DshTheme.type.section, color = DshTheme.colors.text)
        Text(
          text = if (skill.disabled) nativeString("Disabled for all agents.") else nativeString("Enabled for eligible agents."),
          style = DshTheme.type.body,
          color = DshTheme.colors.textMuted,
        )
      }
      Switch(
        checked = !skill.disabled,
        onCheckedChange = { onSkillEnabledChange(skill.skillKey, it) },
        enabled = canManageSkills && !isMutating,
      )
    }
  }
}

@Composable
private fun SkillSetupPanel(skill: GatewaySkillSummary) {
  DshPanel {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(text = nativeString("Setup"), style = DshTheme.type.section, color = DshTheme.colors.text)
      Text(text = skillConfigurationText(skill), style = DshTheme.type.body, color = DshTheme.colors.textMuted)
    }
  }
}

@Composable
private fun SkillDetailPanel(
  skill: GatewaySkillSummary?,
  isConnected: Boolean,
) {
  if (!isConnected) {
    DshPanel {
      Text(text = nativeString("Connect the gateway to load skill details."), style = DshTheme.type.body, color = DshTheme.colors.textMuted)
    }
    return
  }
  if (skill == null) {
    DshPanel {
      Text(text = nativeString("Skill detail is not available in the current skills status."), style = DshTheme.type.body, color = DshTheme.colors.textMuted)
    }
    return
  }
  SettingsMetricPanel(
    rows =
      listOf(
        SettingsMetric(nativeString("Skill Key"), skill.skillKey),
        SettingsMetric(nativeString("Display"), skill.name),
        SettingsMetric(nativeString("Source"), skillSourceLabel(skill)),
        SettingsMetric(nativeString("Install Options"), skill.installCount.toString()),
      ),
  )
  skill.description?.let { description ->
    DshPanel {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = nativeString("Description"), style = DshTheme.type.section, color = DshTheme.colors.text)
        Text(text = description, style = DshTheme.type.body, color = DshTheme.colors.textMuted)
      }
    }
  }
}

@Composable
private fun SkillsPanel(
  skills: List<GatewaySkillSummary>,
  canManageSkills: Boolean,
  mutatingSkillKeys: Set<String>,
  onSkillClick: (GatewaySkillSummary) -> Unit,
  onSkillEnabledChange: (String, Boolean) -> Unit,
) {
  DshListPanel(items = skills) { skill ->
    SkillListRow(
      skill = skill,
      canManageSkills = canManageSkills,
      isMutating = skill.skillKey in mutatingSkillKeys,
      onClick = { onSkillClick(skill) },
      onSkillEnabledChange = onSkillEnabledChange,
    )
  }
}

@Composable
private fun SkillListRow(
  skill: GatewaySkillSummary,
  canManageSkills: Boolean,
  isMutating: Boolean,
  onClick: () -> Unit,
  onSkillEnabledChange: (String, Boolean) -> Unit,
) {
  DshDetailRow(
    title = skill.name,
    subtitle = skillSubtitle(skill),
    modifier = Modifier.clickable(onClickLabel = nativeString("Open skill detail"), onClick = onClick),
    leading = { DshTextBadge(text = skillBadge(skill)) },
    trailing = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DshStatusPill(text = skillStatusText(skill), status = skillStatus(skill))
        Switch(
          checked = !skill.disabled,
          onCheckedChange = { onSkillEnabledChange(skill.skillKey, it) },
          enabled = canManageSkills && !isMutating,
        )
      }
    },
  )
}

@Composable
private fun DshHubSkillSearchPanel(
  state: GatewayDshHubSkillSearchState,
  installedSkills: List<GatewaySkillSummary>,
  query: String,
  isConnected: Boolean,
  methodsAvailable: Boolean,
  canManageSkills: Boolean,
  onQueryChange: (String) -> Unit,
  onSearch: () -> Unit,
  onReviewInstall: (GatewayDshHubSkillSummary) -> Unit,
  onAcknowledgeInstall: (String, String?) -> Unit,
  onClearMessage: () -> Unit,
) {
  DshPanel {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(text = nativeString("Find on DshHub"), style = DshTheme.type.section, color = DshTheme.colors.text)
      Text(
        text = nativeString("Search registry metadata. The Gateway verifies trust again before any download."),
        style = DshTheme.type.body,
        color = DshTheme.colors.textMuted,
      )
      if (isConnected && !methodsAvailable) {
        Text(
          text = nativeString(DSHHUB_SKILL_GATEWAY_UNAVAILABLE),
          style = DshTheme.type.body,
          color = DshTheme.colors.warning,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DshTextField(
          value = query,
          onValueChange = onQueryChange,
          placeholder = nativeString("Search DshHub"),
          modifier = Modifier.weight(1f),
        )
        DshIconButton(
          icon = Icons.Default.Search,
          contentDescription = if (state.searching) nativeString("Searching") else nativeString("Search"),
          onClick = onSearch,
          enabled = isConnected && methodsAvailable && !state.searching,
        )
      }
    }
  }
  if (state.errorText != null || state.messageText != null) {
    DshHubNoticeCard(
      errorText = state.errorText,
      messageText = state.messageText,
      acknowledgeSlug = state.acknowledgeSlug,
      acknowledgeVersion = state.acknowledgeVersion,
      canAcknowledge = methodsAvailable && canManageSkills,
      installingSlugs = state.installingSlugs,
      onAcknowledgeInstall = onAcknowledgeInstall,
      onDismiss = onClearMessage,
    )
  }
  if (state.results.isNotEmpty()) {
    DshListPanel(items = state.results) { skill ->
      val installed =
        skill.version?.let { version -> isDshHubSkillInstalled(installedSkills, skill.reference, version) }
          ?: isDshHubSkillInstalled(installedSkills, skill.reference)
      val subtitleParts =
        listOfNotNull(
          skill.summary,
          skill.reference,
          skill.version?.let { nativeString("Version \$it", it) },
        )
      DshDetailRow(
        title = skill.displayName,
        subtitle = subtitleParts.joinToString(" · "),
        leading = { DshTextBadge(text = skillBadge(skill.displayName)) },
        trailing = {
          val reviewing = state.reviewingSlug == skill.reference
          val installing = isDshHubSkillOperationActive(state.installingSlugs, skill.reference)
          DshSecondaryButton(
            text =
              when {
                installed -> nativeString("Installed")
                installing -> nativeString("Installing")
                reviewing -> nativeString("Loading")
                else -> nativeString("Review")
              },
            onClick = { onReviewInstall(skill) },
            enabled = isConnected && methodsAvailable && !installed && !reviewing && !installing,
          )
        },
      )
    }
  }
}

@Composable
private fun DshHubNoticeCard(
  errorText: String?,
  messageText: String?,
  acknowledgeSlug: String?,
  acknowledgeVersion: String?,
  canAcknowledge: Boolean,
  installingSlugs: Set<String>,
  onAcknowledgeInstall: (String, String?) -> Unit,
  onDismiss: () -> Unit,
) {
  val requiresAcknowledgement = acknowledgeSlug != null
  val status =
    when {
      requiresAcknowledgement -> DshStatus.Warning
      errorText != null -> DshStatus.Danger
      else -> DshStatus.Success
    }
  val rawText = errorText ?: messageText.orEmpty()
  val summary =
    if (requiresAcknowledgement) {
      nativeString("The Gateway will verify this exact release with DshHub before download. If the release needs explicit risk acknowledgement, Android will show the Gateway warning before retrying.")
    } else {
      rawText.substringBefore("\n\n").trim()
    }
  val details =
    when {
      requiresAcknowledgement -> rawText.takeIf(String::isNotBlank)
      "\n\n" in rawText -> rawText.substringAfter("\n\n").trim().takeIf(String::isNotBlank)
      else -> null
    }
  var detailsExpanded by rememberSaveable(rawText) { mutableStateOf(false) }
  val accent =
    when (status) {
      DshStatus.Success -> DshTheme.colors.success
      DshStatus.Warning -> DshTheme.colors.warning
      DshStatus.Danger -> DshTheme.colors.danger
      DshStatus.Neutral -> DshTheme.colors.textSubtle
    }
  val background =
    when (status) {
      DshStatus.Success -> DshTheme.colors.successSoft
      DshStatus.Warning -> DshTheme.colors.warningSoft
      DshStatus.Danger -> DshTheme.colors.dangerSoft
      DshStatus.Neutral -> DshTheme.colors.surfaceRaised
    }
  val title =
    when {
      requiresAcknowledgement -> nativeString("Needs attention")
      errorText != null -> nativeString("Blocked")
      else -> nativeString("Installed")
    }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(DshTheme.radii.panel),
    color = background,
    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
        Text(text = title, style = DshTheme.type.section, color = DshTheme.colors.text, modifier = Modifier.weight(1f))
      }
      Text(text = summary, style = DshTheme.type.body, color = DshTheme.colors.textMuted)
      if (detailsExpanded && details != null) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(DshTheme.radii.control),
          color = DshTheme.colors.surface.copy(alpha = 0.72f),
        ) {
          Text(
            text = details,
            modifier = Modifier.padding(10.dp),
            style = DshTheme.type.mono,
            color = DshTheme.colors.textMuted,
          )
        }
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (details != null && !detailsExpanded) {
          DshSecondaryButton(
            text = nativeString("Review"),
            onClick = { detailsExpanded = true },
            modifier = Modifier.weight(1f),
          )
        }
        DshSecondaryButton(
          text = nativeString("Dismiss"),
          onClick = onDismiss,
          modifier = Modifier.weight(1f),
        )
      }
      acknowledgeSlug?.let { slug ->
        DshPrimaryButton(
          text = nativeString("Acknowledge Gateway warning and install"),
          onClick = { onAcknowledgeInstall(slug, acknowledgeVersion) },
          enabled = canAcknowledge && slug !in installingSlugs && (details == null || detailsExpanded),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun DshHubInstallReviewDialog(
  review: GatewayDshHubInstallReview,
  canInstall: Boolean,
  onDismiss: () -> Unit,
  onInstall: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = nativeString("Review DshHub skill")) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = review.displayName, style = DshTheme.type.section, color = DshTheme.colors.text)
        review.summary?.let {
          Text(text = it, style = DshTheme.type.body, color = DshTheme.colors.textMuted)
        }
        ReviewLine(label = nativeString("Version"), value = review.version)
        ReviewLine(label = nativeString("Publisher"), value = review.author)
        Text(
          text = nativeString("The Gateway will verify this exact release with DshHub before download. If the release needs explicit risk acknowledgement, Android will show the Gateway warning before retrying."),
          style = DshTheme.type.body,
          color = DshTheme.colors.textMuted,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onInstall, enabled = canInstall) {
        Text(text = nativeString("Verify and install"))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = nativeString("Cancel"))
      }
    },
  )
}

@Composable
private fun ReviewLine(
  label: String,
  value: String,
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(text = label, style = DshTheme.type.caption, color = DshTheme.colors.textMuted)
    Text(text = value, style = DshTheme.type.body, color = DshTheme.colors.text)
  }
}

private fun filterInstalledSkills(
  skills: List<GatewaySkillSummary>,
  query: String,
  filter: InstalledSkillFilter,
): List<GatewaySkillSummary> {
  val normalizedQuery = query.trim()
  return skills.filter { skill ->
    val matchesQuery =
      normalizedQuery.isEmpty() ||
        skill.name.contains(normalizedQuery, ignoreCase = true) ||
        skill.skillKey.contains(normalizedQuery, ignoreCase = true) ||
        skill.description?.contains(normalizedQuery, ignoreCase = true) == true
    val matchesFilter =
      when (filter) {
        InstalledSkillFilter.All -> true
        InstalledSkillFilter.Ready -> skillReady(skill)
        InstalledSkillFilter.Setup -> skillNeedsSetup(skill)
        InstalledSkillFilter.Off -> skill.disabled
      }
    matchesQuery && matchesFilter
  }
}

private fun installedSkillFilterLabel(filter: InstalledSkillFilter): String =
  when (filter) {
    InstalledSkillFilter.All -> nativeString("All")
    InstalledSkillFilter.Ready -> nativeString("Ready")
    InstalledSkillFilter.Setup -> nativeString("Needs Setup")
    InstalledSkillFilter.Off -> nativeString("Off")
  }

private fun skillReady(skill: GatewaySkillSummary): Boolean =
  !skill.disabled &&
    skill.eligible &&
    !skill.blockedByAllowlist &&
    !skill.blockedByAgentFilter &&
    skill.missingCount == 0

private fun skillNeedsSetup(skill: GatewaySkillSummary): Boolean =
  !skill.disabled &&
    (skill.blockedByAllowlist || skill.blockedByAgentFilter || !skill.eligible || skill.missingCount > 0)

private fun skillStatusText(skill: GatewaySkillSummary): String =
  when {
    skill.disabled -> nativeString("Off")
    skillNeedsSetup(skill) -> nativeString("Setup")
    else -> nativeString("Ready")
  }

private fun skillStatus(skill: GatewaySkillSummary): DshStatus =
  when {
    skill.disabled -> DshStatus.Neutral
    skillNeedsSetup(skill) -> DshStatus.Warning
    else -> DshStatus.Success
  }

private fun skillSubtitle(skill: GatewaySkillSummary): String {
  val issue =
    when {
      skill.disabled -> nativeString("Disabled")
      skill.blockedByAllowlist -> nativeString("Blocked")
      skill.blockedByAgentFilter -> nativeString("Not available to this agent")
      skill.missingCount > 0 -> skillMissingItemsText(skill.missingCount)
      !skill.eligible -> nativeString("Needs setup")
      else -> null
    }
  return listOfNotNull(skill.description, skillSourceLabel(skill), issue).joinToString(" · ")
}

private fun skillConfigurationText(skill: GatewaySkillSummary): String =
  when {
    skill.disabled -> nativeString("This skill is disabled on the gateway. Enable it here when the current connection has operator.admin.")
    skill.blockedByAllowlist -> nativeString("This skill is blocked by the gateway allowlist. Allowlist changes stay on desktop or CLI.")
    skill.blockedByAgentFilter -> nativeString("This skill is installed but not available to the current agent. Agent filters stay on desktop or CLI.")
    skill.missingCount > 0 -> skillMissingConfigurationText(skill.missingCount)
    !skill.eligible -> nativeString("This skill is installed but not currently eligible to run. Use desktop or CLI for configuration changes.")
    else -> nativeString("Ready on this gateway. Android can enable or disable it globally; setup and configuration stay on desktop or CLI.")
  }

internal fun skillMissingItemsText(count: Int): String =
  when (count) {
    0 -> nativeString("No missing items")
    1 -> nativeString("1 missing item")
    else -> nativeString("\$count missing items", count)
  }

internal fun skillMissingConfigurationText(count: Int): String =
  when (count) {
    1 -> nativeString("This skill needs 1 setup item. Android shows what is installed; setup/config changes stay on desktop or CLI.")
    else -> nativeString("This skill needs \$count setup items. Android shows what is installed; setup/config changes stay on desktop or CLI.", count)
  }

private fun skillSourceLabel(skill: GatewaySkillSummary): String =
  when (skill.source) {
    "dsh-bundled" -> if (skill.bundled) nativeString("Built-in") else nativeString("Bundled")
    "dsh-managed" -> nativeString("Installed")
    "dsh-workspace" -> nativeString("Workspace")
    "dsh-extra" -> nativeString("Extra")
    else -> nativeString("Skill")
  }

private fun skillBadge(skill: GatewaySkillSummary): String {
  skill.emoji?.let { return it }
  return skillBadge(skill.name)
}

private fun skillBadge(name: String): String =
  name
    .split(' ', '-', '_')
    .filter { it.isNotBlank() }
    .take(2)
    .mapNotNull { it.uppercaseFirstGraphemeOrNull() }
    .joinToString("")
    .ifBlank { "S" }
