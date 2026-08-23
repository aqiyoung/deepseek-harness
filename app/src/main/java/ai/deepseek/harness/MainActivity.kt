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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  private val loginError = mutableStateOf<String?>(null)
  private val loggingIn = mutableStateOf(false)

  private var filePathCallback: ValueCallback<Array<Uri>>? = null
  private var webViewRef: WebView? = null

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

    if (loggedInState.value == null) {
      val hasCookie = !prefs.getSessionCookie().isNullOrEmpty()
      loggedInState.value = prefs.isLoggedIn.value && hasCookie
    }

    setContent {
      val themeMode by prefs.appearanceThemeMode.collectAsState()
      val dark = themeMode.isDark(systemDark = isSystemInDarkTheme())
      SideEffect {
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
    loggingIn.value = true
    val srv = serverInput.trim().removeSuffix("/")
    val usr = userInput.trim()
    lifecycleScope.launch(Dispatchers.IO) {
      val cookie = sessionLogin(srv, usr, password)
      lifecycleScope.launch(Dispatchers.Main) {
        loggingIn.value = false
        if (cookie != null) {
          prefs.setServerUrl(srv)
          prefs.setLoggedIn(true, usr)
          prefs.setSessionCookie(cookie)
          prefs.setRememberedPassword(if (rememberPwd) password else null)
          CookieManager.getInstance().setCookie(srv, "dsh_session=" + cookie)
          CookieManager.getInstance().flush()
          loggedInState.value = true
        } else {
          loginError.value = "登录失败：请检查服务器地址、账号或密码"
        }
      }
    }
  }

  private suspend fun sessionLogin(server: String, user: String, password: String): String? {
    return withContext(Dispatchers.IO) {
      try {
        val safeUser = user.replace("\\", "\\\\").replace("\"", "\\\"")
        val safePass = password.replace("\\", "\\\\").replace("\"", "\\\"")
        val body = ("{\"user\":\"" + safeUser + "\",\"password\":\"" + safePass + "\"}")
          .toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(server + "/api/session-login").post(body).build()
        http.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) return@use null
          resp.headers("set-cookie").firstOrNull { it.contains("dsh_session=") }
            ?.substringAfter("dsh_session=")
            ?.substringBefore(";")
        }
      } catch (e: Exception) {
        null
      }
    }
  }

  fun performLogout(expired: Boolean) {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    prefs.setLoggedIn(false)
    prefs.setSessionCookie("")
    loginError.value = if (expired) "会话已过期，请重新登录" else null
    loggedInState.value = false
  }

  /** 应用服务器切换：清 cookie → 记住密码时静默重登，否则刷新到登录页。 */
  fun applyServerChange(rawInput: String) {
    val raw = rawInput.trim().removeSuffix("/")
    val current = prefs.serverUrl.value
    if (raw.isEmpty() || raw == current) return
    prefs.setServerUrl(raw)
    CookieManager.getInstance().removeAllCookies(null)
    val usr = prefs.sessionUser.value
    val pwd = prefs.getRememberedPassword() ?: ""
    if (usr.isNotEmpty() && pwd.isNotEmpty()) {
      lifecycleScope.launch(Dispatchers.IO) {
        val ck = sessionLogin(raw, usr, pwd)
        lifecycleScope.launch(Dispatchers.Main) {
          if (ck != null) {
            prefs.setSessionCookie(ck)
            CookieManager.getInstance().setCookie(raw, "dsh_session=" + ck)
            refreshShell()
            Toast.makeText(this@MainActivity, "已切换并登录：" + raw, Toast.LENGTH_SHORT).show()
          } else {
            performLogout(expired = false)
            Toast.makeText(this@MainActivity, "已切换服务器，请重新登录", Toast.LENGTH_SHORT).show()
          }
        }
      }
    } else {
      refreshShell()
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

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().background(DshTheme.colors.canvas)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DshPlainIconButton(
          icon = Icons.Default.Menu,
          contentDescription = "打开会话列表",
          onClick = { toggleWebSidebar() },
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "DeepSeek Harness",
          style = DshTheme.type.title,
          color = DshTheme.colors.text,
          modifier = Modifier.weight(1f),
          maxLines = 1,
        )
        DshPlainIconButton(
          icon = Icons.Default.Settings,
          contentDescription = "打开设置",
          onClick = { showSettings = true },
        )
      }
      HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)

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
  ) {
    val serverUrl by prefs.serverUrl.collectAsState()
    val sessionUser by prefs.sessionUser.collectAsState()
    val themeMode by prefs.appearanceThemeMode.collectAsState()
    val appLanguage by prefs.appLanguage.collectAsState()

    // DSH 服务摘要（当前模型 / 插件启用数 / 默认预设），失败静默显示 "-"
    var dshSummary by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    LaunchedEffect(Unit) {
      val r = withContext(Dispatchers.IO) {
        runCatching {
          val dsh = (application as NodeApp).dsh
          val models = runCatching { dsh.models() }.getOrNull()
          val plugins = runCatching { dsh.plugins() }.getOrNull()
          val presets = runCatching { dsh.presets() }.getOrNull()
          Triple(
            models?.let { it.currentModel.ifBlank { "未选择" } } ?: "-",
            plugins?.let { list -> list.count { p -> p.enabled }.toString() + "/" + list.size.toString() + " 启用" } ?: "-",
            presets?.let { list -> list.firstOrNull { it.isDefault }?.name ?: if (list.isEmpty()) "无" else "-" } ?: "-",
          )
        }
      }
      r.getOrNull()?.let { dshSummary = it }
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp),
    ) {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(text = "设置", style = DshTheme.type.title, color = DshTheme.colors.text)
      }

      Spacer(modifier = Modifier.height(10.dp))

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
            Text(text = "状态", style = DshTheme.type.body, color = DshTheme.colors.text, modifier = Modifier.weight(1f))
            DshStatusPill(text = "已连接", status = DshStatus.Success)
          }
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          DshSettingsRow(title = "刷新页面", onClick = onRefresh)
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      DshSectionLabel("DSH 服务")
      DshSoftPanel {
        Column {
          DshSettingsRow(
            title = "模型",
            value = dshSummary?.first ?: "-",
            onClick = { onOpenRoute(SettingRoute.Models) },
          )
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          DshSettingsRow(
            title = "插件",
            value = dshSummary?.second ?: "-",
            onClick = { onOpenRoute(SettingRoute.Plugins) },
          )
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          DshSettingsRow(
            title = "Agent 预设",
            value = dshSummary?.third ?: "-",
            onClick = { onOpenRoute(SettingRoute.Presets) },
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      DshSectionLabel("外观")
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

      Spacer(modifier = Modifier.height(18.dp))

      DshSectionLabel("账户")
      DshSoftPanel {
        Column {
          DshSettingsRow(title = "账号", value = sessionUser.ifBlank { "-" })
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          DshSettingsRow(title = "退出登录", danger = true, onClick = onLogoutRequest)
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      DshSectionLabel("关于")
      DshSoftPanel {
        Column {
          DshSettingsRow(title = "版本", value = BuildConfig.VERSION_NAME)
          HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
          DshSettingsRow(title = "开源许可证", onClick = { onOpenRoute(SettingRoute.Licenses) })
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
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

    DshDetailFrame(title = "主题", onBack = onBack) {
      Spacer(modifier = Modifier.height(6.dp))
      DshSoftPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          ThemeSegmented(selected = themeMode, onSelect = { prefs.setAppearanceThemeMode(it) })
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

    DshDetailFrame(title = "语言", onBack = onBack) {
      Spacer(modifier = Modifier.height(6.dp))
      DshSoftPanel {
        Column {
          AppLanguage.entries.forEachIndexed { index, language ->
            if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = DshTheme.colors.border)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { prefs.saveAppLanguage(language) }
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
    val notices = remember { loadAndroidLicenseNotices(assets) }

    DshDetailFrame(title = "开源许可证", onBack = onBack) {
      Spacer(modifier = Modifier.height(6.dp))
      notices.forEach { notice ->
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webViewClient = object : WebViewClient() {
          override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
            // 冻结版：App 内的移动 UI 适配插件使用 APK 内置副本，不受服务器更新影响
            if (request.url.toString().contains("dsh-web-ui-mobile/client.js")) {
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
            // 被踢回 nginx 登录页 → 会话失效
            if (url.endsWith("/login")) onNeedLogin(true)
          }
          override fun onPageFinished(view: WebView, url: String) {
            CookieManager.getInstance().flush()
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

    DisposableEffect(Unit) {
      onDispose {
        webViewRef = null
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
      }
    }

    // Cookie 注入 + 加载
    LaunchedEffect(serverUrl) {
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
          Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME)?.let { context.startActivity(it) }
        }
        return true
      }
    }
    return false
  }

  private fun startDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
    val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
    val cookie = CookieManager.getInstance().getCookie(url)
    val request = DownloadManager.Request(Uri.parse(url)).apply {
      setMimeType(mimeType)
      setTitle(name)
      setDescription("DeepSeek Harness")
      setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
      addRequestHeader("User-Agent", userAgent)
      if (!cookie.isNullOrEmpty()) addRequestHeader("Cookie", cookie)
      setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
    }
    runCatching {
      (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
      Toast.makeText(applicationContext, "开始下载：" + name, Toast.LENGTH_SHORT).show()
    }
  }

  // ── 原生对话框（供 Web Bridge 调用）──

  fun showChangeServerDialog(silentRelogin: Boolean) {
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
    webViewRef?.loadUrl(prefs.serverUrl.value.ifBlank { "https://dsh.threel.site" })
  }

  // ── AppBridge：供 Web 设置里的 App 区块调用 ──

  private inner class AppBridge(
    val onChangeServer: () -> Unit = { runOnUiThread { showChangeServerDialog(silentRelogin = true) } },
    val onClearLogin: () -> Unit = { runOnUiThread { performLogout(expired = false) } },
    val onLicenses: () -> Unit = { runOnUiThread { showLicensesDialog() } },
    val onRefresh: () -> Unit = { runOnUiThread { refreshShell() } },
  ) {
    @JavascriptInterface
    fun getVersion(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun changeServer() = onChangeServer()

    @JavascriptInterface
    fun clearLogin() = onClearLogin()

    @JavascriptInterface
    fun showLicenses() = onLicenses()

    @JavascriptInterface
    fun refreshPage() = onRefresh()
  }
}
