package ai.deepseek.harness.update

import ai.deepseek.harness.BuildConfig
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 统一更新检查（对齐 openlist-android / sanyelive / FeiNiuMusic / synapse 的 app_update_core 引擎）。
 *
 * - 数据源：GitHub Release（不再依赖自托管 latest.json）。
 * - 代理链：[gh-llkk.cc → gh-proxy.com → 直连]，国内直连 api.github.com 被墙时自动降级。
 * - 结论语义：所有源都失败 → checkForUpdate() 返回 null（调用方必须如实报"检查失败"，绝不谎报"已是最新"）。
 * - 仅当 tag_name 比当前 versionName 更新时才 hasUpdate。
 * - 启动时是否自动检查可由设置关闭（默认开），手动检查永远执行。
 */
object UpdateManager {
  private val engine =
    GitHubUpdateEngine(
      UpdateConfig(owner = "aqiyoung", repo = "deepseek-harness"),
    )

  private val client =
    OkHttpClient
      .Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build()

  private const val PREFS_NAME = "dsh_update_prefs"
  private const val AUTO_CHECK_KEY = "auto_check_updates"

  /** 当前安装版本（来自 BuildConfig）。 */
  val currentVersionName: String get() = BuildConfig.VERSION_NAME

  /** 启动时是否自动检查更新（设置可关，默认开）。 */
  suspend fun getAutoCheckEnabled(context: Context): Boolean =
    withContext(Dispatchers.IO) {
      context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(AUTO_CHECK_KEY, true)
    }

  fun setAutoCheckEnabled(
    context: Context,
    value: Boolean,
  ) {
    context
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit { putBoolean(AUTO_CHECK_KEY, value) }
  }

  /**
   * 检查更新。返回 null = 所有数据源都失败（网络不可达），调用方应提示"检查失败"。
   * 返回对象但 hasUpdate=false = 确实已是最新。
   */
  suspend fun checkForUpdate(): GitHubUpdateResult? =
    withContext(Dispatchers.IO) {
      engine.check(
        fetch = { url, headers ->
          val builder = okhttp3.Request.Builder().url(url)
          headers.forEach { (k, v) -> builder.header(k, v) }
          val resp = client.newCall(builder.build()).execute()
          UpdateHttpResponse(resp.code, resp.body?.string().orEmpty())
        },
        currentVersion = currentVersionName,
      )
    }

  /** 跳转发布页（GitHub App → 浏览器 → 复制链接）。 */
  fun openRelease(
    context: Context,
    url: String,
  ): OpenReleaseResult = engine.openRelease(context, url)
}
