/*
 * QuestionBar.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo.ui

import ai.metabind.finance.demo.R
import ai.metabind.finance.demo.ui.theme.Accent
import ai.metabind.finance.demo.ui.theme.glass
import ai.metabind.finance.demo.ui.theme.palette
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The one place you ask anything, pinned to the bottom of every surface — home
 * screen and answer sheet alike. Keeping it identical in both is what lets a
 * follow-up feel like it never left the screen it started on.
 *
 * It has three states: the rail of suggestions, the composer, and the in-flight
 * question. Tapping a pill drops the rail off the bottom edge while the processing
 * bubble grows into its place, so the bar reads as one object changing shape rather
 * than three views swapping.
 *
 * (iOS morphs the tapped pill's own glass into the bubble via `glassEffectID`.
 * Android has no equivalent, so the transition is directional instead: the rail
 * leaves downward, the bubble arrives by scaling up.)
 */
@Composable
fun QuestionBar(
    onAsk: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    prompts: Prompts = Prompts.Starters,
    /** Hidden from the rail — usually the question you're already looking at. */
    excluding: String? = null,
    /**
     * Follow-ups the model attached to the card on screen. Where these sit in the
     * rail depends on [Prompts.modelLeads].
     */
    modelSuggestions: List<String> = emptyList(),
    /** The question in flight, if any. Non-null puts the bar in its processing state. */
    pending: String? = null,
) {
    val colors = palette
    var isComposing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    val mode = when {
        pending != null -> Mode.Bubble
        isComposing -> Mode.Composer
        else -> Mode.Rail
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Lifts the bar off whatever is scrolling behind it. It runs past the bottom
        // of the bar so the fade finishes under the navigation bar rather than
        // stopping at a visible edge, and stays clear at the top so the translucent
        // pills still have something to sit over.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    // Reaches near-opaque early: the pills sit low in the bar, and a
                    // linear ramp left card text showing through behind them.
                    Brush.verticalGradient(
                        0.0f to colors.chrome.copy(alpha = 0f),
                        0.28f to colors.chrome.copy(alpha = 0.6f),
                        0.5f to colors.chrome.copy(alpha = 0.93f),
                        0.66f to colors.chrome,
                        1.0f to colors.chrome,
                    )
                )
        )

        AnimatedContent(
            targetState = BarState(mode, pending ?: draft),
            contentKey = { it.mode },
            transitionSpec = {
                when (targetState.mode) {
                    // The bubble grows in where the rail was; the rail leaves
                    // downward so the two never cross-fade on top of each other.
                    Mode.Bubble ->
                        (fadeIn(tween(260, delayMillis = 60)) +
                            scaleIn(tween(320), initialScale = 0.92f)) togetherWith
                            (slideOutVertically(tween(320)) { it } + fadeOut(tween(180)))

                    Mode.Composer ->
                        (fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.96f)) togetherWith
                            fadeOut(tween(140))

                    Mode.Rail ->
                        (slideInVertically(tween(320)) { it } + fadeIn(tween(240))) togetherWith
                            (fadeOut(tween(160)) + slideOutVertically(tween(280)) { it / 3 })
                }
            },
            label = "questionBar",
            // The insets padding goes on the content, not the whole bar, so the scrim
            // above still runs the full height and finishes *under* the navigation bar
            // rather than stopping at a visible edge. `safeDrawing` folds in the
            // keyboard too, so the bar rises with it.
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(top = 22.dp, bottom = 6.dp),
        ) { state ->
            when (state.mode) {
                Mode.Bubble -> ProcessingBubble(
                    question = state.text,
                    // Scoped to the live value rather than the frozen snapshot, so the
                    // sweep starts fading the instant the question clears while this
                    // bubble is still animating out.
                    active = pending != null,
                    onCancel = onCancel,
                )

                Mode.Composer -> Composer(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSubmit = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            draft = ""
                            isComposing = false
                            onAsk(text)
                        }
                    },
                    onAbandon = { isComposing = false },
                )

                Mode.Rail -> Rail(
                    suggestions = suggestions(prompts, modelSuggestions, excluding),
                    onCompose = { isComposing = true },
                    onAsk = onAsk,
                )
            }
        }
    }
}

/** Which set of prompts the rail offers. The home screen opens topics; a sheet continues one. */
enum class Prompts {
    Starters,
    FollowUps,
    ;

