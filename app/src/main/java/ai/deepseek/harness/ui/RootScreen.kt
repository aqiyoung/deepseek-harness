package ai.deepseek.harness.ui

import ai.deepseek.harness.MainViewModel
import ai.deepseek.harness.currentAppLanguage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/** Chooses the login gate or the authenticated app shell. */
@Composable
fun RootScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val isLoggedIn by viewModel.isLoggedIn.collectAsState()

  // Apply saved language on startup
  LaunchedEffect(Unit) {
    val saved = viewModel.appLanguage.value
    if (saved == currentAppLanguage()) return@LaunchedEffect
    viewModel.applyAppLanguage(saved)
  }

  // Auto-connect on startup if logged in
  LaunchedEffect(isLoggedIn) {
    if (isLoggedIn) viewModel.connectDsh()
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
}