package ai.deepseek.harness.ui.chat

import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.chat.ChatMessage
import ai.deepseek.harness.chat.ChatMessageContent
import ai.deepseek.harness.dsh.DshConnectionState
import ai.deepseek.harness.dsh.DshSessionManager
import ai.deepseek.harness.ui.design.DshTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Chat screen with model switcher and real-time trajectory.
 */
@Composable
fun ChatScreen(
  viewModel: MainViewModel,
) {
  val dsh = viewModel.dsh
  val connectionState by dsh.connectionState.collectAsState()
  val sessions by dsh.sessions.collectAsState()
  val activeSessionId by viewModel.activeDshSessionId.collectAsState()
  val chatMessages by viewModel.chatMessages.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val trajectory by viewModel.trajectory.collectAsState()
  val modelGroups by dsh.modelGroups.collectAsState()
  val preferredModel by viewModel.preferredModel.collectAsState()
  val hostInfo by dsh.hostInfo.collectAsState()
  val activeSession = sessions.find { it.sessionId == activeSessionId }

  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  var showModelPicker by remember { mutableStateOf(false) }
  var showTrajectory by remember { mutableStateOf(false) }
  var selectedModel by remember { mutableStateOf(preferredModel) }

  LaunchedEffect(preferredModel) { selectedModel = preferredModel }

  // Auto-scroll to bottom
  LaunchedEffect(chatMessages.size) {
    if (chatMessages.isNotEmpty()) {
      listState.animateScrollToItem(chatMessages.size - 1)
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // Session selector
    if (sessions.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        sessions.take(10).forEach { session ->
          val isSelected = session.sessionId == activeSessionId
          Surface(
            onClick = { viewModel.loadSessionHistory(session.sessionId) },
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) DshTheme.colors.primary.copy(alpha = 0.15f)
            else DshTheme.colors.surfaceRaised,
            modifier = Modifier.height(30.dp),
          ) {
            Text(
              text = session.title.take(12),
              style = DshTheme.type.caption,
              color = if (isSelected) DshTheme.colors.primary else DshTheme.colors.textMuted,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }
      }
    }

    // Messages
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (sessions.isEmpty() && !isConnected) {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Connecting to server...",
              style = DshTheme.type.body,
              color = DshTheme.colors.textMuted,
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      if (sessions.isEmpty() && isConnected) {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Loading sessions...",
              style = DshTheme.type.body,
              color = DshTheme.colors.textMuted,
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      if (sessions.isNotEmpty() && activeSessionId == null && chatMessages.isEmpty()) {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Select or create a session to start chatting",
              style = DshTheme.type.body,
              color = DshTheme.colors.textMuted,
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      items(chatMessages, key = { it.id }) { message ->
        ChatBubble(
          text = message.content.joinToString("") { it.text ?: "" },
          isUser = message.role == "user",
        )
      }

      if (activeSession?.running == true) {
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              color = DshTheme.colors.primary,
              strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Agent is working...",
              style = DshTheme.type.caption,
              color = DshTheme.colors.textMuted,
            )
          }
        }
      }
    }

    // Trajectory toggle + summary
    if (trajectory.isNotEmpty() || activeSession?.running == true) {
      TrajectoryBar(
        trajectory = trajectory,
        expanded = showTrajectory,
        onToggle = { showTrajectory = !showTrajectory },
      )
    }

    // Model switcher + input
    ModelInputRow(
      inputText = inputText,
      onInputTextChanged = { inputText = it },
      selectedModel = selectedModel,
      hostModel = hostInfo?.model ?: activeSession?.agentPreset ?: "",
      onModelSelect = { showModelPicker = true },
      onSend = {
        if (inputText.isNotBlank() && activeSessionId != null) {
          viewModel.sendPrompt(activeSessionId!!, inputText.trim(), model = selectedModel.ifBlank { null })
          inputText = ""
        }
      },
      enabled = inputText.isNotBlank() && isConnected && activeSessionId != null,
    )
  }

  if (showModelPicker) {
    ModelPickerDialog(
      modelGroups = modelGroups,
      currentModel = selectedModel,
      onDismiss = { showModelPicker = false },
      onConfirm = { model ->
        selectedModel = model ?: ""
        viewModel.setPreferredModel(model?.ifBlank { null })
        showModelPicker = false
      },
    )
  }
}

