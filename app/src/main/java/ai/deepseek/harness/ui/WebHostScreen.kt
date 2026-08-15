package ai.deepseek.harness.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Hosted DSH web application. The web app owns authentication and all features. */
private const val DSH_WEB_URL = "https://dsh.threel.site"

@Composable
fun WebHostScreen(modifier: Modifier = Modifier) {
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(true) }
  var currentUrl by remember { mutableStateOf(DSH_WEB_URL) }
  var lastError by remember { mutableStateOf<String?>(null) }
  val webViewRef = remember { mutableStateOf<WebView?>(null) }
  val context = androidx.compose.ui.platform.LocalContext.current

  // Chat/file attachments from the web app surface here.
  var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
  val fileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      fileChooserCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else emptyArray())
      fileChooserCallback = null
    }

  BackHandler(enabled = canGoBack) {
    webViewRef.value?.goBack()
  }

  DisposableEffect(Unit) {
    onDispose {
      webViewRef.value?.destroy()
      webViewRef.value = null
    }
  }

  Column(modifier = modifier.fillMaxSize()) {
    Surface(
      modifier = Modifier.fillMaxWidth().height(48.dp),
      tonalElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        IconButton(
          onClick = { webViewRef.value?.goBack() },
          enabled = canGoBack,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(20.dp),
          )
        }
        IconButton(
          onClick = { webViewRef.value?.goForward() },
          enabled = canGoForward,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Forward",
            modifier = Modifier.size(20.dp),
          )
        }
        IconButton(onClick = { webViewRef.value?.reload() }) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Reload",
            modifier = Modifier.size(20.dp),
          )
        }
        IconButton(
          onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
          },
        ) {
          Text(
            text = "浏览器",
            style = MaterialTheme.typography.labelSmall,
          )
        }
        Text(
          text = currentUrl,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
      }
    }

    if (isLoading) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
    }

    lastError?.let { error ->
      Text(
        text = error,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().weight(1f)) {
      androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
          CookieManager.getInstance().apply {
            setAcceptCookie(true)
          }
          WebView(ctx)
            .apply {
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              settings.databaseEnabled = true
              settings.loadWithOverviewMode = true
              settings.useWideViewPort = true
              settings.builtInZoomControls = false
              settings.displayZoomControls = false
              settings.setSupportZoom(false)
              settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
              settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 DSH-Android/1.0"
              CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
              webViewClient =
                object : WebViewClient() {
                  override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                  ): Boolean = false

                  override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: Bitmap?,
                  ) {
                    isLoading = true
                    lastError = null
                    url?.let { currentUrl = it }
                    Log.d("WebHost", "pageStarted: $url")
                  }

                  override fun onPageFinished(
                    view: WebView?,
                    url: String?,
                  ) {
                    isLoading = false
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                    url?.let { currentUrl = it }
                    view?.requestFocus()
                    Log.d("WebHost", "pageFinished: $url")
                    Toast.makeText(context, "Loaded: $url", Toast.LENGTH_SHORT).show()
                  }

                  override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                  ) {
                    val msg = "ERR_${error?.errorCode}: ${error?.description}"
                    Log.e("WebHost", "receivedError isMain=${request?.isForMainFrame} $msg")
                    if (request?.isForMainFrame == true) {
                      lastError = msg
                      Toast.makeText(context, "Load error: $msg", Toast.LENGTH_LONG).show()
                    }
                  }

                  override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                  ) {
                    val msg = "HTTP_${errorResponse?.statusCode}"
                    Log.e("WebHost", "httpError isMain=${request?.isForMainFrame} $msg")
                    if (request?.isForMainFrame == true) {
                      lastError = msg
                      Toast.makeText(context, "HTTP error: $msg", Toast.LENGTH_LONG).show()
                    }
                  }

                  override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?,
                  ) {
                    val host = error?.url?.let { Uri.parse(it).host }
                    Log.w("WebHost", "sslError host=$host url=${error?.url}")
                    if (host != null && (host == Uri.parse(DSH_WEB_URL).host || host.endsWith(".threel.site"))) {
                      handler?.proceed()
                      Toast.makeText(context, "SSL bypass for $host", Toast.LENGTH_SHORT).show()
                    } else {
                      handler?.cancel()
                      Toast.makeText(context, "SSL cancelled for $host", Toast.LENGTH_LONG).show()
                    }
                  }
                }
              webChromeClient =
                object : WebChromeClient() {
                  override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    val msg = "[${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}] ${consoleMessage?.message()}"
                    Log.d("WebHost", "console ${consoleMessage?.messageLevel()}: $msg")
                    return true
                  }

                  override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                  }

                  override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?,
                  ): Boolean {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = filePathCallback
                    fileLauncher.launch(fileChooserParams?.acceptTypes?.firstOrNull() ?: "*/*")
                    return true
                  }
                }
            }.also { webViewRef.value = it }
        },
        update = { webView ->
          // Load exactly once. The page 302-redirects to /login, which changes
          // webView.url — re-calling loadUrl on that mismatch causes a reload loop.
          if (webView.url == null) {
            Log.d("WebHost", "update initial loadUrl $DSH_WEB_URL")
            webView.loadUrl(DSH_WEB_URL)
          }
        },
      )
    }
  }
}
