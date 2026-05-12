package ai.metabind.ui.composables

import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import ai.metabind.ui.app.SystemUiHandle

/**
 * A [Spacer] that sets itself to full-width and the same height as [SystemUiHandle.topPadding].
 * This composable is intended to be used for situations where we need a quick and easy "buffer"
 * element positioned under the device's status bar.
 */
@Composable
fun AppBarSpacer(
    modifier: Modifier = Modifier,
    systemUiHandle: SystemUiHandle,
    backgroundColor: Color = Color.Unspecified,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1f,
) =
    Spacer(
        modifier =
            modifier
                .fillMaxWidth()
                .height(systemUiHandle.topPadding)
                .alpha(alpha)
                .background(backgroundColor),
    )
