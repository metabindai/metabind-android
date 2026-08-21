/*
 * AISweepBorder.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo.ui

import ai.metabind.finance.demo.ui.theme.Accent
import android.os.Build
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
 * The arc is drawn as a stack of strokes on one shared centreline: two wide, faint,
 * heavily blurred passes for bloom, then a thin core blurred by well under its own
 * width so it has no hard pixel edge on either side. Widening a stroke moves its
 * centre when the inset is derived from its own width, which would bias the bloom
 * inward, so every layer insets by half the *core* width instead.
 *
 * Below API 31 `Modifier.blur` is a no-op, which would land the bloom passes as
 * extra hard-edged strokes rather than glow. There the core is drawn alone.
 */
@Composable
fun AISweepBorder(
    cornerRadius: Dp,
    active: Boolean,
    modifier: Modifier = Modifier,
    lineWidth: Dp = 2.dp,
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

    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Nothing to draw while it's off, and the lap animation keeps running regardless,
    // so this skips three blurred layers redrawing every frame behind a zero alpha.
    if (opacity <= 0.001f) return

    Box(modifier = modifier) {
        if (canBlur) {
            // Two bloom passes rather than one: a wide, faint halo for the ambient
            // light, and a tighter, stronger one so the arc still has a locatable
            // bright head as it travels.
            SweepLayer(turn, cornerRadius, lineWidth, lineWidth * 3f, 12.dp, 0.3f * opacity, false)
            SweepLayer(turn, cornerRadius, lineWidth, lineWidth * 1.6f, 4.dp, 0.45f * opacity, false)
        }
        SweepLayer(
            turn = turn,
            cornerRadius = cornerRadius,
            coreWidth = lineWidth,
            strokeWidth = lineWidth,
            blurRadius = if (canBlur) 1.dp else 0.dp,
            layerAlpha = 0.8f * opacity,
            ring = true,
        )
    }
}

/**
 * One stroke of the stack.
 *
 * The alpha is folded into the paint rather than applied with `Modifier.alpha`.
 * An alpha layer has to composite offscreen into a buffer the size of its own
 * bounds, and a blurred child's bloom reaches past those bounds — so wrapping this
 * in one clips the glow to the layout rect and draws a hard-cornered rectangle
 * around the bubble.
 */
@Composable
private fun SweepLayer(
    turn: Float,
    cornerRadius: Dp,
    coreWidth: Dp,
    strokeWidth: Dp,
    blurRadius: Dp,
    layerAlpha: Float,
    ring: Boolean,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (blurRadius > 0.dp) {
                    Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                }
            )
    ) {
        drawSweep(
            turn = turn,
            cornerRadiusPx = cornerRadius.toPx(),
            insetPx = coreWidth.toPx() / 2f,
            strokePx = strokeWidth.toPx(),
            alpha = layerAlpha,
            drawRing = ring,
        )
    }
}

private fun DrawScope.drawSweep(
    turn: Float,
    cornerRadiusPx: Float,
    insetPx: Float,
    strokePx: Float,
    alpha: Float,
    drawRing: Boolean,
) {
    val topLeft = Offset(insetPx, insetPx)
    val strokedSize = Size(
        width = (size.width - insetPx * 2f).coerceAtLeast(0f),
        height = (size.height - insetPx * 2f).coerceAtLeast(0f),
    )
    val radius = CornerRadius((cornerRadiusPx - insetPx).coerceAtLeast(0f))

    if (drawRing) {
        // A faint full ring so the edge reads as lit all the way round, not just
        // where the bright arc happens to be.
        drawRoundRect(
            color = Accent.copy(alpha = 0.14f),
            topLeft = topLeft,
            size = strokedSize,
            cornerRadius = radius,
            alpha = alpha,
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
        alpha = alpha,
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
