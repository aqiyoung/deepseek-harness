package ai.deepseek.harness.ui

import ai.deepseek.harness.MainViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/** Chooses the login gate or the authenticated app shell. */
@Composable
fun RootScreen(viewModel: MainViewModel) {
  val isLoggedIn by viewModel.isLoggedIn.collectAsState()

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