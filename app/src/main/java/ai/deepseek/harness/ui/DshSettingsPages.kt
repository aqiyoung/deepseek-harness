package ai.deepseek.harness.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ai.deepseek.harness.BuildConfig
import ai.deepseek.harness.NodeApp
import ai.deepseek.harness.dsh.AppUpdateConfig
import ai.deepseek.harness.dsh.AppUpdateCore
import ai.deepseek.harness.dsh.AppUpdateResult
import ai.deepseek.harness.ui.design.DshDetailFrame
import ai.deepseek.harness.ui.design.DshPrimaryButton
import ai.deepseek.harness.ui.design.DshSecondaryButton
import ai.deepseek.harness.ui.design.DshSectionLabel
import ai.deepseek.harness.ui.design.DshSettingsRow
import ai.deepseek.harness.ui.design.DshSoftPanel
import ai.deepseek.harness.ui.design.DshStatus
import ai.deepseek.harness.ui.design.DshStatusPill
import ai.deepseek.harness.ui.design.DshTheme

private fun repo(context: android.content.Context) =
  (context.applicationContext as NodeApp).dsh

/** 异步内容骨架：加载中 / 失败重试 / 数据，DSH 服务子页共用。 */
@Composable
private fun <T> DshAsyncPage(
  load: suspend () -> T,
  content: @Composable (T, () -> Unit) -> Unit,
) {
  var result by remember { mutableStateOf<Result<T>?>(null) }
  var refreshing by remember { mutableStateOf(false) }
  var refreshFailed by remember { mutableStateOf(false) }
  var tick by remember { mutableStateOf(0) }
  LaunchedEffect(tick) {
    refreshing = true
    refreshFailed = false
    try {
      val r = runCatching { load() }
      // 取消不是"加载失败"，必须放行以遵守结构化并发。
      val ce = r.exceptionOrNull()
      if (ce is kotlin.coroutines.cancellation.CancellationException) throw ce
      // 首次加载失败才进错误态；已有数据时保留旧数据原地刷新。
      if (r.isSuccess || result == null) {
        result = r
        refreshFailed = r.isFailure && result != null
      } else {
        refreshFailed = true
      }
    } finally {
      refreshing = false
    }
  }
  val current = result
  when {
    current == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = DshTheme.colors.primary, strokeWidth = 2.dp)
    }
    current.isFailure -> Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
      Text(
        text = run {
          val ex = current.exceptionOrNull()
          val code = (ex as? ai.deepseek.harness.dsh.DshApiException)?.code
          "加载失败：" + (if (code != null) "[$code] " else "") + (ex?.message ?: "未知错误")
        },
        style = DshTheme.type.body,
        color = DshTheme.colors.textMuted,
        textAlign = TextAlign.Center,
      )
      DshPrimaryButton(text = "重试", onClick = { tick++ })
    }
    else -> Column {
      if (refreshing || refreshFailed) {
        Text(
          text = when {
            refreshing -> "刷新中…"
            else -> "刷新失败，显示的是上次结果"
          },
          style = DshTheme.type.captionSmall,
          color = if (refreshFailed) DshTheme.colors.warning else DshTheme.colors.textSubtle,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
      }
      content(current.getOrThrow()) { tick++ }
    }
  }
}

