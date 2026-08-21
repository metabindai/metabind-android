/*
 * AnswerSheet.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo.ui

import ai.metabind.finance.demo.R
import ai.metabind.finance.demo.ui.theme.Accent
import ai.metabind.finance.demo.ui.theme.glass
import ai.metabind.finance.demo.ui.theme.palette
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * One sheet for the whole thread.
 *
 * The first answer looks like a plain detail sheet. Ask a follow-up and a row of
 * chips appears along the top — one per answer, behaving like tabs. That keeps the
 * whole chain reachable and legible without stacking sheets ten deep or scrolling a
 * transcript: the chips *are* the history, and what's in them is what the model is
 * working from.
 *
 * Only the question is pinned. The answer's prose and its card scroll under the
 * header, passing through a fade rather than being cut off at an edge.
 *
 * Full height rather than iOS's medium/large detents. Material's partially-expanded
 * state translates the whole content down, so anything bottom-aligned inside it —
 * the ask bar — ends up below the screen; iOS pins that bar with a `safeAreaInset`
 * that tracks the detent, and Compose has no equivalent. Keeping one height keeps
 * the bar reachable, which matters more here than the glimpse of the home card a
 * half sheet would leave. Swiping down still dismisses, and dismissing still ends
 * the thread.
 *
 * (iOS also lets a component push the sheet to `large` through the host bridge's
 * display-mode handler. bindjs on Android has no display-mode channel, so that
 * request has nowhere to arrive from either way.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswerSheet(router: AnswerRouter) {
    val colors = palette
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val density = LocalDensity.current

    var headerHeight by remember { mutableStateOf(0.dp) }
    var barHeight by remember { mutableStateOf(0.dp) }

    val answer = router.thread.getOrNull(router.selected) ?: router.thread.lastOrNull()
    val showsChips = router.thread.size > 1

    // Ending the thread on dismissal rather than on tap, so the sheet still has its
    // content to animate away with.
    fun close() {
        scope.launch {
            sheetState.hide()
            router.dismissAnswer()
            router.endThread()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            // Swipe-down, scrim tap and back all land here, already animated out.
            router.dismissAnswer()
            router.endThread()
        },
        sheetState = sheetState,
        containerColor = colors.page,
        contentColor = colors.textPrimary,
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = headerHeight, bottom = barHeight + 16.dp)
                    .padding(horizontal = 20.dp),
                // Wider than the title-to-prose gap on purpose: the pair above reads as
                // one block, and the card is the next thing down.
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (answer != null) {
                    key(answer.id) {
                        if (answer.prose.isNotEmpty()) {
                            BlurRevealText(markdown = answer.prose)
                        }
                        if (answer.cards.isNotEmpty()) {
                            AnswerCards(
                                assistant = router.assistant,
                                cards = answer.cards,
                                onSendMessage = router::ask,
                            )
                        } else if (answer.prose.isEmpty()) {
                            EmptyAnswerCard(onRetry = { router.retry(answer) })
                        }
                    }
                }
            }

            if (answer != null) {
                Header(
                    question = answer.question,
                    thread = router.thread,
                    selected = router.selected,
                    showsChips = showsChips,
                    onSelect = router::select,
                    onClose = ::close,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
                )
            }

            QuestionBar(
                onAsk = router::ask,
                onCancel = router::cancelPending,
                prompts = Prompts.FollowUps,
                excluding = answer?.question,
                modelSuggestions = answer?.nextSteps.orEmpty(),
                pending = router.pending?.question,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
            )
        }
    }
}

/**
 * Two shapes, one anchor. With a single answer the question sits beside the close
 * button; once chips appear they take that row and the question drops beneath. The
 * close button doesn't move either way.
 */
@Composable
private fun Header(
    question: String,
    thread: List<AnswerRouter.Answer>,
    selected: Int,
    showsChips: Boolean,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette
    Box(modifier = modifier.fillMaxWidth()) {
        // Opaque behind the header, dissolving over its last stretch so content
        // doesn't disappear at a hard line. The stops hold near-opaque for the first
        // half and do the real falloff late: the gradient overlaps the title, and a
        // linear ramp would let scrolling content show faintly behind the text.
        Column(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(colors.page)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HeaderFadeHeight)
                    .background(
                        Brush.verticalGradient(
                            0.0f to colors.page,
                            0.4f to colors.page.copy(alpha = 0.97f),
                            0.62f to colors.page.copy(alpha = 0.8f),
                            0.84f to colors.page.copy(alpha = 0.35f),
                            1.0f to colors.page.copy(alpha = 0f),
                        )
                    )
            )
        }

        // The fade is drawn *inside* the header's height rather than added to it — it
        // rides up over the title instead of pushing the content down. So the fade can
        // be as long as it needs to be while the title and the prose stay a tight 6dp
        // apart.
        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showsChips) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThreadChips(
                        answers = thread,
                        selected = selected,
                        trailingInset = ChipFadeWidth,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                    CloseButton(onClose = onClose, modifier = Modifier.padding(end = 20.dp))
                }
                Title(question, modifier = Modifier.padding(horizontal = 20.dp))
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    Title(question, modifier = Modifier.weight(1f))
                    CloseButton(onClose = onClose)
                }
            }
        }
    }
}

@Composable
private fun Title(question: String, modifier: Modifier = Modifier) {
    Text(
        text = question,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = palette.textPrimary,
        modifier = modifier,
    )
}

@Composable
private fun CloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = palette
    Box(
        modifier = modifier
            .size(32.dp)
            .glass(CircleShape)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.fd_ic_close),
            contentDescription = stringResource(R.string.answer_close),
            tint = colors.textSecondary,
            modifier = Modifier.size(13.dp),
        )
    }
}

/**
 * The thread as tabs. Solid fills rather than the translucent pills the ask rail
 * uses — these sit on the sheet's own surface, where translucency-on-surface reads
 * as mush, and a selected tab needs to be unambiguous rather than subtle.
 */
@Composable
private fun ThreadChips(
    answers: List<AnswerRouter.Answer>,
    selected: Int,
    trailingInset: Dp,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette
    val pageColor = colors.page
    val listState = rememberLazyListState()
    val fadePx = with(LocalDensity.current) { trailingInset.toPx() }

    LaunchedEffect(selected, answers.size) {
        if (selected in answers.indices) listState.animateScrollToItem(selected)
    }

    Box(modifier = modifier) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // Keeps the last chip out from under the trailing fade at scroll end.
            contentPadding = PaddingValues(start = 20.dp, end = trailingInset, top = 2.dp, bottom = 2.dp),
        ) {
            itemsIndexed(answers, key = { _, answer -> answer.id }) { index, answer ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) Accent else colors.fill)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = PromptLabel.short(answer.question),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) Color.White else colors.textPrimary,
                        maxLines = 1,
                    )
                }
            }
        }

        // Softens the chips out just before the close button, so a tab scrolling past
        // doesn't collide with it.
        //
        // `matchParentSize` rather than `fillMaxHeight`: this Box is measured against
        // the header's own loose height constraint, and filling it would stretch the
        // chip row — and with it the header — to the full height of the sheet, pushing
        // the title and the answer off the bottom of the screen.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, pageColor),
                            startX = size.width - fadePx,
                            endX = size.width,
                        )
                    )
                }
        )
    }
}

private val ChipFadeWidth = 26.dp
