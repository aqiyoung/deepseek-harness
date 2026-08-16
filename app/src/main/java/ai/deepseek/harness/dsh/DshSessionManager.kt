package ai.deepseek.harness.dsh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * High-level DSH session manager: wraps [DshApiClient] (the verified current-harness protocol),
 * holds the auth cookie, opens the two downlink WebSockets, and exposes the real server data as
 * [StateFlow]s the UI can observe. Chat primitives ([prompt], [history], [cancel]) and the live
 * mux event stream ([sessionEvents]) are also provided so a native chat surface can be built on top.
 */
class DshSessionManager(private val scope: CoroutineScope) {
    data class SessionInfo(
        val sessionId: String,
        val title: String,
        val running: Boolean,
        val updatedAt: Long,
        val cwd: String?,
        val agentPreset: String?,
    )

    data class ProviderInfo(val id: String, val name: String, val active: Boolean)
    data class HostInfo(
        val version: String?,
        val cwd: String?,
        val provider: String?,
        val model: String?,
        val attachedSessions: Int,
    )
    data class PresetInfo(val id: String, val name: String, val isDefault: Boolean)
    data class WorkspaceInfo(val workspaceId: String, val path: String?)
    data class ModelGroup(val id: String, val name: String, val models: List<ModelInfo>)
    data class ModelInfo(val id: String, val name: String)
    data class SettingsNs(val name: String)

    private var client: DshApiClient? = null

    private val _connectionState = MutableStateFlow(DshConnectionState.Disconnected)
    val connectionState: StateFlow<DshConnectionState> = _connectionState.asStateFlow()

    /** True once we have authenticated and successfully made at least one DSH API call.
     *  This lets the UI treat the gateway as "online" for sending messages even when the
     *  WebSocket downlink is still connecting (e.g. external nginx not forwarding Upgrade). */
    private val _authenticated = MutableStateFlow(false)
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()

    private val _hostInfo = MutableStateFlow<HostInfo?>(null)
    val hostInfo: StateFlow<HostInfo?> = _hostInfo.asStateFlow()

    private val _providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    val providers: StateFlow<List<ProviderInfo>> = _providers.asStateFlow()

    private val _presets = MutableStateFlow<List<PresetInfo>>(emptyList())
    val presets: StateFlow<List<PresetInfo>> = _presets.asStateFlow()

    private val _workspaces = MutableStateFlow<List<WorkspaceInfo>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceInfo>> = _workspaces.asStateFlow()

    private val _modelGroups = MutableStateFlow<List<ModelGroup>>(emptyList())
    val modelGroups: StateFlow<List<ModelGroup>> = _modelGroups.asStateFlow()

    private val _settingsNamespaces = MutableStateFlow<List<SettingsNs>>(emptyList())
    val settingsNamespaces: StateFlow<List<SettingsNs>> = _settingsNamespaces.asStateFlow()

    /** Raw mux `session/event` frames keyed by sessionId, most-recent-last. */
    private val _sessionEvents = MutableStateFlow<Map<String, List<JsonObject>>>(emptyMap())
    val sessionEvents: StateFlow<Map<String, List<JsonObject>>> = _sessionEvents.asStateFlow()

    /** Authenticate (if [cookie] missing) and open the downlink streams; then load all data. */
    fun connect(baseUrl: String, cookie: String?, user: String? = null, password: String? = null) {
        scope.launch {
            var effectiveCookie = cookie
            if (effectiveCookie.isNullOrBlank() && user != null && password != null) {
                effectiveCookie = DshApiClient(baseUrl, "", scope).login(user, password)
            }
            if (effectiveCookie.isNullOrBlank()) {
                _connectionState.value = DshConnectionState.Error
                return@launch
            }
            val c = DshApiClient(baseUrl.trim().removeSuffix("/"), effectiveCookie, scope)
            client = c
            c.connectionState.collect { state ->
                _connectionState.value = state
                if (state == DshConnectionState.Connected) {
                    loadAll()
                    collectEvents(c)
                }
            }
        }
    }

    private fun collectEvents(c: DshApiClient) {
        scope.launch {
            c.events.collect { ev ->
                if (ev.type == "session/event") {
                    val obj = ev.payloadObject ?: return@collect
                    val sid = obj["sessionId"]?.jsonPrimitive?.contentOrNull ?: return@collect
                    val event = obj["event"]?.takeIf { it is JsonObject } as? JsonObject ?: return@collect
                    _sessionEvents.value = _sessionEvents.value.toMutableMap().apply {
                        val list = (this[sid] ?: emptyList()).toMutableList().apply { add(event) }
                        this[sid] = list
                    }
                }
            }
        }
    }

    private fun loadAll() {
        scope.launch {
            runCatching { loadSessions() }.onSuccess { _authenticated.value = true }
            runCatching { loadHost() }
            runCatching { loadProviders() }
            runCatching { loadPresets() }
            runCatching { loadWorkspaces() }
            runCatching { loadModels() }
            runCatching { loadSettings() }
        }
    }

    suspend fun loadSessions() {
        val v = client?.call("session.list", buildJsonObject { put("cursor", JsonPrimitive("")) })
            ?: return
        val items = (v as? JsonObject)?.get("items")?.takeIf { it is JsonArray }?.jsonArray ?: return
        _sessions.value = items.mapNotNull { it as? JsonObject }.mapNotNull { mapSession(it) }
    }

