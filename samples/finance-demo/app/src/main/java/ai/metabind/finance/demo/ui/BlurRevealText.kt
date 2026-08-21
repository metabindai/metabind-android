/*
 * BlurRevealText.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

/**
 * A result's description, revealed a word at a time with a soft blur-in — a quick
 * attention pull when the answer lands.
 *
 * Height is settled up front: every word is laid out at full size from the first
 * frame and only blur and opacity animate, so the block never reflows as it fills.
 * Markdown is parsed once and sliced into word tokens that each keep their own
 * styling, so a bold run survives being split — a plain space-split would turn
 * `**$3,412**` into literal asterisks.
 *
 * Resilient to streaming. The prose arrives in a response that updates many times,
 * so a one-shot reveal would only catch the first frame's words. The reveal cursor
 * walks forward one index at a time and a streamed chunk simply raises the target
 * it is heading toward; words already shown keep their state, and a word that
 * merely grows (`$3` → `$3,412`) updates in place without flashing.
 *
 * The cursor is driven from here rather than from each word, so the ripple always
 * runs left to right regardless of the order Compose happens to compose them in.
 *
 * Reveal progress lives in this composable's slot, so wrap the call in
 * `key(answer.id) { … }` to make a new answer start its own ripple from zero.
 *
 * Below API 31 `Modifier.blur` is a no-op, so the reveal degrades to a staggered
 * fade — same rhythm, no defocus.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlurRevealText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
) {
    val words = remember(markdown) { tokenize(parseInlineMarkdown(markdown)) }
    var revealed by remember { mutableIntStateOf(0) }

    // Keyed on the count, so a chunk arriving mid-walk extends it rather than
    // restarting it, and a word growing in place doesn't re-trigger anything.
    LaunchedEffect(words.size) {
        while (revealed < words.size) {
            delay(STEP_MILLIS)
            revealed++
        }
    }

    val plain = remember(words) { words.joinToString(" ") { it.text } }

    FlowRow(
        modifier = modifier.semantics { contentDescription = plain },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        words.forEachIndexed { index, word ->
            WordReveal(word = word, revealed = index < revealed, style = style, color = color)
        }
    }
}

/**
 * One word. Stateless: whether it's revealed is told to it, and the transition
 * animates from there.
 */
@Composable
private fun WordReveal(
    word: AnnotatedString,
    revealed: Boolean,
    style: TextStyle,
    color: Color,
) {
    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "wordReveal",
    )
    Text(
        text = word,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .blur(((1f - progress) * MAX_BLUR_DP).dp, BlurredEdgeTreatment.Unbounded)
            .alpha(progress),
    )
}

private const val STEP_MILLIS = 30L
private const val MAX_BLUR_DP = 5f

/**
 * Splits into bare word tokens, keeping each token's styling.
 *
 * Runs of whitespace — including the `\n\n` paragraph breaks in the prose — all
 * collapse: [FlowRow] supplies the gap between words instead. Folding the original
 * whitespace into a token would hand the layout something three lines tall and blow
 * a gap into the flow; a word token has to stay one line high.
 */
private fun tokenize(text: AnnotatedString): List<AnnotatedString> {
    val source = text.text
    val tokens = mutableListOf<AnnotatedString>()
    var i = 0
    while (i < source.length) {
        while (i < source.length && source[i].isWhitespace()) i++
        if (i >= source.length) break
        val start = i
        while (i < source.length && !source[i].isWhitespace()) i++
        tokens += text.subSequence(start, i)
    }
    return tokens
}

/**
 * Parses the inline markdown an assistant actually emits in a sentence — bold,
 * italic and code — into styled text.
 *
 * Deliberately not a full markdown renderer: the prose here is one paragraph of
 * analysis, and anything unrecognised is passed through as written rather than
 * swallowed, so a stray `*` shows up as a `*` instead of eating the rest of the
 * line. A half-streamed emphasis run (`**$3,4`) has no closing delimiter yet and so
 * falls through as literal text for the one frame before the rest arrives.
 */
private fun parseInlineMarkdown(source: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < source.length) {
        when {
            source.startsWith("**", i) -> {
                val end = source.indexOf("**", i + 2)
                if (end < 0) {
                    append("**"); i += 2
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(parseInlineMarkdown(source.substring(i + 2, end)))
                    }
                    i = end + 2
                }
            }

            source.startsWith("__", i) -> {
                val end = source.indexOf("__", i + 2)
                if (end < 0) {
                    append("__"); i += 2
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(parseInlineMarkdown(source.substring(i + 2, end)))
                    }
                    i = end + 2
                }
            }

            source[i] == '*' || source[i] == '_' -> {
                val delimiter = source[i]
                val end = source.indexOf(delimiter, i + 1)
                if (end < 0 || end == i + 1) {
                    append(delimiter); i++
                } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(parseInlineMarkdown(source.substring(i + 1, end)))
                    }
                    i = end + 1
                }
            }

            source[i] == '`' -> {
                val end = source.indexOf('`', i + 1)
                if (end < 0) {
                    append('`'); i++
                } else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(source.substring(i + 1, end))
                    }
                    i = end + 1
                }
            }

            else -> {
                append(source[i]); i++
            }
        }
    }
}
