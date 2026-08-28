/**
 * DeepSeek Harness Android 更新检查引擎。
 *
 * 多路径可达（国内 gh-proxy 代理 → 直连兜底），只比 tag_name vs 当前版本，
 * 不依赖 APK 文件名格式。逻辑与 Synapse app_update_core.dart 对齐。
 */
package ai.deepseek.harness.dsh

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

/** 仓库配置。 */
data class AppUpdateConfig(
  val owner: String,
  val repo: String,
  val proxyPrefixes: List<String> = listOf("https://gh-proxy.com/", ""),
  val useMetaFallback: Boolean = false,
  val metaBranch: String = "meta",
  /** 服务器代理地址（首选数据源：手机已连上 DSH 服务器，服务器代查 GitHub 比手机直连靠谱）。 */
  val serverUrl: String? = null,
  val serverUpdatePath: String = "/api/update/check",
  val serverDownloadPath: String = "/api/update/download",
) {
  val apiLatestUrl: String get() = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest"
  val apiListUrl: String get() = "https://api.github.com/repos/" + owner + "/" + repo + "/releases"
  val metaUrl: String get() = "https://cdn.jsdelivr.net/gh/" + owner + "/" + repo + "@" + metaBranch + "/version.json"
  val releasePageUrl: String get() = "https://github.com/" + owner + "/" + repo + "/releases/latest"
  fun releaseTagUrl(tag: String): String = "https://github.com/" + owner + "/" + repo + "/releases/tag/" + tag
}

/** 检查结果。 */
data class AppUpdateResult(
  val tagName: String,
  val latestVersion: String,
  val releaseName: String,
  val releaseUrl: String,
  val releaseNotes: String?,
  val isCritical: Boolean,
  val hasUpdate: Boolean,
  val source: String,
  val versionCode: Int,
  val apkAssetName: String?,
  val apkDownloadUrl: String?,
)

