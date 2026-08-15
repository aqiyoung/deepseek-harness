package ai.deepseek.harness

import ai.deepseek.harness.gateway.GatewayErrorDetails
import ai.deepseek.harness.gateway.GatewaySession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillManagementTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun searchResultsKeepOnlyIdentifiedSkills() {
    val results =
      parseDshHubSearchResults(
        """{"results":[{"slug":" alpha ","installRef":"@alice/alpha","displayName":"Alpha","summary":"Useful","version":"1.2.3"},{"slug":"missing-name"},{"displayName":"Missing slug"}]}""",
        json,
      )

    assertEquals(
      listOf(
        GatewayDshHubSkillSummary(
          slug = "alpha",
          installRef = "@alice/alpha",
          displayName = "Alpha",
          summary = "Useful",
          version = "1.2.3",
        ),
      ),
      results,
    )
  }

  @Test
  fun sameSlugResultsKeepSeparatePublisherReferences() {
    val results =
      parseDshHubSearchResults(
        """{"results":[{"slug":"email","installRef":"@alice/email","displayName":"Email"},{"slug":"email","installRef":"@bob/email","displayName":"Email"},{"slug":"orphan","displayName":"Orphan"}]}""",
        json,
      )

    assertEquals(listOf("@alice/email", "@bob/email", "orphan"), results.map { it.reference })
  }

  @Test
  fun detailBindsExactVersionAndPublisherIdentity() {
    val review =
      parseDshHubInstallReview(
        """{"skill":{"displayName":"Alpha Skill","summary":"Reviewed metadata"},"latestVersion":{"version":"2.0.0"},"owner":{"displayName":"Alice","handle":"alice"}}""",
        GatewayDshHubSkillSummary("alpha", null, "Alpha", null, null),
        json,
      )

    assertEquals(
      GatewayDshHubInstallReview(
        slug = "@alice/alpha",
        displayName = "Alpha Skill",
        summary = "Reviewed metadata",
        version = "2.0.0",
        author = "Alice",
      ),
      review,
    )
  }

  @Test
  fun detailVersionWinsWhenSearchResultIsStale() {
    val review =
      parseDshHubInstallReview(
        """{"skill":{"displayName":"Alpha"},"latestVersion":{"version":"2.0.0"},"owner":{"handle":"alice"}}""",
        GatewayDshHubSkillSummary("alpha", null, "Alpha", null, "1.9.0"),
        json,
      )

    assertEquals("2.0.0", review?.version)
  }

  @Test
  fun detailFailsClosedWithoutAnInstallableVersion() {
    val review =
      parseDshHubInstallReview(
        """{"skill":{"displayName":"Alpha"},"owner":{"handle":"alice"}}""",
        GatewayDshHubSkillSummary("alpha", null, "Alpha", null, null),
        json,
      )

    assertNull(review)
  }

  @Test
  fun installParamsKeepRegistryAndTrustPolicyOnGateway() {
    val params = json.parseToJsonElement(dshHubInstallParams("alpha", "1.2.3", acknowledgeRisk = true)).jsonObject

    assertEquals(setOf("source", "slug", "version", "acknowledgeDshHubRisk", "timeoutMs"), params.keys)
    assertEquals("dshhub", params.getValue("source").jsonPrimitive.content)
    assertEquals("alpha", params.getValue("slug").jsonPrimitive.content)
    assertEquals("1.2.3", params.getValue("version").jsonPrimitive.content)
    assertTrue(params.getValue("acknowledgeDshHubRisk").jsonPrimitive.boolean)
    assertEquals(120_000, params.getValue("timeoutMs").jsonPrimitive.int)
  }

  @Test
  fun onlyStructuredReviewRequiredFailureOffersAcknowledgement() {
    val rejection =
      dshHubInstallRejection(
        GatewaySession.ErrorShape(
          code = "UNAVAILABLE",
          message = "review required",
          details =
            GatewayErrorDetails(
              code = null,
              canRetryWithDeviceToken = false,
              recommendedNextStep = null,
              dshhubTrustCode = "dshhub_risk_acknowledgement_required",
              dshhubWarning = "Scanner found elevated permissions.",
              dshhubVersion = "1.2.3",
            ),
        ),
        attemptedVersion = "1.2.3",
      )

    assertTrue(rejection.requiresAcknowledgement)
    assertEquals("1.2.3", rejection.acknowledgeVersion)
    assertEquals("Scanner found elevated permissions.", rejection.warning)
  }

  @Test
  fun changedGatewayVersionRequiresFreshReview() {
    val rejection =
      dshHubInstallRejection(
        GatewaySession.ErrorShape(
          code = "UNAVAILABLE",
          message = "review required",
          details =
            GatewayErrorDetails(
              code = null,
              canRetryWithDeviceToken = false,
              recommendedNextStep = null,
              dshhubTrustCode = "dshhub_risk_acknowledgement_required",
              dshhubWarning = "Scanner found elevated permissions.",
              dshhubVersion = "1.2.4",
            ),
        ),
        attemptedVersion = "1.2.3",
      )

    assertFalse(rejection.requiresAcknowledgement)
    assertNull(rejection.acknowledgeVersion)
    assertTrue(rejection.message.contains("different DshHub release"))
  }

  @Test
  fun blockedFailureNeverOffersAcknowledgement() {
    val rejection =
      dshHubInstallRejection(
        GatewaySession.ErrorShape(
          code = "UNAVAILABLE",
          message = "download blocked",
          details =
            GatewayErrorDetails(
              code = null,
              canRetryWithDeviceToken = false,
              recommendedNextStep = null,
              dshhubTrustCode = "dshhub_download_blocked",
              dshhubWarning = "DshHub marked this release malicious.",
              dshhubVersion = "1.2.3",
            ),
        ),
        attemptedVersion = "1.2.3",
      )

    assertFalse(rejection.requiresAcknowledgement)
    assertNull(rejection.acknowledgeVersion)
  }

  @Test
  fun unknownInstallReadbackUsesDshHubProvenanceSlug() {
    val skill =
      GatewaySkillSummary(
        skillKey = "custom-frontmatter-key",
        name = "Custom display name",
        description = null,
        source = "dsh-managed",
        emoji = null,
        disabled = false,
        eligible = true,
        blockedByAllowlist = false,
        blockedByAgentFilter = false,
        bundled = false,
        missingCount = 0,
        installCount = 0,
        dshHubSlug = "registry-slug",
        dshHubValid = true,
        dshHubOwnerHandle = "registry-owner",
        dshHubInstalledVersion = "1.2.3",
      )

    assertTrue(isDshHubSkillInstalled(listOf(skill), "registry-slug", "1.2.3"))
    assertTrue(isDshHubSkillInstalled(listOf(skill), "registry-slug"))
    assertTrue(isDshHubSkillInstalled(listOf(skill), "@registry-owner/registry-slug", "1.2.3"))
    assertFalse(isDshHubSkillInstalled(listOf(skill), "@other-owner/registry-slug", "1.2.3"))
    assertFalse(isDshHubSkillInstalled(listOf(skill), "registry-slug", "1.2.4"))
    assertFalse(isDshHubSkillInstalled(listOf(skill.copy(dshHubValid = false)), "registry-slug", "1.2.3"))
    assertFalse(isDshHubSkillInstalled(listOf(skill), "custom-frontmatter-key", "1.2.3"))
  }

  @Test
  fun ownerQualifiedInstallStaysActiveForBrowseSlug() {
    assertTrue(isDshHubSkillOperationActive(setOf("@registry-owner/registry-slug"), "registry-slug"))
    assertTrue(
      isDshHubSkillOperationActive(
        setOf("@registry-owner/registry-slug"),
        "@registry-owner/registry-slug",
      ),
    )
    assertFalse(
      isDshHubSkillOperationActive(
        setOf("@other-owner/registry-slug"),
        "@registry-owner/registry-slug",
      ),
    )
  }

  @Test
  fun dshHubManagementRequiresEveryAdvertisedMethod() {
    assertTrue(supportsDshHubSkillManagement(DSHHUB_SKILL_GATEWAY_METHODS))
    assertFalse(supportsDshHubSkillManagement(DSHHUB_SKILL_GATEWAY_METHODS - "skills.detail"))
  }
}
