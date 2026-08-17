@file:Suppress("DEPRECATION")

package ai.deepseek.harness

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Simplified settings facade for DSH direct client.
 * Only stores DSH server URL, session cookie, and basic preferences.
 */
class SecurePrefs(context: Context) {
  companion object {
    private const val plainPrefsName = "dsh.client"
    private const val securePrefsName = "dsh.client.secure"
  }

  private val appContext = context.applicationContext
  private val json = Json { ignoreUnknownKeys = true }

  private val plainPrefs: SharedPreferences =
    appContext.getSharedPreferences(plainPrefsName, Context.MODE_PRIVATE)

  private val masterKey by lazy {
    MasterKey
      .Builder(appContext)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
  }

  private val securePrefs: SharedPreferences by lazy {
    EncryptedSharedPreferences.create(
      appContext,
      securePrefsName,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  // ── DSH Login ──

  private val _serverUrl = MutableStateFlow(
    plainPrefs.getString("auth.serverUrl", "https://dsh.threel.site") ?: "https://dsh.threel.site",
  )
  val serverUrl: StateFlow<String> = _serverUrl

  private val _isLoggedIn = MutableStateFlow(plainPrefs.getBoolean("auth.loggedIn", false))
  val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

  private val _sessionUser = MutableStateFlow(securePrefs.getString("auth.username", "") ?: "")
  val sessionUser: StateFlow<String> = _sessionUser

  fun setServerUrl(value: String) {
    val url = value.trim().removeSuffix("/")
    plainPrefs.edit { putString("auth.serverUrl", url) }
    _serverUrl.value = url
  }

  fun setLoggedIn(value: Boolean, username: String = "") {
    plainPrefs.edit {
      putBoolean("auth.loggedIn", value)
      if (username.isNotEmpty()) putString("auth.username", username)
    }
    _isLoggedIn.value = value
    if (username.isNotEmpty()) {
      securePrefs.edit { putString("auth.username", username) }
      _sessionUser.value = username
    }
  }

  fun setSessionCookie(value: String) {
    securePrefs.edit { putString("auth.sessionCookie", value) }
  }

  fun getSessionCookie(): String? = securePrefs.getString("auth.sessionCookie", null)

  // ── Theme ──

  private val _appearanceThemeMode = MutableStateFlow(
    AppearanceThemeMode.fromRawValue(plainPrefs.getString("appearance.themeMode", null)),
  )
  val appearanceThemeMode: StateFlow<AppearanceThemeMode> = _appearanceThemeMode

  fun setAppearanceThemeMode(mode: AppearanceThemeMode) {
    plainPrefs.edit { putString("appearance.themeMode", mode.rawValue) }
    _appearanceThemeMode.value = mode
  }

  // ── Language / i18n ──

  private val _appLanguage = MutableStateFlow(
    AppLanguage.fromLanguageTag(plainPrefs.getString("ui.language", null)),
  )
  val appLanguage: StateFlow<AppLanguage> = _appLanguage

  fun saveAppLanguage(language: AppLanguage) {
    plainPrefs.edit {
      if (language != AppLanguage.System) {
        putString("ui.language", language.languageTag ?: "")
      } else {
        remove("ui.language")
      }
    }
    _appLanguage.value = language
  }

  // ── Preferred Model / Preset ──

  private val _preferredModel = MutableStateFlow(
    plainPrefs.getString("chat.preferredModel", null) ?: "",
  )
  val preferredModel: StateFlow<String> = _preferredModel

  fun setPreferredModel(model: String?) {
    plainPrefs.edit {
      if (model.isNullOrBlank()) { remove("chat.preferredModel") }
      else { putString("chat.preferredModel", model.trim()) }
    }
    _preferredModel.value = model ?: ""
  }

  private val _preferredPreset = MutableStateFlow(
    plainPrefs.getString("chat.preferredPreset", null) ?: "",
  )
  val preferredPreset: StateFlow<String> = _preferredPreset

  fun setPreferredPreset(preset: String?) {
    plainPrefs.edit {
      if (preset.isNullOrBlank()) { remove("chat.preferredPreset") }
      else { putString("chat.preferredPreset", preset.trim()) }
    }
    _preferredPreset.value = preset ?: ""
  }

  private val _modelFavorites = MutableStateFlow(loadStringList("chat.modelFavorites"))
  val modelFavorites: StateFlow<List<String>> = _modelFavorites

  private val _modelRecents = MutableStateFlow(loadStringList("chat.modelRecents"))
  val modelRecents: StateFlow<List<String>> = _modelRecents

  fun toggleModelFavorite(ref: String) {
    val trimmed = ref.trim()
    if (trimmed.isEmpty()) return
    val next = if (trimmed in _modelFavorites.value) {
      _modelFavorites.value - trimmed
    } else {
      _modelFavorites.value + trimmed
    }
    persistStringList("chat.modelFavorites", next)
    _modelFavorites.value = next
  }

  fun recordModelRecent(ref: String) {
    val trimmed = ref.trim()
    if (trimmed.isEmpty()) return
    val next = (listOf(trimmed) + _modelRecents.value.filterNot { it == trimmed }).take(5)
    persistStringList("chat.modelRecents", next)
    _modelRecents.value = next
  }

  // ── Active Session (for restart restore) ──

  private val _activeSessionId = MutableStateFlow(
    plainPrefs.getString("chat.activeSessionId", "") ?: "",
  )
  val activeSessionId: StateFlow<String> = _activeSessionId

  fun setActiveSessionId(value: String?) {
    plainPrefs.edit {
      if (value.isNullOrBlank()) remove("chat.activeSessionId")
      else putString("chat.activeSessionId", value.trim())
    }
    _activeSessionId.value = value ?: ""
  }

  // ── Helpers ──

  private fun loadStringList(key: String): List<String> {
    val raw = plainPrefs.getString(key, null)?.trim() ?: return emptyList()
    return try {
      val element = json.parseToJsonElement(raw)
      (element as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() } }?.distinct() ?: emptyList()
    } catch (_: Throwable) { emptyList() }
  }

  private fun persistStringList(key: String, list: List<String>) {
    plainPrefs.edit { putString(key, JsonArray(list.map(::JsonPrimitive)).toString()) }
  }
}