@Composable
private fun ChatBubble(
  text: String,
  isUser: Boolean,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp,
      ),
      color = if (isUser) DshTheme.colors.primary else DshTheme.colors.surfaceRaised,
      modifier = Modifier.widthIn(max = 300.dp),
    ) {
      Text(
        text = text,
        style = DshTheme.type.body,
        color = if (isUser) DshTheme.colors.primaryText else DshTheme.colors.text,
        modifier = Modifier.padding(12.dp),
        maxLines = 50,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun TrajectoryBar(
  trajectory: List<DshSessionManager.TrajectoryStep>,
  expanded: Boolean,
  onToggle: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 4.dp),
  ) {
    // Header row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onToggle)
        .background(DshTheme.colors.surfaceRaised, RoundedCornerShape(8.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⏱", style = DshTheme.type.caption)
        Text("轨迹", style = DshTheme.type.caption, color = DshTheme.colors.textMuted)
        val userSteps = trajectory.filter { it.type == "user" }
        val toolSteps = trajectory.filter { it.type == "tool" }
        val modelSteps = trajectory.filter { it.type == "model" }
        if (modelSteps.isNotEmpty()) Text("LLM: ${modelSteps.size}", style = DshTheme.type.caption, color = DshTheme.colors.primary)
        if (toolSteps.isNotEmpty()) Text("Tools: ${toolSteps.size}", style = DshTheme.type.caption, color = DshTheme.colors.primary)
      }
      Icon(
        Icons.Default.KeyboardArrowDown,
        contentDescription = null,
        tint = DshTheme.colors.textMuted,
        modifier = Modifier.size(16.dp),
      )
    }

    if (expanded) {
      val recent = trajectory.takeLast(20)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .heightIn(max = 200.dp)
          .background(Color(0xFFF8F8F8), RoundedCornerShape(8.dp))
          .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        recent.forEach { step ->
          TrajectoryStepRow(step = step)
        }
      }
    }
  }
}

@Composable
private fun TrajectoryStepRow(step: DshSessionManager.TrajectoryStep) {
  val bgColor = when (step.type) {
    "user" -> Color(0xFFE3F2FD)
    "assistant" -> Color(0xFFF5F5F5)
    "model" -> Color(0xFFFFF3E0)
    "tool" -> Color(0xFFE8F5E9)
    else -> Color.Transparent
  }
  val textColor = when (step.type) {
    "user" -> Color(0xFF1565C0)
    "model" -> Color(0xFFE65100)
    "tool" -> Color(0xFF2E7D32)
    else -> DshTheme.colors.textMuted
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(bgColor, RoundedCornerShape(4.dp))
      .padding(horizontal = 8.dp, vertical = 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "[${step.type.uppercase()}]",
      style = DshTheme.type.caption.copy(fontSize = 9.sp),
      color = textColor,
      maxLines = 1,
    )
    step.text?.let {
      Text(
        text = it.take(60),
        style = DshTheme.type.caption.copy(fontSize = 10.sp),
        color = DshTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
      )
    }
    step.status?.let {
      Text(
        text = "[$it]".uppercase(Locale.ROOT),
        style = DshTheme.type.caption.copy(fontSize = 9.sp),
        color = textColor,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun ModelInputRow(
  inputText: String,
  onInputTextChanged: (String) -> Unit,
  selectedModel: String,
  hostModel: String,
  onModelSelect: () -> Unit,
  onSend: () -> Unit,
  enabled: Boolean,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(8.dp),
  ) {
    // Model switcher chip
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onModelSelect)
        .background(DshTheme.colors.surfaceRaised, RoundedCornerShape(12.dp))
        .padding(horizontal = 10.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Default.Settings, contentDescription = null, tint = DshTheme.colors.primary, modifier = Modifier.size(12.dp))
      Spacer(modifier = Modifier.width(4.dp))
      val display = selectedModel.ifBlank { hostModel.ifBlank { "Default Model" } }
      Text(text = display, style = DshTheme.type.caption, color = DshTheme.colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
      Spacer(modifier = Modifier.width(4.dp))
      Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DshTheme.colors.primary, modifier = Modifier.size(12.dp))
    }
    Spacer(modifier = Modifier.height(4.dp))

    // Input + send
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = inputText,
        onValueChange = { onInputTextChanged(it) },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Type a message...") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = DshTheme.colors.primary,
          unfocusedBorderColor = DshTheme.colors.border,
        ),
        singleLine = true,
      )
      Spacer(modifier = Modifier.width(8.dp))
      IconButton(onClick = onSend, enabled = enabled) {
        Icon(
          Icons.Default.Send,
          contentDescription = "Send",
          tint = if (enabled) DshTheme.colors.primary else DshTheme.colors.textMuted,
        )
      }
    }
  }
}

@Composable
private fun ModelPickerDialog(
  modelGroups: List<DshSessionManager.ModelGroup>,
  currentModel: String,
  onDismiss: () -> Unit,
  onConfirm: (selectedModel: String?) -> Unit,
) {
  var selected by remember { mutableStateOf(currentModel) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Select Model", style = DshTheme.type.title) },
    confirmButton = {
      TextButton(onClick = { onConfirm(selected.ifBlank { null }) }) {
        Text("Apply", style = DshTheme.type.body)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", style = DshTheme.type.body)
      }
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).height(400.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Clear selection
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { selected = "" }
            .padding(8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Default", style = DshTheme.type.body, color = DshTheme.colors.text, modifier = Modifier.weight(1f))
          if (selected.isEmpty()) Icon(Icons.Default.Check, contentDescription = null, tint = DshTheme.colors.primary)
        }

        if (modelGroups.isEmpty()) {
          Text("No models available", style = DshTheme.type.body, color = DshTheme.colors.textMuted)
        } else {
          modelGroups.forEach { group ->
            Text(group.name, style = DshTheme.type.caption, color = DshTheme.colors.primary)
            group.models.forEach { model ->
              val label = model.name
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { selected = label }
                  .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(label, style = DshTheme.type.body, color = DshTheme.colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (selected == label) Icon(Icons.Default.Check, contentDescription = null, tint = DshTheme.colors.primary)
              }
            }
          }
        }
      }
    },
  )
}