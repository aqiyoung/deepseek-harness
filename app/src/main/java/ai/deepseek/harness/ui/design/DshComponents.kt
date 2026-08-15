package ai.deepseek.harness.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class DshStatus {
  Neutral,
  Success,
  Warning,
  Danger,
}

/** Full-screen mobile scaffold that applies DeepSeekHarness safe-area and canvas tokens. */
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

/** Section title row with an optional trailing action slot. */
@Composable
internal fun DshSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = title,
      style = DshTheme.type.section,
      color = DshTheme.colors.text,
    )
    action?.invoke()
  }
}

/** Primary call-to-action button using the mobile design token set. */
@Composable
internal fun DshPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
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
    if (icon != null) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(text = text, style = DshTheme.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** Secondary action button for non-default commands. */
@Composable
internal fun DshSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.heightIn(min = DshTheme.spacing.touchTarget),
    shape = RoundedCornerShape(DshTheme.radii.button),
    color = if (enabled) DshTheme.colors.surfaceRaised else DshTheme.colors.surface,
    contentColor = if (enabled) DshTheme.colors.text else DshTheme.colors.textSubtle,
    border = BorderStroke(1.dp, if (enabled) DshTheme.colors.borderStrong else DshTheme.colors.border),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(7.dp))
      }
      Text(text = text, style = DshTheme.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/** Fixed-size circular icon button for toolbar actions. */
@Composable
internal fun DshIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.size(DshTheme.spacing.touchTarget),
    shape = CircleShape,
    color = if (enabled) DshTheme.colors.surfaceRaised else DshTheme.colors.surface,
    contentColor = if (enabled) DshTheme.colors.text else DshTheme.colors.textSubtle,
    border = BorderStroke(1.dp, DshTheme.colors.border),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
    }
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

/** Compact label/value row for health and readiness summaries. */
@Composable
internal fun DshStatusRow(
  title: String,
  value: String,
  healthy: Boolean,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    Text(
      text = title,
      style = DshTheme.type.body,
      color = DshTheme.colors.text,
      modifier = Modifier.weight(1f),
      maxLines = 1,
    )
    DshStatusPill(
      text = value,
      status = if (healthy) DshStatus.Success else DshStatus.Warning,
    )
  }
}

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
      Text(text = text, style = DshTheme.type.caption.copy(fontSize = 13.sp, lineHeight = 17.sp), color = DshTheme.colors.textMuted, maxLines = 1)
    }
  }
}

/** Small optional-selectable pill used for filters and metadata chips. */
@Composable
internal fun DshPill(
  text: String,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val surfaceModifier =
    if (onClick == null) {
      modifier
    } else {
      modifier.clickable(onClick = onClick)
    }

  Surface(
    modifier = surfaceModifier,
    shape = RoundedCornerShape(DshTheme.radii.pill),
    color = if (selected) DshTheme.colors.primary else DshTheme.colors.surfaceRaised,
    contentColor = if (selected) DshTheme.colors.primaryText else DshTheme.colors.textMuted,
    border = BorderStroke(1.dp, if (selected) DshTheme.colors.primary else DshTheme.colors.border),
  ) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      style = DshTheme.type.caption,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** Panel wrapper for homogeneous lists with standard row separators. */
@Composable
internal fun <T> DshListPanel(
  items: List<T>,
  modifier: Modifier = Modifier,
  row: @Composable (T) -> Unit,
) {
  DshPanel(modifier = modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
    DshSeparatedColumn(items = items, row = row)
  }
}

/** Column helper that inserts standard dividers between rendered rows. */
@Composable
internal fun <T> DshSeparatedColumn(
  items: List<T>,
  modifier: Modifier = Modifier,
  row: @Composable (T) -> Unit,
) {
  Column(modifier = modifier) {
    items.forEachIndexed { index, item ->
      row(item)
      if (index != items.lastIndex) {
        HorizontalDivider(color = DshTheme.colors.border.copy(alpha = 0.82f), thickness = 1.dp)
      }
    }
  }
}

/** Two-line settings/detail row with caller-provided leading and trailing slots. */
@Composable
internal fun DshDetailRow(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  leading: @Composable () -> Unit,
  trailing: @Composable () -> Unit,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 54.dp)
        .padding(horizontal = 0.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    leading()
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      Text(text = title, style = DshTheme.type.body, color = DshTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(text = subtitle, style = DshTheme.type.caption, color = DshTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    trailing()
  }
}

/** Circular text badge used for compact numeric or initials-style row marks. */
@Composable
internal fun DshTextBadge(
  text: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.size(30.dp),
    shape = CircleShape,
    color = DshTheme.colors.surfacePressed,
    border = BorderStroke(1.dp, DshTheme.colors.border),
    contentColor = DshTheme.colors.text,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(text = text, style = DshTheme.type.label, color = DshTheme.colors.text, maxLines = 1)
    }
  }
}

