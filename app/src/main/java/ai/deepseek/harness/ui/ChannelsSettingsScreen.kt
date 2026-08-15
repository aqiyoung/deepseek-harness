package ai.deepseek.harness.ui

import ai.deepseek.harness.GatewayChannelSummary
import ai.deepseek.harness.GatewayChannelsSummary
import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.i18n.nativeString
import ai.deepseek.harness.ui.design.DshDetailRow
import ai.deepseek.harness.ui.design.DshListPanel
import ai.deepseek.harness.ui.design.DshPanel
import ai.deepseek.harness.ui.design.DshSecondaryButton
import ai.deepseek.harness.ui.design.DshStatus
import ai.deepseek.harness.ui.design.DshStatusPill
import ai.deepseek.harness.ui.design.DshTextBadge
import ai.deepseek.harness.ui.design.DshTheme
import ai.deepseek.harness.uppercaseFirstGraphemeOrNull
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Settings screen for gateway channel readiness and account status. */
@Composable
internal fun ChannelsSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val summary by viewModel.channelsSummary.collectAsState()
  val refreshing by viewModel.channelsRefreshing.collectAsState()
  val errorText by viewModel.channelsErrorText.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val channels = summary.channels

  LaunchedEffect(isConnected) {
    if (isConnected) {
      viewModel.refreshChannels()
    }
  }

  SettingsDetailFrame(
    title = nativeString("Channels"),
    subtitle = nativeString("Messaging surfaces connected to this gateway."),
    icon = Icons.Default.Notifications,
    onBack = onBack,
  ) {
    SettingsMetricPanel(
      rows =
        listOf(
          SettingsMetric(nativeString("Channels"), channels.size.toString()),
          SettingsMetric(nativeString("Connected"), channels.count { it.connected }.toString()),
          SettingsMetric(nativeString("Configured"), channels.count { it.configured }.toString()),
          SettingsMetric(nativeString("Issues"), channels.count { it.error != null }.toString()),
        ),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      DshSecondaryButton(
        text = if (refreshing) nativeString("Refreshing") else nativeString("Refresh"),
        onClick = viewModel::refreshChannels,
        enabled = isConnected && !refreshing,
        modifier = Modifier.weight(1f),
      )
    }
    errorText?.let { error ->
      DshPanel {
        Text(text = error, style = DshTheme.type.body, color = DshTheme.colors.warning)
      }
    }
    if (summary.partial || summary.warnings.isNotEmpty()) {
      // Partial channel scans still include useful rows; surface the warning
      // without hiding successful channel status.
      DshPanel {
        Text(text = channelsWarningText(summary), style = DshTheme.type.body, color = DshTheme.colors.textMuted)
      }
    }
    when {
      !isConnected ->
        DshPanel {
          Text(text = nativeString("Connect the gateway to load channels."), style = DshTheme.type.body, color = DshTheme.colors.textMuted)
        }
      channels.isEmpty() ->
        DshPanel {
          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = nativeString("No channels found."), style = DshTheme.type.section, color = DshTheme.colors.text)
            Text(text = nativeString("Telegram, WhatsApp, email, and other channels appear here after setup."), style = DshTheme.type.body, color = DshTheme.colors.textMuted)
          }
        }
      else -> ChannelsPanel(channels = channels)
    }
  }
}

@Composable
private fun ChannelsPanel(channels: List<GatewayChannelSummary>) {
  DshListPanel(items = channels) { channel ->
    ChannelRow(channel = channel)
  }
}

@Composable
private fun ChannelRow(channel: GatewayChannelSummary) {
  DshDetailRow(
    title = channel.label,
    subtitle = channelSubtitle(channel),
    leading = { DshTextBadge(text = channelBadge(channel.label)) },
    trailing = { DshStatusPill(text = channelStatusText(channel), status = channelStatus(channel)) },
  )
}

private fun channelSubtitle(channel: GatewayChannelSummary): String {
  val accounts =
    when (channel.accountCount) {
      0 -> null
      1 -> nativeString("1 account")
      else -> nativeString("\${channel.accountCount} accounts", channel.accountCount)
    }
  val lifecycle =
    when {
      channel.connected -> nativeString("Connected")
      channel.running -> nativeString("Running")
      channel.linked -> nativeString("Linked")
      channel.configured -> nativeString("Configured")
      channel.enabled -> nativeString("Enabled")
      else -> nativeString("Off")
    }
  return listOfNotNull(accounts, lifecycle, channel.error).joinToString(" · ")
}

private fun channelStatusText(channel: GatewayChannelSummary): String =
  when {
    channel.error != null -> nativeString("Issue")
    channel.connected -> nativeString("Connected")
    channel.running -> nativeString("Running")
    channel.linked || channel.configured -> nativeString("Ready")
    channel.enabled -> nativeString("Setup")
    else -> nativeString("Off")
  }

private fun channelStatus(channel: GatewayChannelSummary): DshStatus =
  when {
    channel.error != null -> DshStatus.Danger
    channel.connected || channel.running -> DshStatus.Success
    channel.linked || channel.configured -> DshStatus.Neutral
    channel.enabled -> DshStatus.Warning
    else -> DshStatus.Neutral
  }

private fun channelBadge(label: String): String =
  label
    .split(' ', '-', '_')
    .filter { it.isNotBlank() }
    .take(2)
    .mapNotNull { it.uppercaseFirstGraphemeOrNull() }
    .joinToString("")
    .ifBlank { "C" }

/** Chooses the first gateway warning or a generic partial-scan message. */
private fun channelsWarningText(summary: GatewayChannelsSummary): String = summary.warnings.firstOrNull()?.takeIf { it.isNotBlank() } ?: nativeString("Some channel status checks did not complete.")
