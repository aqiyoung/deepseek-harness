package ai.deepseek.harness

import ai.deepseek.harness.i18n.NativeStringResources
import ai.deepseek.harness.i18n.notifyNativeLocaleChanged
import android.app.Application
import android.content.res.Configuration
import android.os.StrictMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Simplified Android Application singleton for DSH direct client.
 * Owns process-wide secure prefs and the DSH server RPC repo.
 */
class NodeApp : Application() {
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val prefs: SecurePrefs by lazy { SecurePrefs(this) }
  val dsh: ai.deepseek.harness.dsh.DshRepo by lazy { ai.deepseek.harness.dsh.DshRepo(this) }

  override fun onCreate() {
    super.onCreate()
    // StrictMode 必须最先安装，否则启动路径上的主线程 IO（含加密存储初始化）逃过检测。
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
    NativeStringResources.install(this)
    // 后台预热加密存储（masterKey + EncryptedSharedPreferences），避免主线程卡顿。
    appScope.launch { runCatching { prefs.warmUp() } }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    NativeStringResources.setConfigurationLocales(newConfig)
    notifyNativeLocaleChanged()
  }
}