/** Circular icon badge used as a neutral leading marker in list rows. */
@Composable
internal fun DshIconBadge(
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.size(30.dp),
    shape = CircleShape,
    color = DshTheme.colors.surfacePressed,
    border = BorderStroke(1.dp, DshTheme.colors.border),
    contentColor = DshTheme.colors.text,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = DshTheme.colors.text)
    }
  }
}

/** Reusable one-line list row with optional subtitle, metadata, slots, and click handling. */
@Composable
internal fun DshListItem(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  metadata: String? = null,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
) {
  val rowModifier =
    if (onClick == null) {
      modifier
    } else {
      modifier.clickable(onClick = onClick)
    }

  Row(
    modifier =
      rowModifier
        .fillMaxWidth()
        .heightIn(min = DshTheme.spacing.touchTarget)
        .clip(RoundedCornerShape(DshTheme.radii.row))
        .padding(horizontal = 2.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    leading?.invoke()
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = DshTheme.type.body,
        color = DshTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = DshTheme.type.caption,
          color = DshTheme.colors.textSubtle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (metadata != null) {
      Text(text = metadata, style = DshTheme.type.caption, color = DshTheme.colors.textSubtle, maxLines = 1)
    }
    trailing?.invoke()
  }
}

/** Keeps segmented options on one row unless a caller explicitly opts into wrapping. */
internal fun segmentedControlRows(
  options: List<String>,
  maxOptionsPerRow: Int? = null,
): List<List<String>> {
  if (options.isEmpty()) return emptyList()
  if (maxOptionsPerRow == null || options.size <= maxOptionsPerRow) return listOf(options)
  require(maxOptionsPerRow > 0) { "maxOptionsPerRow must be positive" }

  val rowCount = (options.size + maxOptionsPerRow - 1) / maxOptionsPerRow
  val minimumRowSize = options.size / rowCount
  val largerRowCount = options.size % rowCount
  var startIndex = 0
  return List(rowCount) { rowIndex ->
    val rowSize = minimumRowSize + if (rowIndex < largerRowCount) 1 else 0
    options.subList(startIndex, startIndex + rowSize).toList().also {
      startIndex += rowSize
    }
  }
}

/** Equal-width segmented control with caller-controlled wrapping. */
@Composable
internal fun DshSegmentedControl(
  options: List<String>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabledOptions: Set<String> = options.toSet(),
  maxOptionsPerRow: Int? = null,
) {
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(DshTheme.radii.control))
        .border(1.dp, DshTheme.colors.border, RoundedCornerShape(DshTheme.radii.control))
        .padding(2.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    segmentedControlRows(options, maxOptionsPerRow).forEach { rowOptions ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        rowOptions.forEach { option ->
          val active = option == selected
          val enabled = option in enabledOptions
          Box(
            modifier =
              Modifier
                .weight(1f)
                .clip(RoundedCornerShape(DshTheme.radii.control))
                .background(if (active) DshTheme.colors.primary else Color.Transparent)
                .clickable(enabled = enabled) { onSelect(option) }
                .padding(horizontal = 9.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = option,
              style = DshTheme.type.caption,
              color =
                when {
                  active -> DshTheme.colors.primaryText
                  enabled -> DshTheme.colors.textMuted
                  else -> DshTheme.colors.textSubtle
                },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

/** Token-styled text field used by settings and prototype screens. */
@Composable
internal fun DshTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  minLines: Int = 1,
  label: String? = null,
  enabled: Boolean = true,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
  val fieldModifier =
    if (label == null) modifier else modifier.semantics { contentDescription = label }
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    enabled = enabled,
    modifier =
      fieldModifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(DshTheme.radii.control))
        .background(DshTheme.colors.surfaceRaised)
        .border(1.dp, DshTheme.colors.border, RoundedCornerShape(DshTheme.radii.control))
        .padding(horizontal = 11.dp, vertical = 8.dp),
    textStyle =
      DshTheme.type.body.copy(
        color = if (enabled) DshTheme.colors.text else DshTheme.colors.textSubtle,
      ),
    cursorBrush = SolidColor(DshTheme.colors.primary),
    visualTransformation = visualTransformation,
    keyboardOptions = keyboardOptions,
    minLines = minLines,
    decorationBox = { innerTextField ->
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        label?.let {
          Text(text = it, style = DshTheme.type.caption, color = DshTheme.colors.textMuted)
        }
        Box(modifier = Modifier.fillMaxWidth()) {
          if (value.isEmpty()) {
            Text(text = placeholder, style = DshTheme.type.body, color = DshTheme.colors.textSubtle)
          }
          innerTextField()
        }
      }
    },
  )
}

/** Local design-system preview surface for visual smoke checks. */
@Composable
internal fun DshComponentShowcase(modifier: Modifier = Modifier) {
  var selected by rememberSaveable { mutableStateOf("Chat") }
  var prompt by rememberSaveable { mutableStateOf("") }

  DshScaffold(modifier = modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
      DshTopBar(
        title = "DeepSeekHarness",
        subtitle = "Local command center",
        navigation = { DshAvatarMark(text = "OC") },
        actions = {
          DshIconButton(icon = Icons.Default.Search, contentDescription = "Search", onClick = {})
        },
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(text = "DeepSeekHarness", style = DshTheme.type.display, color = DshTheme.colors.text)
          Text(text = "Design system prototype", style = DshTheme.type.body, color = DshTheme.colors.textMuted)
        }
        DshStatusPill(text = "Connected", status = DshStatus.Success)
      }

      DshSegmentedControl(
        options = listOf("Chat", "Voice", "Threads"),
        selected = selected,
        onSelect = { selected = it },
        modifier = Modifier.fillMaxWidth(),
      )

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DshSectionHeader(title = "Threads")
        DshListItem(
          title = "Testing testing 1 2 3",
          subtitle = "14 messages · Android",
          metadata = "now",
        )
        DshListItem(
          title = "Provider setup",
          subtitle = "DeepSeekHarness gateway",
          metadata = "8m",
        )
      }

      DshTextField(value = prompt, onValueChange = { prompt = it }, placeholder = "Ask DeepSeekHarness anything", minLines = 3)

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DshPrimaryButton(text = "Start Chat", onClick = {}, modifier = Modifier.weight(1f))
        DshSecondaryButton(text = "Voice", onClick = {}, modifier = Modifier.weight(1f))
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DshPill(text = "Realtime", selected = true)
        DshPill(text = "Dictation")
        DshPill(text = "Screen")
      }

      DshEmptyState(
        title = "Nothing needs your attention",
        body = "DeepSeekHarness will surface approvals, failed jobs, and channel issues here.",
      )

      DshBottomNav(
        items =
          listOf(
            DshNavItem(key = "overview", label = "Home", icon = Icons.Default.Home),
            DshNavItem(key = "chat", label = "Chat", icon = Icons.Default.ChatBubble),
            DshNavItem(key = "voice", label = "Voice", icon = Icons.Default.Mic),
            DshNavItem(key = "settings", label = "Settings", icon = Icons.Default.Settings),
          ),
        selectedKey = "chat",
        onSelect = {},
      )
    }
  }
}
