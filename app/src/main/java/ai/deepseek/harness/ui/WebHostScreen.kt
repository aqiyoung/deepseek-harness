package ai.deepseek.harness.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Hosted DSH web application via Chrome Custom Tabs.
 *
 *  WebView on this device fails to render the DSH login page (white screen),
 *  so we delegate rendering to the system browser kernel through Custom Tabs,
 *  which handles TLS, cookies, and modern CSS reliably.
 */
private const val DSH_WEB_URL = "https://dsh.threel.site"

private fun openDshInCustomTab(context: Context, toolbarColor: Int) {
  val colorSchemeParams = CustomTabColorSchemeParams.Builder()
    .setToolbarColor(toolbarColor)
    .build()
  val intent = CustomTabsIntent.Builder()
    .setDefaultColorSchemeParams(colorSchemeParams)
    .setShowTitle(true)
    .setUrlBarHidingEnabled(false)
    .build()
  intent.launchUrl(context, Uri.parse(DSH_WEB_URL))
}

@Composable
fun WebHostScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val toolbarColor = MaterialTheme.colorScheme.primary.toArgb()
  var autoOpened by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    if (!autoOpened) {
      openDshInCustomTab(context, toolbarColor)
      autoOpened = true
    }
  }

  Surface(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "DSH Web",
        style = MaterialTheme.typography.headlineMedium,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "因当前设备 WebView 无法渲染 DSH 页面，已改用系统浏览器标签打开。",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(24.dp))
      Button(onClick = { openDshInCustomTab(context, toolbarColor) }) {
        Text("重新打开 DSH")
      }
    }
  }
}

private fun Color.toArgb(): Int {
  return android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
  )
}
