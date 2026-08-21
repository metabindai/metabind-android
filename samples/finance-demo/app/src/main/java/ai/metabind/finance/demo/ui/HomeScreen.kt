/*
 * HomeScreen.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo.ui

import ai.metabind.finance.demo.R
import ai.metabind.finance.demo.ui.theme.Accent
import ai.metabind.finance.demo.ui.theme.palette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The whole app, more or less. One question is asked on launch and its rendered
 * answer *is* the home screen; every later question is asked from the rail at the
 * bottom and answered in a sheet on top of it. There is no transcript and nothing
 * pushing anywhere — you're always here.
 */
@Composable
fun HomeScreen(
    router: AnswerRouter,
    onResetApiKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }

    LaunchedEffect(router) { router.start() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page)
    ) {
        // A soft wash of the accent behind the top of the screen, so the home answer
        // reads as the top of a page rather than a floating card. Behind the title
        // bar as well as the content, which is why it sits outside the column.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Accent.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(
                isBusy = router.isBusy,
                onRefresh = router::reloadHome,
                onResetApiKey = onResetApiKey,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    // The ask bar floats over this content rather than pushing it, so
                    // the last card has to clear the bar's measured height itself.
                    .padding(top = 4.dp, bottom = barHeight + 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The title bar says what the app is; this says what you're looking at.
                Text(
                    text = AnswerRouter.HOME_QUESTION,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                HomeAnswer(router)
            }
        }

        QuestionBar(
            onAsk = router::ask,
            onCancel = router::cancelPending,
            prompts = Prompts.Starters,
            modelSuggestions = router.homeNextSteps,
            pending = router.pending?.question,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
        )
    }

    if (router.isAnswerPresented) {
        AnswerSheet(router)
    }
}

@Composable
private fun HomeAnswer(router: AnswerRouter) {
    val home = router.home
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (home == null) {
            LoadingCard()
            return@Column
        }

        if (home.prose.isNotEmpty()) {
            // Keyed to the answer so a refresh re-runs the reveal; a stable key would
            // leave it static after the first load.
            key(home.id) { BlurRevealText(markdown = home.prose) }
        }

        if (home.cards.isNotEmpty()) {
            key(home.id) {
                AnswerCards(
                    assistant = router.assistant,
                    cards = home.cards,
                    onSendMessage = router::ask,
                )
            }
        } else if (home.prose.isEmpty()) {
            EmptyAnswerCard(onRetry = { router.retry(home) })
        }
    }
}

/**
 * Title plus the overflow menu. Everything that acts on the whole screen rather
 * than on one answer lives in the menu.
 *
 * iOS also offers a "Reset Cache" item: its SDK caches `ui://` resources and the
 * decoded BindJS packages, and republishing a component needs that dropped. The
 * Android assistant reads the resource fresh for every tool call, so there is
 * nothing to drop and the item would be a no-op.
 */
@Composable
private fun TitleBar(
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onResetApiKey: () -> Unit,
) {
    val colors = palette
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.fd_ic_more_vert),
                    contentDescription = stringResource(R.string.menu_more),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_refresh)) },
                    enabled = !isBusy,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.fd_ic_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onRefresh()
                    },
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.menu_debug),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.menu_reset_api_key),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.fd_ic_key),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onResetApiKey()
                    },
                )
            }
        }
    }
}

/** Height of the fade the sheet header dissolves through. Shared with [AnswerSheet]. */
internal val HeaderFadeHeight: Dp = 30.dp