/** 模型：按提供方分组的目录，点击切换当前会话模型。 */
@Composable
fun ModelsDetailPage(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  DshDetailFrame(title = "模型", onBack = onBack) {
    DshAsyncPage(load = { repo(context).models() }) { snap, reload ->
      Spacer(modifier = Modifier.height(6.dp))
      if (!snap.routable) {
        DshSoftPanel {
          Text(
            text = "当前模型没有可用的服务通道，会话无法开始新一轮对话。",
            style = DshTheme.type.caption,
            color = DshTheme.colors.danger,
          )
        }
        Spacer(modifier = Modifier.height(14.dp))
      }
      if (snap.failures.isNotEmpty()) {
        DshSoftPanel {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (f in snap.failures) {
              Text(text = f, style = DshTheme.type.caption, color = DshTheme.colors.warning)
            }
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
      }

      val groups = snap.options.groupBy { it.providerId }
      for ((pid, items) in groups) {
        val providerName = items.first().providerName
        DshSectionLabel(providerName)
        DshSoftPanel {
          Column {
            items.forEachIndexed { index, option ->
              if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
              val selected = snap.currentProvider == pid && snap.currentModel == option.modelId
              DshSettingsRow(
                title = option.modelName,
                value = if (selected) "当前" else null,
                onClick = {
                  if (!selected) {
                    scope.launch {
                      try {
                        repo(context).selectModel(pid, option.modelId)
                        Toast.makeText(context, "已切换到 ${option.modelName}", Toast.LENGTH_SHORT).show()
                        reload()
                      } catch (ce: kotlin.coroutines.cancellation.CancellationException) {
                        throw ce
                      } catch (e: Exception) {
                        Toast.makeText(context, "切换失败：${e.message}", Toast.LENGTH_SHORT).show()
                      }
                    }
                  }
                },
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
      }
      if (snap.options.isEmpty()) {
        Text(
          text = "没有可用模型：请检查服务端模型配置。",
          style = DshTheme.type.caption,
          color = DshTheme.colors.textSubtle,
        )
      }
    }
  }
}

/** 插件：只读清单（启用状态 + 加载阶段）。 */
@Composable
fun PluginsDetailPage(onBack: () -> Unit) {
  val context = LocalContext.current

  DshDetailFrame(title = "插件", onBack = onBack) {
    DshAsyncPage(load = { repo(context).plugins() }) { entries, _ ->
      Spacer(modifier = Modifier.height(6.dp))
      val enabledCount = entries.count { it.enabled }
      DshSoftPanel {
        Text(
          text = "共 ${entries.size} 个插件，$enabledCount 个已启用",
          style = DshTheme.type.body,
          color = DshTheme.colors.textMuted,
        )
      }
      Spacer(modifier = Modifier.height(14.dp))
      DshSoftPanel {
        Column {
          entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = entry.name,
                style = DshTheme.type.body,
                color = DshTheme.colors.text,
                modifier = Modifier.weight(1f),
              )
              val (label, status) = when {
                !entry.enabled -> "已停用" to DshStatus.Neutral
                entry.phase == "active" -> "运行中" to DshStatus.Success
                entry.phase == "failed" -> "加载失败" to DshStatus.Danger
                entry.phase == "installed" -> "已安装" to DshStatus.Neutral
                else -> "加载中" to DshStatus.Warning
              }
              DshStatusPill(text = label, status = status)
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = if (entries.any { it.phase == "installed" }) {
          "当前服务端未提供插件状态接口，以上为服务器首页清单中的插件；参数请到网页端 设置 → 插件 调整。"
        } else {
          "插件参数（Bash、AgentLoop、WebSearch 等）请到网页端 设置 → 插件 调整。"
        },
        style = DshTheme.type.caption,
        color = DshTheme.colors.textSubtle,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    }
  }
}

/** Agent 预设：名单 + 设为默认。 */
@Composable
fun PresetsDetailPage(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  DshDetailFrame(title = "Agent 预设", onBack = onBack) {
    DshAsyncPage(load = { repo(context).presets() }) { presets, reload ->
      Spacer(modifier = Modifier.height(6.dp))
      if (presets.isEmpty()) {
        Text(
          text = "该部署未配置任何预设，所有会话使用宿主默认组合。",
          style = DshTheme.type.caption,
          color = DshTheme.colors.textSubtle,
        )
        return@DshAsyncPage
      }
      DshSoftPanel {
        Column {
          presets.forEachIndexed { index, preset ->
            if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = preset.name,
                  style = DshTheme.type.body,
                  color = if (preset.broken != null) DshTheme.colors.textSubtle else DshTheme.colors.text,
                  modifier = Modifier.weight(1f),
                )
                if (preset.isDefault) DshStatusPill(text = "默认", status = DshStatus.Success)
              }
              if (preset.description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = preset.description, style = DshTheme.type.caption, color = DshTheme.colors.textMuted)
              }
              if (preset.broken != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "不可用：${preset.broken}", style = DshTheme.type.caption, color = DshTheme.colors.danger)
              }
              Spacer(modifier = Modifier.height(8.dp))
              if (!preset.isDefault && preset.broken == null) {
                DshSecondaryButton(
                  text = if (preset.trust == "system") "设为默认" else "设为默认（本地预设）",
                  onClick = {
                    scope.launch {
                      try {
                        repo(context).setDefaultPreset(preset.id)
                        Toast.makeText(context, "默认预设已设为 ${preset.name}", Toast.LENGTH_SHORT).show()
                        reload()
                      } catch (ce: kotlin.coroutines.cancellation.CancellationException) {
                        throw ce
                      } catch (e: Exception) {
                        Toast.makeText(context, "设置失败：${e.message}", Toast.LENGTH_SHORT).show()
                      }
                    }
                  },
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "默认预设决定新会话使用的 Agent 组合；已开始的会话锁定原组合。",
        style = DshTheme.type.caption,
        color = DshTheme.colors.textSubtle,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    }
  }
}


/** 检查更新详情页：当前版本 → 检查 → 结果（已有新版 / 已是最新 / 网络失败）。 */
@Composable
fun CheckUpdatesDetailPage(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var checking by remember { mutableStateOf(false) }
  var result by remember { mutableStateOf<CheckUpdateResult?>(null) }
  var retryTick by remember { mutableStateOf(0) }

  LaunchedEffect(retryTick) {
    scope.launch {
      try {
        checking = true
        result = null
        val current = BuildConfig.VERSION_NAME
        val server = (context.applicationContext as NodeApp).prefs.serverUrl.value
        val config = AppUpdateConfig(owner = "aqiyoung", repo = "deepseek-harness", serverUrl = server)
        val http = okhttp3.OkHttpClient.Builder()
          .followRedirects(false)
          .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
          .build()
        val core = AppUpdateCore(config)
        val r = core.check(http, current, channel = "stable")
        checking = false
        result = if (r != null) {
          if (r.hasUpdate) CheckUpdateResult.UpdateAvailable(r)
          else CheckUpdateResult.UpToDate(r)
        } else {
          CheckUpdateResult.Failure
        }
      } catch (e: Exception) {
        checking = false
        result = CheckUpdateResult.Failure
      }
    }
  }

  DshDetailFrame(title = "检查更新", onBack = onBack) {
    Spacer(modifier = Modifier.height(6.dp))

    DshSoftPanel {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "当前版本",
            style = DshTheme.type.body,
            color = DshTheme.colors.text,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = BuildConfig.VERSION_NAME,
            style = DshTheme.type.caption,
            color = DshTheme.colors.textSubtle,
            maxLines = 1,
          )
        }
        HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
        Text(
          text = "检查 DeepSeek Harness 服务端是否有新版本可下载。",
          style = DshTheme.type.caption,
          color = DshTheme.colors.textSubtle,
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    when (val current = result) {
      null -> {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
          if (checking) {
            CircularProgressIndicator(color = DshTheme.colors.primary, strokeWidth = 2.dp)
          }
        }
        DshPrimaryButton(
          text = if (checking) "检查中…" else "检查更新",
          onClick = { retryTick++ },
          enabled = !checking,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      is CheckUpdateResult.Failure -> {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
          Text(
            text = "无法检查更新，请检查网络后重试",
            style = DshTheme.type.body,
            color = DshTheme.colors.warning,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        DshSecondaryButton(
          text = "重新检查",
          onClick = { retryTick++ },
          modifier = Modifier.fillMaxWidth(),
        )
      }
      is CheckUpdateResult.UpToDate -> {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
          DshStatusPill(text = "已是最新版本", status = DshStatus.Success)
        }
        Text(
          text = "当前版本已是最新，无需更新。",
          style = DshTheme.type.caption,
          color = DshTheme.colors.textSubtle,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
          textAlign = TextAlign.Center,
        )
      }
      is CheckUpdateResult.UpdateAvailable -> {
        val r = current.result
        DshSoftPanel {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              DshStatusPill(text = "有新版本", status = DshStatus.Success)
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = r.tagName,
                style = DshTheme.type.body,
                color = DshTheme.colors.success,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            if (r.releaseName != r.tagName) {
              Text(text = r.releaseName, style = DshTheme.type.caption, color = DshTheme.colors.textMuted)
            }
            if (!r.releaseNotes.isNullOrBlank()) {
              Text(
                text = "更新内容：",
                style = DshTheme.type.captionSmall,
                color = DshTheme.colors.textSubtle,
              )
              Text(
                text = r.releaseNotes.substring(0, minOf(r.releaseNotes.length, 300)),
                style = DshTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = DshTheme.colors.textMuted,
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
        DshPrimaryButton(
          text = "前往下载页",
          onClick = { openInBrowser(context, r.releaseUrl) },
          modifier = Modifier.fillMaxWidth(),
        )
        if (r.apkDownloadUrl != null) {
          Spacer(modifier = Modifier.height(8.dp))
          DshSecondaryButton(
            text = "直接下载 APK",
            onClick = { startApkDownload(context, r.apkDownloadUrl, r.tagName) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (r.isCritical) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "此版本为重要更新，建议立即升级。",
            style = DshTheme.type.caption,
            color = DshTheme.colors.danger,
            textAlign = TextAlign.Center,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
      text = "App 更新需通过 GitHub Release 页手动下载安装。",
      style = DshTheme.type.captionSmall,
      color = DshTheme.colors.textSubtle,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
      textAlign = TextAlign.Center,
    )
  }
}

private sealed class CheckUpdateResult {
  object Failure : CheckUpdateResult()
  data class UpToDate(val result: AppUpdateResult) : CheckUpdateResult()
  data class UpdateAvailable(val result: AppUpdateResult) : CheckUpdateResult()
}

private fun openInBrowser(context: android.content.Context, url: String) {
  val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
  runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    .onFailure { Toast.makeText(context, "无法打开浏览器", android.widget.Toast.LENGTH_SHORT).show() }
}

private fun startApkDownload(context: android.content.Context, url: String, tag: String) {
  val name = "dsh-android-" + tag + ".apk"
  val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
    setTitle("DeepSeek Harness " + tag)
    setDescription("正在下载新版本")
    setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
  }
  runCatching {
    (context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(request)
    Toast.makeText(context, "开始下载：" + name, android.widget.Toast.LENGTH_SHORT).show()
  }.onFailure {
    Toast.makeText(context, "下载失败：" + it.message, android.widget.Toast.LENGTH_SHORT).show()
  }
}