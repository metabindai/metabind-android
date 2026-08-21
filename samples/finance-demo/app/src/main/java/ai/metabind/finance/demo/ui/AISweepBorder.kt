/*
 * AISweepBorder.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo.ui

import ai.metabind.finance.demo.ui.theme.Accent
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A bright arc that sweeps continuously around a rounded-rect edge — the app's
 * "AI is working" signal, standing in for a spinner.
 *
 * The rotation is a lap counter fed into [sweepStops], which re-derives the
 * gradient's stop positions each frame. Compose has no rotatable sweep gradient
 * and rotating the canvas would turn the *shape* with it, so the colours are moved
 * around a fixed shape instead.
 *
 * [active] gates its own opacity, asymmetrically: on it holds back briefly then
 * fades in, so it isn't drawn at full strength while the bubble it outlines is
 * still growing into place; off it fades fast, so it's gone almost as the bubble
 * starts shrinking back rather than popping.
 *
 * Below API 31 `Modifier.blur` is a no-op, so the glow layer collapses onto the
 * crisp arc — the sweep still reads, just without the bloom.
 */
@Composable
fun AISweepBorder(
    cornerRadius: Dp,
    active: Boolean,
    modifier: Modifier = Modifier,
    lineWidth: Dp = 4.6.dp,
    periodMillis: Int = 2200,
    appearDelayMillis: Int = 400,
) {
    // Lit only once mounted *and* active — so a fresh mount fades in rather than
    // snapping on.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val visible = appeared && active

    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        // Both ease-out: fading in, a soft arrival after the delay that clears the
        // bubble's own transition; fading out, alpha drops fast at the start so it
        // reads as an immediate vanish rather than holding full then snapping.
        animationSpec = if (visible) {
            tween(durationMillis = 300, delayMillis = appearDelayMillis, easing = EaseOut)
        } else {
            tween(durationMillis = 150, easing = EaseOut)
        },
        label = "sweepOpacity",
    )

    val transition = rememberInfiniteTransition(label = "sweep")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweepTurn",
    )

    Box(modifier = modifier.alpha(opacity)) {
        // The blurred copy under the crisp arc, for glow.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(6.dp, BlurredEdgeTreatment.Unbounded)
        ) {
            drawSweep(turn, cornerRadius.toPx(), lineWidth.toPx(), drawRing = false)
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSweep(turn, cornerRadius.toPx(), lineWidth.toPx(), drawRing = true)
        }
    }
}

private fun DrawScope.drawSweep(
    turn: Float,
    cornerRadiusPx: Float,
    strokePx: Float,
    drawRing: Boolean,
) {
    val inset = strokePx / 2f
    val topLeft = Offset(inset, inset)
    val strokedSize = Size(
        width = (size.width - strokePx).coerceAtLeast(0f),
        height = (size.height - strokePx).coerceAtLeast(0f),
    )
    val radius = CornerRadius((cornerRadiusPx - inset).coerceAtLeast(0f))

    if (drawRing) {
        // A faint full ring so the edge reads as lit all the way round, not just
        // where the bright arc happens to be.
        drawRoundRect(
            color = Accent.copy(alpha = 0.18f),
            topLeft = topLeft,
            size = strokedSize,
            cornerRadius = radius,
            style = Stroke(width = strokePx),
        )
    }

    drawRoundRect(
        brush = Brush.sweepGradient(
            *sweepStops(turn),
            center = Offset(size.width / 2f, size.height / 2f),
        ),
        topLeft = topLeft,
        size = strokedSize,
        cornerRadius = radius,
        style = Stroke(width = strokePx),
    )
}

/**
 * The gradient's colours around one lap, as a cyclic ramp. The last segment runs
 * from [BASE]'s final stop back around to its first, which is what lets the whole
 * thing be rotated without a visible seam.
 */
private val BASE: List<Pair<Float, Color>> = listOf(
    0.00f to Color(0xFF0A84FF),
    0.26f to Accent.copy(alpha = 0.9f),
    0.38f to Color.White,
    0.50f to Accent.copy(alpha = 0.9f),
    0.74f to Color.White,
    0.88f to Accent.copy(alpha = 0.9f),
)

/** The ramp's colour at [x], wrapping past the end back to the first stop. */
private fun cyclicColorAt(x: Float): Color {
    val at = x.mod(1f)
    for (i in BASE.indices) {
        val (start, startColor) = BASE[i]
        val next = BASE.getOrNull(i + 1)
        val end = next?.first ?: 1f
        val endColor = next?.second ?: BASE.first().second
        if (at in start..end) {
            val span = end - start
            return if (span <= 0f) startColor else lerp(startColor, endColor, (at - start) / span)
        }
    }
    return BASE.first().second
}

/**
 * [BASE] rotated forward by [turn] laps.
 *
 * Every stop moves by the same amount and the ones that run past 1 wrap around to
 * the front, which keeps the list monotonically increasing — a sweep gradient whose
 * stops aren't ordered is rejected outright. The pair pinned at 0 and 1 is the
 * colour the ramp holds at the seam, so the wrap is invisible as it spins.
 */
private fun sweepStops(turn: Float): Array<Pair<Float, Color>> {
    val shift = turn.mod(1f)
    val seam = cyclicColorAt(1f - shift)
    val wrapped = mutableListOf<Pair<Float, Color>>()
    val ahead = mutableListOf<Pair<Float, Color>>()
    for ((position, color) in BASE) {
        val moved = position + shift
        if (moved >= 1f) wrapped += (moved - 1f) to color else ahead += moved to color
    }
    return (listOf(0f to seam) + wrapped + ahead + listOf(1f to seam)).toTypedArray()
}
