package ai.deepseek.harness.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatWorkingIndicatorTest {
  @Test
  fun stanceSelectionIsDeterministicForRunAndSalt() {
    val first = pickWorkingDshStance("run-123", salt = 42)

    repeat(20) {
      assertEquals(first, pickWorkingDshStance("run-123", salt = 42))
    }
  }

  @Test
  fun stanceSelectionUsesConfiguredWeights() {
    val key = "run-weight-check"
    val hash = workingDshHash(key)
    val counts = mutableMapOf<WorkingDshStance, Int>()

    repeat(1_000) { bucket ->
      val stance = pickWorkingDshStance(key, salt = hash xor bucket)
      counts[stance] = counts.getOrDefault(stance, 0) + 1
    }

    assertEquals(1_000, counts.values.sum())
    assertEquals(WorkingDshStance.entries.toSet(), counts.keys)
    assertEquals(550, counts[WorkingDshStance.Default])
    assertEquals(180, counts[WorkingDshStance.Southpaw])
    assertEquals(50, counts[WorkingDshStance.Flurry])
    assertEquals(40, counts[WorkingDshStance.Spin])
    assertEquals(30, counts[WorkingDshStance.Shadowbox])
    assertEquals(20, counts[WorkingDshStance.Backflip])
    assertEquals(20, counts[WorkingDshStance.Zen])
    assertEquals(20, counts[WorkingDshStance.Drummer])
    assertEquals(20, counts[WorkingDshStance.Peekaboo])
    assertEquals(20, counts[WorkingDshStance.NodOff])
    assertEquals(20, counts[WorkingDshStance.Curious])
    assertEquals(20, counts[WorkingDshStance.OmNom])
    assertEquals(10, counts[WorkingDshStance.FakeOut])
  }

  @Test
  fun newStancesUseSpecifiedCyclesAndKeyframePoses() {
    assertEquals(6_000L, workingDshCycleMs(WorkingDshStance.Zen))
    assertPose(
      workingDshPose(WorkingDshStance.Zen, 0.30f),
      scale = 1.08f,
      jawRotation = -10f,
    )
    assertPose(
      workingDshPose(WorkingDshStance.Zen, 0.70f),
      jawRotation = -24f,
    )
    assertPose(
      workingDshPose(WorkingDshStance.Zen, 0.76f),
      scale = 1f,
      jawRotation = 2f,
    )

    assertEquals(1_200L, workingDshCycleMs(WorkingDshStance.Drummer))
    assertEquals(-20f, workingDshPose(WorkingDshStance.Drummer, 0.10f).jawRotation, 0.001f)
    assertPose(
      workingDshPose(WorkingDshStance.Drummer, 0.15f),
      rotationZ = -8f,
      jawRotation = 2f,
    )
    assertEquals(-20f, workingDshPose(WorkingDshStance.Drummer, 0.50f).jawRotation, 0.001f)
    assertPose(
      workingDshPose(WorkingDshStance.Drummer, 0.55f),
      rotationZ = 8f,
      jawRotation = 2f,
    )

    assertEquals(2_400L, workingDshCycleMs(WorkingDshStance.Peekaboo))
    assertPose(
      workingDshPose(WorkingDshStance.Peekaboo, 0.62f),
      translationYDp = 5f,
      scale = 0.72f,
      jawRotation = -2f,
    )
    assertPose(
      workingDshPose(WorkingDshStance.Peekaboo, 0.78f),
      translationYDp = -1.5f,
      scale = 1.06f,
      jawRotation = -28f,
    )

    assertEquals(3_600L, workingDshCycleMs(WorkingDshStance.NodOff))
    assertPose(
      workingDshPose(WorkingDshStance.NodOff, 0.64f),
      rotationZ = -3f,
      translationYDp = -0.5f,
      jawRotation = -6f,
    )

    assertEquals(2_400L, workingDshCycleMs(WorkingDshStance.Curious))
    assertPose(
      workingDshPose(WorkingDshStance.Curious, 0.40f),
      rotationZ = -14f,
      jawRotation = -16f,
    )

    assertEquals(2_400L, workingDshCycleMs(WorkingDshStance.OmNom))
    assertPose(
      workingDshPose(WorkingDshStance.OmNom, 0.36f),
      translationXDp = 2.5f,
      jawRotation = 8f,
    )

    assertEquals(2_400L, workingDshCycleMs(WorkingDshStance.FakeOut))
    assertPose(
      workingDshPose(WorkingDshStance.FakeOut, 0.58f),
      rotationZ = 3f,
      jawRotation = 4f,
    )
  }

  @Test
  fun phraseStrideVisitsEveryPhraseWithoutAdjacentRepeats() {
    val indexes = (0L until 19L).map { bucket -> workingPhraseIndex("run-phrase", bucket) }

    assertEquals(19, indexes.toSet().size)
    indexes.zipWithNext().forEach { (previous, next) -> assertNotEquals(previous, next) }
    assertNotEquals(indexes.last(), workingPhraseIndex("run-phrase", 19L))
  }

  @Test
  fun phraseWaitsThirtySecondsAndRotatesEveryFortyFive() {
    assertEquals(null, workingPhraseIndexForElapsed("run-phrase", WORKING_PHRASE_SHOW_AFTER_MS - 1L))
    val first = workingPhraseIndexForElapsed("run-phrase", WORKING_PHRASE_SHOW_AFTER_MS)
    assertEquals(first, workingPhraseIndexForElapsed("run-phrase", WORKING_PHRASE_SHOW_AFTER_MS + 44_999L))
    assertNotEquals(first, workingPhraseIndexForElapsed("run-phrase", WORKING_PHRASE_SHOW_AFTER_MS + 45_000L))
  }

  @Test
  fun compactDurationClampsToOneSecond() {
    assertEquals("1s", formatChatDurationCompact(0L))
    assertEquals("1m 30s", formatChatDurationCompact(90_000L))
    assertTrue(formatChatDurationCompact(3_600_000L).startsWith("1h"))
  }

  @Test
  fun ackRekeyKeepsOptimisticClockAndLocalStart() {
    val tracker = ChatWorkingRunTracker("agent:main:main")
    val provisional =
      requireNotNull(
        tracker.resolve(
          indicatorVisible = true,
          clockKey = "message-1",
          authoritativeRunId = "client-run",
          nowElapsedMs = 5_000L,
          outputTokens = null,
        ),
      )
    val authoritative =
      requireNotNull(
        tracker.resolve(
          indicatorVisible = true,
          clockKey = "message-1",
          authoritativeRunId = "server-run",
          nowElapsedMs = 8_000L,
          outputTokens = 40L,
        ),
      )

    assertEquals(provisional.clockKey, authoritative.clockKey)
    assertEquals(5_000L, authoritative.observedAtElapsedMs)
    assertEquals("server-run", authoritative.authoritativeRunId)
    assertEquals(40L, authoritative.outputTokens)
  }

  @Test
  fun serverRunReplacementGetsANewClock() {
    val tracker = ChatWorkingRunTracker("agent:main:main")
    val first =
      requireNotNull(
        tracker.resolve(true, "run-1", "run-1", 7_000L, null),
      )
    val replacement =
      requireNotNull(
        tracker.resolve(true, "run-2", "run-2", 9_000L, null),
      )

    assertEquals("run-1", first.clockKey)
    assertEquals("run-2", replacement.clockKey)
    assertEquals(9_000L, replacement.observedAtElapsedMs)
  }

  private fun assertPose(
    pose: WorkingDshPose,
    rotationZ: Float = 0f,
    translationXDp: Float = 0f,
    translationYDp: Float = 0f,
    scale: Float = 1f,
    jawRotation: Float = -10f,
  ) {
    assertEquals(rotationZ, pose.rotationZ, 0.001f)
    assertEquals(0f, pose.rotationY, 0.001f)
    assertEquals(translationXDp, pose.translationXDp, 0.001f)
    assertEquals(translationYDp, pose.translationYDp, 0.001f)
    assertEquals(scale, pose.scale, 0.001f)
    assertEquals(jawRotation, pose.jawRotation, 0.001f)
  }
}