    /**
     * Whether the model's follow-ups lead the rail or trail it.
     *
     * The sheet is mid-thread, so what the model suggests about the answer on screen
     * beats a generic continuation. The home rail is the app's front door: its job is
     * to offer the same openers every launch, so "How am I doing?" is always the
     * first thing under your thumb. Let the model lead there and the front door
     * reshuffles itself after every turn, which reads as randomness.
     */
    val modelLeads: Boolean get() = this == FollowUps
}

private enum class Mode { Rail, Composer, Bubble }

private data class BarState(val mode: Mode, val text: String)

/**
 * Most the rail will show. Enough to be worth scrolling, few enough that the
 * model's suggestions aren't buried behind the fixed ones.
 */
private const val RAIL_LIMIT = 5

private fun suggestions(
    prompts: Prompts,
    modelSuggestions: List<String>,
    excluding: String?,
): List<Suggestion> {
    val fixed = when (prompts) {
        Prompts.Starters -> Suggestion.starters
        Prompts.FollowUps -> Suggestion.followUps
    }
    val fromModel = modelSuggestions.map(Suggestion::fromModel)
    val ordered = if (prompts.modelLeads) fromModel + fixed else fixed + fromModel

    // A suggestion's identity is its text, so this drops both the question already
    // being answered and any fixed prompt the model happened to repeat.
    val seen = mutableSetOf<String>()
    excluding?.let { seen += it }
    return ordered.filter { seen.add(it.text) }.take(RAIL_LIMIT)
}

// MARK: - Rail

@Composable
private fun Rail(
    suggestions: List<Suggestion>,
    onCompose: () -> Unit,
    onAsk: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
    ) {
        item(key = Suggestion.ASK_ID) {
            Pill(
                icon = R.drawable.fd_ic_sparkles,
                text = stringResource(R.string.ask_anything),
                prominent = true,
                onClick = onCompose,
            )
        }
        items(suggestions, key = { it.id }) { suggestion ->
            Pill(
                icon = suggestion.icon,
                text = suggestion.short,
                onClick = { onAsk(suggestion.text) },
            )
        }
    }
}

@Composable
private fun Pill(
    @DrawableRes icon: Int,
    text: String,
    prominent: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(150),
        label = "pillPress",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .scale(scale)
            .glass(CircleShape, tint = if (prominent) Accent.copy(alpha = 0.18f) else null)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (prominent) Accent else colors.textSecondary,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// MARK: - Composer

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAbandon: () -> Unit,
) {
    val colors = palette
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // `onFocusChanged` reports the *initial* unfocused state as well as later changes,
    // so an unguarded "lost focus on an empty field" check fires on the very first
    // composition and bounces the composer straight back to the rail before the focus
    // request lands.
    var hadFocus by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .glass(CircleShape),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            if (draft.isEmpty()) {
                Text(
                    text = stringResource(R.string.ask_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }
            // Deliberately single-line. A field that grows with its content changes
            // the height of the bar it lives in, which moves the keyboard inset,
            // which re-measures the field — one ask box isn't worth that.
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary)
                ),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focus ->
                        // Dismissing the keyboard on an untouched field puts the pills
                        // back rather than stranding an empty text box.
                        if (focus.isFocused) hadFocus = true
                        else if (hadFocus && draft.isEmpty()) onAbandon()
                    },
            )
        }

        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(if (draft.isBlank()) colors.fill else Accent)
                .clickable(enabled = draft.isNotBlank()) {
                    keyboard?.hide()
                    onSubmit()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.fd_ic_arrow_upward),
                contentDescription = stringResource(R.string.ask_send),
                tint = if (draft.isBlank()) colors.textSecondary else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// MARK: - In flight

/**
 * Deliberately the biggest thing in the bar: full width, with room for two lines.
 * While it's up it's the only thing on screen that's moving, and the size alone is
 * enough to say so.
 */
@Composable
private fun ProcessingBubble(
    question: String,
    active: Boolean,
    onCancel: () -> Unit,
) {
    val colors = palette
    // A rounded rect rather than a capsule: at two lines a capsule's ends bow out
    // into lozenge shapes.
    val corner = 26.dp
    val shape = RoundedCornerShape(corner)

    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape)
                .padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.fill)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fd_ic_close),
                    contentDescription = stringResource(R.string.ask_cancel),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Sized to the bubble's own rect — the geometry the border is drawn for.
        AISweepBorder(
            cornerRadius = corner,
            active = active,
            modifier = Modifier.matchParentSize(),
        )
    }
}
