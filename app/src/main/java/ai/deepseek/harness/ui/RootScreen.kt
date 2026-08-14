package ai.deepseek.harness.ui

import ai.deepseek.harness.MainViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/** Chooses the login gate or the authenticated app shell from persisted auth state. */
@Composable
fun RootScreen(viewModel: MainViewModel) {
  val isLoggedIn by viewModel.isLoggedIn.collectAsState()

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
