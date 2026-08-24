package ai.deepseek.harness.dsh

import android.content.Context
import android.webkit.CookieManager
import ai.deepseek.harness.NodeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/** DSH 服务端 RPC 错误（code 对齐上游 rpc.schema.ts 判别表）。 */
class DshApiException(val code: String, message: String) : Exception(message)

internal fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
internal fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

/** 结构异常（合法 JSON 但不是预期对象）归一化为协议错误，而不是裸 ClassCastException。 */
internal fun JsonElement.expectObj(): JsonObject =
  this as? JsonObject ?: throw DshApiException("bad-response", "响应字段结构异常")

data class DshModelOption(
  val providerId: String,
  val providerName: String,
  val modelId: String,
  val modelName: String,
  val description: String?,
)

data class DshModelsSnapshot(
  val currentProvider: String,
  val currentModel: String,
  val routable: Boolean,
  val options: List<DshModelOption>,
  val failures: List<String>,
)

data class DshPluginEntry(
  val name: String,
  val enabled: Boolean,
  val phase: String?,
)

data class DshPresetEntry(
  val id: String,
  val name: String,
  val description: String?,
  val isDefault: Boolean,
  val trust: String,
  val broken: String?,
)

/**
 * DeepSeek Harness 服务端 API 客户端。
 *
 * 协议：POST {server}/api/{method}，报文 {type:"client-request", rpcId, method, payload}，
 * 响应 {type:"server-response", rpcId, result:{ok, value | error}}。
 * 鉴权：nginx 层校验 WebView CookieManager 里的 dsh_session。
 */
class DshRepo(context: Context) {
  private val appContext = context.applicationContext
  private val prefs get() = (appContext as NodeApp).prefs
  private val http = OkHttpClient()
  private val json = Json { ignoreUnknownKeys = true }

  /** 串行化 session.list/create 解析，防止并发首次调用各自创建重复会话。 */
  private val sessionMutex = Mutex()

  @Volatile
  private var cachedSessionId: String? = null

  /** 登出 / 切换服务器时必须调用：作废进程内缓存的 sessionId。 */
  fun invalidate() {
    cachedSessionId = null
  }

