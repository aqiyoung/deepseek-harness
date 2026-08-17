package ai.deepseek.harness.ui.chat

import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.chat.ChatMessage
import ai.deepseek.harness.chat.ChatMessageContent
import ai.deepseek.harness.dsh.DshConnectionState
import ai.deepseek.harness.ui.design.DshScaffold
import ai.deepseek.harness.ui.design.DshTheme
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Simplified chat screen that works directly with DshSessionManager.
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

  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  // Auto-select first session
  LaunchedEffect(sessions, activeSessionId) {
    if (activeSessionId == null && sessions.isNotEmpty()) {
      viewModel.loadSessionHistory(sessions.first().sessionId)
    }
  }

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
            modifier = Modifier.height(32.dp),
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
      if (chatMessages.isEmpty() && !isConnected) {
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

      if (chatMessages.isEmpty() && isConnected) {
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
    }

    // Input
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = inputText,
        onValueChange = { inputText = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Type a message...") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
          onSend = {
            val sessionId = activeSessionId ?: return@KeyboardActions
            if (inputText.isNotBlank()) {
              viewModel.sendPrompt(sessionId, inputText.trim())
              inputText = ""
            }
          },
        ),
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = DshTheme.colors.primary,
          unfocusedBorderColor = DshTheme.colors.border,
        ),
        singleLine = true,
      )

      Spacer(modifier = Modifier.width(8.dp))

      IconButton(
        onClick = {
          val sessionId = activeSessionId ?: return@IconButton
          if (inputText.isNotBlank()) {
            viewModel.sendPrompt(sessionId, inputText.trim())
            inputText = ""
          }
        },
        enabled = inputText.isNotBlank() && isConnected,
      ) {
        Icon(
          Icons.Default.Send,
          contentDescription = "Send",
          tint = if (inputText.isNotBlank() && isConnected) DshTheme.colors.primary
          else DshTheme.colors.textMuted,
        )
      }
    }
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
      )
    }
  }
}