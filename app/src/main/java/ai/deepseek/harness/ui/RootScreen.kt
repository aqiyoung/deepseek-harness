package ai.deepseek.harness.ui

import ai.deepseek.harness.MainViewModel
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** Chooses the login gate or the authenticated app shell from persisted auth state. */
@Composable
fun RootScreen(viewModel: MainViewModel) {
  val isLoggedIn by viewModel.isLoggedIn.collectAsState()
  val updateResult by viewModel.appUpdateResult.collectAsState()
  val updateToast by viewModel.appUpdateToast.collectAsState()
  val context = LocalContext.current

  // 手动检查结果的 Toast 提示。
  LaunchedEffect(updateToast) {
    updateToast?.let {
      Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
      viewModel.consumeAppUpdateToast()
    }
  }

  if (!isLoggedIn) {
    LoginScreen(
      viewModel = viewModel,
      onLoginSuccess = { viewModel.setLoggedIn(true) },
      modifier = Modifier.fillMaxSize(),
    )
    return
  }

  ShellScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())

  // 发现新版本时弹出更新框。
  updateResult?.let { result ->
    AppUpdateDialog(
      result = result,
      onOpenRelease = { viewModel.openAppUpdateRelease() },
      onDismiss = { viewModel.dismissAppUpdate() },
    )
  }
}

@Composable
private fun AppUpdateDialog(
  result: ai.deepseek.harness.update.GitHubUpdateResult,
  onOpenRelease: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("发现新版本 ${result.latestVersion}") },
    text = {
      val notes =
        result.releaseNotes
          ?.lines()
          ?.drop(1)
          ?.joinToString("\n")
          ?.trim()
          ?.takeIf { it.isNotEmpty() }
      Text(
        buildString {
          append("当前: ${ai.deepseek.harness.update.UpdateManager.currentVersionName}\n")
          append("最新: ${result.latestVersion}")
          if (notes != null) {
            append("\n\n更新内容:\n")
            append(if (notes.length > 400) notes.take(400) + "…" else notes)
          }
          if (result.isCritical) append("\n\n⚠️ 关键更新，建议尽快升级")
        },
      )
    },
    confirmButton = {
      TextButton(onClick = {
        onOpenRelease()
        onDismiss()
      }) {
        Text("前往更新")
      }
    },
    dismissButton =
      if (result.isCritical) {
        null
      } else {
        {
          TextButton(onClick = onDismiss) { Text("稍后") }
        }
      },
  )
}
