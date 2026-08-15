package ai.deepseek.harness.ui

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
        Text(
          text = currentUrl,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 8.dp),
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
        factory = { context ->
          CookieManager.getInstance().apply {
            setAcceptCookie(true)
          }
          WebView(context)
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
                  }

                  override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                  ) {
                    if (request?.isForMainFrame == true) {
                      lastError = "ERR_${error?.errorCode}: ${error?.description}"
                    }
                  }

                  override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?,
                  ) {
                    // DSH self-hosted instances may use private/CN-issued certs;
                    // allow the user through on the same host we intended to load.
                    val host = error?.url?.let { Uri.parse(it).host }
                    if (host != null && (host == Uri.parse(DSH_WEB_URL).host || host.endsWith(".threel.site"))) {
                      handler?.proceed()
                    } else {
                      handler?.cancel()
                    }
                  }
                }
              webChromeClient =
                object : WebChromeClient() {
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
              loadUrl(DSH_WEB_URL)
            }.also { webViewRef.value = it }
        },
      )
    }
  }
}
