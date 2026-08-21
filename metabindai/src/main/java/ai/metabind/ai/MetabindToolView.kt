/*
 * MetabindToolView.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.ai

import ai.metabind.bindjs.JsRuntime
import ai.metabind.bindjs.JsRuntimeImpl
import ai.metabind.bindjs.McpHost
import ai.metabind.bindjs.composables.BindJSView
import ai.metabind.bindjs.composables.LocalHostScrollsVertically
import ai.metabind.bindjs.composables.UiEvent
import ai.metabind.bindjs.model.BaseComponent
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Renders one tool call's UI — the MCP App a tool declared through its `ui`
 * resource — as native Compose (BindJS) or in a sandboxed [WebView] (`text/html`).
 *
 * This is the same renderer [MetabindAssistantView] mounts for its tool bubbles,
 * exposed so a custom conversational surface can place cards wherever it likes
 * (a home screen, a sheet, a tab) while keeping the host bridge, hook state and
 * re-render plumbing identical to the drop-in view.
 *
 * Pair it with [MetabindAssistant.toolUIContent], which is keyed by tool-call id
 * — the same id as the `TOOL` [ChatMessage] the call produced:
 *
 * ```kotlin
 * val content = assistant.toolUIContent.collectAsState().value[message.id]
 * if (content != null) {
 *     MetabindToolView(
 *         assistant = assistant,
 *         toolName = message.toolName ?: "",
 *         content = content,
 *     )
 * }
 * ```
 *
 * @param assistant the assistant that produced [content]; supplies `host.toolCall`
 *   and `host.updateModelContext` for the rendered component.
 * @param toolName the tool this UI belongs to, passed to the component as
 *   `environment.toolName`.
 * @param content the resolved UI content for the tool call.
 * @param onSendMessage where `host.sendMessage` from the component lands. Defaults
 *   to [MetabindAssistant.send]; a custom surface that routes turns itself (so a
 *   component-driven question shows up in the right place) should override it.
 * @param onOpenLink where `host.openLink` lands. Defaults to an external
 *   `ACTION_VIEW` intent.
 * @param placeholder shown until the component's first render lands.
 */
@Composable
fun MetabindToolView(
    assistant: MetabindAssistant,
    toolName: String,
    content: ToolUIContent,
    modifier: Modifier = Modifier,
    onSendMessage: (String) -> Unit = { assistant.send(it) },
    onOpenLink: ((String) -> Unit)? = null,
    placeholder: @Composable () -> Unit = {},
) {
    when (content) {
        is ToolUIContent.BindJS -> BindJSToolContent(
            content = content,
            toolName = toolName,
            modifier = modifier,
            onSendMessage = onSendMessage,
            onOpenLink = onOpenLink,
            onUpdateModelContext = assistant::mergePendingContext,
            onCallTool = assistant::callMcpTool,
            placeholder = placeholder,
        )

        is ToolUIContent.Html -> HtmlToolContent(
            content = content,
            modifier = modifier,
        )
    }
}

@Composable
private fun BindJSToolContent(
    content: ToolUIContent.BindJS,
    toolName: String,
    modifier: Modifier,
    onSendMessage: (String) -> Unit,
    onOpenLink: ((String) -> Unit)?,
    onUpdateModelContext: (Map<String, Any?>) -> Unit,
    onCallTool: suspend (String, Map<String, Any?>) -> Any?,
    placeholder: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Each tool card gets its own isolate: a shared runtime would let sibling
    // cards overwrite each other's handler table, hook state, rerender
    // listener and mcpHost — so rendering a new card freezes the older ones.
    val jsRuntime = remember { JsRuntimeImpl.create(context.applicationContext) }
    DisposableEffect(jsRuntime) {
        onDispose { jsRuntime.close() }
    }
    var renderedComponent by remember { mutableStateOf<BaseComponent<*>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun rerender() {
        try {
            // Atomic willRender + callComponent (see JsRuntime.renderComponent):
            // splitting the pair lets concurrent re-renders corrupt the shared
            // JS hook state and handlers stop firing.
            val next = jsRuntime.renderComponent(
                content.layoutComponentName,
                jsonObjectToMap(content.toolArguments),
            )
            renderedComponent = next
            version++
        } catch (e: CancellationException) {
            // The card left composition, or its content was replaced mid-render (a
            // tool result landing behind the arguments does exactly that). Neither is
            // a failure, and swallowing it would break the caller's cancellation.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "rerender failed", e)
        }
    }

    LaunchedEffect(content) {
        try {
            jsRuntime.setOnRerenderRequested { coroutineScope.launch { rerender() } }
            jsRuntime.setMcpHost(object : McpHost {
                override fun openLink(url: String) {
                    val handler = onOpenLink
                    if (handler != null) {
                        handler(url)
                        return
                    }
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

                override suspend fun toolCall(name: String, args: Map<String, Any?>): Any? =
                    onCallTool(name, args)
            })
            jsRuntime.awaitReady()
            jsRuntime.setComponents(content.designerComponent)
            jsRuntime.setEnvironment(
                buildBindJSEnvironment(
                    toolName = toolName,
                    toolArguments = content.toolArguments,
                    toolResult = content.toolResultText
                )
            )
            renderedComponent = jsRuntime.renderComponent(
                content.layoutComponentName,
                jsonObjectToMap(content.toolArguments),
            )
            version++
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render BindJS component", e)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
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
        } else {
            placeholder()
        }
    }
}

@Composable
private fun HtmlToolContent(
    content: ToolUIContent.Html,
    modifier: Modifier,
) {
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
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
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

private const val TAG = "MetabindToolView"

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

private fun handleBindJSEvent(jsRuntime: JsRuntime, event: UiEvent) {
    CoroutineScope(Dispatchers.IO).launch {
        when (event) {
            is UiEvent.OnTap -> jsRuntime.callEventHandler(event.handlerId)
            is UiEvent.OnAppear -> jsRuntime.callEventHandler(event.handlerId)
            is UiEvent.OnDisappear -> jsRuntime.callEventHandler(event.handlerId)
            is UiEvent.OnChange -> jsRuntime.callEventHandler(
                event.handlerId,
                arrayOf(event.oldValue ?: "", event.newValue ?: "")
            )
            is UiEvent.OnLongPress -> jsRuntime.callEventHandler(event.handlerId)
            is UiEvent.OnSwitch -> jsRuntime.callEventHandler(event.handlerId, arrayOf(event.checked))
            is UiEvent.OnTextChange -> jsRuntime.callEventHandler(event.handlerId, arrayOf(event.text))
            // Coalesced + serialized inside bindjs (latest-wins on the `changed`
            // phase); re-renders via the setOnRerenderRequested listener.
            is UiEvent.OnDrag -> jsRuntime.dispatchDragEvent(event.handlerId, event.state)
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
