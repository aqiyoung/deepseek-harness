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

  @Volatile
  var isSecureStorageDegraded = false
    private set

  private val securePrefs: SharedPreferences by lazy { createSecurePrefs() }

  private fun openSecurePrefs(): SharedPreferences =
    EncryptedSharedPreferences.create(
      appContext,
      securePrefsName,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

  private fun createSecurePrefs(): SharedPreferences =
    try {
      openSecurePrefs()
    } catch (_: Exception) {
      // KeyStore/加密文件损坏不能变成启动崩溃循环：重置一次；仍失败则进入降级模式——
      // 机密（Cookie、记住的密码）一律不持久化，并复位登录态要求重新登录。
      appContext.deleteSharedPreferences(securePrefsName)
      try {
        openSecurePrefs()
      } catch (_: Exception) {
        isSecureStorageDegraded = true
        plainPrefs.edit { remove("auth.loggedIn") }
        plainPrefs
      }
    }

  /** 后台预热加密存储（masterKey + ESP 初始化较重），避免主线程首次访问卡顿。 */
  fun warmUp() {
    securePrefs.getString("auth.sessionCookie", null)
  }

  // ── DSH Login ──

  private val _serverUrl = MutableStateFlow(plainPrefs.getString("auth.serverUrl", null).orEmpty())
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
    plainPrefs.edit { putBoolean("auth.loggedIn", value) }
    _isLoggedIn.value = value
    // 账号名只进加密存储；明文层只保留 loggedIn 标志。
    if (!isSecureStorageDegraded && username.isNotEmpty()) {
      securePrefs.edit { putString("auth.username", username) }
      _sessionUser.value = username
    }
  }

  fun setSessionCookie(value: String) {
    if (isSecureStorageDegraded) return
    securePrefs.edit { putString("auth.sessionCookie", value) }
  }

  fun getSessionCookie(): String? =
    if (isSecureStorageDegraded) null else securePrefs.getString("auth.sessionCookie", null)

  // ── Remembered credentials（可选：记住密码，加密存储） ──

  fun setRememberedPassword(value: String?) {
    if (isSecureStorageDegraded) return
    securePrefs.edit {
      if (value.isNullOrEmpty()) remove("auth.rememberedPassword")
      else putString("auth.rememberedPassword", value)
    }
  }

  fun getRememberedPassword(): String? =
    if (isSecureStorageDegraded) null else securePrefs.getString("auth.rememberedPassword", null)

  // ── Theme ──

  private val _appearanceThemeMode = MutableStateFlow(
    plainPrefs.getString("appearance.themeMode", null)
      ?.let { AppearanceThemeMode.fromRawValue(it) }
      ?: AppearanceThemeMode.System,
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