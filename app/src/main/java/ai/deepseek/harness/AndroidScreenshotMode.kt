package ai.deepseek.harness

import ai.deepseek.harness.ui.SettingsRoute
import android.content.Intent

const val extraAndroidScreenshotMode = "dsh.screenshotMode"
const val extraAndroidScreenshotScene = "dsh.screenshotScene"

enum class AndroidScreenshotScene(
  val rawValue: String,
  val homeDestination: HomeDestination,
  internal val settingsRoute: SettingsRoute? = null,
) {
  Home("home", HomeDestination.Connect),
  Chat("chat", HomeDestination.Chat),
  Swarm("swarm", HomeDestination.Chat),
  Settings("settings", HomeDestination.Settings),
  Gateway("gateway", HomeDestination.Settings, SettingsRoute.Gateway),
  DeepSeekHarness("dsh", HomeDestination.Settings, SettingsRoute.SystemAgent),
  ;

  companion object {
    fun fromRawValue(raw: String?): AndroidScreenshotScene = entries.firstOrNull { it.rawValue == raw?.trim()?.lowercase() } ?: Home
  }
}

fun parseAndroidScreenshotModeIntent(intent: Intent?): AndroidScreenshotScene? {
  if (intent?.getBooleanExtra(extraAndroidScreenshotMode, false) != true) {
    return null
  }
  return AndroidScreenshotScene.fromRawValue(intent.getStringExtra(extraAndroidScreenshotScene))
}
