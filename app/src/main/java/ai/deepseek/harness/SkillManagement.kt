package ai.deepseek.harness

import ai.deepseek.harness.gateway.GatewaySession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

private const val DSHHUB_RISK_ACKNOWLEDGEMENT_REQUIRED = "dshhub_risk_acknowledgement_required"
internal const val DSHHUB_INSTALL_REQUEST_TIMEOUT_MS = 125_000L
internal const val DSHHUB_SKILL_GATEWAY_UNAVAILABLE = "Update the Gateway to search and install DeepSeek Harness Hub skills from Android."
internal val DSHHUB_SKILL_GATEWAY_METHODS = setOf("skills.search", "skills.detail", "skills.install")

data class GatewayDshHubSkillSearchState(
  val query: String = "",
  val searching: Boolean = false,
  val results: List<GatewayDshHubSkillSummary> = emptyList(),
  val reviewingSlug: String? = null,
  val installReview: GatewayDshHubInstallReview? = null,
  val installingSlugs: Set<String> = emptySet(),
  val acknowledgeSlug: String? = null,
  val acknowledgeVersion: String? = null,
  val errorText: String? = null,
  val messageText: String? = null,
)

data class GatewayDshHubSkillSummary(
  val slug: String,
  val installRef: String?,
  val displayName: String,
  val summary: String?,
  val version: String?,
) {
  /**
   * Several publishers can share one slug, so the Gateway-supplied reference is what identifies a
   * result, what distinguishes rows, and what detail and install must send back.
   */
  val reference: String
    get() = installRef?.trim()?.takeIf(String::isNotEmpty) ?: slug
}

data class GatewayDshHubInstallReview(
  val slug: String,
  val displayName: String,
  val summary: String?,
  val version: String,
  val author: String,
)

internal data class GatewayDshHubInstallRejection(
  val message: String,
  val warning: String?,
  val acknowledgeVersion: String?,
  val requiresAcknowledgement: Boolean,
)

internal fun parseDshHubSearchResults(
  raw: String,
  json: Json,
): List<GatewayDshHubSkillSummary> {
  val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
  return (root["results"] as? JsonArray)
    ?.mapNotNull { item ->
      val value = item as? JsonObject ?: return@mapNotNull null
      val slug = value.string("slug") ?: return@mapNotNull null
      val displayName = value.string("displayName") ?: return@mapNotNull null
      GatewayDshHubSkillSummary(
        slug = slug,
        installRef = value.string("installRef"),
        displayName = displayName,
        summary = value.string("summary"),
        version = value.string("version"),
      )
    }.orEmpty()
}

internal fun parseDshHubInstallReview(
  raw: String,
  fallback: GatewayDshHubSkillSummary,
  json: Json,
): GatewayDshHubInstallReview? {
  val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
  val skill = root["skill"] as? JsonObject
  val latestVersion = root["latestVersion"] as? JsonObject
  val owner = root["owner"] as? JsonObject
  // The detail response is the install review boundary. Prefer its current
  // version over the potentially stale search result shown before review.
  val version = latestVersion?.string("version") ?: fallback.version ?: return null
  val ownerDisplayName = owner?.string("displayName")
  val ownerHandle = owner?.string("handle")
  val reviewedSlug =
    canonicalDshHubSkillReference(
      slug = skill?.string("slug") ?: fallback.slug,
      ownerHandle = ownerHandle,
    ) ?: return null
  val author =
    when {
      ownerDisplayName != null && ownerHandle != null && !ownerDisplayName.equals(ownerHandle, ignoreCase = true) ->
        "$ownerDisplayName (@$ownerHandle)"
      ownerDisplayName != null -> ownerDisplayName
      ownerHandle != null -> "@$ownerHandle"
      else -> "Unknown publisher"
    }
  return GatewayDshHubInstallReview(
    slug = reviewedSlug,
    displayName = skill?.string("displayName") ?: fallback.displayName,
    summary = skill?.string("summary") ?: fallback.summary,
    version = version,
    author = author,
  )
}

internal fun dshHubInstallRejection(
  error: GatewaySession.ErrorShape,
  attemptedVersion: String?,
): GatewayDshHubInstallRejection {
  val details = error.details
  val reviewedVersion = attemptedVersion?.trim()?.takeIf(String::isNotEmpty)
  val gatewayVersion = details?.dshhubVersion?.trim()?.takeIf(String::isNotEmpty)
  val acknowledgementRequested =
    details?.dshhubTrustCode == DSHHUB_RISK_ACKNOWLEDGEMENT_REQUIRED
  val requiresAcknowledgement =
    acknowledgementRequested && reviewedVersion != null && gatewayVersion == reviewedVersion
  return GatewayDshHubInstallRejection(
    message =
      if (acknowledgementRequested && !requiresAcknowledgement) {
        "The Gateway evaluated a different DshHub release. Review the skill again before installing."
      } else {
        error.message.ifBlank { "The Gateway rejected this DshHub install." }
      },
    warning = details?.dshhubWarning?.trim()?.takeIf(String::isNotEmpty),
    acknowledgeVersion = reviewedVersion.takeIf { requiresAcknowledgement },
    requiresAcknowledgement = requiresAcknowledgement,
  )
}

