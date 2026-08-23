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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ai.deepseek.harness.NodeApp
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
  var tick by remember { mutableStateOf(0) }
  LaunchedEffect(tick) {
    result = null
    result = runCatching { load() }
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
        text = "加载失败：" + (current.exceptionOrNull()?.message ?: "未知错误"),
        style = DshTheme.type.body,
        color = DshTheme.colors.textMuted,
        textAlign = TextAlign.Center,
      )
      DshPrimaryButton(text = "重试", onClick = { tick++ })
    }
    else -> content(current.getOrThrow()) { tick++ }
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
                      runCatching { repo(context).selectModel(pid, option.modelId) }
                        .onSuccess {
                          Toast.makeText(context, "已切换到 ${option.modelName}", Toast.LENGTH_SHORT).show()
                          reload()
                        }
                        .onFailure { e ->
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
                else -> "加载中" to DshStatus.Warning
              }
              DshStatusPill(text = label, status = status)
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "插件参数（Bash、AgentLoop、WebSearch 等）请到网页端 设置 → 插件 调整。",
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
                      runCatching { repo(context).setDefaultPreset(preset.id) }
                        .onSuccess {
                          Toast.makeText(context, "默认预设已设为 ${preset.name}", Toast.LENGTH_SHORT).show()
                          reload()
                        }
                        .onFailure { e ->
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
