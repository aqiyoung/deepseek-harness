package ai.deepseek.harness

/** User-selectable app theme mode for Android appearance settings. */
enum class AppearanceThemeMode(
  val rawValue: String,
  val displayLabel: String,
) {
  System(rawValue = "system", displayLabel = "System"),
  Dark(rawValue = "dark", displayLabel = "Dark"),
  Light(rawValue = "light", displayLabel = "Light"),
  ;

  fun isDark(systemDark: Boolean): Boolean =
    when (this) {
      System -> systemDark
      Dark -> true
      Light -> false
    }

  companion object {
    // 未识别/缺省一律回退 System，与 SecurePrefs 的初始默认保持一致。
    fun fromRawValue(value: String?): AppearanceThemeMode = entries.firstOrNull { it.rawValue == value?.trim()?.lowercase() } ?: System

    fun fromDisplayLabel(label: String): AppearanceThemeMode = entries.firstOrNull { it.displayLabel.equals(label.trim(), ignoreCase = true) } ?: System
  }
}