/** JsonObject 快捷访问（与 DshRepo.kt 对齐）。 */
private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.content
private fun JsonElement.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.optString(key: String): String? = this[key]?.str()
private fun JsonObject.optBool(key: String): Boolean? = this[key]?.bool()
private fun JsonObject.optArray(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonObject.optObject(key: String): JsonObject? = this[key] as? JsonObject

class AppUpdateCore(val config: AppUpdateConfig) {

  companion object {
    private val json = Json { ignoreUnknownKeys = true }
    private val apiHeaders = mapOf("User-Agent" to "dsh-android", "Accept" to "application/vnd.github.v3+json")
    private val metaHeaders = mapOf("User-Agent" to "dsh-android", "Accept" to "application/json")

    /** 版本比较。>0 = a 比 b 新，0 = 相同，<0 = a 更旧。 */
    fun compareVersions(a: String, b: String): Int {
      fun release(v: String): List<Int> {
        var s = v.trim().removePrefix("v").removePrefix("V")
        val plus = s.indexOf('+')
        val dash = s.indexOf('-', 1)
        val cut = listOf(plus.takeIf { it >= 0 }, dash.takeIf { it >= 0 })
          .filterNotNull().minOrNull()
        if (cut != null) s = s.substring(0, cut)
        return s.split(".").map { it.toIntOrNull() ?: 0 }
      }
      val left = release(a)
      val right = release(b)
      val len = maxOf(left.size, right.size)
      for (i in 0 until len) {
        val av = left.getOrElse(i) { 0 }
        val bv = right.getOrElse(i) { 0 }
        if (av != bv) return av - bv
      }
      return 0
    }

    /** Release 正文首非空行含 **P0** / **critical** → 重要更新。 */
    fun isCritical(body: String): Boolean {
      val firstLine = body.split("\n").map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return false
      val lower = firstLine.lowercase()
      return lower.contains("**p0**") || lower.contains("**critical**")
    }
  }

  private data class Parsed(
    val tagName: String,
    val latestVersion: String,
    val releaseName: String,
    val notes: String?,
    val isCritical: Boolean,
    val isPrerelease: Boolean,
    val versionCode: Int,
    val apkAssetName: String?,
    val apkDownloadUrl: String?,
  )

  /**
   * 检查更新。返回 null = 所有数据源都失败（调用方应提示"网络不可达"，
   * 不要误报"已是最新"）。
   */
  suspend fun check(http: OkHttpClient, currentVersion: String, channel: String = "stable"): AppUpdateResult? {
    val failures = mutableListOf<String>()
    val isBeta = channel == "beta"

    // ── 0) 服务器代理（首选：手机已连上 DSH 服务器，服务器代查 GitHub，比手机直连靠谱）──
    val server = config.serverUrl?.trimEnd('/')
    if (server != null && server.isNotEmpty()) {
      try {
        val url = "$server${config.serverUpdatePath}?channel=$channel"
        val resp = doGet(http, url, apiHeaders)
        if (resp.code == 200) {
          val body = resp.body?.string() ?: ""
          resp.close()
          val data = decodeJson(body) as? JsonObject
          if (data != null) {
            val tagName = data.optString("latest_version")
            if (tagName != null && tagName.isNotEmpty()) {
              val releaseNotes = data.optString("release_notes") ?: ""
              val releaseName = tagName
              val notes = releaseNotes
              val isCritical = isCritical(notes)
              val newer = compareVersions(tagName, currentVersion) > 0
              val releaseUrl = config.releaseTagUrl(tagName)
              val fullDownloadUrl = "$server${config.serverDownloadPath}"
              return AppUpdateResult(
                tagName = tagName,
                latestVersion = stripV(tagName),
                releaseName = releaseName,
                releaseUrl = releaseUrl,
                releaseNotes = notes,
                isCritical = isCritical,
                hasUpdate = newer,
                source = "server",
                versionCode = versionCodeFromTag(tagName),
                apkAssetName = null,
                apkDownloadUrl = fullDownloadUrl,
              )
            }
          }
        } else {
          failures.add("server " + url + " -> HTTP " + resp.code)
        }
        resp.close()
      } catch (e: Exception) {
        failures.add("server -> " + e.message)
      }
    }

    // ── 1) GitHub API：代理链 ──
    val base = if (isBeta) config.apiListUrl else config.apiLatestUrl
    for (prefix in config.proxyPrefixes) {
      val url = if (prefix.isEmpty()) base else prefix + base
      try {
        val resp = doGet(http, url, apiHeaders)
        if (resp.code != 200) {
          failures.add("api " + url + " -> HTTP " + resp.code)
          resp.close()
          continue
        }
        val body = resp.body?.string() ?: ""
        resp.close()
        val data = decodeJson(body) ?: continue
        if (isBeta && data is JsonArray) {
          for (e in data) {
            val obj = e as? JsonObject ?: continue
            if (obj.optBool("prerelease") == true) {
              val parsed = parseRelease(obj)
              if (parsed != null) return toResult(parsed, currentVersion, "github", channel)
              break
            }
          }
          failures.add("api " + url + " -> 无 prerelease")
        } else if (data is JsonObject) {
          val parsed = parseRelease(data)
          if (parsed != null) return toResult(parsed, currentVersion, "github", channel)
          failures.add("api " + url + " -> 解析失败")
        } else {
          failures.add("api " + url + " -> 非 JSON")
        }
      } catch (e: Exception) {
        failures.add("api " + url + " -> " + e.message)
      }
    }

    if (config.useMetaFallback && !isBeta) {
      val cacheBuster = System.currentTimeMillis()
      for (prefix in config.proxyPrefixes) {
        val url = prefix + config.metaUrl + "?_t=" + cacheBuster
        try {
          val resp = doGet(http, url, metaHeaders)
          if (resp.code != 200) {
            failures.add("meta " + url + " -> HTTP " + resp.code)
            resp.close()
            continue
          }
          val body = resp.body?.string() ?: ""
          resp.close()
          val data = decodeJson(body) as? JsonObject ?: continue
          if (data.optString("tag") != null && data.optString("versionCode") != null) {
            val parsed = parseMeta(data)
            if (parsed != null) return toResult(parsed, currentVersion, "meta", channel)
          }
          failures.add("meta " + url + " -> 解析失败")
        } catch (e: Exception) {
          failures.add("meta " + url + " -> " + e.message)
        }
      }
    }

    return null
  }

  private suspend fun doGet(http: OkHttpClient, url: String, headers: Map<String, String>): okhttp3.Response {
    val req = Request.Builder().url(url).get().apply {
      headers.forEach { (k, v) -> addHeader(k, v) }
      addHeader("X-Request-Id", UUID.randomUUID().toString())
    }.build()
    return http.newCall(req).execute()
  }

  private fun toResult(p: Parsed, currentVersion: String, source: String, channel: String): AppUpdateResult {
    val newer = compareVersions(p.tagName, currentVersion) > 0
    val crossChannel = (channel == "beta" && !p.isPrerelease) ||
      (channel != "beta" && p.isPrerelease && !newer)
    return AppUpdateResult(
      tagName = p.tagName,
      latestVersion = p.latestVersion,
      releaseName = p.releaseName,
      releaseUrl = config.releaseTagUrl(p.tagName),
      releaseNotes = p.notes,
      isCritical = p.isCritical,
      hasUpdate = newer && !crossChannel,
      source = source,
      versionCode = p.versionCode,
      apkAssetName = p.apkAssetName,
      apkDownloadUrl = p.apkDownloadUrl,
    )
  }

  private fun decodeJson(body: String): JsonElement? {
    val s = body.trim()
    if (s.isEmpty() || s.startsWith("<")) return null
    return runCatching { json.parseToJsonElement(s) }.getOrNull()
  }

  private fun parseRelease(json: JsonObject): Parsed? {
    val tagName = json.optString("tag_name") ?: return null
    if (tagName.isBlank()) return null

    var apkName: String? = null
    var apkUrl: String? = null
    val assets = json.optArray("assets")
    if (assets != null) {
      for (a in assets) {
        val obj = a as? JsonObject ?: continue
        val name = obj.optString("name") ?: continue
        if (!name.endsWith(".apk")) continue
        if (name.contains("arm64-v8a") || apkName == null) {
          apkName = name
          apkUrl = obj.optString("browser_download_url")
          if (name.contains("arm64-v8a")) break
        }
      }
    }

    val body = json.optString("body") ?: ""
    val name = json.optString("name")
    return Parsed(
      tagName = tagName,
      latestVersion = stripV(tagName),
      releaseName = name ?: tagName,
      notes = body,
      isCritical = isCritical(body),
      isPrerelease = json.optBool("prerelease") == true,
      versionCode = versionCodeFromTag(tagName),
      apkAssetName = apkName,
      apkDownloadUrl = apkUrl,
    )
  }

  private fun parseMeta(json: JsonObject): Parsed? {
    val tag = json.optString("tag") ?: return null
    if (tag.isBlank()) return null
    val code = json.optString("versionCode")?.toIntOrNull() ?: return null

    var apkUrl: String? = null
    val apks = json.optObject("apk")
    if (apks != null) {
      for (key in listOf("arm64-v8a", "armeabi-v7a", "x86_64")) {
        val url = apks.optString(key)
        if (url != null && url.isNotEmpty()) {
          apkUrl = url
          break
        }
      }
    }
    val apkName = apkUrl?.substringAfterLast('/')

    val body = json.optString("notes") ?: ""
    val name = json.optString("releaseName")
    return Parsed(
      tagName = tag,
      latestVersion = stripV(tag),
      releaseName = name ?: tag,
      notes = body,
      isCritical = json.optBool("critical") == true || isCritical(body),
      isPrerelease = false,
      versionCode = code,
      apkAssetName = apkName,
      apkDownloadUrl = apkUrl,
    )
  }

  private fun stripV(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

  private fun versionCodeFromTag(tag: String): Int {
    val segs = stripV(tag).split(".")
    for (i in segs.size - 1 downTo 0) {
      segs[i].trim().toIntOrNull()?.let { return it }
    }
    return 0
  }
}