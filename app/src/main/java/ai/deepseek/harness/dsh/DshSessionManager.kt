package ai.deepseek.harness.dsh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
class DshSessionManager(private val scope: kotlinx.coroutines.CoroutineScope) {
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

    /** A single trajectory step observed from the mux stream. */
    data class TrajectoryStep(
        val timestamp: Long,
        val type: String,
        val text: String? = null,
        val model: String? = null,
        val status: String? = null,
    )

    private var client: DshApiClient? = null

    private val _connectionState = MutableStateFlow(DshConnectionState.Disconnected)
    val connectionState: StateFlow<DshConnectionState> = _connectionState.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

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

    /** Real-time trajectory for the active session. */
    private val _trajectory = MutableStateFlow<List<TrajectoryStep>>(emptyList())
    val trajectory: StateFlow<List<TrajectoryStep>> = _trajectory.asStateFlow()

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
            val normalizedUrl = baseUrl.trim().removeSuffix("/")
            _serverUrl.value = normalizedUrl
            val c = DshApiClient(normalizedUrl, effectiveCookie, scope)
            client = c
            c.connect()
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
                    // Build trajectory step
                    val step = parseTrajectoryStep(event, sid)
                    if (step != null) {
                        _trajectory.value = (_trajectory.value.toMutableList() + step).takeLast(100)
                    }
                }
            }
        }
    }

    private fun parseTrajectoryStep(event: JsonObject, sid: String): TrajectoryStep? {
        val type = event["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val timestamp = System.currentTimeMillis()

        return when (type) {
            "user/message" -> {
                val text = event["content"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull
                TrajectoryStep(timestamp, "user", text = text, status = "sent")
            }
            "assistant/message" -> {
                val text = event["content"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull
                TrajectoryStep(timestamp, "assistant", text = text, status = "received")
            }
            "model/call" -> {
                val model = event["model"]?.jsonPrimitive?.contentOrNull
                    ?: event["provider"]?.jsonPrimitive?.contentOrNull
                TrajectoryStep(timestamp, "model", text = "LLM call", model = model, status = "started")
            }
            "model/response" -> {
                val model = event["model"]?.jsonPrimitive?.contentOrNull
                TrajectoryStep(timestamp, "model", text = "LLM response", model = model, status = "done")
            }
            "tool/call" -> {
                val name = event["toolName"]?.jsonPrimitive?.contentOrNull
                    ?: event["name"]?.jsonPrimitive?.contentOrNull
                    ?: event["tool"]?.jsonPrimitive?.contentOrNull
                TrajectoryStep(timestamp, "tool", text = "Tool: $name", status = "started")
            }
            "tool/result" -> {
                val name = event["toolName"]?.jsonPrimitive?.contentOrNull
                    ?: event["name"]?.jsonPrimitive?.contentOrNull
                    ?: event["tool"]?.jsonPrimitive?.contentOrNull
                val text = event["result"]?.jsonPrimitive?.contentOrNull
                    ?: event["output"]?.jsonPrimitive?.contentOrNull
                TrajectoryStep(timestamp, "tool", text = "Tool result: $name" + (text?.let { " — $it" } ?: ""), status = "done")
            }
            else -> {
                // Generic step for unknown types (keep for visibility)
                TrajectoryStep(timestamp, type, text = null, status = null)
            }
        }
    }

    fun clearTrajectory() {
        _trajectory.value = emptyList()
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
        val v = client?.call("session.list", buildJsonObject { put("cursor", kotlinx.serialization.json.JsonPrimitive("")) })
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
        val sid = _sessions.value.firstOrNull()?.sessionId ?: return
        val v = client?.call("session.models", buildJsonObject { put("sessionId", kotlinx.serialization.json.JsonPrimitive(sid)) })
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

    suspend fun createSession(cwd: String? = null, agentPreset: String? = null): String? {
        val payload = buildJsonObject {
            if (cwd != null) put("cwd", kotlinx.serialization.json.JsonPrimitive(cwd))
            if (agentPreset != null) put("agentPreset", kotlinx.serialization.json.JsonPrimitive(agentPreset))
        }
        val v = client?.call("session.create", payload) ?: return null
        return (v as? JsonObject)?.get("sessionId")?.jsonPrimitive?.contentOrNull
    }

    suspend fun prompt(sessionId: String, text: String, mode: String = "queue", model: String? = null): Boolean {
        val payload = buildJsonObject {
            put("sessionId", kotlinx.serialization.json.JsonPrimitive(sessionId))
            put("mode", kotlinx.serialization.json.JsonPrimitive(mode))
            if (!model.isNullOrBlank()) put("model", kotlinx.serialization.json.JsonPrimitive(model))
            put(
                "content",
                kotlinx.serialization.json.buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                            put("text", kotlinx.serialization.json.JsonPrimitive(text))
                        },
                    )
                },
            )
        }
        return runCatching { client?.call("session.prompt", payload); true }.getOrDefault(false)
    }

    suspend fun history(sessionId: String): List<JsonObject> {
        val v = client?.call(
            "session.history",
            buildJsonObject { put("sessionId", kotlinx.serialization.json.JsonPrimitive(sessionId)) },
        ) ?: return emptyList()
        val arr = (v as? JsonObject)?.get("events")?.takeIf { it is JsonArray }?.jsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonObject)?.get("event") as? JsonObject }
    }

    suspend fun cancel(sessionId: String): Boolean {
        return runCatching {
            client?.call("session.cancel", buildJsonObject { put("sessionId", kotlinx.serialization.json.JsonPrimitive(sessionId)) })
            true
        }.getOrDefault(false)
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _connectionState.value = DshConnectionState.Disconnected
        _authenticated.value = false
        _serverUrl.value = ""
    }
}