package ai.deepseek.harness.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(
  name = "DeepSeekHarness Design System",
  showBackground = true,
  backgroundColor = 0xFF030303,
)
@Composable
private fun DshComponentShowcasePreview() {
  // Preview uses the design-system theme directly so token regressions show up in isolation.
  DshDesignTheme {
    DshComponentShowcase()
  }
}
