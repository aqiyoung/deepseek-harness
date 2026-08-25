package ai.deepseek.harness

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import java.util.Locale
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ai.deepseek.harness.ui.design.DshDetailFrame
import ai.deepseek.harness.ui.design.DshDesignTheme
import ai.deepseek.harness.ui.design.DshPlainIconButton
import ai.deepseek.harness.ui.design.DshPrimaryButton
import ai.deepseek.harness.ui.design.DshScaffold
import ai.deepseek.harness.ui.design.DshSectionLabel
import ai.deepseek.harness.ui.design.DshSettingsRow
import ai.deepseek.harness.ui.design.DshSoftPanel
import ai.deepseek.harness.ui.design.DshStatus
import ai.deepseek.harness.ui.design.DshStatusPill
import ai.deepseek.harness.ui.design.DshTheme

/**
 * DeepSeek Harness 安卓客户端（Kotlin + Jetpack Compose）。
 *
 * 原生登录页（服务器地址/账号/密码/记住密码）
 *   → 会话 Cookie 注入 WebView
 *   → 完整承载 Web 手机界面（dsh-web-ui-mobile 自适应插件）。
 * 顶栏 ☰ 打开 Web 会话列表侧边栏，右上 ⚙ 进入原生气泡设置页。
 */
class MainActivity : ComponentActivity() {

  private val prefs by lazy { (application as NodeApp).prefs }

  private val loggedInState = mutableStateOf<Boolean?>(null)
  private val sidebarOpenState = mutableStateOf(false)
  private val settingsOpenTick = mutableStateOf(0)
  private val loginError = mutableStateOf<String?>(null)
  private val loggingIn = mutableStateOf(false)

  private var filePathCallback: ValueCallback<Array<Uri>>? = null
  private var webViewRef: WebView? = null

  /** 主文档当前 host（UI 线程在 onPageStarted 更新），供 JS 桥做来源门禁。 */
  @Volatile
  private var webHost: String? = null

  private fun bridgeAllowed(): Boolean {
    val serverHost = Uri.parse(prefs.serverUrl.value).host?.lowercase() ?: return false
    return webHost == serverHost
  }

  private val http by lazy { OkHttpClient() }