internal fun supportsDshHubSkillManagement(methods: Set<String>): Boolean = methods.containsAll(DSHHUB_SKILL_GATEWAY_METHODS)

internal fun dshHubSearchParams(query: String): String =
  buildJsonObject {
    query.trim().takeIf(String::isNotEmpty)?.let { put("query", JsonPrimitive(it)) }
    put("limit", JsonPrimitive(25))
  }.toString()

internal fun dshHubDetailParams(slug: String): String = buildJsonObject { put("slug", JsonPrimitive(slug)) }.toString()

internal fun dshHubInstallParams(
  slug: String,
  version: String?,
  acknowledgeRisk: Boolean,
): String =
  buildJsonObject {
    put("source", JsonPrimitive("dshhub"))
    put("slug", JsonPrimitive(slug))
    version?.trim()?.takeIf(String::isNotEmpty)?.let { put("version", JsonPrimitive(it)) }
    if (acknowledgeRisk) put("acknowledgeDshHubRisk", JsonPrimitive(true))
    put("timeoutMs", JsonPrimitive(120_000))
  }.toString()

internal fun skillEnabledParams(
  skillKey: String,
  enabled: Boolean,
): String =
  buildJsonObject {
    put("skillKey", JsonPrimitive(skillKey))
    put("enabled", JsonPrimitive(enabled))
  }.toString()

internal fun formatDshHubInstallMessage(
  message: String,
  warning: String?,
): String = if (warning.isNullOrBlank()) message else "$message\n\n$warning"

internal fun isDshHubSkillInstalled(
  skills: List<GatewaySkillSummary>,
  slug: String,
): Boolean {
  val reference = parseDshHubSkillReference(slug) ?: return false
  return skills.any { it.matchesDshHubReference(reference) }
}

internal fun isDshHubSkillInstalled(
  skills: List<GatewaySkillSummary>,
  slug: String,
  version: String,
): Boolean =
  parseDshHubSkillReference(slug)?.let { reference ->
    skills.any { it.matchesDshHubReference(reference) && it.dshHubInstalledVersion == version }
  } ?: false

internal fun isDshHubSkillOperationActive(
  activeSlugs: Set<String>,
  slug: String,
): Boolean {
  val reference = parseDshHubSkillReference(slug) ?: return false
  return activeSlugs.any { activeSlug ->
    val active = parseDshHubSkillReference(activeSlug) ?: return@any false
    active.slug.equals(reference.slug, ignoreCase = true) &&
      (
        active.ownerHandle == null ||
          reference.ownerHandle == null ||
          active.ownerHandle.equals(reference.ownerHandle, ignoreCase = true)
      )
  }
}

private data class DshHubSkillReference(
  val slug: String,
  val ownerHandle: String?,
)

private fun parseDshHubSkillReference(rawValue: String): DshHubSkillReference? {
  val value = rawValue.trim()
  if (value.isEmpty()) return null
  if (!value.startsWith("@")) return DshHubSkillReference(value, null)
  val parts = value.drop(1).split("/")
  if (parts.size != 2 || parts.any(String::isEmpty)) return null
  return DshHubSkillReference(slug = parts[1], ownerHandle = parts[0].lowercase())
}

private fun canonicalDshHubSkillReference(
  slug: String,
  ownerHandle: String?,
): String? {
  val reference = parseDshHubSkillReference(slug) ?: return null
  val owner = ownerHandle?.trim()?.takeIf(String::isNotEmpty)?.lowercase() ?: reference.ownerHandle
  return owner?.let { "@$it/${reference.slug}" } ?: reference.slug
}

private fun GatewaySkillSummary.matchesDshHubReference(reference: DshHubSkillReference): Boolean {
  if (!dshHubValid) return false
  val installedReference = dshHubSlug?.let(::parseDshHubSkillReference) ?: return false
  if (!installedReference.slug.equals(reference.slug, ignoreCase = true)) return false
  val requestedOwner = reference.ownerHandle ?: return true
  val installedOwner = installedReference.ownerHandle ?: dshHubOwnerHandle
  return installedOwner?.equals(requestedOwner, ignoreCase = true) == true
}

internal fun dshHubInstallOutcomeUnknownMessage(slug: String): String = "The result for $slug is unknown. Reconnect, refresh Skills, then retry; the Gateway safely joins a matching install that is still running."

private fun JsonObject.string(key: String): String? =
  (get(key) as? JsonPrimitive)
    ?.contentOrNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
