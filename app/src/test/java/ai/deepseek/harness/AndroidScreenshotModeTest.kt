package ai.deepseek.harness

import ai.deepseek.harness.ui.SettingsRoute
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidScreenshotModeTest {
  @Test
  fun ignoresNormalLaunches() {
    assertNull(parseAndroidScreenshotModeIntent(Intent(Intent.ACTION_MAIN)))
  }

  @Test
  fun parsesRequestedScene() {
    val parsed =
      parseAndroidScreenshotModeIntent(
        Intent(Intent.ACTION_MAIN)
          .putExtra(extraAndroidScreenshotMode, true)
          .putExtra(extraAndroidScreenshotScene, "chat"),
      )

    assertEquals(AndroidScreenshotScene.Chat, parsed)
  }

  @Test
  fun defaultsUnknownScenesToHome() {
    val parsed =
      parseAndroidScreenshotModeIntent(
        Intent(Intent.ACTION_MAIN)
          .putExtra(extraAndroidScreenshotMode, true)
          .putExtra(extraAndroidScreenshotScene, "unknown"),
      )

    assertEquals(AndroidScreenshotScene.Home, parsed)
  }

  @Test
  fun mapsScenesToProductionShellDestinations() {
    assertEquals(HomeDestination.Connect, AndroidScreenshotScene.Home.homeDestination)
    assertEquals(HomeDestination.Chat, AndroidScreenshotScene.Chat.homeDestination)
    assertEquals(HomeDestination.Chat, AndroidScreenshotScene.Swarm.homeDestination)
    assertEquals(HomeDestination.Settings, AndroidScreenshotScene.Settings.homeDestination)
    assertEquals(HomeDestination.Settings, AndroidScreenshotScene.Desktop.homeDestination)
  }

  @Test
  fun gatewaySceneTargetsSettingsGatewayRoute() {
    val parsed =
      parseAndroidScreenshotModeIntent(
        Intent(Intent.ACTION_MAIN)
          .putExtra(extraAndroidScreenshotMode, true)
          .putExtra(extraAndroidScreenshotScene, "gateway"),
      )

    assertEquals(AndroidScreenshotScene.Gateway, parsed)
    assertEquals(HomeDestination.Settings, parsed?.homeDestination)
    assertEquals(SettingsRoute.Gateway, parsed?.settingsRoute)
    assertNull(AndroidScreenshotScene.Settings.settingsRoute)
  }

  @Test
  fun dshSceneTargetsSystemAgentSettings() {
    val scene = AndroidScreenshotScene.fromRawValue("dsh")

    assertEquals(AndroidScreenshotScene.DeepSeekHarness, scene)
    assertEquals(HomeDestination.Settings, scene.homeDestination)
    assertEquals(SettingsRoute.SystemAgent, scene.settingsRoute)
  }

  @Test
  fun desktopSceneTargetsDesktopSettings() {
    val scene = AndroidScreenshotScene.fromRawValue("desktop")

    assertEquals(AndroidScreenshotScene.Desktop, scene)
    assertEquals(SettingsRoute.Desktop, scene.settingsRoute)
  }
}