  private val fileChooserLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val callback = filePathCallback
      filePathCallback = null
      val uris: Array<Uri> =
        WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data) ?: arrayOf()
      callback?.onReceiveValue(uris)
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 会话过期（RPC 遇到 302/401/403）时自动回到登录页，避免设置页停留在"已登录但报错"的状态。
    (application as NodeApp).dsh.onUnauthorized = {
      runOnUiThread { if (loggedInState.value == true) performLogout(expired = true) }
    }

    if (loggedInState.value == null) {
      val hasCookie = !prefs.getSessionCookie().isNullOrEmpty()
      loggedInState.value = prefs.isLoggedIn.value && hasCookie
    }

    setContent {
      val themeMode by prefs.appearanceThemeMode.collectAsState()
      val dark = themeMode.isDark(systemDark = isSystemInDarkTheme())
      SideEffect {
        // 沉浸式状态栏：edge-to-edge + 状态栏透明，窗口底色由主题控制
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)
          .isAppearanceLightStatusBars = !dark
      }
      LaunchedEffect(prefs.appLanguage.collectAsState().value) {
        val tag = prefs.appLanguage.value.languageTag
        AppCompatDelegate.setApplicationLocales(
          if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
      }
      DshDesignTheme(dark = dark) {
        val loggedIn by loggedInState
        if (loggedIn == true) {
          ShellRoot(
            onNeedLogin = { expired -> performLogout(expired) },
            onFileChoose = { callback, intent ->
              // 上一次选择未完成时先取消旧回调，避免覆盖后旧 ValueCallback 永不回收。
              filePathCallback?.onReceiveValue(null)
              filePathCallback = callback
              try {
                fileChooserLauncher.launch(intent)
              } catch (e: Exception) {
                filePathCallback = null
                Toast.makeText(applicationContext, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
              }
            },
            onDownload = { url, ua, disposition, mime ->
              startDownload(url, ua, disposition, mime)
            },
          )
        } else {
          LoginScreen()
        }
      }
    }
  }

  // ── 登录页 ──

  @Composable
  private fun LoginScreen() {
    val server by prefs.serverUrl.collectAsState()
    var serverValue by remember { mutableStateOf(server) }
    var userValue by remember { mutableStateOf(prefs.sessionUser.value) }
    var passValue by remember { mutableStateOf(prefs.getRememberedPassword() ?: "") }
    var rememberPwd by remember { mutableStateOf(!(prefs.getRememberedPassword() ?: "").isNullOrEmpty()) }
    val loading = loggingIn.value
    val err = loginError.value

    DshScaffold(contentPadding = PaddingValues(horizontal = 24.dp)) {
      Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(
          modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Spacer(modifier = Modifier.height(70.dp))
          Surface(
            modifier = Modifier.size(78.dp),
            shape = CircleShape,
            color = DshTheme.colors.surfaceRaised,
            contentColor = DshTheme.colors.text,
            border = BorderStroke(1.dp, DshTheme.colors.border),
          ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Image(
                painter = painterResource(R.drawable.login_logo_black),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                colorFilter = ColorFilter.tint(DshTheme.colors.text),
              )
            }
          }
          Spacer(modifier = Modifier.height(26.dp))
          Text(
            text = "DeepSeek Harness",
            style = DshTheme.type.display.copy(fontSize = 31.sp, lineHeight = 36.sp),
            color = DshTheme.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "登录到你的 DSH 服务器",
            style = DshTheme.type.body,
            color = DshTheme.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(28.dp))
          DshSoftPanel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              OutlinedTextField(
                value = serverValue,
                onValueChange = { serverValue = it },
                label = { Text("服务器地址") },
                singleLine = true,
                placeholder = { Text("https://dsh.example.com") },
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedTextField(
                value = userValue,
                onValueChange = { userValue = it },
                label = { Text("账号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedTextField(
                value = passValue,
                onValueChange = { passValue = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = rememberPwd, onCheckedChange = { rememberPwd = it })
                Text("记住密码", style = DshTheme.type.body, color = DshTheme.colors.textMuted)
              }
              if (!err.isNullOrEmpty()) {
                Text(err, color = DshTheme.colors.danger, style = DshTheme.type.caption)
              }
            }
          }
          Spacer(modifier = Modifier.height(24.dp))
        }
        DshPrimaryButton(
          text = if (loading) "登录中…" else "登 录",
          onClick = { submitLogin(serverValue, userValue, passValue, rememberPwd) },
          enabled = !loading,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "提示：服务器需部署 DeepSeek Harness Web 端",
          style = DshTheme.type.captionSmall,
          color = DshTheme.colors.textSubtle,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  private fun submitLogin(serverInput: String, userInput: String, password: String, rememberPwd: Boolean) {
    loginError.value = null
    val srv = normalizeServerUrl(serverInput)
    if (srv == null) {
      loginError.value = "服务器地址无效：必须以 https:// 开头"
      return
    }
    loggingIn.value = true
    val usr = userInput.trim()
    lifecycleScope.launch(Dispatchers.Main) {
      val outcome = withContext(Dispatchers.IO) { sessionLogin(srv, usr, password) }
      loggingIn.value = false
      val cookie = outcome.cookie
      if (cookie != null) {
        prefs.setServerUrl(srv)
        prefs.setLoggedIn(true, usr)
        prefs.setSessionCookie(cookie)
        prefs.setRememberedPassword(if (rememberPwd) password else null)
        CookieManager.getInstance().setCookie(srv, "dsh_session=" + cookie)
        CookieManager.getInstance().flush()
        loggedInState.value = true
      } else {
        loginError.value = when (outcome.failure) {
          LoginFailure.NETWORK -> "网络错误：无法连接到服务器"
          LoginFailure.AUTH -> "登录失败：账号或密码不正确"
          else -> "登录失败：请检查服务器地址、账号或密码"
        }
      }
    }
  }

  private enum class LoginFailure { NETWORK, AUTH, SERVER }

  private data class LoginOutcome(val cookie: String?, val failure: LoginFailure? = null)

  private suspend fun sessionLogin(server: String, user: String, password: String): LoginOutcome =
    withContext(Dispatchers.IO) {
      try {
        // 用 kotlinx.serialization 构造请求体，控制字符/引号不再破坏报文。
        val body = buildJsonObject {
          put("user", user)
          put("password", password)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(server + "/api/session-login").post(body).build()
        http.newCall(req).execute().use { resp ->
          val cookie = resp.headers("set-cookie").firstOrNull { it.contains("dsh_session=") }
            ?.substringAfter("dsh_session=")
            ?.substringBefore(";")
          if (!resp.isSuccessful) {
            val kind = if (resp.code == 401 || resp.code == 403) LoginFailure.AUTH else LoginFailure.SERVER
            return@use LoginOutcome(null, kind)
          }
          if (cookie.isNullOrEmpty()) return@use LoginOutcome(null, LoginFailure.SERVER)
          LoginOutcome(cookie)
        }
      } catch (e: Exception) {
        LoginOutcome(null, LoginFailure.NETWORK)
      }
    }

  fun performLogout(expired: Boolean) {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    prefs.setLoggedIn(false)
    prefs.setSessionCookie("")
    (application as NodeApp).dsh.invalidate()
    loginError.value = if (expired) "会话已过期，请重新登录" else null
    loggedInState.value = false
  }

  /** 应用服务器切换：先清空存量 Cookie 再换地址（防旧凭据被注入新域名），记住密码时静默重登。 */
  fun applyServerChange(rawInput: String) {
    val raw = normalizeServerUrl(rawInput)
    if (raw == null) {
      Toast.makeText(this, "服务器地址无效：必须以 https:// 开头", Toast.LENGTH_LONG).show()
      return
    }
    if (raw == prefs.serverUrl.value) return
    // P0：必须在 serverUrl 生效前清空旧服务器的会话 Cookie，
    // 否则 LaunchedEffect(serverUrl) 会把旧 dsh_session 注入新域名并随请求跨域发送。
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    prefs.setSessionCookie("")
    (application as NodeApp).dsh.invalidate()
    prefs.setServerUrl(raw)
    val usr = prefs.sessionUser.value
    val pwd = prefs.getRememberedPassword() ?: ""
    if (usr.isNotEmpty() && pwd.isNotEmpty()) {
      lifecycleScope.launch(Dispatchers.Main) {
        val outcome = withContext(Dispatchers.IO) { sessionLogin(raw, usr, pwd) }
        val ck = outcome.cookie
        if (ck != null) {
          prefs.setLoggedIn(true, usr)
          prefs.setSessionCookie(ck)
          CookieManager.getInstance().setCookie(raw, "dsh_session=" + ck)
          CookieManager.getInstance().flush()
          refreshShell()
          Toast.makeText(this@MainActivity, "已切换并登录：" + raw, Toast.LENGTH_SHORT).show()
        } else {
          performLogout(expired = false)
          Toast.makeText(this@MainActivity, "已切换服务器，自动登录失败，请重新登录", Toast.LENGTH_SHORT).show()
        }
      }
    } else {
      // 没有记住的密码：直接回原生登录页，避免把未认证的 WebView 指向新服务器。
      performLogout(expired = false)
      Toast.makeText(this, "已切换服务器，请重新登录", Toast.LENGTH_SHORT).show()
    }
  }

  // ── Web 壳（原生顶栏 + WebView + 设置覆盖层）──

  @Composable
  private fun ShellRoot(
    onNeedLogin: (Boolean) -> Unit,
    onFileChoose: (ValueCallback<Array<Uri>>, Intent) -> Unit,
    onDownload: (String, String, String, String) -> Unit,
  ) {
    var showSettings by remember { mutableStateOf(false) }
    var settingsRoute by remember { mutableStateOf<SettingRoute?>(null) }
    LaunchedEffect(settingsOpenTick.value) {
      if (settingsOpenTick.value > 0) showSettings = true
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().background(DshTheme.colors.canvas)) {
      // 完全无顶栏（v1.0.67）— 侧边栏通过 Web 边缘左滑打开（setupTouch 已支持），原生设置可从侧边栏 logoRow 齿轮进入
      Box(modifier = Modifier.weight(1f).fillMaxSize()) {
        ShellScreen(
          backEnabled = !showSettings,
          onNeedLogin = onNeedLogin,
          onFileChoose = onFileChoose,
          onDownload = onDownload,
        )
        if (showSettings) {
          SettingsOverlay(
            route = settingsRoute,
            onRouteChange = { settingsRoute = it },
            onClose = { showSettings = false },
            onRefresh = {
              showSettings = false
              settingsRoute = null
              refreshShell()
            },
          )
        }
      }
    }

    BackHandler(enabled = showSettings) {
      if (settingsRoute != null) settingsRoute = null else showSettings = false
    }
  }

  /** 打开 Web UI 自带的侧边栏（含会话列表），与 v1.0.52 行为一致。 */
  private fun toggleWebSidebar() {
    webViewRef?.evaluateJavascript(
      "(function(){ var sb=document.querySelector('.hHd-Xa_root'); var t=sb&&sb.querySelector('.hHd-Xa_toggle'); if(t){t.click()} })()",
      null,
    )
  }

  // ── 设置页（OpenClaw 分组样式：首页 + 二级详情页）──

  private enum class SettingRoute { Server, Models, Plugins, Presets, Theme, Language, Licenses }

  @Composable
  private fun SettingsOverlay(
    route: SettingRoute?,
    onRouteChange: (SettingRoute?) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
  ) {
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = DshTheme.colors.canvas) {
      Column(modifier = Modifier.fillMaxSize()) {
        when (route) {
          null -> SettingsHome(
            onOpenRoute = onRouteChange,
            onRefresh = onRefresh,
            onLogoutRequest = { showLogoutConfirm = true },
            onClose = onClose,
          )
          SettingRoute.Server -> ServerDetailPage(onBack = { onRouteChange(null) })
          SettingRoute.Models -> ai.deepseek.harness.ui.ModelsDetailPage(onBack = { onRouteChange(null) })
          SettingRoute.Plugins -> ai.deepseek.harness.ui.PluginsDetailPage(onBack = { onRouteChange(null) })
          SettingRoute.Presets -> ai.deepseek.harness.ui.PresetsDetailPage(onBack = { onRouteChange(null) })
          SettingRoute.Theme -> ThemeDetailPage(onBack = { onRouteChange(null) })
          SettingRoute.Language -> LanguageDetailPage(onBack = { onRouteChange(null) })
          SettingRoute.Licenses -> LicensesDetailPage(onBack = { onRouteChange(null) })
        }
      }
    }

    if (showLogoutConfirm) {
      AlertDialog(
        onDismissRequest = { showLogoutConfirm = false },
        title = { Text("退出登录") },
        text = { Text("将清除本机的会话与登录状态。") },
        confirmButton = {
          TextButton(onClick = {
            showLogoutConfirm = false
            performLogout(expired = false)
          }) { Text("退出", color = DshTheme.colors.danger) }
        },
        dismissButton = {
          TextButton(onClick = { showLogoutConfirm = false }) {
            Text("取消")
          }
        },
      )
    }
  }

  @Composable
  private fun SettingsHome(
    onOpenRoute: (SettingRoute) -> Unit,
    onRefresh: () -> Unit,
    onLogoutRequest: () -> Unit,
    onClose: () -> Unit,
  ) {
    val serverUrl by prefs.serverUrl.collectAsState()
    val sessionUser by prefs.sessionUser.collectAsState()
    val themeMode by prefs.appearanceThemeMode.collectAsState()
    val appLanguage by prefs.appLanguage.collectAsState()

    // 联机状态：探测服务器连通性（HEAD 根地址，能拿到任意 HTTP 响应即视为在线）
    var online by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(serverUrl) {
      online = withContext(Dispatchers.IO) {
        runCatching { (application as NodeApp).dsh.ping() }.getOrDefault(false)
      }
    }

    // DSH 服务摘要（当前模型 / 插件启用数 / 默认预设），失败静默显示 "-"
    data class DshSummary(val model: String, val plugins: String, val preset: String)
    var dshSummary by remember { mutableStateOf<DshSummary?>(null) }
    LaunchedEffect(Unit) {
      val r = withContext(Dispatchers.IO) {
        runCatching {
          val dsh = (application as NodeApp).dsh
          val models = runCatching { dsh.models() }.getOrNull()
          val plugins = runCatching { dsh.plugins() }.getOrNull()
          val presets = runCatching { dsh.presets() }.getOrNull()
          DshSummary(
            models?.let { it.currentModel.ifBlank { "未选择" } } ?: "-",
            plugins?.let { list -> list.count { p -> p.enabled }.toString() + "/" + list.size.toString() + " 启用" } ?: "-",
            presets?.let { list -> list.firstOrNull { it.isDefault }?.name ?: if (list.isEmpty()) "无" else "-" } ?: "-",
          )
        }
      }
      val s = r.getOrNull() ?: return@LaunchedEffect
      dshSummary = s
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp),
    ) {
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = DshTheme.spacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DshPlainIconButton(
          icon = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "关闭",
          onClick = onClose,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "设置", style = DshTheme.type.title, color = DshTheme.colors.text)
      }

      Spacer(modifier = Modifier.height(6.dp))

      DshSectionLabel("连接")
      DshSoftPanel {
        Column {
          DshSettingsRow(
            title = "服务器地址",
            value = serverUrl.removePrefix("https://").removePrefix("http://"),
            onClick = { onOpenRoute(SettingRoute.Server) },
          )
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(text = "联机状态", style = DshTheme.type.body, color = DshTheme.colors.text, modifier = Modifier.weight(1f))
            DshStatusPill(
              text = if (online == null) "检测中…" else if (online == true) "已连接" else "未连接",
              status = if (online == null) DshStatus.Neutral else if (online == true) DshStatus.Success else DshStatus.Danger,
            )
          }
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          DshSettingsRow(title = "刷新页面", onClick = onRefresh)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      DshSectionLabel("DSH 服务")
      DshSoftPanel {
        Column {
          DshSettingsRow(
            title = "模型",
            value = dshSummary?.model ?: "-",
            onClick = { onOpenRoute(SettingRoute.Models) },
          )
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          DshSettingsRow(
            title = "插件",
            value = dshSummary?.plugins ?: "-",
            onClick = { onOpenRoute(SettingRoute.Plugins) },
          )
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          DshSettingsRow(
            title = "Agent 预设",
            value = dshSummary?.preset ?: "-",
            onClick = { onOpenRoute(SettingRoute.Presets) },
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      DshSectionLabel("通用设置")
      DshSoftPanel {
        Column {
          DshSettingsRow(
            title = "主题",
            value = themeDisplayLabel(themeMode),
            onClick = { onOpenRoute(SettingRoute.Theme) },
          )
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          DshSettingsRow(
            title = "语言",
            value = languageDisplayLabel(appLanguage),
            onClick = { onOpenRoute(SettingRoute.Language) },
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      DshSectionLabel("账户")
      DshSoftPanel {
        Column {
          DshSettingsRow(title = "账号", value = sessionUser.ifBlank { "-" })
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          DshSettingsRow(title = "退出登录", danger = true, onClick = onLogoutRequest)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      DshSectionLabel("关于")
      DshSoftPanel {
        Column {
          DshSettingsRow(title = "版本", value = BuildConfig.VERSION_NAME)
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          DshSettingsRow(title = "开源许可证", onClick = { onOpenRoute(SettingRoute.Licenses) })
        }
      }

      Spacer(modifier = Modifier.height(14.dp))
    }

  }

  @Composable
  private fun ServerDetailPage(onBack: () -> Unit) {
    val current = prefs.serverUrl.collectAsState().value
    var serverValue by remember(current) { mutableStateOf(current) }
    val changed = serverValue.trim().removeSuffix("/") != current

    DshDetailFrame(title = "服务器地址", onBack = onBack) {
      Spacer(modifier = Modifier.height(6.dp))
      DshSoftPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = serverValue,
            onValueChange = { serverValue = it },
            label = { Text("DSH 服务器地址") },
            placeholder = { Text("https://dsh.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            text = "保存后会清除当前会话；若已记住密码将自动重新登录，否则需要重新登录。",
            style = DshTheme.type.caption,
            color = DshTheme.colors.textSubtle,
          )
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
      DshPrimaryButton(
        text = "保存并重连",
        enabled = changed,
        onClick = {
          applyServerChange(serverValue)
          onBack()
        },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }

  @Composable
  private fun ThemeDetailPage(onBack: () -> Unit) {
    val themeMode by prefs.appearanceThemeMode.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    DshDetailFrame(title = "主题", onBack = onBack) {
      Spacer(modifier = Modifier.height(6.dp))
      DshSoftPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          ThemeSegmented(
            selected = themeMode,
            onSelect = { mode ->
              prefs.setAppearanceThemeMode(mode)
              scope.launch {
                runCatching { (context.applicationContext as NodeApp).dsh.updateThemePreference(mode.rawValue) }
              }
            },
          )
          Text(
            text = "跟随系统：亮暗随系统深色模式自动切换。仅影响 App 原生界面（登录页、顶栏、设置），网页内容由服务器主题决定。",
            style = DshTheme.type.caption,
            color = DshTheme.colors.textSubtle,
          )
        }
      }
    }
  }

  @Composable
  private fun LanguageDetailPage(onBack: () -> Unit) {
    val appLanguage by prefs.appLanguage.collectAsState()
    val activityContext = LocalContext.current

    DshDetailFrame(title = "语言", onBack = onBack) {
      Spacer(modifier = Modifier.height(6.dp))
      DshSoftPanel {
        Column {
          AppLanguage.entries.forEachIndexed { index, language ->
            if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  prefs.saveAppLanguage(language)
                  // ComponentActivity 不随 AppCompatDelegate.setApplicationLocales 自动重建，显式重建使切换立即生效。
                  (activityContext as? android.app.Activity)?.recreate()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = languageDisplayLabel(language),
                style = DshTheme.type.body,
                color = DshTheme.colors.text,
                modifier = Modifier.weight(1f),
              )
              if (language == appLanguage) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = DshTheme.colors.success,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "切换语言后界面会立即应用并重启当前页面。",
        style = DshTheme.type.caption,
        color = DshTheme.colors.textSubtle,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    }
  }

  @Composable
  private fun LicensesDetailPage(onBack: () -> Unit) {
    var notices by remember { mutableStateOf<List<AndroidLicenseNotice>?>(null) }
    LaunchedEffect(Unit) {
      // 资产文件读取放后台线程，避免组合期主线程磁盘 IO 卡顿。
      notices = withContext(Dispatchers.IO) {
        runCatching { loadAndroidLicenseNotices(assets) }.getOrDefault(emptyList())
      }
    }

    DshDetailFrame(title = "开源许可证", onBack = onBack) {
      val current = notices
      when {
        current == null -> Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(color = DshTheme.colors.primary, strokeWidth = 2.dp)
        }
        current.isEmpty() -> Text(
          text = "暂未打包第三方许可证文件。",
          style = DshTheme.type.body,
          color = DshTheme.colors.textMuted,
          modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
          textAlign = TextAlign.Center,
        )
        else -> current.forEach { notice ->
        DshSectionLabel(notice.title)
        DshSoftPanel {
          Text(
            text = notice.text,
            style = DshTheme.type.mono.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = DshTheme.colors.textMuted,
          )
        }
            Spacer(modifier = Modifier.height(14.dp))
          }
        }
      }
    }

  private fun themeDisplayLabel(mode: AppearanceThemeMode): String = when (mode) {
    AppearanceThemeMode.System -> "跟随系统"
    AppearanceThemeMode.Light -> "浅色"
    AppearanceThemeMode.Dark -> "深色"
  }

  private fun languageDisplayLabel(language: AppLanguage): String =
    if (language == AppLanguage.System) "跟随系统" else language.displayName

  @Composable
  private fun ThemeSegmented(
    selected: AppearanceThemeMode,
    onSelect: (AppearanceThemeMode) -> Unit,
  ) {
    val options = listOf(
      AppearanceThemeMode.System to "跟随系统",
      AppearanceThemeMode.Light to "浅色",
      AppearanceThemeMode.Dark to "深色",
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      options.forEach { (mode, label) ->
        val selectedHere = mode == selected
        Surface(
          onClick = { onSelect(mode) },
          shape = RoundedCornerShape(DshTheme.radii.control),
          color = if (selectedHere) DshTheme.colors.surfacePressed else Color.Transparent,
          border = BorderStroke(1.dp, if (selectedHere) DshTheme.colors.borderStrong else DshTheme.colors.border),
          modifier = Modifier.weight(1f).heightIn(min = 40.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = label,
              style = DshTheme.type.label,
              color = if (selectedHere) DshTheme.colors.text else DshTheme.colors.textMuted,
              maxLines = 1,
            )
          }
        }
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  @Composable
  private fun ShellScreen(
    backEnabled: Boolean,
    onNeedLogin: (Boolean) -> Unit,
    onFileChoose: (ValueCallback<Array<Uri>>, Intent) -> Unit,
    onDownload: (String, String, String, String) -> Unit,
  ) {
    val context = LocalContext.current
    val serverUrl by prefs.serverUrl.collectAsState()

    val webView = remember {
      WebView(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
          android.view.ViewGroup.LayoutParams.MATCH_PARENT,
          android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.allowFileAccess = false
        settings.userAgentString = settings.userAgentString + " DshAndroid/" + BuildConfig.VERSION_NAME
        // 网页内容深色模式：跟随系统深色主题自动切换（API 29+），避免网页永远强制亮色
        settings.forceDark = android.webkit.WebSettings.FORCE_DARK_AUTO
        // 会话凭据是第一方 Cookie；关闭第三方 Cookie 收窄 CSRF/跟踪暴露面。
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

        webViewClient = object : WebViewClient() {
          override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
            // 冻结版：App 内的移动 UI 适配插件使用 APK 内置副本，不受服务器更新影响。
            // 仅对当前配置服务器的 https 请求提供，避免向任意 frame/主机泄漏内置资源。
            val serverHost = Uri.parse(prefs.serverUrl.value).host?.lowercase()
            val reqHost = request.url.host?.lowercase()
            if (serverHost != null && reqHost == serverHost &&
                request.url.toString().contains("dsh-web-ui-mobile/client.js")) {
              try {
                val input = assets.open("dsh-mobile-client.js")
                return android.webkit.WebResourceResponse("application/javascript", "utf-8", input)
              } catch (_: Exception) { /* fallback to server */ }
            }
            return null
          }

          override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // 每次导航实时读取服务器地址，避免切换服务器后闭包里的旧域名误判外链
            return handleExternalNavigation(context, prefs.serverUrl.value, request.url)
          }
          override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            // 被踢回本服务器 nginx 登录页 → 会话失效（精确匹配 path，避免误伤 /docs/login 等）
            val serverHost = Uri.parse(prefs.serverUrl.value).host?.lowercase()
            val u = Uri.parse(url)
            val kickedToLogin = u.host != null && u.host.equals(serverHost, ignoreCase = true) &&
              u.path?.removeSuffix("/") == "/login"
            if (kickedToLogin) onNeedLogin(true)
          }
          override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            webHost = Uri.parse(url).host?.lowercase()
          }
          override fun onPageFinished(view: WebView, url: String) {
            CookieManager.getInstance().flush()
          }

          override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!isFatalNetworkError(error)) return
            if (request.isForMainFrame == false) return
            val isZh = Locale.getDefault().language.startsWith("zh")
            val html = buildOfflinePage(isZh, isServiceUnavailable = false, BuildConfig.VERSION_NAME)
            view.loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null)
          }

          override fun onReceivedHttpError(
            view: WebView, request: WebResourceRequest,
            errorResponse: android.webkit.WebResourceResponse,
          ) {
            if (errorResponse.statusCode !in 502..504) return
            if (request.isForMainFrame == false) return
            val isZh = Locale.getDefault().language.startsWith("zh")
            val html = buildOfflinePage(isZh, isServiceUnavailable = true, BuildConfig.VERSION_NAME)
            view.loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null)
          }
        }
        webChromeClient = object : WebChromeClient() {
          override fun onShowFileChooser(
            view: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams,
          ): Boolean {
            val intent = params.createIntent().apply {
              addCategory(Intent.CATEGORY_OPENABLE)
              type = "*/*"
            }
            onFileChoose(callback, intent)
            return true
          }
        }
        setDownloadListener { url, ua, disposition, mime, _ ->
          onDownload(url, ua, disposition, mime)
        }
        addJavascriptInterface(AppBridge(), "DshAppBridge")
      }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
      val observer = LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_PAUSE -> { webView.onPause(); webView.pauseTimers() }
          Lifecycle.Event.ON_RESUME -> { webView.onResume(); webView.resumeTimers() }
          else -> Unit
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
        webViewRef = null
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
      }
    }

    // Cookie 注入 + 加载；服务器地址为空时绝不发起加载
    LaunchedEffect(serverUrl) {
      if (serverUrl.isBlank()) return@LaunchedEffect
      val ck = prefs.getSessionCookie()
      if (!ck.isNullOrEmpty()) {
        CookieManager.getInstance().setCookie(serverUrl, "dsh_session=" + ck)
        CookieManager.getInstance().flush()
      }
      webView.loadUrl(serverUrl)
    }

    AndroidView(
      factory = { webViewRef = webView; webView },
      modifier = Modifier.fillMaxSize(),
    )

    BackHandler(enabled = backEnabled) {
      if (webView.canGoBack()) webView.goBack() else finish()
    }
  }

  private fun handleExternalNavigation(context: Context, serverUrl: String, url: Uri): Boolean {
    val serverHost = Uri.parse(serverUrl).host ?: return false
    val targetHost = url.host
    val sameHost = targetHost != null && targetHost.equals(serverHost, ignoreCase = true)
    if (sameHost && (url.scheme == "https" || url.scheme == "http")) return false
    when (url.scheme) {
      "http", "https" -> {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
        return true
      }
      "intent" -> {
        runCatching {
          val parsed = Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME) ?: return@runCatching
          // 消毒：剥离 component/selector/extras，防 intent 重定向拉起任意组件。
          parsed.component = null
          parsed.selector = null
          parsed.action = Intent.ACTION_VIEW
          parsed.replaceExtras(android.os.Bundle())
          if (parsed.resolveActivity(context.packageManager) != null) {
            context.startActivity(parsed)
          }
        }
        return true
      }
    }
    return false
  }

  private fun startDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
    val parsed = Uri.parse(url)
    val scheme = parsed.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") {
      Toast.makeText(applicationContext, "不支持的下载地址", Toast.LENGTH_SHORT).show()
      return
    }
    try {
      val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
      // 仅当下载端点与会话服务器同域时才附带 dsh_session Cookie。
      val serverHost = Uri.parse(prefs.serverUrl.value).host?.lowercase()
      val sameHost = parsed.host?.lowercase() != null && parsed.host.equals(serverHost, ignoreCase = true)
      val request = DownloadManager.Request(parsed).apply {
        setMimeType(mimeType)
        setTitle(name)
        setDescription("DeepSeek Harness")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        addRequestHeader("User-Agent", userAgent)
        if (sameHost) {
          CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
        }
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
      }
      (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
      Toast.makeText(applicationContext, "开始下载：" + name, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
      Toast.makeText(applicationContext, "下载失败：" + (e.message ?: "未知错误"), Toast.LENGTH_SHORT).show()
    }
  }

  // ── 原生对话框（供 Web Bridge 调用）──

  fun showChangeServerDialog() {
    val input = EditText(this).apply {
      setText(prefs.serverUrl.value)
      inputType = InputType.TYPE_TEXT_VARIATION_URI
      setSingleLine(true)
    }
    android.app.AlertDialog.Builder(this)
      .setTitle("切换服务器")
      .setView(input)
      .setPositiveButton("保存") { _, _ -> applyServerChange(input.text.toString()) }
      .setNegativeButton("取消", null)
      .show()
  }

  fun showLicensesDialog() {
    val notices = loadAndroidLicenseNotices(assets)
    val text = TextView(this).apply {
      typeface = Typeface.MONOSPACE
      textSize = 11f
      setPadding(40, 30, 40, 30)
      text = notices.joinToString("\n\n") { n -> "── " + n.title + " ──\n" + n.text }
    }
    android.app.AlertDialog.Builder(this)
      .setTitle("软件许可证")
      .setView(text)
      .setPositiveButton("关闭", null)
      .show()
  }

  fun refreshShell() {
    CookieManager.getInstance().flush()
    val target = prefs.serverUrl.value
    if (target.isBlank()) return
    webViewRef?.loadUrl(target)
  }

  // ── AppBridge：供 Web 设置里的 App 区块调用 ──

  private inner class AppBridge(
    val onChangeServer: () -> Unit = { runOnUiThread { showChangeServerDialog() } },
    val onClearLogin: () -> Unit = { runOnUiThread { performLogout(expired = false) } },
    val onLicenses: () -> Unit = { runOnUiThread { showLicensesDialog() } },
    val onRefresh: () -> Unit = { runOnUiThread { refreshShell() } },
  ) {
    /** 来源门禁：仅当前配置服务器的主文档可调用桥方法，跨域 iframe 一律忽略。 */
    private fun gated(): Boolean = bridgeAllowed()

    @JavascriptInterface
    fun getVersion(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun updateCheckUrl(): String =
      if (gated()) "https://api.github.com/repos/aqiyoung/deepseek-harness/releases/latest" else ""

    @JavascriptInterface
    fun changeServer() { if (gated()) onChangeServer() }

    @JavascriptInterface
    fun clearLogin() { if (gated()) onClearLogin() }

    @JavascriptInterface
    fun showLicenses() { if (gated()) onLicenses() }

    @JavascriptInterface
    fun refreshPage() { if (gated()) onRefresh() }

    @JavascriptInterface
    fun setSidebarOpen(open: Boolean) { if (gated()) runOnUiThread { sidebarOpenState.value = open } }

    @JavascriptInterface
    fun openAppSettings() { if (gated()) runOnUiThread { settingsOpenTick.value += 1 } }

  }

private fun isFatalNetworkError(error: WebResourceError): Boolean {
  // android.webkit.WebResourceError 常量在 SDK 里不可见，改用字面量
  return error.errorCode == -2 ||   // ERROR_HOST_LOOKUP
    error.errorCode == -3 ||        // ERROR_IO
    error.errorCode == -4 ||        // ERROR_TIMEOUT
    error.errorCode == -28 ||       // ERROR_FAILED
    error.errorCode == -12 ||       // ERROR_SSL
    error.errorCode == -13          // ERROR_BAD_URL
}

private fun buildOfflinePage(isZh: Boolean, isServiceUnavailable: Boolean, version: String): String {
  val (title, subtitle, hint) = if (isZh) {
    if (isServiceUnavailable) {
      Triple("\u670D\u52A1\u5668\u65F6\u671F\u4E0D\u53EF\u7528",
        "\u76EE\u6807\u670D\u52A1\u5668\u8FD4\u56DE\u4E86\u670D\u52A1\u7AEF\u9519\u8BE5\uFF085xx\uFF09\uFF0C\u8BF7\u7A0D\u540E\u518D\u8BD5\u3002",
        "\u70B9\u51FB\u91CD\u8BD5")
    } else {
      Triple("\u65E0\u6CD5\u8FDE\u63A5\u670D\u52A1\u5668",
        "\u8BF7\u68C0\u67E5\u7F51\u7EDC\uFF0C\u6216\u786E\u8BA4\u670D\u52A1\u5668\u5730\u5740 ${prefs.serverUrl.value} \u53EF\u8FBE\u3002",
        "\u70B9\u51FB\u91CD\u8BD5")
    }
  } else {
    if (isServiceUnavailable) {
      Triple("Server temporarily unavailable",
        "The target server returned a 5xx error. Please try again later.",
        "Retry")
    } else {
      Triple("Cannot connect to server",
        "Please check your network or confirm the server address is reachable.",
        "Retry")
    }
  }
  return """
<!doctype html>
<html lang="zh" data-theme="auto">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box}
html,body{height:100%}
body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
  display:flex;align-items:center;justify-content:center;
  background:var(--bg);color:var(--fg);transition:background .2s,color .2s}
@keyframes ping{0%{transform:scale(.8);opacity:.7}80%,100%{transform:scale(1.5);opacity:0}}
.container{max-width:420px;width:calc(100% - 32px);padding:32px 24px;border-radius:20px;
  background:var(--card);border:1px solid var(--border);box-shadow:0 8px 32px var(--shadow);
  text-align:center;animation:rise .35s cubic-bezier(.22,1,.36,1) both}
@keyframes rise{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:none}}
.ring-wrap{position:relative;width:84px;height:84px;margin:0 auto 18px;display:flex;
  align-items:center;justify-content:center;border-radius:50%;background:var(--ring-bg)}
.ring-pulse{position:absolute;inset:0;border-radius:50%;border:2px solid var(--fg);opacity:0;
  animation:ping 2.2s cubic-bezier(0,0,.2,1) infinite}
.icon{position:relative;width:36px;height:36px;color:var(--fg);opacity:.85}
.icon svg{width:100%;height:100%;display:block}
h1{font-size:20px;font-weight:600;line-height:1.4;margin-bottom:10px;letter-spacing:-.01em}
p{font-size:14px;line-height:1.6;color:var(--muted);margin-bottom:18px}
.address{font-size:12px;color:var(--muted);opacity:.75;word-break:break-all;margin-bottom:22px}
.retry{display:inline-flex;align-items:center;gap:8px;padding:12px 28px;border-radius:12px;
  border:1px solid var(--border);background:var(--btn-bg);color:var(--btn-fg);font-size:14px;
  font-weight:500;cursor:pointer;transition:transform .1s,background .15s;-webkit-tap-highlight-color:transparent}
.retry:active{transform:scale(.97);background:var(--btn-press)}
.retry .arrow{width:16px;height:16px}
.footer{margin-top:22px;padding-top:16px;border-top:1px solid var(--border);
  font-size:11px;color:var(--muted);opacity:.7;letter-spacing:.02em}
:root{--bg:#f5f5f7;--fg:#1d1d1f;--muted:#6e6e73;--card:#fff;--border:#e5e5ea;
  --shadow:rgba(0,0,0,.06);--ring-bg:#f0f0f5;--btn-bg:#f0f0f5;--btn-fg:#1d1d1f;--btn-press:#e5e5ea}
@media(prefers-color-scheme:dark){:root{--bg:#000;--fg:#f5f5f7;--muted:#a1a1a6;
  --card:#1c1c1e;--border:#38383a;--shadow:rgba(0,0,0,.4);--ring-bg:#2c2c2e;
  --btn-bg:#2c2c2e;--btn-fg:#f5f5f7;--btn-press:#3a3a3c}}
</style>
</head>
<body>
<div class="container">
  <div class="ring-wrap">
    <span class="ring-pulse"></span>
    <span class="icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
        <path d="M5 12.55a11 11 0 0 1 14.08 0"/>
        <path d="M1.42 9a16 16 0 0 1 21.16 0"/>
        <path d="M8.53 16.11a6 6 0 0 1 6.95 0"/>
        <line x1="12" y1="20" x2="12" y2="20"/>
      </svg>
    </span>
  </div>
  <h1>$title</h1>
  <p>$subtitle</p>
  <div class="address">${prefs.serverUrl.value}</div>
  <button class="retry" onclick="window.DshAppBridge.refreshPage()">
    <span class="arrow" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><path d="M20 4H7a5 5 0 0 0-4.6 7L1 14h3l.5 4L4 14"/></svg></span>
    $hint
  </button>
  <div class="footer">DeepSeek Harness Android v$version</div>
</div>
</body>
</html>
""".trimIndent()
}
}
