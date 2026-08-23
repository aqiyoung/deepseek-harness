package ai.deepseek.harness

import ai.deepseek.harness.i18n.NativeStringResources
import ai.deepseek.harness.i18n.notifyNativeLocaleChanged
import android.app.Application
import android.content.res.Configuration
import android.os.StrictMode

/**
 * Simplified Android Application singleton for DSH direct client.
 * Owns process-wide secure prefs.
 */
class NodeApp : Application() {
  val prefs: SecurePrefs by lazy { SecurePrefs(this) }
  val dsh: ai.deepseek.harness.dsh.DshRepo by lazy { ai.deepseek.harness.dsh.DshRepo(this) }

  override fun onCreate() {
    super.onCreate()
    NativeStringResources.install(this)
    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .build(),
      )
      StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .build(),
      )
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    NativeStringResources.setConfigurationLocales(newConfig)
    notifyNativeLocaleChanged()
  }
}