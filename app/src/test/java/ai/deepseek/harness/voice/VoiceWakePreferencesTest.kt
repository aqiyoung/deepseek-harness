package ai.deepseek.harness.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceWakePreferencesTest {
  @Test
  fun sanitizeTrimsDropsEmptyAndUsesDefaults() {
    assertEquals(listOf("hello", "computer"), VoiceWakePreferences.sanitizeTriggerWords(listOf(" hello ", "", "computer")))
    assertEquals(VoiceWakePreferences.defaultTriggerWords, VoiceWakePreferences.sanitizeTriggerWords(emptyList()))
  }

  @Test
  fun sanitizePreservesPhrasePunctuationAndNewlines() {
    assertEquals(
      listOf("hey, dsh", "line\nbreak"),
      VoiceWakePreferences.sanitizeTriggerWords(listOf(" hey, dsh ", "line\nbreak")),
    )
  }

  @Test
  fun matcherRequiresWordBoundariesAndCommand() {
    assertNull(VoiceWakePhraseMatcher.match("redsh show status", listOf("dsh")))
    assertNull(VoiceWakePhraseMatcher.match("dsh", listOf("dsh")))
    assertNull(VoiceWakePhraseMatcher.match("tell dsh show status", listOf("dsh")))
    assertEquals(
      VoiceWakeMatch(trigger = "DeepSeekHarness", command = "show status"),
      VoiceWakePhraseMatcher.match("Hey DeepSeekHarness, show status", listOf("dsh")),
    )
  }

  @Test
  fun matcherUsesEarliestTrigger() {
    assertEquals(
      VoiceWakeMatch(trigger = "computer", command = "ask claude for status"),
      VoiceWakePhraseMatcher.match("computer ask claude for status", listOf("claude", "computer")),
    )
  }

  @Test
  fun matcherSupportsScriptsWithoutWhitespaceWordBoundaries() {
    assertEquals(
      VoiceWakeMatch(trigger = "小龙虾", command = "天气怎么样"),
      VoiceWakePhraseMatcher.match("小龙虾天气怎么样", listOf("小龙虾")),
    )
    assertEquals(
      VoiceWakeMatch(trigger = "โอเพนคลอ", command = "สภาพอากาศ"),
      VoiceWakePhraseMatcher.match("โอเพนคลอสภาพอากาศ", listOf("โอเพนคลอ")),
    )
  }

  @Test
  fun matcherNormalizesSpokenPunctuationAndWhitespace() {
    assertEquals(
      VoiceWakeMatch(trigger = "Hey DeepSeekHarness", command = "show status"),
      VoiceWakePhraseMatcher.match("Hey DeepSeekHarness show status", listOf("hey,\ndsh")),
    )
  }
}
