package ai.deepseek.harness.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepseek.harness.R

// Design tokens ported from the OpenClaw Android "Claw" design system,
// forced dark to match its visual language.
internal val dshFontFamily =
  FontFamily(
    Font(resId = R.font.manrope_400_regular, weight = FontWeight.Normal),
    Font(resId = R.font.manrope_500_medium, weight = FontWeight.Medium),
    Font(resId = R.font.manrope_600_semibold, weight = FontWeight.SemiBold),
    Font(resId = R.font.manrope_700_bold, weight = FontWeight.Bold),
  )

@Immutable
internal data class DshColors(
  val canvas: Color,
  val surface: Color,
  val surfaceRaised: Color,
  val surfacePressed: Color,
  val border: Color,
  val borderStrong: Color,
  val text: Color,
  val textMuted: Color,
  val textSubtle: Color,
  val primary: Color,
  val primaryText: Color,
  val success: Color,
  val successSoft: Color,
  val warning: Color,
  val warningSoft: Color,
  val danger: Color,
  val dangerSoft: Color,
)

@Immutable
internal data class DshSpacing(
  val xxxs: Dp = 4.dp,
  val xxs: Dp = 8.dp,
  val xs: Dp = 12.dp,
  val sm: Dp = 16.dp,
  val md: Dp = 20.dp,
  val lg: Dp = 24.dp,
  val xl: Dp = 32.dp,
  val xxl: Dp = 40.dp,
  val touchTarget: Dp = 48.dp,
)

@Immutable
internal data class DshRadii(
  val row: Dp = 4.dp,
  val panel: Dp = 5.dp,
  val control: Dp = 6.dp,
  val button: Dp = 8.dp,
  val sheet: Dp = 10.dp,
  val pill: Dp = 12.dp,
)

@Immutable
internal data class DshTypography(
  val display: TextStyle,
  val title: TextStyle,
  val section: TextStyle,
  val body: TextStyle,
  val label: TextStyle,
  val caption: TextStyle,
  val captionSmall: TextStyle,
  val mono: TextStyle,
)

private val DshDarkColors =
  DshColors(
    canvas = Color(0xFF030303),
    surface = Color(0xFF0A0A0A),
    surfaceRaised = Color(0xFF111111),
    surfacePressed = Color(0xFF1A1A1A),
    border = Color(0xFF242424),
    borderStrong = Color(0xFF3A3A3A),
    text = Color(0xFFF8F8F8),
    textMuted = Color(0xFFA8A8A8),
    textSubtle = Color(0xFF707070),
    primary = Color(0xFFFFFFFF),
    primaryText = Color(0xFF050505),
    success = Color(0xFF3EDB82),
    successSoft = Color(0xFF102719),
    warning = Color(0xFFE6B956),
    warningSoft = Color(0xFF2B2412),
    danger = Color(0xFFFF6B6B),
    dangerSoft = Color(0xFF2C1414),
  )

private val LocalDshColors = staticCompositionLocalOf { DshDarkColors }
private val LocalDshSpacing = staticCompositionLocalOf { DshSpacing() }
private val LocalDshRadii = staticCompositionLocalOf { DshRadii() }
private val LocalDshTypography = staticCompositionLocalOf { dshTypography(dshFontFamily) }

internal object DshTheme {
  val colors: DshColors
    @Composable
    @ReadOnlyComposable
    get() = LocalDshColors.current

  val spacing: DshSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalDshSpacing.current

  val radii: DshRadii
    @Composable
    @ReadOnlyComposable
    get() = LocalDshRadii.current

  val type: DshTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalDshTypography.current
}

@Composable
internal fun DshDesignTheme(content: @Composable () -> Unit) {
  val colors = DshDarkColors
  val typography = dshTypography(dshFontFamily)

  CompositionLocalProvider(
    LocalDshColors provides colors,
    LocalDshSpacing provides DshSpacing(),
    LocalDshRadii provides DshRadii(),
    LocalDshTypography provides typography,
  ) {
    MaterialTheme(
      colorScheme = darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.primaryText,
        background = colors.canvas,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        surfaceVariant = colors.surfaceRaised,
        onSurfaceVariant = colors.textMuted,
        outline = colors.border,
        error = colors.danger,
        onError = colors.primaryText,
      ),
      typography = materialTypography(typography),
      shapes = Shapes(),
      content = content,
    )
  }
}

private fun dshTypography(fontFamily: FontFamily) =
  DshTypography(
    display =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
      ),
    title =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
      ),
    section =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
      ),
    body =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
      ),
    label =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
      ),
    caption =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
      ),
    captionSmall =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
      ),
    mono =
      TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
      ),
  )

private fun materialTypography(type: DshTypography) =
  Typography(
    displayMedium = type.display,
    titleLarge = type.title,
    titleMedium = type.section,
    bodyLarge = type.body,
    labelLarge = type.label,
    labelSmall = type.caption,
  )
