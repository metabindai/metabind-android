/*
 * MetabindAssistantView.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import kotlinx.coroutines.launch

/**
 * A drop-in conversational AI view powered by [MetabindAssistant].
 *
 * Renders the full conversation UI: user/assistant message bubbles, tool result
 * rendering via BindJS or WebView, a text input bar, and streaming indicators.
 * Theming follows Material3 defaults from the host app's theme.
 *
 * For custom UI, observe [MetabindAssistant.messages] directly and build your
 * own views around the [ChatMessage] list, mounting [MetabindToolView] for the
 * tool cards.
 *
 * ```kotlin
 * val assistant = remember {
 *     MetabindAssistant(apiKey = key, orgId = orgId, projectId = projectId)
 * }
 * MetabindAssistantView(assistant = assistant)
 * ```
 */
@Composable
fun MetabindAssistantView(
    assistant: MetabindAssistant,
    modifier: Modifier = Modifier,
) {
    val messages by assistant.messages.collectAsState()
    val isLoading by assistant.isLoading.collectAsState()
    val toolUIContent by assistant.toolUIContent.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()

    val sendAndScroll: (String) -> Unit = remember(assistant, listState, scrollScope) {
        { text ->
            assistant.send(text)
            scrollScope.launch { listState.smoothScrollToBottom() }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.smoothScrollToBottom()
    }

    // 0f when the large "Metabind Assistant" title is fully in view at the top,
    // 1f once it has scrolled completely under the top edge. Drives the collapse
    // into the pinned, centered compact title bar.
    val collapseProgress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val header = info.visibleItemsInfo.firstOrNull { it.key == "header" }
            if (header == null) {
                // Header not laid out: either nothing yet (0f) or scrolled past it (1f).
                if (info.totalItemsCount > 0 && info.visibleItemsInfo.isNotEmpty()) 1f else 0f
            } else {
                val hidden = (info.viewportStartOffset - header.offset).coerceAtLeast(0)
                (hidden.toFloat() / header.size.coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }

    // In-flow layout: the message list takes the remaining space (weight 1f) and
    // CONTRACTS when the input bar rises with the keyboard (imePadding on the root
    // Column), so chat content always stays above the input field — never hidden
    // behind it.
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
      Box(
          modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
      ) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading && messages.lastOrNull()?.let { last ->
                    last.role == MessageRole.USER ||
                            (last.role == MessageRole.ASSISTANT && last.content.isEmpty()) ||
                            (last.role == MessageRole.TOOL && last.toolStatus == ToolStatus.LOADING)
                } == true) {
                item(key = "thinking") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Thinking...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(messages.asReversed(), key = { it.id }) { message ->
                MessageBubble(
                    assistant = assistant,
                    message = message,
                    toolUIContent = toolUIContent[message.id],
                    onSendMessage = sendAndScroll,
                )
            }

            // reverseLayout = true, so the last-declared item renders at the very
            // top of the conversation — the large "Metabind Assistant" title that
            // fades out as it scrolls under the pinned compact title (matches iOS).
            item(key = "header") {
                Text(
                    text = "Metabind Assistant",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                        .graphicsLayer { alpha = 1f - collapseProgress }
                )
            }
        }

        // Pinned compact title: centered, fades in behind a translucent surface
        // scrim as the large title scrolls under the top edge. Chat scrolls (and
        // is faintly visible) underneath it. Fade is driven in the draw phase via
        // graphicsLayer alpha so scrolling doesn't recompose the bar. It has no
        // pointer handler, so touches/scroll pass through to the list below.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = collapseProgress }
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Metabind Assistant",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
      }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ChatInputBar(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank() && !isLoading) {
                    sendAndScroll(inputText.trim())
                    inputText = ""
                }
            },
            enabled = !isLoading
        )
    }
}

private suspend fun LazyListState.smoothScrollToBottom() {
    val firstIndex = firstVisibleItemIndex
    val firstOffset = firstVisibleItemScrollOffset
    if (firstIndex == 0 && firstOffset == 0) return

    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val avgItemSize = if (visible.isNotEmpty()) {
        visible.sumOf { it.size }.toFloat() / visible.size
    } else {
        200f
    }
    val itemStride = avgItemSize + info.mainAxisItemSpacing
    val distancePx = (firstIndex * itemStride + firstOffset).coerceAtLeast(1f)
    val target = -(distancePx + 64f)
    val duration = (distancePx / 1.5f).toInt().coerceIn(250, 700)

    animateScrollBy(
        value = target,
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
    )
}

@Composable
private fun MessageBubble(
    assistant: MetabindAssistant,
    message: ChatMessage,
    toolUIContent: ToolUIContent? = null,
    onSendMessage: (String) -> Unit = {},
) {
    when (message.role) {
        MessageRole.USER -> UserBubble(message)
        MessageRole.ASSISTANT -> AssistantBubble(message)
        MessageRole.ERROR -> ErrorBubble(message)
        MessageRole.TOOL -> {
            if (toolUIContent != null) {
                MetabindToolView(
                    assistant = assistant,
                    toolName = message.toolName ?: "",
                    content = toolUIContent,
                    onSendMessage = onSendMessage,
                    placeholder = {
                        if (message.toolStatus == ToolStatus.LOADING) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun UserBubble(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp),
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 20.dp,
                        bottomEnd = 4.dp
                    )
                )
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun AssistantBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        if (message.content.isNotEmpty()) {
            RichText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 32.dp)
            ) {
                Markdown(message.content)
            }
        }
    }
}

@Composable
private fun ErrorBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message.content,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = text,
            onValueChange = { newValue ->
                if (newValue.endsWith("\n")) onSend() else onTextChange(newValue)
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            maxLines = 5
        )

        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank() && enabled,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (text.isNotBlank() && enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.mb_ic_send),
                contentDescription = "Send",
                tint = if (text.isNotBlank() && enabled)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