    private fun mapSession(o: JsonObject): SessionInfo? {
        val id = o["sessionId"]?.jsonPrimitive?.contentOrNull ?: return null
        val proj = (o["projections"] as? JsonObject)?.get("values") as? JsonObject
        val title = proj?.get("title")?.jsonPrimitive?.contentOrNull ?: "新会话"
        val running = o["running"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val updatedAt = o["updatedAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val cwd = o["cwd"]?.jsonPrimitive?.contentOrNull
        val preset = o["agentPreset"]?.jsonPrimitive?.contentOrNull
        return SessionInfo(id, title, running, updatedAt, cwd, preset)
    }

    suspend fun loadHost() {
        val v = client?.call("host.describe") ?: return
        val o = v as? JsonObject ?: return
        _hostInfo.value = HostInfo(
            version = o["version"]?.jsonPrimitive?.contentOrNull,
            cwd = o["cwd"]?.jsonPrimitive?.contentOrNull,
            provider = o["provider"]?.jsonPrimitive?.contentOrNull,
            model = o["model"]?.jsonPrimitive?.contentOrNull,
            attachedSessions = o["attachedSessions"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
        )
    }

    suspend fun loadProviders() {
        val v = client?.call("llm.providers") ?: return
        val arr = (v as? JsonObject)?.get("providers")?.takeIf { it is JsonArray }?.jsonArray ?: return
        _providers.value = arr.mapNotNull { it as? JsonObject }.mapNotNull {
            val id = it["provider"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = it["displayName"]?.jsonPrimitive?.contentOrNull ?: id
            val active = it["active"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            ProviderInfo(id, name, active)
        }
    }

    suspend fun loadPresets() {
        val v = client?.call("agentPreset.list") ?: return
        val arr = (v as? JsonObject)?.get("presets")?.takeIf { it is JsonArray }?.jsonArray ?: return
        _presets.value = arr.mapNotNull { it as? JsonObject }.mapNotNull {
            val id = it["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = it["name"]?.jsonPrimitive?.contentOrNull ?: id
            val def = it["isDefault"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            PresetInfo(id, name, def)
        }
    }

    suspend fun loadWorkspaces() {
        val v = client?.call("workspace.list") ?: return
        val arr = (v as? JsonObject)?.get("items")?.takeIf { it is JsonArray }?.jsonArray ?: return
        _workspaces.value = arr.mapNotNull { it as? JsonObject }.mapNotNull {
            val id = it["workspaceId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val path = it["path"]?.jsonPrimitive?.contentOrNull
            WorkspaceInfo(id, path)
        }
    }

    suspend fun loadModels() {
        // Per-session model catalog; use the first session if available, else skip.
        val sid = _sessions.value.firstOrNull()?.sessionId ?: return
        val v = client?.call("session.models", buildJsonObject { put("sessionId", JsonPrimitive(sid)) })
            ?: return
        val o = v as? JsonObject ?: return
        val groups = o["groups"]?.takeIf { it is JsonArray }?.jsonArray ?: return
        _modelGroups.value = groups.mapNotNull { it as? JsonObject }.mapNotNull { g ->
            val id = g["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = g["name"]?.jsonPrimitive?.contentOrNull ?: id
            val models = (g["models"] as? JsonArray)?.mapNotNull { m ->
                (m as? JsonObject)?.let {
                    ModelInfo(
                        it["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        it["name"]?.jsonPrimitive?.contentOrNull ?: it["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    )
                }
            } ?: emptyList()
            ModelGroup(id, name, models)
        }
    }

    suspend fun loadSettings() {
        val v = client?.call("settings.describe") ?: return
        val arr = (v as? JsonObject)?.get("namespaces")?.takeIf { it is JsonArray }?.jsonArray ?: return
        _settingsNamespaces.value = arr.mapNotNull { it as? JsonObject }
            .mapNotNull { it["ns"]?.jsonPrimitive?.contentOrNull }
            .map { SettingsNs(it) }
    }

    /** Create a new ordinary session in [cwd] (or the host cwd) and return its id, or null. */
    suspend fun createSession(cwd: String? = null, agentPreset: String? = null): String? {
        val payload = buildJsonObject {
            if (cwd != null) put("cwd", JsonPrimitive(cwd))
            if (agentPreset != null) put("agentPreset", JsonPrimitive(agentPreset))
        }
        val v = client?.call("session.create", payload) ?: return null
        return (v as? JsonObject)?.get("sessionId")?.jsonPrimitive?.contentOrNull
    }

    /** Send a user message to a session. Returns true if accepted. */
    suspend fun prompt(sessionId: String, text: String, mode: String = "queue"): Boolean {
        val payload = buildJsonObject {
            put("sessionId", JsonPrimitive(sessionId))
            put("mode", JsonPrimitive(mode))
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive(text))
                        },
                    )
                },
            )
        }
        return runCatching { client?.call("session.prompt", payload); true }.getOrDefault(false)
    }

    /** Fetch the history tail (raw events) for a session. */
    suspend fun history(sessionId: String): List<JsonObject> {
        val v = client?.call(
            "session.history",
            buildJsonObject { put("sessionId", JsonPrimitive(sessionId)) },
        ) ?: return emptyList()
        val arr = (v as? JsonObject)?.get("events")?.takeIf { it is JsonArray }?.jsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonObject)?.get("event") as? JsonObject }
    }

    suspend fun cancel(sessionId: String): Boolean {
        return runCatching {
            client?.call("session.cancel", buildJsonObject { put("sessionId", JsonPrimitive(sessionId)) })
            true
        }.getOrDefault(false)
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _connectionState.value = DshConnectionState.Disconnected
    }
}
