/*
 * AnswerCards.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo.ui

import ai.metabind.ai.MetabindAssistant
import ai.metabind.ai.MetabindToolView
import ai.metabind.finance.demo.R
import ai.metabind.finance.demo.ui.theme.Accent
import ai.metabind.finance.demo.ui.theme.palette
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The frame every rendered MCP component sits in, so a component that brings its
 * own background and one that doesn't look like the same app.
 */
@Composable
fun AnswerCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = palette
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .clip(shape)
            .background(colors.card)
            .border(0.5.dp, colors.separator, shape),
        contentAlignment = Alignment.TopStart,
    ) {
        content()
    }
}

/**
 * The card — or cards — a turn produced.
 *
 * A model can emit several tool calls in one message, which costs a single
 * inference pass rather than one per card. That makes "show me two things"
 * dramatically cheaper as a turn than as a conversation, so when it happens the
 * answer keeps all of them and offers a tab per card rather than showing the first
 * and discarding the rest.
 *
 * With one card — the overwhelmingly common case — there is no tab bar.
 */
@Composable
fun AnswerCards(
    assistant: MetabindAssistant,
    cards: List<AnswerRouter.Card>,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // View state, not router state: it should reset when the answer changes, which
    // the caller's `key(answer.id)` already handles.
    var selected by remember { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, (cards.size - 1).coerceAtLeast(0))

    if (cards.size > 1) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTabs(cards = cards, selected = index, onSelect = { selected = it })
            AnimatedContent(
                targetState = index,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                label = "cardTab",
            ) { shown ->
                // Keyed by card so switching tabs swaps the card outright instead of
                // animating one into the other's frame.
                val card = cards[shown.coerceIn(0, cards.size - 1)]
                AnswerCard {
                    MetabindToolView(
                        assistant = assistant,
                        toolName = card.toolName,
                        content = card.content,
                        onSendMessage = onSendMessage,
                    )
                }
            }
        }
    } else {
        cards.firstOrNull()?.let { card ->
            AnswerCard(modifier = modifier) {
                MetabindToolView(
                    assistant = assistant,
                    toolName = card.toolName,
                    content = card.content,
                    onSendMessage = onSendMessage,
                )
            }
        }
    }
}

@Composable
private fun CardTabs(
    cards: List<AnswerRouter.Card>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = palette
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        cards.forEachIndexed { offset, card ->
            val isSelected = offset == selected
            val shape = CircleShape
            Box(
                modifier = Modifier
                    .then(
                        if (isSelected) {
                            // The card's own surface, so the selected tab reads as the
                            // front edge of the card beneath it rather than a separate chip.
                            Modifier
                                .shadow(4.dp, shape, clip = false)
                                .clip(shape)
                                .background(colors.card)
                                .border(0.5.dp, colors.separator, shape)
                        } else {
                            // A recess rather than a colour, so it darkens in light mode
                            // without going invisible against a dark background.
                            Modifier.clip(shape).background(colors.fill)
                        }
                    )
                    .clickable { onSelect(offset) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = tabLabel(card.toolName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}

/**
 * A tab needs a name before its card has finished streaming, so this reads the tool
 * name rather than anything inside the arguments. Falls back to prettifying the
 * identifier, which keeps an unmapped tool legible instead of blank.
 */
private fun tabLabel(toolName: String): String = when (toolName) {
    "net_worth_trend" -> "Net worth"
    "spending_breakdown" -> "Spending"
    "transaction_list" -> "Transactions"
    "subscriptions" -> "Subscriptions"
    "trend_card" -> "Trend"
    else -> toolName.replace('_', ' ').split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}

/**
 * Stand-in for the home answer while the first tool call is in flight. A shaped
 * placeholder beats a spinner here — the screen keeps its layout instead of jumping
 * when the card lands.
 */
@Composable
fun LoadingCard(modifier: Modifier = Modifier) {
    val colors = palette
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )

    // The pulse goes on the card's *contents*, not the card. An alpha layer wrapping
    // AnswerCard forces its shadow to composite offscreen, which loses the rounded
    // outline and leaves a square shadow poking out of all four corners.
    AnswerCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .alpha(shimmer)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SkeletonBar(widthFraction = 0.5f, color = colors.fill)
            SkeletonBar(widthFraction = 0.8f, color = colors.fill)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.fill)
            )
            SkeletonBar(widthFraction = 0.65f, color = colors.fill)
        }
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(13.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * A turn that came back with nothing to show — usually a dropped connection or a
 * question the tools can't answer.
 */
@Composable
fun EmptyAnswerCard(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette
    AnswerCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.fd_ic_help),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(R.string.answer_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.answer_retry), color = Accent)
            }
        }
    }
}
