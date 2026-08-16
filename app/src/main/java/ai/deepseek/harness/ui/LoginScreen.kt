package ai.deepseek.harness.ui

import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepseek.harness.ui.design.DshDesignTheme
import ai.deepseek.harness.ui.design.DshPrimaryButton
import ai.deepseek.harness.ui.design.DshScaffold
import ai.deepseek.harness.ui.design.DshTextField
import ai.deepseek.harness.ui.design.DshTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val loginClient =
  OkHttpClient
    .Builder()
    .followRedirects(false)
    .build()

private sealed interface LoginResult {
  data class Success(val cookie: String?) : LoginResult

  data class Failure(val message: String) : LoginResult
}

private const val DEFAULT_SERVER = "https://dsh.threel.site"

@Composable
fun LoginScreen(
  viewModel: MainViewModel,
  onLoginSuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val serverUrl by viewModel.serverUrl.collectAsState()
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var isVerifying by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  // 强制浅色，对齐 DeepSeek 官网/DSH 网页版清爽气质。
  DshDesignTheme(dark = false) {
    DshScaffold(modifier = modifier) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // 黑色鲸鱼 logo
          Image(
            painter = painterResource(R.drawable.login_logo_black),
            contentDescription = "DeepSeek Harness",
            modifier = Modifier.size(100.dp),
          )

          Spacer(modifier = Modifier.height(20.dp))

          Text(
            text = "DeepSeek Harness",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A1A1A),
            textAlign = TextAlign.Center,
          )

          Text(
            text = "探索未至之境",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF6B7280),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
          )

          Spacer(modifier = Modifier.height(48.dp))

          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            DshTextField(
              value = serverUrl,
              onValueChange = { viewModel.setServerUrl(it) },
              placeholder = "https://dsh.threel.site",
              label = "服务器地址",
              enabled = !isVerifying,
              keyboardOptions =
                KeyboardOptions(
                  autoCorrect = false,
                  keyboardType = KeyboardType.Uri,
                  imeAction = ImeAction.Next,
                ),
            )

            DshTextField(
              value = username,
              onValueChange = { username = it },
              placeholder = "用户名",
              label = "用户名",
              enabled = !isVerifying,
              keyboardOptions =
                KeyboardOptions(
                  autoCorrect = false,
                  keyboardType = KeyboardType.Text,
                  imeAction = ImeAction.Next,
                ),
            )

            DshTextField(
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

            Spacer(modifier = Modifier.height(8.dp))

            DshPrimaryButton(
              text = if (isVerifying) "登录中…" else "登录",
              enabled = !isVerifying,
              modifier = Modifier.fillMaxWidth(),
              onClick = {
                if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                  error = "请填写服务器地址、用户名和密码"
                  return@DshPrimaryButton
                }
                error = null
                isVerifying = true
                scope.launch {
                  val url = serverUrl.trim().removeSuffix("/")
                  when (val result = dshAuthenticate(url, username.trim(), password)) {
                    is LoginResult.Success -> {
                      result.cookie?.let { viewModel.setSessionCookie(it) }
                      viewModel.setLoggedIn(true, username)
                      viewModel.connectDsh()
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
                fontSize = 13.sp,
                color = Color(0xFFE11D48),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }
    }
  }
}

private suspend fun dshAuthenticate(
  baseUrl: String,
  user: String,
  password: String,
): LoginResult =
  withContext(Dispatchers.IO) {
    try {
      val loginUrl = "$baseUrl/api/session-login"
      val json =
        """{"user":${JSONObject.quote(user)},"password":${JSONObject.quote(password)}}"""
      val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
      val request =
        Request
          .Builder()
          .url(loginUrl)
          .post(body)
          .build()
      loginClient.newCall(request).execute().use { response ->
        val cookie = response.headers("Set-Cookie").firstOrNull()
        if (response.isSuccessful) {
          LoginResult.Success(cookie)
        } else {
          LoginResult.Failure("用户名或密码错误")
        }
      }
    } catch (_: Exception) {
      LoginResult.Failure("网络错误，请检查服务器地址")
    }
  }
