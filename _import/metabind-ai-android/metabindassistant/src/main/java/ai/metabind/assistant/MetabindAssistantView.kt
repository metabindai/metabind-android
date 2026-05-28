/*
 * MetabindAssistantView.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.metabind.bindjs.JsRuntimeImpl
import ai.metabind.bindjs.McpHost
import ai.metabind.bindjs.composables.BindJSView
import ai.metabind.bindjs.composables.LocalHostScrollsVertically
import ai.metabind.bindjs.composables.UiEvent
import ai.metabind.bindjs.model.BaseComponent
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A drop-in conversational AI view powered by [MetabindAssistant].
 *
 * Renders the full conversation UI: user/assistant message bubbles, tool result
 * rendering via BindJS or WebView, a text input bar, and streaming indicators.
 * Theming follows Material3 defaults from the host app's theme.
 *
 * For custom UI, observe [MetabindAssistant.messages] directly and build your
 * own views around the [ChatMessage] list.
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
    val error by assistant.error.collectAsState()
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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
                    message = message,
                    toolUIContent = toolUIContent[message.id],
                    onSendMessage = sendAndScroll,
                    onUpdateModelContext = assistant::mergePendingContext,
                    onCallTool = assistant::callMcpTool,
                )
            }
        }

        if (error != null) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = null,
                dismissAction = null
            ) {
                Text(error ?: "")
            }
            LaunchedEffect(error) {
                kotlinx.coroutines.delay(3000)
                assistant.clearError()
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
                    message, toolUIContent, onSendMessage, onUpdateModelContext, onCallTool
                )
                is ToolUIContent.Html -> HtmlToolBubble(message, toolUIContent)
                null -> {}
            }
        }
    }
}

@Composable
private fun ToolStatusHeader(message: ChatMessage) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        when (message.toolStatus) {
            ToolStatus.LOADING -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ToolStatus.COMPLETED -> Text(
                text = "✓",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            ToolStatus.ERROR -> Text(
                text = "✗",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
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
            jsRuntime.setOnRerenderRequested { coroutineScope.launch { rerender() } }
            jsRuntime.setMcpHost(object : McpHost {
                override fun openLink(url: String) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                override fun sendMessage(message: String) { onSendMessage(message) }
                override fun updateModelContext(content: Map<String, Any?>) { onUpdateModelContext(content) }
                override fun log(level: String, message: String) { Log.d("BindJSHost", "[$level] $message") }
                override suspend fun toolCall(name: String, args: Map<String, Any?>): Any? =
                    onCallTool(name, args)
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
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
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

        val heightModifier = contentHeight?.let { Modifier.height(it) } ?: Modifier.height(384.dp)

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
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
                .clip(RoundedCornerShape(16.dp))
        )
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
                .widthIn(max = 300.dp)
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
                    .widthIn(max = 320.dp)
                    .padding(end = 32.dp)
            ) {
                Markdown(message.content)
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
            is UiEvent.OnSwitch -> jsRuntime.callEventHandler(event.handlerId, arrayOf(event.checked))
            is UiEvent.OnDrag -> jsRuntime.callEventHandler(event.handlerId, arrayOf(event.state))
            is UiEvent.OnNavigationTap -> jsRuntime.callEventHandler(event.handlerId)
            is UiEvent.OnPickerTap -> jsRuntime.callPickerSetter(event.setterId, event.tag)
            is UiEvent.OnChartSelection -> jsRuntime.callEventHandler(event.handlerId, arrayOf(event.value))
        }
    }
}

private fun buildBindJSEnvironment(
    toolName: String,
    toolArguments: JsonElement?,
    toolResult: String?,
): Map<String, Any> {
    val env = mutableMapOf<String, Any>("toolName" to toolName)
    if (toolArguments != null) env["toolArguments"] = jsonElementToAny(toolArguments)
    if (toolResult != null) env["toolResult"] = toolResult
    return env
}

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
