package ai.deepseek.harness.node

import ai.deepseek.harness.protocol.DeepSeekHarnessCalendarCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessCallLogCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessCameraCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessCapability
import ai.deepseek.harness.protocol.DeepSeekHarnessContactsCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessDeviceCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessLocationCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessMobileUiCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessMotionCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessNotificationsCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessPhotosCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessSmsCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessSystemCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessTalkCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvokeCommandRegistryTest {
  private val coreCapabilities =
    setOf(
      DeepSeekHarnessCapability.Canvas.rawValue,
      DeepSeekHarnessCapability.Device.rawValue,
      DeepSeekHarnessCapability.Notifications.rawValue,
      DeepSeekHarnessCapability.System.rawValue,
      DeepSeekHarnessCapability.Talk.rawValue,
      DeepSeekHarnessCapability.Contacts.rawValue,
      DeepSeekHarnessCapability.Calendar.rawValue,
    )

  private val optionalCapabilities =
    setOf(
      DeepSeekHarnessCapability.Camera.rawValue,
      DeepSeekHarnessCapability.Location.rawValue,
      DeepSeekHarnessCapability.Sms.rawValue,
      DeepSeekHarnessCapability.CallLog.rawValue,
      DeepSeekHarnessCapability.Motion.rawValue,
      DeepSeekHarnessCapability.Photos.rawValue,
      DeepSeekHarnessCapability.VoiceWake.rawValue,
      DeepSeekHarnessCapability.MobileUI.rawValue,
    )

  private val coreCommands =
    setOf(
      DeepSeekHarnessDeviceCommand.Status.rawValue,
      DeepSeekHarnessDeviceCommand.Info.rawValue,
      DeepSeekHarnessDeviceCommand.Permissions.rawValue,
      DeepSeekHarnessDeviceCommand.Health.rawValue,
      DeepSeekHarnessNotificationsCommand.List.rawValue,
      DeepSeekHarnessNotificationsCommand.Actions.rawValue,
      DeepSeekHarnessSystemCommand.Notify.rawValue,
      DeepSeekHarnessTalkCommand.PttStart.rawValue,
      DeepSeekHarnessTalkCommand.PttStop.rawValue,
      DeepSeekHarnessTalkCommand.PttCancel.rawValue,
      DeepSeekHarnessTalkCommand.PttOnce.rawValue,
      DeepSeekHarnessContactsCommand.Search.rawValue,
      DeepSeekHarnessContactsCommand.Add.rawValue,
      DeepSeekHarnessCalendarCommand.Events.rawValue,
      DeepSeekHarnessCalendarCommand.Add.rawValue,
    )

  private val optionalCommands =
    setOf(
      DeepSeekHarnessCameraCommand.Snap.rawValue,
      DeepSeekHarnessCameraCommand.Clip.rawValue,
      DeepSeekHarnessCameraCommand.List.rawValue,
      DeepSeekHarnessLocationCommand.Get.rawValue,
      DeepSeekHarnessMotionCommand.Activity.rawValue,
      DeepSeekHarnessMotionCommand.Pedometer.rawValue,
      DeepSeekHarnessSmsCommand.Send.rawValue,
      DeepSeekHarnessSmsCommand.Search.rawValue,
      DeepSeekHarnessCallLogCommand.Search.rawValue,
      DeepSeekHarnessPhotosCommand.Latest.rawValue,
      DeepSeekHarnessMobileUiCommand.Observe.rawValue,
      DeepSeekHarnessMobileUiCommand.Act.rawValue,
    )

  private val debugCommands = setOf("debug.logs", "debug.ed25519")

  @Test
  fun advertisedCapabilities_respectsFeatureAvailability() {
    val capabilities = InvokeCommandRegistry.advertisedCapabilities(defaultFlags())

    assertContainsAll(capabilities, coreCapabilities)
    assertMissingAll(capabilities, optionalCapabilities)
  }

  @Test
  fun advertisedCapabilities_includesFeatureCapabilitiesWhenEnabled() {
    val capabilities =
      InvokeCommandRegistry.advertisedCapabilities(
        defaultFlags(
          cameraEnabled = true,
          locationEnabled = true,
          sendSmsAvailable = true,
          readSmsAvailable = true,
          smsSearchPossible = true,
          callLogAvailable = true,
          photosAvailable = true,
          motionActivityAvailable = true,
          motionPedometerAvailable = true,
          voiceWakeEnabled = true,
          mobileUiAvailable = true,
        ),
      )

    assertContainsAll(capabilities, coreCapabilities + optionalCapabilities)
  }

  @Test
  fun advertisedCommands_respectsFeatureAvailability() {
    val commands = InvokeCommandRegistry.advertisedCommands(defaultFlags())

    assertContainsAll(commands, coreCommands)
    assertMissingAll(commands, optionalCommands + debugCommands)
  }

  @Test
  fun advertisedCommands_includesDeviceAppsOnlyWhenUserOptedIn() {
    val disabled = InvokeCommandRegistry.advertisedCommands(defaultFlags(installedAppsSharingEnabled = false))
    val enabled = InvokeCommandRegistry.advertisedCommands(defaultFlags(installedAppsSharingEnabled = true))

    assertFalse(disabled.contains(DeepSeekHarnessDeviceCommand.Apps.rawValue))
    assertTrue(enabled.contains(DeepSeekHarnessDeviceCommand.Apps.rawValue))
  }

  @Test
  fun advertisedCommands_includesFeatureCommandsWhenEnabled() {
    val commands =
      InvokeCommandRegistry.advertisedCommands(
        defaultFlags(
          cameraEnabled = true,
          locationEnabled = true,
          sendSmsAvailable = true,
          readSmsAvailable = true,
          smsSearchPossible = true,
          callLogAvailable = true,
          photosAvailable = true,
          motionActivityAvailable = true,
          motionPedometerAvailable = true,
          debugBuild = true,
          mobileUiAvailable = true,
        ),
      )

    assertContainsAll(commands, coreCommands + optionalCommands + debugCommands)
  }

  @Test
  fun advertisedCommands_onlyIncludesSupportedMotionCommands() {
    val commands =
      InvokeCommandRegistry.advertisedCommands(
        NodeRuntimeFlags(
          cameraEnabled = false,
          locationEnabled = false,
          sendSmsAvailable = false,
          readSmsAvailable = false,
          smsSearchPossible = false,
          callLogAvailable = false,
          photosAvailable = false,
          motionActivityAvailable = true,
          motionPedometerAvailable = false,
          installedAppsSharingEnabled = false,
          debugBuild = false,
        ),
      )

    assertTrue(commands.contains(DeepSeekHarnessMotionCommand.Activity.rawValue))
    assertFalse(commands.contains(DeepSeekHarnessMotionCommand.Pedometer.rawValue))
  }

  @Test
  fun advertisedCommands_splitsSmsSendAndSearchAvailability() {
    val readOnlyCommands =
      InvokeCommandRegistry.advertisedCommands(
        defaultFlags(readSmsAvailable = true, smsSearchPossible = true),
      )
    val sendOnlyCommands =
      InvokeCommandRegistry.advertisedCommands(
        defaultFlags(sendSmsAvailable = true),
      )
    val requestableSearchCommands =
      InvokeCommandRegistry.advertisedCommands(
        defaultFlags(smsSearchPossible = true),
      )

    assertTrue(readOnlyCommands.contains(DeepSeekHarnessSmsCommand.Search.rawValue))
    assertFalse(readOnlyCommands.contains(DeepSeekHarnessSmsCommand.Send.rawValue))
    assertTrue(sendOnlyCommands.contains(DeepSeekHarnessSmsCommand.Send.rawValue))
    assertFalse(sendOnlyCommands.contains(DeepSeekHarnessSmsCommand.Search.rawValue))
    assertTrue(requestableSearchCommands.contains(DeepSeekHarnessSmsCommand.Search.rawValue))
  }

  @Test
  fun advertisedCapabilities_includeSmsWhenEitherSmsPathIsAvailable() {
    val readOnlyCapabilities =
      InvokeCommandRegistry.advertisedCapabilities(
        defaultFlags(readSmsAvailable = true),
      )
    val sendOnlyCapabilities =
      InvokeCommandRegistry.advertisedCapabilities(
        defaultFlags(sendSmsAvailable = true),
      )
    val requestableSearchCapabilities =
      InvokeCommandRegistry.advertisedCapabilities(
        defaultFlags(smsSearchPossible = true),
      )

    assertTrue(readOnlyCapabilities.contains(DeepSeekHarnessCapability.Sms.rawValue))
    assertTrue(sendOnlyCapabilities.contains(DeepSeekHarnessCapability.Sms.rawValue))
    assertFalse(requestableSearchCapabilities.contains(DeepSeekHarnessCapability.Sms.rawValue))
  }

  @Test
  fun advertisedCommands_excludesCallLogWhenUnavailable() {
    val commands = InvokeCommandRegistry.advertisedCommands(defaultFlags(callLogAvailable = false))

    assertFalse(commands.contains(DeepSeekHarnessCallLogCommand.Search.rawValue))
  }

  @Test
  fun advertisedCapabilities_excludesCallLogWhenUnavailable() {
    val capabilities = InvokeCommandRegistry.advertisedCapabilities(defaultFlags(callLogAvailable = false))

    assertFalse(capabilities.contains(DeepSeekHarnessCapability.CallLog.rawValue))
  }

  @Test
  fun advertisedPhotosSurface_respectsFeatureAvailability() {
    val disabledFlags = defaultFlags(photosAvailable = false)
    val enabledFlags = defaultFlags(photosAvailable = true)

    assertFalse(InvokeCommandRegistry.advertisedCapabilities(disabledFlags).contains(DeepSeekHarnessCapability.Photos.rawValue))
    assertFalse(InvokeCommandRegistry.advertisedCommands(disabledFlags).contains(DeepSeekHarnessPhotosCommand.Latest.rawValue))
    assertTrue(InvokeCommandRegistry.advertisedCapabilities(enabledFlags).contains(DeepSeekHarnessCapability.Photos.rawValue))
    assertTrue(InvokeCommandRegistry.advertisedCommands(enabledFlags).contains(DeepSeekHarnessPhotosCommand.Latest.rawValue))
  }

  @Test
  fun find_returnsForegroundMetadataForCameraCommands() {
    val list = InvokeCommandRegistry.find(DeepSeekHarnessCameraCommand.List.rawValue)
    val location = InvokeCommandRegistry.find(DeepSeekHarnessLocationCommand.Get.rawValue)
    val pttStart = InvokeCommandRegistry.find(DeepSeekHarnessTalkCommand.PttStart.rawValue)
    val pttStop = InvokeCommandRegistry.find(DeepSeekHarnessTalkCommand.PttStop.rawValue)
    val pttCancel = InvokeCommandRegistry.find(DeepSeekHarnessTalkCommand.PttCancel.rawValue)
    val pttOnce = InvokeCommandRegistry.find(DeepSeekHarnessTalkCommand.PttOnce.rawValue)

    assertNotNull(list)
    assertEquals(true, list?.requiresForeground)
    assertNotNull(location)
    assertEquals(false, location?.requiresForeground)
    assertNotNull(pttStart)
    assertEquals(false, pttStart?.requiresForeground)
    assertNotNull(pttStop)
    assertEquals(false, pttStop?.requiresForeground)
    assertNotNull(pttCancel)
    assertEquals(false, pttCancel?.requiresForeground)
    assertNotNull(pttOnce)
    assertEquals(true, pttOnce?.requiresForeground)
  }

  @Test
  fun find_returnsNullForUnknownCommand() {
    assertNull(InvokeCommandRegistry.find("not.real"))
  }

  private fun defaultFlags(
    cameraEnabled: Boolean = false,
    locationEnabled: Boolean = false,
    sendSmsAvailable: Boolean = false,
    readSmsAvailable: Boolean = false,
    smsSearchPossible: Boolean = false,
    callLogAvailable: Boolean = false,
    photosAvailable: Boolean = false,
    motionActivityAvailable: Boolean = false,
    motionPedometerAvailable: Boolean = false,
    installedAppsSharingEnabled: Boolean = false,
    debugBuild: Boolean = false,
    voiceWakeEnabled: Boolean = false,
    mobileUiAvailable: Boolean = false,
  ): NodeRuntimeFlags =
    NodeRuntimeFlags(
      cameraEnabled = cameraEnabled,
      locationEnabled = locationEnabled,
      sendSmsAvailable = sendSmsAvailable,
      readSmsAvailable = readSmsAvailable,
      smsSearchPossible = smsSearchPossible,
      callLogAvailable = callLogAvailable,
      photosAvailable = photosAvailable,
      motionActivityAvailable = motionActivityAvailable,
      motionPedometerAvailable = motionPedometerAvailable,
      installedAppsSharingEnabled = installedAppsSharingEnabled,
      debugBuild = debugBuild,
      voiceWakeEnabled = voiceWakeEnabled,
      mobileUiAvailable = mobileUiAvailable,
    )

  private fun assertContainsAll(
    actual: List<String>,
    expected: Set<String>,
  ) {
    expected.forEach { value -> assertTrue(actual.contains(value)) }
  }

  private fun assertMissingAll(
    actual: List<String>,
    forbidden: Set<String>,
  ) {
    forbidden.forEach { value -> assertFalse(actual.contains(value)) }
  }
}
