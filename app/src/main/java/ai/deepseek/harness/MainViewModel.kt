package ai.deepseek.harness

import ai.deepseek.harness.chat.ChatMessage
import ai.deepseek.harness.chat.ChatMessageContent
import ai.deepseek.harness.chat.ChatSessionEntry
import ai.deepseek.harness.dsh.DshConnectionState
import ai.deepseek.harness.dsh.DshSessionManager
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Simplified ViewModel for DSH direct client.
 * Works directly with [DshSessionManager] to provide all UI state.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
  private val nodeApp = app as NodeApp
  private val prefs = nodeApp.prefs

  /** DSH session manager (direct protocol client). */
  val dsh = DshSessionManager(viewModelScope)

  // ── Connection State ──

  val connectionState: StateFlow<DshConnectionState> = dsh.connectionState
  val authenticated: StateFlow<Boolean> = dsh.authenticated
  val serverUrl: StateFlow<String> = dsh.serverUrl

  val isConnected: StateFlow<Boolean> =
    combine(dsh.connectionState, dsh.authenticated) { state, auth ->
      state == DshConnectionState.Connected || auth
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val statusText: StateFlow<String> =
    combine(dsh.connectionState, dsh.authenticated) { state, auth ->
      when {
        state == DshConnectionState.Connected -> "Connected"
        auth -> "Connected"
        state == DshConnectionState.Connecting -> "Connecting…"
        state == DshConnectionState.Error -> "Connection error"
        else -> "Offline"
      }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "Offline")

  // ── Login State (from prefs) ──

  val isLoggedIn: StateFlow<Boolean> = prefs.isLoggedIn
  val sessionUser: StateFlow<String> = prefs.sessionUser
  val prefsServerUrl: StateFlow<String> = prefs.serverUrl
  val appearanceThemeMode: StateFlow<AppearanceThemeMode> = prefs.appearanceThemeMode

  // ── Language / i18n ──

  val appLanguage: StateFlow<AppLanguage> = prefs.appLanguage

  fun applyAppLanguage(language: AppLanguage) {
    prefs.saveAppLanguage(language)
    setAppLanguage(language)
  }

  // ── Sessions ──

  val sessions: StateFlow<List<DshSessionManager.SessionInfo>> = dsh.sessions
  val hostInfo: StateFlow<DshSessionManager.HostInfo?> = dsh.hostInfo
  val modelGroups: StateFlow<List<DshSessionManager.ModelGroup>> = dsh.modelGroups
  val presets: StateFlow<List<DshSessionManager.PresetInfo>> = dsh.presets
  val providers: StateFlow<List<DshSessionManager.ProviderInfo>> = dsh.providers

  // ── Chat Messages ──

  private val _dshChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val chatMessages: StateFlow<List<ChatMessage>> = _dshChatMessages.asStateFlow()

  private val _activeDshSessionId = MutableStateFlow<String?>(null)
  val activeDshSessionId: StateFlow<String?> = _activeDshSessionId.asStateFlow()

  // ── Model Favorites (from prefs) ──

  val modelFavorites: StateFlow<List<String>> = prefs.modelFavorites
  val modelRecents: StateFlow<List<String>> = prefs.modelRecents

  // ── Chat Session Entries (for session list UI) ──

  val chatSessions: StateFlow<List<ChatSessionEntry>> =
    dsh.sessions.map { list ->
      list.map { s ->
        ChatSessionEntry(
          key = s.sessionId,
          updatedAtMs = s.updatedAt,
          sessionId = s.sessionId,
          displayName = s.title,
          derivedTitle = s.title,
          label = s.title,
          hasActiveRun = s.running,
          lastActivityAt = s.updatedAt,
          modelProvider = s.agentPreset,
        )
      }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  // ── Actions ──

  fun connectDsh() {
    val cookie = prefs.getSessionCookie()
    val url = prefs.serverUrl.value.trim().removeSuffix("/").ifEmpty { "https://dsh.threel.site" }
    dsh.connect(baseUrl = url, cookie = cookie)
  }

  fun disconnectDsh() {
    dsh.disconnect()
  }

  fun refreshDshConnection() {
    dsh.disconnect()
    connectDsh()
  }

  fun setLoggedIn(value: Boolean, username: String = "") {
    prefs.setLoggedIn(value, username)
  }

  fun setServerUrl(value: String) {
    prefs.setServerUrl(value)
  }

  fun setSessionCookie(value: String) {
    prefs.setSessionCookie(value)
  }

  fun setAppearanceThemeMode(mode: AppearanceThemeMode) {
    prefs.setAppearanceThemeMode(mode)
  }

  fun toggleModelFavorite(ref: String) {
    prefs.toggleModelFavorite(ref)
  }

  fun recordModelRecent(ref: String) {
    prefs.recordModelRecent(ref)
  }

  /** Load history for a session and populate chat messages. */
  fun loadSessionHistory(sessionId: String) {
    viewModelScope.launch {
      _activeDshSessionId.value = sessionId
      val events = dsh.history(sessionId)
      val messages = events.mapNotNull { event ->
        parseChatMessage(event)
      }
      _dshChatMessages.value = messages
    }
  }

  /** Send a prompt to a session with optional model override. */
  fun sendPrompt(sessionId: String, text: String, model: String? = null) {
    viewModelScope.launch {
      dsh.prompt(sessionId, text, model = model)
      // Refresh history after sending
      loadSessionHistory(sessionId)
    }
  }

  /** Create a new session with optional model preset. */
  fun createSession(cwd: String? = null, agentPreset: String? = null, model: String? = null) {
    viewModelScope.launch {
      val id = dsh.createSession(cwd, agentPreset)
      dsh.loadSessions()
      if (id != null) {
        _activeDshSessionId.value = id
        viewModelScope.launch {
          dsh.history(id)
        }
      }
    }
  }

  /** Create session and send prompt with model override. */
  fun createSessionAndSend(cwd: String? = null, agentPreset: String? = null, model: String? = null, prompt: String = "") {
    viewModelScope.launch {
      val id = dsh.createSession(cwd, agentPreset)
      dsh.loadSessions()
      if (id != null) {
        _activeDshSessionId.value = id
        if (prompt.isNotBlank()) {
          dsh.prompt(id, prompt, model = model)
        }
        dsh.history(id)
      }
    }
  }

  /** Cancel the current run on a session. */
  fun cancelSession(sessionId: String) {
    viewModelScope.launch {
      dsh.cancel(sessionId)
    }
  }

  fun logout() {
    dsh.disconnect()
    prefs.setLoggedIn(false)
    prefs.setSessionCookie("")
    _dshChatMessages.value = emptyList()
    _activeDshSessionId.value = null
  }

  // ── Helpers ──

  private fun parseChatMessage(event: JsonObject): ChatMessage? {
    val type = event["type"]?.jsonPrimitive?.contentOrNull ?: return null
    val content = event["content"]?.takeIf { it is JsonArray }?.jsonArray ?: return null
    val role = when (type) {
      "user/message" -> "user"
      "assistant/message" -> "assistant"
      else -> return null
    }
    val text = content.joinToString("") { block ->
      val obj = block as? JsonObject ?: return@joinToString ""
      if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
        obj["text"]?.jsonPrimitive?.contentOrNull ?: ""
      } else ""
    }
    return ChatMessage(
      id = event["seq"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString(),
      role = role,
      content = listOf(ChatMessageContent(text = text)),
      timestampMs = event["timestamp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: System.currentTimeMillis(),
    )
  }
}