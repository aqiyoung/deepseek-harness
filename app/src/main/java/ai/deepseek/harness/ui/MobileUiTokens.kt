package ai.deepseek.harness.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Mobile UI color tokens used by the DSH design theme.
 */
internal data class MobileColors(
  val canvas: Color,
  val surface: Color,
  val surfaceRaised: Color,
  val surfacePressed: Color,
  val border: Color,
  val text: Color,
  val textMuted: Color,
  val textSubtle: Color,
  val primary: Color,
  val primaryText: Color,
  val success: Color,
  val warning: Color,
  val danger: Color,
)

internal val LocalMobileColors = staticCompositionLocalOf { darkMobileColors() }

internal val mobileFontFamily: FontFamily = FontFamily.Default

internal fun darkMobileColors() =
  MobileColors(
    canvas = Color(0xFF030303),
    surface = Color(0xFF0A0A0A),
    surfaceRaised = Color(0xFF111111),
    surfacePressed = Color(0xFF1A1A1A),
    border = Color(0xFF242424),
    text = Color(0xFFF5F5F5),
    textMuted = Color(0xFF9E9E9E),
    textSubtle = Color(0xFF616161),
    primary = Color(0xFF77C8FF),
    primaryText = Color(0xFF003044),
    success = Color(0xFF4CAF50),
    warning = Color(0xFFFFC107),
    danger = Color(0xFFE53935),
  )

internal fun lightMobileColors() =
  MobileColors(
    canvas = Color(0xFFF7F7F8),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF0F0F2),
    surfacePressed = Color(0xFFE6E6E8),
    border = Color(0xFFE0E0E3),
    text = Color(0xFF1A1A1B),
    textMuted = Color(0xFF6B6B70),
    textSubtle = Color(0xFF9E9EA4),
    primary = Color(0xFF0EA5E9),
    primaryText = Color(0xFFFFFFFF),
    success = Color(0xFF16A34A),
    warning = Color(0xFFF59E0B),
    danger = Color(0xFFDC2626),
  )