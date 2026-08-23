package ai.deepseek.harness.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen scaffold that applies safe-area and canvas tokens. */
@Composable
internal fun DshScaffold(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(horizontal = DshTheme.spacing.lg, vertical = DshTheme.spacing.lg),
  contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
  content: @Composable () -> Unit,
) {
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(DshTheme.colors.canvas)
        .windowInsetsPadding(contentWindowInsets)
        .padding(contentPadding),
  ) {
    content()
  }
}

/** Raised card with a hairline border, matching the onboarding SoftPanel look. */
@Composable
internal fun DshSoftPanel(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(DshTheme.radii.panel),
    color = DshTheme.colors.surfaceRaised,
    contentColor = DshTheme.colors.text,
    border = BorderStroke(1.dp, DshTheme.colors.border),
  ) {
    Column(modifier = Modifier.padding(18.dp), content = content)
  }
}

/** Primary call-to-action button. */
@Composable
internal fun DshPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.heightIn(min = DshTheme.spacing.touchTarget),
    shape = RoundedCornerShape(DshTheme.radii.button),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = DshTheme.colors.primary,
        contentColor = DshTheme.colors.primaryText,
        disabledContainerColor = DshTheme.colors.surfacePressed,
        disabledContentColor = DshTheme.colors.textSubtle,
      ),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
  ) {
    Text(text = text, style = DshTheme.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** Transparent circular icon button for low-emphasis toolbar actions. */
@Composable
internal fun DshPlainIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.size(DshTheme.spacing.touchTarget),
    shape = CircleShape,
    color = Color.Transparent,
    contentColor = DshTheme.colors.text,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
    }
  }
}

enum class DshStatus { Neutral, Success, Warning, Danger }

/** Compact status chip with a semantic color dot. */
@Composable
internal fun DshStatusPill(
  text: String,
  status: DshStatus,
  modifier: Modifier = Modifier,
) {
  val colors = DshTheme.colors
  val (dotColor, backgroundColor) =
    when (status) {
      DshStatus.Neutral -> colors.textSubtle to colors.surfaceRaised
      DshStatus.Success -> colors.success to colors.successSoft
      DshStatus.Warning -> colors.warning to colors.warningSoft
      DshStatus.Danger -> colors.danger to colors.dangerSoft
    }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(DshTheme.radii.control),
    color = backgroundColor,
    border = BorderStroke(1.dp, DshTheme.colors.border),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Box(
        modifier =
          Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(dotColor),
      )
      Text(text = text, style = DshTheme.type.caption.copy(fontSize = 13.sp, lineHeight = 17.sp), color = colors.textMuted, maxLines = 1)
    }
  }
}

/** Uppercase muted caption above a grouped settings panel. */
@Composable
internal fun DshSectionLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    style = DshTheme.type.captionSmall,
    color = DshTheme.colors.textSubtle,
    modifier = modifier.padding(start = 4.dp, bottom = 6.dp),
  )
}

/** Grouped settings list row with optional trailing value and chevron disclosure. */
@Composable
internal fun DshSettingsRow(
  title: String,
  modifier: Modifier = Modifier,
  value: String? = null,
  danger: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val rowContent: @Composable () -> Unit = {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = 54.dp)
          .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style = DshTheme.type.body,
        color = if (danger) DshTheme.colors.danger else DshTheme.colors.text,
        modifier = Modifier.weight(1f),
      )
      value?.let {
        Text(text = it, style = DshTheme.type.caption, color = DshTheme.colors.textSubtle, maxLines = 1)
        Spacer(modifier = Modifier.width(8.dp))
      }
      if (onClick != null) {
        Icon(
          imageVector = Icons.Default.ChevronRight,
          contentDescription = null,
          tint = DshTheme.colors.textSubtle,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }

  if (onClick != null) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), color = Color.Transparent) {
      rowContent()
    }
  } else {
    Box(modifier = modifier.fillMaxWidth()) { rowContent() }
  }
}
