/*
 * HomeScreen.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.ui.screens

import ai.metabind.assistant.ChatMessage
import ai.metabind.assistant.MessageRole
import ai.metabind.assistant.ToolStatus
import ai.metabind.assistant.ToolUIContent
import ai.metabind.assistant.demo.R
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import ai.metabind.bindjs.JsRuntimeImpl
import ai.metabind.bindjs.McpHost
import ai.metabind.bindjs.composables.BindJSView
import ai.metabind.bindjs.composables.LocalHostScrollsVertically
import ai.metabind.bindjs.composables.UiEvent
import ai.metabind.bindjs.model.BaseComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun HomeScreen(
    onNavigateToKeyEntry: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    BackHandler {}

    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val toolUIContent by viewModel.toolUIContent.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()

    // Stable across recompositions so the lambda passed to MessageBubble
    // keeps referential equality and doesn't force item recomposition.
    val sendMessageAndScroll: (String) -> Unit = remember(viewModel, listState, scrollScope) {
        { text ->
            viewModel.sendMessage(text)
            scrollScope.launch { listState.smoothScrollToBottom() }
        }
    }

    // Whenever a new bubble is appended (assistant turn start, tool call,
    // tool result), scroll to it. Keyed on size only — text streaming inside
    // an existing bubble doesn't change size, so it won't fight the stream.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.smoothScrollToBottom()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        LazyColumn(
            state = listState,
            // Anchored to the bottom: index 0 is the newest message, items are
            // stacked upward. Streaming text and BindJS height growth push
            // older content up naturally — no manual scroll-to-bottom needed.
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = dimensionResource(R.dimen.space2x),
                vertical = dimensionResource(R.dimen.space1_5x)
            ),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.space2x))
        ) {
            // In reverseLayout, the FIRST declared item is at the visual
            // bottom, so the spinner sits below the newest message.
            if (isLoading && messages.lastOrNull()?.let { last ->
                    last.role == MessageRole.USER ||
                            (last.role == MessageRole.ASSISTANT && last.content.isEmpty()) ||
                            (last.role == MessageRole.TOOL && last.toolStatus == ToolStatus.LOADING)
                } == true) {
                item(key = "thinking") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.space1x))
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(dimensionResource(R.dimen.space2x)),
                            strokeWidth = dimensionResource(R.dimen.space0_25x),
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
                    message = message,
                    toolUIContent = toolUIContent[message.id],
                    onSendMessage = sendMessageAndScroll,
                    onUpdateModelContext = viewModel::mergePendingContext,
                    onCallTool = viewModel::callMcpTool,
                )
            }
        }

        if (error != null) {
            Snackbar(
                modifier = Modifier.padding(dimensionResource(R.dimen.space2x)),
                action = null,
                dismissAction = null
            ) {
                Text(error ?: "")
            }
            LaunchedEffect(error) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearError()
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ChatInputBar(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank() && !isLoading) {
                    sendMessageAndScroll(inputText.trim())
                    inputText = ""
                }
            },
            enabled = !isLoading
        )
    }
}

/**
 * Smoothly scroll a reverseLayout LazyColumn to its visual bottom (index 0).
 *
 * Why not `animateScrollToItem`: it teleports close to the target before
 * animating the tail end — reads as a jump for far targets.
 *
 * Why not a fixed-target `animateScrollBy(-100_000f)`: each frame's delta is
 * clamped to the remaining scroll, so if the requested per-frame delta
 * exceeds the actual distance, the entire scroll consumes in a single frame
 * regardless of `tween` duration. We have to size the animation `value` to
 * the real remaining distance and scale duration with it.
 */
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

    // Small overshoot so the clamp lands us cleanly at offset 0 even when
    // off-viewport item heights differ from our average estimate.
    val target = -(distancePx + 64f)
    // Ramp duration with distance for a chat-feeling motion: short hops feel
    // snappy, long jumps stay paced.
    val duration = (distancePx / 1.5f).toInt().coerceIn(250, 700)

    animateScrollBy(
        value = target,
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
    )
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    toolUIContent: ToolUIContent? = null,
    onSendMessage: (String) -> Unit = {},
    onUpdateModelContext: (Map<String, Any?>) -> Unit = {},
    onCallTool: suspend (String, Map<String, Any?>) -> Any? = { _, _ -> null },
) {
    when (message.role) {
        MessageRole.USER -> UserBubble(message)
        MessageRole.ASSISTANT -> AssistantBubble(message)
        MessageRole.TOOL -> {
            when (toolUIContent) {
                is ToolUIContent.BindJS -> BindJSToolBubble(
                    message,
                    toolUIContent,
                    onSendMessage,
                    onUpdateModelContext,
                    onCallTool,
                )
                is ToolUIContent.Html -> HtmlToolBubble(message, toolUIContent)
                null -> {} // Hide tools without UI — data-only tools are not user-facing
            }
        }
    }
}

