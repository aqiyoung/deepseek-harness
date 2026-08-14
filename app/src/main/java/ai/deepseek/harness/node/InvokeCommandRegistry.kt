package ai.deepseek.harness.node

import ai.deepseek.harness.protocol.DeepSeekHarnessCalendarCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessCallLogCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessCameraCommand
import ai.deepseek.harness.protocol.DeepSeekHarnessCanvasA2UICommand
import ai.deepseek.harness.protocol.DeepSeekHarnessCanvasCommand
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

/** Runtime feature flags used to decide which node tools are advertised. */
data class NodeRuntimeFlags(
  val cameraEnabled: Boolean,
  val locationEnabled: Boolean,
  val sendSmsAvailable: Boolean,
  val readSmsAvailable: Boolean,
  val smsSearchPossible: Boolean,
  val callLogAvailable: Boolean,
  val photosAvailable: Boolean,
  val motionActivityAvailable: Boolean,
  val motionPedometerAvailable: Boolean,
  val installedAppsSharingEnabled: Boolean,
  val debugBuild: Boolean,
  val voiceWakeEnabled: Boolean = false,
  val mobileUiAvailable: Boolean = false,
)

/** Per-command availability gates checked before advertising invoke methods. */
enum class InvokeCommandAvailability {
  Always,
  CameraEnabled,
  LocationEnabled,
  SendSmsAvailable,
  ReadSmsAvailable,
  RequestableSmsSearchAvailable,
  CallLogAvailable,
  PhotosAvailable,
  MotionActivityAvailable,
  MotionPedometerAvailable,
  InstalledAppsSharingEnabled,
  DebugBuild,
  MobileUiAvailable,
}

/** Per-capability availability gates for the node capabilities manifest. */
enum class NodeCapabilityAvailability {
  Always,
  CameraEnabled,
  LocationEnabled,
  SmsAvailable,
  CallLogAvailable,
  PhotosAvailable,
  MotionAvailable,
  VoiceWakeEnabled,
  MobileUiAvailable,
}

/** Capability entry reported to the gateway when its availability gate passes. */
data class NodeCapabilitySpec(
  val name: String,
  val availability: NodeCapabilityAvailability = NodeCapabilityAvailability.Always,
)

/** Invoke method entry advertised to gateway plus foreground routing metadata. */
data class InvokeCommandSpec(
  val name: String,
  val requiresForeground: Boolean = false,
  val availability: InvokeCommandAvailability = InvokeCommandAvailability.Always,
)

object InvokeCommandRegistry {
  /** Capabilities mirror gateway protocol ids and are filtered by device state. */
  val capabilityManifest: List<NodeCapabilitySpec> =
    listOf(
      NodeCapabilitySpec(name = DeepSeekHarnessCapability.Canvas.rawValue),
      NodeCapabilitySpec(name = DeepSeekHarnessCapability.Device.rawValue),
      NodeCapabilitySpec(name = DeepSeekHarnessCapability.Notifications.rawValue),
      NodeCapabilitySpec(name = DeepSeekHarnessCapability.System.rawValue),
      NodeCapabilitySpec(
        name = DeepSeekHarnessCapability.Camera.rawValue,
        availability = NodeCapabilityAvailability.CameraEnabled,
      ),
      NodeCapabilitySpec(
        name = DeepSeekHarnessCapability.Sms.rawValue,
        availability = NodeCapabilityAvailability.SmsAvailable,
      ),
      NodeCapabilitySpec(name = DeepSeekHarnessCapability.Talk.rawValue),
      NodeCapabilitySpec(
        name = DeepSeekHarnessCapability.Location.rawValue,
        availability = NodeCapabilityAvailability.LocationEnabled,
      ),
      NodeCapabilitySpec(
        name = DeepSeekHarnessCapability.Photos.rawValue,
        availability = NodeCapabilityAvailability.PhotosAvailable,
      ),
      NodeCapabilitySpec(name = DeepSeekHarnessCapability.Contacts.rawValue),
      NodeCapabilitySpec(name = DeepSeekHarnessCapability.Calendar.rawValue),
      NodeCapabilitySpec(
        name = DeepSeekHarnessCapability.Motion.rawValue,
        availability = NodeCapabilityAvailability.MotionAvailable,
      ),
      NodeCapabilitySpec(
        name = DeepSeekHarnessCapability.CallLog.rawValue,
        availability = NodeCapabilityAvailability.CallLogAvailable,
      ),
      NodeCapabilitySpec(
        name = DeepSeekHarnessCapability.VoiceWake.rawValue,
        availability = NodeCapabilityAvailability.VoiceWakeEnabled,
      ),
      NodeCapabilitySpec(
        name = DeepSeekHarnessCapability.MobileUI.rawValue,
        availability = NodeCapabilityAvailability.MobileUiAvailable,
      ),
    )

