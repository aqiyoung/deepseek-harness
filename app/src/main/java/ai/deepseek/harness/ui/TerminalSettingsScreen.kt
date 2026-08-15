package ai.deepseek.harness.ui

import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.i18n.nativeString
import ai.deepseek.harness.ui.design.DshPlainIconButton
import ai.deepseek.harness.ui.design.DshScaffold
import ai.deepseek.harness.ui.design.DshTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Full-height terminal surface: embeds the gateway-served terminal-only
 * Control UI document (`/?view=terminal`, the same ghostty-web surface the
 * desktop Control UI uses) for the currently connected gateway.
 */
@Composable
internal fun TerminalSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val isConnected by viewModel.isConnected.collectAsState()
  val controlPage by viewModel.gatewayControlPage.collectAsState()
  DshScaffold(
    contentPadding = PaddingValues(start = DshTheme.spacing.lg, top = 14.dp, end = DshTheme.spacing.lg, bottom = 6.dp),
  ) {
    Column(modifier = Modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        DshPlainIconButton(
          icon = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = nativeString("Back"),
          onClick = onBack,
        )
        Text(text = nativeString("Terminal"), style = DshTheme.type.title, color = DshTheme.colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(imageVector = Icons.Outlined.Terminal, contentDescription = null, tint = DshTheme.colors.textMuted)
      }
      Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        val page = controlPage
        if (isConnected && page != null) {
          // GatewayControlPage equality includes the accepted TLS pin, so trust changes
          // recreate the WebView while unrelated recompositions preserve live shells.
          key(page) {
            ControlUiWebView(
              page = page,
              url = "${page.baseUrl}/?view=terminal",
              modifier = Modifier.fillMaxSize(),
            )
          }
        } else {
          Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = nativeString("Terminal needs a connected gateway"), style = DshTheme.type.section, color = DshTheme.colors.text)
            Text(text = nativeString("Connect to your gateway to open a shell in the agent workspace."), style = DshTheme.type.body, color = DshTheme.colors.textMuted)
          }
        }
      }
    }
  }
}