@Composable
private fun ToolStatusHeader(message: ChatMessage) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.space0_5x)),
        modifier = Modifier.padding(bottom = dimensionResource(R.dimen.space0_5x))
    ) {
        when (message.toolStatus) {
            ToolStatus.LOADING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(dimensionResource(R.dimen.space2x)),
                    strokeWidth = dimensionResource(R.dimen.space0_25x),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ToolStatus.COMPLETED -> {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            ToolStatus.ERROR -> {
                Text(
                    text = "✗",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            null -> {}
        }
        Text(
            text = message.toolName ?: "Tool",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BindJSToolBubble(
    message: ChatMessage,
    content: ToolUIContent.BindJS,
    onSendMessage: (String) -> Unit,
    onUpdateModelContext: (Map<String, Any?>) -> Unit,
    onCallTool: suspend (String, Map<String, Any?>) -> Any?,
) {
    val context = LocalContext.current
    val jsRuntime = remember { JsRuntimeImpl.getInstance(context.applicationContext) }
    var renderedComponent by remember { mutableStateOf<BaseComponent<*>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // Re-fetch the tree from JS after a `useState` setter fires. The JS
    // shim coalesces bursts, so this fires once per microtask flush. We
    // call back into the runtime singleton — if another bubble has since
    // claimed the host, that's fine, hooks are keyed by render path and
    // the previous bubble simply won't re-render.
    //
    // `willRender()` is required before each `callComponent`: it resets
    // `hookState.path` / `childIndex` to zero so the JS tree traversal
    // can re-find each component's hooks via the same path it used the
    // first time. Without this, the second render reads from stale paths
    // and `useState` returns its initial value, so freshly-hydrated data
    // looks like it never landed.
    suspend fun rerender() {
        try {
            jsRuntime.willRender()
            val next = jsRuntime.callComponent(
                content.layoutComponentName,
                jsonObjectToMap(content.toolArguments),
            )
            renderedComponent = next
            version++
        } catch (e: Exception) {
            Log.e("BindJSToolBubble", "rerender failed", e)
        }
    }

    LaunchedEffect(content) {
        try {
            jsRuntime.setOnRerenderRequested {
                coroutineScope.launch { rerender() }
            }
            jsRuntime.setMcpHost(object : McpHost {
                override fun openLink(url: String) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }

                override fun sendMessage(message: String) {
                    onSendMessage(message)
                }

                override fun updateModelContext(content: Map<String, Any?>) {
                    onUpdateModelContext(content)
                }

                override fun log(level: String, message: String) {
                    Log.d("BindJSHost", "[$level] $message")
                }

                override suspend fun toolCall(
                    name: String,
                    args: Map<String, Any?>,
                ): Any? {
                    return onCallTool(name, args)
                }
            })
            jsRuntime.awaitReady()
            jsRuntime.setComponents(content.designerComponent)
            jsRuntime.setEnvironment(
                buildBindJSEnvironment(
                    toolName = message.toolName ?: "",
                    toolArguments = content.toolArguments,
                    toolResult = content.toolResultText
                )
            )
            jsRuntime.willRender()
            renderedComponent = jsRuntime.callComponent(
                content.layoutComponentName,
                jsonObjectToMap(content.toolArguments),
            )
            version++
        } catch (e: Exception) {
            Log.e("BindJSToolBubble", "Failed to render BindJS component", e)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        ToolStatusHeader(message)

        val component = renderedComponent
        if (component != null) {
            CompositionLocalProvider(LocalHostScrollsVertically provides true) {
                BindJSView(
                    jsRuntime = jsRuntime,
                    component = component,
                    version = version,
                    onUiEvent = { event -> handleBindJSEvent(jsRuntime, event) }
                )
            }
        } else if (message.toolStatus == ToolStatus.LOADING) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.space6x)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(dimensionResource(R.dimen.space3x)),
                    strokeWidth = dimensionResource(R.dimen.space0_25x)
                )
            }
        }
    }
}

@Composable
private fun HtmlToolBubble(message: ChatMessage, content: ToolUIContent.Html) {
    val hostedHtml = remember(content.html, content.toolArguments) {
        buildMcpAppHostHtml(content.html, content.toolArguments)
    }

    var contentHeight by remember(content.html) { mutableStateOf<Dp?>(null) }
    val density = LocalDensity.current
    val bridge = remember {
        object {
            @JavascriptInterface
            fun onSizeChanged(heightCssPx: Int) {
                if (heightCssPx > 0) contentHeight = with(density) { heightCssPx.toDp() }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        ToolStatusHeader(message)

        val heightModifier = contentHeight
            ?.let { Modifier.height(it) }
            ?: Modifier.height(dimensionResource(R.dimen.space6x) * 8)

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.loadsImagesAutomatically = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webViewClient = WebViewClient()
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    addJavascriptInterface(bridge, "MBAndroidHost")
                }
            },
            update = { webView ->
                if (webView.tag != hostedHtml) {
                    webView.tag = hostedHtml
                    webView.loadDataWithBaseURL(
                        "https://localhost/",
                        hostedHtml,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(heightModifier)
                .clip(RoundedCornerShape(dimensionResource(R.dimen.space2x)))
        )
    }
}

/**
 * Wraps an MCP-app HTML page in a tiny iframe host so the renderer (which
 * talks JSON-RPC via `window.parent.postMessage`) finds a real parent to
 * handshake with. Mirrors the iOS host: reply to `ui/initialize` and push
 * `ui/notifications/tool-input` with the tool arguments.
 */
private fun buildMcpAppHostHtml(mcpAppHtml: String, toolArguments: JsonElement?): String {
    val srcdoc = mcpAppHtml
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    val argsJson = toolArguments?.toString() ?: "{}"
    return """
<!DOCTYPE html><html><head><meta charset="utf-8">
<style>
html,body{margin:0;padding:0;height:100%;background:transparent}
iframe{border:0;width:100%;height:100%;display:block;background:transparent}
</style></head><body>
<iframe id="mb-child" srcdoc="$srcdoc"></iframe>
<script>
(function(){
  var iframe = document.getElementById('mb-child');
  var child = iframe.contentWindow;
  var toolArgs = $argsJson;
  var sentToolInput = false;

  window.addEventListener('message', function(ev){
    var m = ev.data;
    if (!m || m.jsonrpc !== '2.0') return;
    if (m.method === 'ui/initialize' && m.id != null) {
      child.postMessage({
        jsonrpc:'2.0', id:m.id,
        result:{
          hostContext:{ theme:'light' },
          hostInfo:{ name:'Metabind Android', version:'1.0.0' },
          hostCapabilities:{},
          protocolVersion: m.params && m.params.protocolVersion || '2026-01-26'
        }
      }, '*');
      return;
    }
    if (m.method === 'ui/notifications/initialized') {
      if (sentToolInput) return;
      sentToolInput = true;
      child.postMessage({
        jsonrpc:'2.0',
        method:'ui/notifications/tool-input',
        params:{ arguments: toolArgs }
      }, '*');
      return;
    }
    if (m.method === 'ui/notifications/size-changed') {
      var h = m.params && m.params.height;
      if (typeof h === 'number' && window.MBAndroidHost && window.MBAndroidHost.onSizeChanged) {
        window.MBAndroidHost.onSizeChanged(Math.ceil(h));
      }
      return;
    }
    if (m.id != null && m.method) {
      child.postMessage({ jsonrpc:'2.0', id:m.id, result:{} }, '*');
    }
  });
})();
</script></body></html>
""".trimIndent()
}

private fun handleBindJSEvent(jsRuntime: ai.metabind.bindjs.JsRuntime, event: UiEvent) {
    CoroutineScope(Dispatchers.IO).launch {
        when (event) {
            is UiEvent.OnTap -> jsRuntime.callEventHandler(event.handlerId)
            is UiEvent.OnAppear -> jsRuntime.callEventHandler(event.handlerId)
            is UiEvent.OnDisappear -> jsRuntime.callEventHandler(event.handlerId)
            is UiEvent.OnLongPress -> jsRuntime.callEventHandler(event.handlerId)
            is UiEvent.OnSwitch -> jsRuntime.callEventHandler(
                event.handlerId,
                arrayOf(event.checked)
            )

            is UiEvent.OnDrag -> jsRuntime.callEventHandler(event.handlerId)
            is UiEvent.OnNavigationTap -> jsRuntime.callEventHandler(event.handlerId)
            is UiEvent.OnPickerTap -> jsRuntime.callPickerSetter(event.setterId, event.tag)
        }
    }
}

/**
 * Build the environment map for the BindJS component, matching the iOS MCPAppContent behavior.
 */
private fun buildBindJSEnvironment(
    toolName: String,
    toolArguments: JsonElement?,
    toolResult: String?,
): Map<String, Any> {
    val env = mutableMapOf<String, Any>(
        "toolName" to toolName
    )
    if (toolArguments != null) {
        env["toolArguments"] = jsonElementToAny(toolArguments)
    }
    if (toolResult != null) {
        env["toolResult"] = toolResult
    }
    return env
}

/**
 * Convert a kotlinx.serialization JsonElement to a plain Any value for the BindJS environment.
 */
private fun jsonElementToAny(element: JsonElement): Any {
    return when (element) {
        is JsonPrimitive -> {
            if (element.isString) element.content
            else element.content.toBooleanStrictOrNull()
                ?: element.content.toLongOrNull()
                ?: element.content.toDoubleOrNull()
                ?: element.content
        }

        is JsonObject -> element.entries.associate { (k, v) -> k to jsonElementToAny(v) }
        is JsonArray -> element.map { jsonElementToAny(it) }
        is JsonNull -> "null"
    }
}

/**
 * Convert a JsonElement (expected to be an object) into the loosely-typed map
 * used by [ai.metabind.bindjs.JsRuntime.callComponent] for component props.
 * Non-object inputs and null produce an empty map.
 */
private fun jsonObjectToMap(element: JsonElement?): Map<String, Any?> {
    val obj = element as? JsonObject ?: return emptyMap()
    return obj.entries.associate { (k, v) -> k to jsonElementToNullableAny(v) }
}

private fun jsonElementToNullableAny(element: JsonElement): Any? {
    return when (element) {
        is JsonNull -> null
        is JsonPrimitive -> {
            if (element.isString) element.content
            else element.content.toBooleanStrictOrNull()
                ?: element.content.toLongOrNull()
                ?: element.content.toDoubleOrNull()
                ?: element.content
        }
        is JsonObject -> element.entries.associate { (k, v) -> k to jsonElementToNullableAny(v) }
        is JsonArray -> element.map { jsonElementToNullableAny(it) }
    }
}

@Composable
private fun UserBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = dimensionResource(R.dimen.bubble_max_width))
                .clip(
                    RoundedCornerShape(
                        topStart = dimensionResource(R.dimen.space2_5x),
                        topEnd = dimensionResource(R.dimen.space2_5x),
                        bottomStart = dimensionResource(R.dimen.space2_5x),
                        bottomEnd = dimensionResource(R.dimen.space0_5x)
                    )
                )
                .background(MaterialTheme.colorScheme.primary)
                .padding(
                    horizontal = dimensionResource(R.dimen.space2x),
                    vertical = dimensionResource(R.dimen.space1_25x)
                )
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
                    .widthIn(max = dimensionResource(R.dimen.assistant_max_width))
                    .padding(end = dimensionResource(R.dimen.space4x))
            ) {
                Markdown(message.content)
            }
        }
    }
}

