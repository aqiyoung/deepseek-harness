package ai.deepseek.harness.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Standard inset panel for grouped Android app content.
 */
@Composable
internal fun DshPanel(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(12.dp),
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(DshTheme.radii.panel),
    color = DshTheme.colors.surfaceRaised.copy(alpha = 0.82f),
    contentColor = DshTheme.colors.text,
    border = null,
    tonalElevation = 2.dp,
    shadowElevation = 4.dp,
  ) {
    Column(modifier = Modifier.padding(contentPadding)) {
      content()
    }
  }
}

/**
 * Shared empty state used when a screen has no records but can still offer an action.
 */
@Composable
internal fun DshEmptyState(
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  DshPanel(modifier = modifier) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(text = title, style = DshTheme.type.section, color = DshTheme.colors.text)
      Text(text = body, style = DshTheme.type.body, color = DshTheme.colors.textMuted)
      action?.invoke()
    }
  }
}

/**
 * Shared loading placeholder that keeps async screen states visually consistent.
 */
@Composable
internal fun DshLoadingState(
  title: String,
  modifier: Modifier = Modifier,
) {
  DshPanel(modifier = modifier) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      CircularProgressIndicator(color = DshTheme.colors.primary, strokeWidth = 2.dp)
      Text(text = title, style = DshTheme.type.body, color = DshTheme.colors.textMuted)
    }
  }
}