  /** Complete Android node command catalog before runtime availability filtering. */
  val all: List<InvokeCommandSpec> =
    listOf(
      InvokeCommandSpec(
        name = DeepSeekHarnessCanvasCommand.Present.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCanvasCommand.Hide.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCanvasCommand.Navigate.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCanvasCommand.Eval.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCanvasCommand.Snapshot.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCanvasA2UICommand.Push.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCanvasA2UICommand.PushJSONL.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCanvasA2UICommand.Reset.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessSystemCommand.Notify.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessTalkCommand.PttStart.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessTalkCommand.PttStop.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessTalkCommand.PttCancel.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessTalkCommand.PttOnce.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCameraCommand.List.rawValue,
        requiresForeground = true,
        availability = InvokeCommandAvailability.CameraEnabled,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCameraCommand.Snap.rawValue,
        requiresForeground = true,
        availability = InvokeCommandAvailability.CameraEnabled,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCameraCommand.Clip.rawValue,
        requiresForeground = true,
        availability = InvokeCommandAvailability.CameraEnabled,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessLocationCommand.Get.rawValue,
        availability = InvokeCommandAvailability.LocationEnabled,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessDeviceCommand.Status.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessDeviceCommand.Info.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessDeviceCommand.Permissions.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessDeviceCommand.Health.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessDeviceCommand.Apps.rawValue,
        availability = InvokeCommandAvailability.InstalledAppsSharingEnabled,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessNotificationsCommand.List.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessNotificationsCommand.Actions.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessPhotosCommand.Latest.rawValue,
        availability = InvokeCommandAvailability.PhotosAvailable,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessContactsCommand.Search.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessContactsCommand.Add.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCalendarCommand.Events.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCalendarCommand.Add.rawValue,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessMotionCommand.Activity.rawValue,
        availability = InvokeCommandAvailability.MotionActivityAvailable,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessMotionCommand.Pedometer.rawValue,
        availability = InvokeCommandAvailability.MotionPedometerAvailable,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessSmsCommand.Send.rawValue,
        availability = InvokeCommandAvailability.SendSmsAvailable,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessSmsCommand.Search.rawValue,
        availability = InvokeCommandAvailability.RequestableSmsSearchAvailable,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessCallLogCommand.Search.rawValue,
        availability = InvokeCommandAvailability.CallLogAvailable,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessMobileUiCommand.Observe.rawValue,
        availability = InvokeCommandAvailability.MobileUiAvailable,
      ),
      InvokeCommandSpec(
        name = DeepSeekHarnessMobileUiCommand.Act.rawValue,
        availability = InvokeCommandAvailability.MobileUiAvailable,
      ),
      InvokeCommandSpec(
        name = "debug.logs",
        availability = InvokeCommandAvailability.DebugBuild,
      ),
      InvokeCommandSpec(
        name = "debug.ed25519",
        availability = InvokeCommandAvailability.DebugBuild,
      ),
    )

  private val byNameInternal: Map<String, InvokeCommandSpec> = all.associateBy { it.name }

  /** Finds the command metadata used by dispatch and advertised-method builders. */
  fun find(command: String): InvokeCommandSpec? = byNameInternal[command]

  /** Returns gateway capability ids the current Android device can actually serve. */
  fun advertisedCapabilities(flags: NodeRuntimeFlags): List<String> =
    capabilityManifest
      .filter { spec ->
        when (spec.availability) {
          NodeCapabilityAvailability.Always -> true
          NodeCapabilityAvailability.CameraEnabled -> flags.cameraEnabled
          NodeCapabilityAvailability.LocationEnabled -> flags.locationEnabled
          NodeCapabilityAvailability.SmsAvailable -> flags.sendSmsAvailable || flags.readSmsAvailable
          NodeCapabilityAvailability.CallLogAvailable -> flags.callLogAvailable
          NodeCapabilityAvailability.PhotosAvailable -> flags.photosAvailable
          NodeCapabilityAvailability.MotionAvailable -> flags.motionActivityAvailable || flags.motionPedometerAvailable
          NodeCapabilityAvailability.VoiceWakeEnabled -> flags.voiceWakeEnabled
          NodeCapabilityAvailability.MobileUiAvailable -> flags.mobileUiAvailable
        }
      }.map { it.name }

  /** Returns gateway invoke method ids available under current permissions/build flags. */
  fun advertisedCommands(flags: NodeRuntimeFlags): List<String> =
    all
      .filter { spec ->
        when (spec.availability) {
          InvokeCommandAvailability.Always -> true
          InvokeCommandAvailability.CameraEnabled -> flags.cameraEnabled
          InvokeCommandAvailability.LocationEnabled -> flags.locationEnabled
          InvokeCommandAvailability.SendSmsAvailable -> flags.sendSmsAvailable
          InvokeCommandAvailability.ReadSmsAvailable -> flags.readSmsAvailable
          InvokeCommandAvailability.RequestableSmsSearchAvailable -> flags.smsSearchPossible
          InvokeCommandAvailability.CallLogAvailable -> flags.callLogAvailable
          InvokeCommandAvailability.PhotosAvailable -> flags.photosAvailable
          InvokeCommandAvailability.MotionActivityAvailable -> flags.motionActivityAvailable
          InvokeCommandAvailability.MotionPedometerAvailable -> flags.motionPedometerAvailable
          InvokeCommandAvailability.InstalledAppsSharingEnabled -> flags.installedAppsSharingEnabled
          InvokeCommandAvailability.DebugBuild -> flags.debugBuild
          InvokeCommandAvailability.MobileUiAvailable -> flags.mobileUiAvailable
        }
      }.map { it.name }
}