@Composable
private fun PlainToolBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimensionResource(R.dimen.space2x)))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(dimensionResource(R.dimen.space1_5x))
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.space1x))
                ) {
                    when (message.toolStatus) {
                        ToolStatus.LOADING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(dimensionResource(R.dimen.space2x)),
                                strokeWidth = dimensionResource(R.dimen.space0_25x),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ToolStatus.COMPLETED -> {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        ToolStatus.ERROR -> {
                            Text(
                                text = "✗",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        null -> {}
                    }
                    Text(
                        text = message.toolName ?: "Tool",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (message.content.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space1x)))
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.toolStatus == ToolStatus.ERROR)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6
                    )
                }
            }
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
            .padding(
                horizontal = dimensionResource(R.dimen.space1_5x),
                vertical = dimensionResource(R.dimen.space1x)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.space1x))
    ) {
        TextField(
            value = text,
            onValueChange = { newValue ->
                if (newValue.endsWith("\n")) {
                    onSend()
                } else {
                    onTextChange(newValue)
                }
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") },
            shape = RoundedCornerShape(dimensionResource(R.dimen.space3x)),
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
                .size(dimensionResource(R.dimen.space6x))
                .clip(CircleShape)
                .background(
                    if (text.isNotBlank() && enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_send),
                contentDescription = "Send",
                tint = if (text.isNotBlank() && enabled)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimensionResource(R.dimen.space3x))
            )
        }
    }
}
