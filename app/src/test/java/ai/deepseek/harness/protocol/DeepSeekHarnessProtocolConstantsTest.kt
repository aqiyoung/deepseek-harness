package ai.deepseek.harness.protocol

import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekHarnessProtocolConstantsTest {
  @Test
  fun generatedCapabilitiesAreUniqueProtocolIds() {
    val values = DeepSeekHarnessCapability.entries.map { it.rawValue }

    assertTrue(values.isNotEmpty())
    assertTrue(values.all { it.isNotBlank() && "." !in it })
    assertTrue(values.size == values.toSet().size)
  }

  @Test
  fun generatedCommandGroupsMatchTheirNamespaces() {
    val groups =
      listOf(
        DeepSeekHarnessCanvasCommand.NamespacePrefix to DeepSeekHarnessCanvasCommand.entries.map { it.rawValue },
        DeepSeekHarnessCanvasA2UICommand.NamespacePrefix to DeepSeekHarnessCanvasA2UICommand.entries.map { it.rawValue },
        DeepSeekHarnessCameraCommand.NamespacePrefix to DeepSeekHarnessCameraCommand.entries.map { it.rawValue },
        DeepSeekHarnessSmsCommand.NamespacePrefix to DeepSeekHarnessSmsCommand.entries.map { it.rawValue },
        DeepSeekHarnessTalkCommand.NamespacePrefix to DeepSeekHarnessTalkCommand.entries.map { it.rawValue },
        DeepSeekHarnessLocationCommand.NamespacePrefix to DeepSeekHarnessLocationCommand.entries.map { it.rawValue },
        DeepSeekHarnessDeviceCommand.NamespacePrefix to DeepSeekHarnessDeviceCommand.entries.map { it.rawValue },
        DeepSeekHarnessNotificationsCommand.NamespacePrefix to DeepSeekHarnessNotificationsCommand.entries.map { it.rawValue },
        DeepSeekHarnessSystemCommand.NamespacePrefix to DeepSeekHarnessSystemCommand.entries.map { it.rawValue },
        DeepSeekHarnessPhotosCommand.NamespacePrefix to DeepSeekHarnessPhotosCommand.entries.map { it.rawValue },
        DeepSeekHarnessContactsCommand.NamespacePrefix to DeepSeekHarnessContactsCommand.entries.map { it.rawValue },
        DeepSeekHarnessCalendarCommand.NamespacePrefix to DeepSeekHarnessCalendarCommand.entries.map { it.rawValue },
        DeepSeekHarnessMotionCommand.NamespacePrefix to DeepSeekHarnessMotionCommand.entries.map { it.rawValue },
        DeepSeekHarnessCallLogCommand.NamespacePrefix to DeepSeekHarnessCallLogCommand.entries.map { it.rawValue },
      )

    val commands = groups.flatMap { (prefix, values) -> values.onEach { assertTrue(it.startsWith(prefix)) } }
    assertTrue(commands.size == commands.toSet().size)
  }
}
