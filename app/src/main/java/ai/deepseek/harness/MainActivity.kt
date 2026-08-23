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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * DeepSeek Harness 安卓客户端（Kotlin + Jetpack Compose，与 OpenClaw app 同栈）。
 *
 * 原生登录页（服务器地址/账号/密码/记住密码）
 *   → 会话 Cookie 注入 WebView
 *   → 完整承载 Web 手机界面（dsh-web-ui-mobile 自适应插件）。
 * App 设置项注入 Web 设置 → 通用底部，经 DshAppBridge 调回原生。
 */
class MainActivity : ComponentActivity() {

  private val prefs by lazy { (application as NodeApp).prefs }

  private val loggedInState = mutableStateOf<Boolean?>(null)
  private val bridgeAction = mutableStateOf<String?>(null)
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

    // 沉浸式：与 OpenClaw app 一致，内容延伸到透明状态栏底下
    WindowCompat.getInsetsController(window, window.decorView)
      .isAppearanceLightStatusBars = true

    if (loggedInState.value == null) {
      val hasCookie = !prefs.getSessionCookie().isNullOrEmpty()
      loggedInState.value = prefs.isLoggedIn.value && hasCookie
    }

    val bridge = AppBridge(
      onChangeServer = { showChangeServerDialog(silentRelogin = true) },
      onClearLogin = { performLogout(expired = false) },
      onLicenses = { showLicensesDialog() },
      onRefresh = { refreshShell() },
    )

    setContent {
      MaterialTheme {
        val loggedIn by loggedInState
        if (loggedIn == true) {
          // 原生顶栏 + WebView（顶栏写死在 App，不依赖服务器）
          Column(modifier = Modifier.fillMaxSize()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                "☰",
                fontSize = 20.sp,
                modifier = Modifier.clickable { toggleDrawer() },
              )
              Text(
                "DeepSeek Harness",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
              )
            }
            HorizontalDivider(thickness = 0.5.dp)
            // WebView 填满剩余空间
            ShellScreen(
              bridge = bridge,
              onNeedLogin = { expired -> performLogout(expired) },
              onFileChoose = { callback, intent ->
                filePathCallback = callback
                try {
                  fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                  filePathCallback = null
                  Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
                }
              },
              onDownload = { url, ua, disposition, mime ->
                startDownload(url, ua, disposition, mime)
              },
            )
          }
        } else {
          LoginScreenComposable()
        }
      }
    }
  }

  // ── 登录页 ──

  @Composable
  private fun LoginScreenComposable() {
    val context = LocalContext.current
    val server by prefs.serverUrl.collectAsState()
    var serverValue by remember { mutableStateOf(server) }
    var userValue by remember { mutableStateOf(prefs.sessionUser.value) }
    var passValue by remember { mutableStateOf(prefs.getRememberedPassword() ?: "") }
    var rememberPwd by remember { mutableStateOf(!(prefs.getRememberedPassword() ?: "").isNullOrEmpty()) }
    val loading = loggingIn.value
    val err = loginError.value

    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Text("DeepSeek Harness", style = MaterialTheme.typography.headlineMedium)
      Text("登录到你的 DSH 服务器", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(modifier = Modifier.padding(top = 20.dp))
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
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
      )
      OutlinedTextField(
        value = passValue,
        onValueChange = { passValue = it },
        label = { Text("密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
      )
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Checkbox(checked = rememberPwd, onCheckedChange = { rememberPwd = it })
        Text("记住密码", style = MaterialTheme.typography.bodyMedium)
      }
      if (!err.isNullOrEmpty()) {
        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
      }
      Spacer(modifier = Modifier.padding(top = 14.dp))
      Button(
        onClick = {
          loginError.value = null
          loggingIn.value = true
          val srv = serverValue.trim().removeSuffix("/")
          val usr = userValue.trim()
          val pwd = passValue
          MainScope().launch(Dispatchers.IO) {
            val cookie = sessionLogin(srv, usr, pwd)
            MainScope().launch(Dispatchers.Main) {
              loggingIn.value = false
              if (cookie != null) {
                prefs.setServerUrl(srv)
                prefs.setLoggedIn(true, usr)
                prefs.setSessionCookie(cookie)
                prefs.setRememberedPassword(if (rememberPwd) pwd else null)
                CookieManager.getInstance().setCookie(srv, "dsh_session=" + cookie)
                CookieManager.getInstance().flush()
                loggedInState.value = true
              } else {
                loginError.value = "登录失败：请检查服务器地址、账号或密码"
              }
            }
          }
        },
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
      ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("登 录")
      }
      Text(
        "提示：服务器需部署 DeepSeek Harness Web 端",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
      )
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

  // ── Web 壳 ──

  @SuppressLint("SetJavaScriptEnabled")
  @Composable
  private fun ShellScreen(
    bridge: AppBridge,
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
          override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return handleExternalNavigation(context, serverUrl, request.url)
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
        addJavascriptInterface(bridge, "DshAppBridge")
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

    Box(modifier = Modifier.fillMaxSize()) {
      AndroidView(
        factory = { webViewRef = webView; webView },
        modifier = Modifier.fillMaxSize(),
      )
    }

    BackHandler(enabled = true) {
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
      Toast.makeText(this, "开始下载：" + name, Toast.LENGTH_SHORT).show()
    }
  }

  // ── 原生对话框 ──

  fun showChangeServerDialog(silentRelogin: Boolean) {
    val current = prefs.serverUrl.value
    val input = EditText(this).apply {
      setText(current)
      inputType = InputType.TYPE_TEXT_VARIATION_URI
      setSingleLine(true)
    }
    android.app.AlertDialog.Builder(this)
      .setTitle("切换服务器")
      .setView(input)
      .setPositiveButton("保存") { _, _ ->
        val raw = input.text.toString().trim().removeSuffix("/")
        if (raw.isNotEmpty() && raw != current) {
          prefs.setServerUrl(raw)
          CookieManager.getInstance().removeAllCookies(null)
          val usr = prefs.sessionUser.value
          val pwd = prefs.getRememberedPassword() ?: ""
          if (silentRelogin && usr.isNotEmpty() && pwd.isNotEmpty()) {
            MainScope().launch(Dispatchers.IO) {
              val ck = sessionLogin(raw, usr, pwd)
              MainScope().launch(Dispatchers.Main) {
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
      }
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

  fun toggleDrawer() {
    webViewRef?.evaluateJavascript("(function(){ var b=document.querySelector('.dsh-tb-btn'); if(b) b.click(); })()", null)
  }

  fun refreshShell() {
    CookieManager.getInstance().flush()
    webViewRef?.loadUrl(prefs.serverUrl.value.ifBlank { "https://dsh.threel.site" })
    // 兜底：直接对当前 WebView 触发重载
    webViewRef?.reload()
  }

  // ── AppBridge：供 Web 设置里的 App 区块调用 ──

  private inner class AppBridge(
    val onChangeServer: () -> Unit,
    val onClearLogin: () -> Unit,
    val onLicenses: () -> Unit,
    val onRefresh: () -> Unit,
  ) {
    @JavascriptInterface
    fun getVersion(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun changeServer() = runOnUiThread { onChangeServer() }

    @JavascriptInterface
    fun clearLogin() = runOnUiThread { onClearLogin() }

    @JavascriptInterface
    fun showLicenses() = runOnUiThread { onLicenses() }

    @JavascriptInterface
    fun refreshPage() = runOnUiThread { onRefresh() }
  }
}
