package ai.deepseek.harness.ui.design

import ai.deepseek.harness.R
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * DeepSeek Harness brand mark — the DeepSeek whale — rendered as the in-app mascot.
 * Replaces the original OpenClaw lobster mascot geometry.
 */
@Composable
fun DeepSeekHarnessMascot(
  modifier: Modifier = Modifier,
  tint: Color? = null,
  contentDescription: String? = null,
  mood: MascotMood = MascotMood.Idle,
) {
  val whale = ImageBitmap.imageResource(R.drawable.login_logo_black)
  val semantics =
    if (contentDescription == null) {
      Modifier
    } else {
      Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
      }
    }
  Canvas(modifier = modifier.then(semantics)) {
    // Keep the whale small enough that its fins/tail don’t visually touch the edge.
    val artSize = (size.minDimension * 0.72f).toInt().coerceAtLeast(1)
    val left = ((size.width - artSize) / 2f).toInt()
    val top = ((size.height - artSize) / 2f).toInt()
    drawImage(
      image = whale,
      dstOffset = IntOffset(left, top),
      dstSize = IntSize(artSize, artSize),
    )
  }
}
