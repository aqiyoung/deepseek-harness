package ai.deepseek.harness.ui

import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.dsh.DshSessionManager
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Session list screen with model picker for new sessions.
 */
@Composable
fun SessionsScreen(
  viewModel: MainViewModel,
) {
  val dsh = viewModel.dsh
  val sessions by dsh.sessions.collectAsState()
  val activeSessionId by viewModel.activeDshSessionId.collectAsState()
  val modelGroups by dsh.modelGroups.collectAsState()
  val presets by dsh.presets.collectAsState()

  var showModelPicker by remember { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
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
        IconButton(onClick = { viewModel.dsh.loadSessions() }) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = DshTheme.colors.textMuted)
        }
      }

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

    FloatingActionButton(
      onClick = { showModelPicker = true },
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(16.dp),
      containerColor = DshTheme.colors.primary,
    ) {
      Icon(Icons.Default.Add, contentDescription = "New session", tint = DshTheme.colors.primaryText)
    }
  }

  if (showModelPicker) {
    ModelPickerDialog(
      modelGroups = modelGroups,
      presets = presets,
      onDismiss = { showModelPicker = false },
      onConfirm = { selectedModel, selectedPreset ->
        showModelPicker = false
        viewModel.createSession(agentPreset = selectedPreset, model = selectedModel)
      },
    )
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (session.agentPreset != null) {
        Text(
          text = session.agentPreset,
          style = DshTheme.type.caption,
          color = DshTheme.colors.textSubtle,
        )
      }
      Text(
        text = session.sessionId.take(16),
        style = DshTheme.type.caption,
        color = DshTheme.colors.textSubtle,
      )
    }
  }
}

@Composable
private fun ModelPickerDialog(
  modelGroups: List<DshSessionManager.ModelGroup>,
  presets: List<DshSessionManager.PresetInfo>,
  onDismiss: () -> Unit,
  onConfirm: (selectedModel: String?, selectedPreset: String?) -> Unit,
) {
  var selectedGroup by remember { mutableStateOf(modelGroups.firstOrNull()?.id ?: "") }
  var selectedModel by remember { mutableStateOf<String?>(null) }
  var selectedPreset by remember { mutableStateOf(presets.firstOrNull { it.isDefault }?.id ?: presets.firstOrNull()?.id) }

  val allModels = modelGroups.flatMap { g -> g.models.map { g.name + "/" + it.name } }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Session", style = DshTheme.type.title) },
    confirmButton = {
      Button(
        onClick = { onConfirm(selectedModel, selectedPreset) },
        enabled = selectedModel != null || selectedPreset != null,
      ) {
        Text("Create", style = DshTheme.type.body)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", style = DshTheme.type.body)
      }
    },
    text = {
      Column(
        modifier = Modifier
          .verticalScroll(rememberScrollState())
          .height(400.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Presets
        if (presets.isNotEmpty()) {
          Text("Preset", style = DshTheme.type.caption, color = DshTheme.colors.textMuted)
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.forEach { p ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable(onClick = {
                    selectedPreset = if (selectedPreset == p.id) null else p.id
                    selectedModel = null
                  })
                  .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (selectedPreset == p.id) {
                  Icon(Icons.Default.Check, contentDescription = null, tint = DshTheme.colors.primary, modifier = Modifier.width(20.dp))
                }
                Text(p.name, style = DshTheme.type.body, color = DshTheme.colors.text, modifier = Modifier.weight(1f))
                if (p.isDefault) {
                  Text("Default", style = DshTheme.type.caption, color = DshTheme.colors.textSubtle)
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Models
        if (modelGroups.isNotEmpty()) {
          Text("Model", style = DshTheme.type.caption, color = DshTheme.colors.textMuted)

          modelGroups.forEach { group ->
            Text(group.name, style = DshTheme.type.caption, color = DshTheme.colors.primary)
            group.models.forEach { model ->
              val label = model.name
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable(onClick = {
                    selectedModel = if (selectedModel == label) null else label
                    selectedPreset = null
                  })
                  .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (selectedModel == label) {
                  Icon(Icons.Default.Check, contentDescription = null, tint = DshTheme.colors.primary, modifier = Modifier.width(20.dp))
                }
                Text(label, style = DshTheme.type.body, color = DshTheme.colors.text, modifier = Modifier.weight(1f))
              }
            }
          }
        }

        if (allModels.isEmpty() && presets.isEmpty()) {
          Text("No models available yet", style = DshTheme.type.body, color = DshTheme.colors.textMuted)
        }
      }
    },
  )
}