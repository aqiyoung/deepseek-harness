package ai.deepseek.harness

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex

/**
 * DeepSeek Harness 安卓客户端：WebView 壳（Kotlin + Jetpack Compose，与 OpenClaw app 同栈）。
 *
 * 完整承载 Web 手机界面（含 dsh-web-ui-mobile 移动适配插件）：
 * 登录走站点自带登录页，会话 Cookie 由 CookieManager 持久化；
 * 原生层仅提供：服务器切换 / 清除登录 / 刷新 / 开源许可证 / 下载与文件选择。
 */
class MainActivity : ComponentActivity() {

  private val prefs by lazy { (application as NodeApp).prefs }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        HarnessShell(prefs = prefs)
      }
    }
  }
}

private const val DEFAULT_SERVER_URL = "https://dsh.threel.site"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HarnessShell(prefs: SecurePrefs) {
  val context = LocalContext.current
  val serverUrl by prefs.serverUrl.collectAsState()
  var showSheet by remember { mutableStateOf(false) }
  var showServerDialog by remember { mutableStateOf(false) }
  var showLicenses by remember { mutableStateOf(false) }
  var reloadTick by remember { mutableIntStateOf(0) }
  var clearedTick by remember { mutableIntStateOf(0) }

  var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
  val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val callback = filePathCallback
    filePathCallback = null
    val uris: Array<Uri> = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data) ?: arrayOf()
    callback?.onReceiveValue(uris)
  }

  val webView = remember {
    WebView(context).apply {
      layoutParams = android.view.ViewGroup.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
      )
      configureWebSettings(settings)
      CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
      webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
          handleExternalNavigation(context, prefs.serverUrl.value, request.url)

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
          filePathCallback?.onReceiveValue(arrayOf())
          filePathCallback = callback
          val intent = params.createIntent().apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
          }
          return try {
            fileChooserLauncher.launch(intent)
            true
          } catch (e: Exception) {
            filePathCallback = null
            Toast.makeText(context, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
            false
          }
        }
      }
      setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        startDownload(context, url, userAgent, contentDisposition, mimeType)
      }
    }
  }

  // 服务器变更 / 清除登录 / 手动刷新时重新加载
  LaunchedEffect(serverUrl, reloadTick, clearedTick) {
    CookieManager.getInstance().flush()
    webView.loadUrl(serverUrl.ifBlank { DEFAULT_SERVER_URL })
  }

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 10.dp, end = 10.dp)
        .size(width = 30.dp, height = 18.dp)
        .alpha(0.55f)
        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        .clickable { showSheet = true }
        .zIndex(2f),
      contentAlignment = Alignment.Center,
    ) {
      Text("⌄", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }

  var lastBackPress by remember { mutableStateOf(0L) }

  BackHandler(enabled = true) {
    if (webView.canGoBack()) {
      webView.goBack()
    } else {
      val now = System.currentTimeMillis()
      if (now - lastBackPress < 2000L) {
        (context as? ComponentActivity)?.finish()
      } else {
        lastBackPress = now
        Toast.makeText(context, "再按一次返回退出", Toast.LENGTH_SHORT).show()
      }
    }
  }

  if (showSheet) {
    ModalBottomSheet(
      onDismissRequest = { showSheet = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
      SheetContent(
        onRefresh = { showSheet = false; reloadTick++ },
        onChangeServer = { showSheet = false; showServerDialog = true },
        onClearLogin = {
          showSheet = false
          clearedTick++
          CookieManager.getInstance().removeAllCookies(null)
          CookieManager.getInstance().flush()
          Toast.makeText(context, "已清除登录，请重新登录", Toast.LENGTH_SHORT).show()
        },
        onLicenses = { showSheet = false; showLicenses = true },
        versionName = BuildConfig.VERSION_NAME,
      )
    }
  }

  if (showServerDialog) {
    ServerDialog(current = serverUrl) { newValue ->
      showServerDialog = false
      val normalized = newValue.trim().removeSuffix("/").let { if (it.startsWith("http")) it else "https://$it" }
      if (normalized.isNotBlank() && normalized != serverUrl) {
        prefs.setServerUrl(normalized)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
      }
    }
  }

  if (showLicenses) {
    LicensesDialog(onDismiss = { showLicenses = false })
  }
}

@Composable
private fun SheetContent(
  onRefresh: () -> Unit,
  onChangeServer: () -> Unit,
  onClearLogin: () -> Unit,
  onLicenses: () -> Unit,
  versionName: String,
) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
    Text(
      "DeepSeek Harness",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
    )
    SheetItem("刷新页面", onRefresh)
    SheetItem("切换服务器…", onChangeServer)
    SheetItem("清除登录状态", onClearLogin)
    Text(
      "关于",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp),
    )
    SheetItem("软件许可证", onLicenses)
    SheetItem("版本 " + versionName, onClick = {})
  }
}

@Composable
private fun SheetItem(label: String, onClick: () -> Unit) {
  Text(
    label,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .padding(horizontal = 14.dp, vertical = 13.dp),
  )
}

@Composable
private fun ServerDialog(current: String, onConfirm: (String) -> Unit) {
  var value by remember { mutableStateOf(current) }
  AlertDialog(
    onDismissRequest = {},
    title = { Text("切换服务器") },
    text = {
      OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        singleLine = true,
        placeholder = { Text("https://dsh.example.com") },
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("保存") } },
    dismissButton = { TextButton(onClick = {}) { Text("取消") } },
  )
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val notices = remember { loadAndroidLicenseNotices(context.assets) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("软件许可证") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 480.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        notices.forEach { notice ->
          Text(
            "── " + notice.title + " ──",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
          )
          Text(
            notice.text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
  )
}

// ── WebView 配置与工具 ──

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebSettings(settings: WebSettings) {
  settings.javaScriptEnabled = true
  settings.domStorageEnabled = true
  settings.useWideViewPort = true
  settings.loadWithOverviewMode = true
  settings.mediaPlaybackRequiresUserGesture = false
  settings.cacheMode = WebSettings.LOAD_DEFAULT
  settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
  settings.allowFileAccess = false
}

private fun handleExternalNavigation(context: Context, serverUrl: String, url: Uri): Boolean {
  val serverHost = Uri.parse(serverUrl.ifBlank { DEFAULT_SERVER_URL }).host ?: return false
  val targetHost = url.host
  val sameHost = targetHost != null && targetHost.equals(serverHost, ignoreCase = true)
  if (sameHost && (url.scheme == "https" || url.scheme == "http")) return false
  when (url.scheme) {
    "http", "https" -> {
      runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
        .onFailure { Toast.makeText(context, "没有可打开的应用", Toast.LENGTH_SHORT).show() }
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

private fun startDownload(
  context: Context,
  url: String,
  userAgent: String,
  contentDisposition: String,
  mimeType: String,
) {
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
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    Toast.makeText(context, "开始下载：" + name, Toast.LENGTH_SHORT).show()
  }.onFailure {
    Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show()
  }
}
