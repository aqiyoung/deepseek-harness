package ai.deepseek.harness.ui

import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.dsh.DshSessionManager
import ai.deepseek.harness.ui.design.DshScaffold
import ai.deepseek.harness.ui.design.DshTheme
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Simple session list screen.
 */
@Composable
fun SessionsScreen(
  viewModel: MainViewModel,
) {
  val dsh = viewModel.dsh
  val sessions by dsh.sessions.collectAsState()
  val activeSessionId by viewModel.activeDshSessionId.collectAsState()
  val scope = rememberCoroutineScope()

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(DshTheme.spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Sessions",
          style = DshTheme.type.section,
          color = DshTheme.colors.text,
        )
        IconButton(onClick = { scope.launch { dsh.loadSessions() } }) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = DshTheme.colors.textMuted)
        }
      }

      // Session list
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items(sessions, key = { it.sessionId }) { session ->
          SessionRow(
            session = session,
            isActive = session.sessionId == activeSessionId,
            onClick = { viewModel.loadSessionHistory(session.sessionId) },
          )
        }
      }
    }

    // New session FAB
    FloatingActionButton(
      onClick = { viewModel.createSession() },
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(16.dp),
      containerColor = DshTheme.colors.primary,
    ) {
      Icon(Icons.Default.Add, contentDescription = "New session", tint = DshTheme.colors.primaryText)
    }
  }
}

@Composable
private fun SessionRow(
  session: DshSessionManager.SessionInfo,
  isActive: Boolean,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = session.title,
        style = DshTheme.type.body,
        color = if (isActive) DshTheme.colors.primary else DshTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      if (session.running) {
        Text(
          text = "●",
          color = DshTheme.colors.success,
          style = DshTheme.type.caption,
        )
      }
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = session.sessionId.take(16) + "...",
      style = DshTheme.type.caption,
      color = DshTheme.colors.textMuted,
    )
  }
}