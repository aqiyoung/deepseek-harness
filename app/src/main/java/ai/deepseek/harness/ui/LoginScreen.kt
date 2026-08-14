package ai.deepseek.harness.ui

import ai.deepseek.harness.MainViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.deepseek.harness.ui.design.ClawDesignTheme
import ai.deepseek.harness.ui.design.ClawPrimaryButton
import ai.deepseek.harness.ui.design.ClawScaffold
import ai.deepseek.harness.ui.design.ClawTextField
import ai.deepseek.harness.ui.design.ClawTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** DSH web login endpoint.nginx returns 302 -> /login for anonymous GET /,
 *  but /api/session-login answers the credential check directly. */
private const val DSH_LOGIN_URL = "https://dsh.threel.site/api/session-login"

private val loginClient =
  OkHttpClient
    .Builder()
    .followRedirects(false)
    .build()

private sealed interface LoginResult {
  data class Success(val cookie: String?) : LoginResult

  data class Failure(val message: String) : LoginResult
}

@Composable
fun LoginScreen(
  viewModel: MainViewModel,
  onLoginSuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var isVerifying by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  ClawDesignTheme {
    ClawScaffold(modifier = modifier) {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(0.9f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "DeepSeek Harness",
            style = ClawTheme.type.display,
            color = ClawTheme.colors.text,
          )
          Text(
            text = "登录以继续使用",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }

        Box(modifier = Modifier.fillMaxWidth(0.9f).padding(top = 24.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ClawTextField(
              value = username,
              onValueChange = { username = it },
              placeholder = "用户名",
              label = "用户名",
              enabled = !isVerifying,
            )
            ClawTextField(
              value = password,
              onValueChange = { password = it },
              placeholder = "密码",
              label = "密码",
              enabled = !isVerifying,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions =
                KeyboardOptions(
                  autoCorrect = false,
                  keyboardType = KeyboardType.Password,
                  imeAction = ImeAction.Done,
                ),
            )
            ClawPrimaryButton(
              text = if (isVerifying) "验证中…" else "登 录",
              enabled = !isVerifying,
              onClick = {
                if (username.isBlank() || password.isBlank()) {
                  error = "请输入用户名和密码"
                  return@ClawPrimaryButton
                }
                error = null
                isVerifying = true
                scope.launch {
                  when (val result = dshAuthenticate(username.trim(), password)) {
                    is LoginResult.Success -> {
                      result.cookie?.let { viewModel.setSessionCookie(it) }
                      onLoginSuccess()
                    }
                    is LoginResult.Failure -> {
                      isVerifying = false
                      error = result.message
                    }
                  }
                }
              },
            )
            if (error != null) {
              Text(
                text = error!!,
                style = ClawTheme.type.caption,
                color = Color(0xFFE11D48),
              )
            }
          }
        }
      }
    }
  }
}

private suspend fun dshAuthenticate(
  user: String,
  password: String,
): LoginResult =
  withContext(Dispatchers.IO) {
    try {
      val json =
        """{"user":${JSONObject.quote(user)},"password":${JSONObject.quote(password)}}"""
      val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
      val request =
        Request
          .Builder()
          .url(DSH_LOGIN_URL)
          .post(body)
          .build()
      loginClient.newCall(request).use { call ->
        val response = call.execute()
        val cookie = response.headers("Set-Cookie").firstOrNull()
        if (response.isSuccessful) {
          LoginResult.Success(cookie)
        } else {
          LoginResult.Failure("用户名或密码错误")
        }
      }
    } catch (_: Exception) {
      LoginResult.Failure("网络错误，请重试")
    }
  }