  private suspend fun call(method: String, payload: JsonObject = JsonObject(emptyMap())): JsonElement =
    withContext(Dispatchers.IO) {
      val base = prefs.serverUrl.value.trimEnd('/')
      require(base.isNotEmpty()) { "server url is empty" }
      val url = "$base/api/$method"
      val body = buildJsonObject {
        put("type", "client-request")
        put("rpcId", UUID.randomUUID().toString())
        put("method", method)
        put("payload", payload)
      }
      val req = Request.Builder().url(url)
        .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        .header("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
        .build()
      http.newCall(req).execute().use { resp ->
        val text = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw DshApiException("transport", "HTTP ${resp.code}")
        val root = runCatching { json.parseToJsonElement(text).expectObj() }
          .getOrElse { throw DshApiException("bad-response", "响应解析失败") }
        val result = root.obj("result") ?: throw DshApiException("bad-response", "响应缺少 result")
        if (result.bool("ok") != true) {
          val err = result.obj("error")
          throw DshApiException(err?.str("code") ?: "unknown", err?.str("message") ?: "请求失败")
        }
        result["value"] ?: JsonNull
      }
    }

  /**
   * 解析目标会话 id：session.list 按 updatedAt 降序返回；优先复用最近的非空白会话，
   * 全部空白取第一个，没有任何会话时创建新会话。全程持锁，避免并发重复建会话。
   */
  suspend fun resolveSessionId(): String = sessionMutex.withLock {
    cachedSessionId?.let { return it }
    val items = call("session.list").expectObj().arr("items").orEmpty()
    val summaries = items.mapNotNull { it as? JsonObject }
    val chosen = summaries.firstOrNull { it.bool("blank") == false }?.str("sessionId")
      ?: summaries.firstOrNull()?.str("sessionId")
    if (chosen != null) {
      cachedSessionId = chosen
      return chosen
    }
    val created = call("session.create").expectObj()
    val id = created.str("sessionId") ?: throw DshApiException("bad-response", "创建会话失败")
    cachedSessionId = id
    return id
  }

  suspend fun models(): DshModelsSnapshot {
    val sessionId = resolveSessionId()
    val v = call("session.models", buildJsonObject { put("sessionId", sessionId) }).expectObj()
    return parseModelsSnapshot(v)
  }

  suspend fun selectModel(providerId: String, modelId: String) {
    val sessionId = resolveSessionId()
    call(
      "session.selectModel",
      buildJsonObject {
        put("sessionId", sessionId)
        put("provider", providerId)
        put("model", modelId)
      },
    )
  }

  suspend fun plugins(): List<DshPluginEntry> =
    call("pluginInventory.list").expectObj().arr("entries").orEmpty()
      .mapNotNull { it as? JsonObject }
      .map { o ->
        DshPluginEntry(
          name = o.str("moduleName") ?: o.str("entryId").orEmpty(),
          enabled = o.bool("enabled") == true,
          phase = o.str("fiberPhase"),
        )
      }

  suspend fun presets(): List<DshPresetEntry> =
    call("agentPreset.list").expectObj().arr("presets").orEmpty()
      .mapNotNull { it as? JsonObject }
      .map { o ->
        DshPresetEntry(
          id = o.str("id").orEmpty(),
          name = o.str("name") ?: o.str("id").orEmpty(),
          description = o.str("description"),
          isDefault = o.bool("isDefault") == true,
          trust = o.str("trust") ?: "system",
          broken = o.str("broken"),
        )
      }

  /** 主题偏好同步到服务端 ui-theme 命名空间，网页端外观跟随 App 选择。 */
  suspend fun updateThemePreference(preference: String) {
    call(
      "settings.update",
      buildJsonObject {
        put("ns", "ui-theme")
        put("patch", buildJsonObject { put("preference", preference) })
      },
    )
  }

  /** 把某个预设设为部署默认（新会话无预设时使用），与网页端 settings.update 同路径。 */
  suspend fun setDefaultPreset(presetId: String) {
    call(
      "settings.update",
      buildJsonObject {
        put("ns", "agent-presets")
        put("patch", buildJsonObject { put("default", presetId) })
      },
    )
  }

  /** 轻量连通性探测：HEAD 服务器根地址，能拿到任意 HTTP 响应即视为在线；网络异常为离线。 */
  suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
    val base = prefs.serverUrl.value.trimEnd('/')
    if (base.isEmpty()) return@withContext false
    try {
      val req = Request.Builder().url(base).method("HEAD", null).build()
      http.newCall(req).execute().use { resp -> resp.code in 200..599 }
    } catch (e: Exception) {
      false
    }
  }
}

/** 纯解析逻辑，供 JVM 单测直接覆盖（不触网、不依赖 Android）。 */
internal fun parseModelsSnapshot(v: JsonObject): DshModelsSnapshot {
  val current = v.obj("current")
  val options = mutableListOf<DshModelOption>()
  for (group in v.arr("groups").orEmpty()) {
    val g = group as? JsonObject ?: continue
    val pid = g.str("id").orEmpty()
    val pname = g.str("name") ?: pid
    for (m in g.arr("models").orEmpty()) {
      val mm = m as? JsonObject ?: continue
      options.add(
        DshModelOption(
          providerId = pid,
          providerName = pname,
          modelId = mm.str("id").orEmpty(),
          modelName = mm.str("name") ?: mm.str("id").orEmpty(),
          description = mm.str("description"),
        ),
      )
    }
  }
  val failures = v.arr("failures").orEmpty().mapNotNull { (it as? JsonObject)?.str("message") }
  return DshModelsSnapshot(
    currentProvider = current?.str("provider").orEmpty(),
    currentModel = current?.str("model").orEmpty(),
    routable = v.bool("routable") != false,
    options = options,
    failures = failures,
  )
}
