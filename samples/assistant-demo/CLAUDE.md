# CLAUDE.md

Context for agents working in this repo. Read together with `README.md` (user-facing) — this file is the developer/internals view.

This is the `assistant-demo` sample inside the `metabind-android` monorepo
(`samples/assistant-demo`). It is its own Gradle build; see the composite-build wiring below.

## What this app is

A Jetpack Compose chat app demoing the **`metabindai-android`** SDK (module `:metabindai`, package `ai.metabind.ai`). The user types a message, an LLM (via the Metabind Agent proxy) streams a reply and emits MCP tool calls; for tools that declare a `ui` resource, the app fetches the resource and renders it natively via **BindJS** (Compose-native JS-driven UI) — or in a sandboxed `WebView` for `text/html` resources.

No upstream LLM credentials ship in the binary. One Metabind API key authenticates both the MCP server and the agent proxy.

## SDK source (live source, do NOT decompile JARs)

By default this sample builds the AI SDK from the monorepo sources. `settings.gradle.kts` is wired with an active `includeBuild` composite-build substitution onto the monorepo root:

```
samples/assistant-demo            (this build)
  └── includeBuild("../..")       (the metabind-android monorepo root)
        ├── substitutes ai.metabind:metabindai-android  → :metabindai
        └── substitutes ai.metabind:mcpappshost-android → :mcpappshost
```

BindJS stays a **published** GitHub Packages artifact (`ai.metabind:bindjs-android`) — it is
not folded into the monorepo and is not substituted here. So editing source in
`../../metabindai` or `../../mcpappshost` flows into the next `:app:installDebug` build
without publishing anything; BindJS changes still require a published `bindjs-android`.
Trust the in-tree SDK sources over the resolved artifact when investigating runtime behavior.

| Module (monorepo root `../..`) | Path | What lives there |
|---|---|---|
| `:metabindai` | `../../metabindai` | `metabindai-android`: the `MetabindAgentProvider` SSE client + `ToolUIContent`. Package `ai.metabind.ai`. |
| `:mcpappshost` | `../../mcpappshost` | `mcpappshost-android`: the `MCPAppsClient` JSON-RPC client over MCP. |
| `:metabind-content` | `../../metabind-content` | `metabind-content-android`, the content SDK (not consumed by this demo). |
| `bindjs-android` | published artifact | The BindJS Kotlin runtime + Compose renderer (`JsRuntimeImpl`, `BindJSView`) and the JS isolate script. Consumed from GitHub Packages, own repo. |

## Module / package layout

```
app/src/main/java/ai/metabind/assistant/demo/
  MainActivity.kt                  — single-activity Compose host
  MetabindAssistantDemoApp.kt      — Hilt @HiltAndroidApp entry
  data/
    ApiKeyRepository.kt            — persisted via Jetpack DataStore (Preferences), cached in-memory for sync reads
    SdkModule.kt                   — Hilt @Module providing MetabindAgentProvider
  navigation/AppNavigation.kt      — KeyEntry → Home
  ui/screens/
    KeyEntryScreen.kt + ViewModel  — paste API key, validate, hand off
    HomeScreen.kt                  — chat UI + the BindJS / WebView tool bubbles
    HomeViewModel.kt               — drives streamMessage, owns llmHistory + MCPAppsClient
```

DI: Hilt. The interesting injection is `HomeViewModel(apiKeyRepository, agentProvider)`; `MCPAppsClient` is created lazily inside the VM after the API key is known.

## End-to-end tool-rendering flow

This is the part that's easy to lose track of. When the user sends a message:

1. **`HomeViewModel.sendMessage`** appends a `LLMMessage.User(...)` to `llmHistory` and calls `agentProvider.streamMessage(...)`. Any context buffered via `mergePendingContext` from a previous BindJS view gets prefixed as `<context>{…}</context>` on the model-visible text only (user-visible bubble stays clean).
2. **`MetabindAgentProvider`** streams SSE frames; the VM translates them to `LLMStreamEvent`s.
3. On `ToolCallStart`:
   - A `ChatMessage(role=TOOL, toolStatus=LOADING)` is appended.
   - If `toolUIMap[event.name]` has a `resourceUri`, `fetchToolUIContent` reads the MCP resource via `client.readResource(uri)` and parses it through `ToolUIContent.fromResource`. That yields either `ToolUIContent.BindJS` (mime `application/vnd.metabind.bindjs+json` or `application/json`) or `ToolUIContent.Html`.
4. **`HomeScreen.BindJSToolBubble`** mounts. Its `LaunchedEffect(content)` runs, in order:
   - `jsRuntime.setOnRerenderRequested { … }` — wires the JS→native rerender signal.
   - `jsRuntime.setMcpHost(host)` — JS-side this sets `runtime.mcpHost` so user code's `useMCPHost()` returns truthy.
   - `setComponents(bundle)` then `setEnvironment(...)` then `willRender()` (resets `hookState.path/childIndex` so hooks resolve to the same paths across renders).
   - `callComponent(layoutName, props)` — runs the JS body, returns a `BaseComponent<*>` tree.
   - `renderedComponent = next; version++` causes `BindJSView` to compose.
5. **BindJS host bridge** (`McpHost` impl inside the bubble): `openLink` opens an external `ACTION_VIEW` intent; `sendMessage` re-enters `HomeViewModel.sendMessage` (driving recursive turns); `updateModelContext` buffers into `pendingContext`; `toolCall(name, args)` routes through `HomeViewModel.callMcpTool` → `MCPAppsClient.callTool` → parses `textContent` as JSON → `jsonElementToPlain` → returned to JS.

### The JS ↔ Kotlin bridge — console.log channel

There is **no JS interop binding**. Communication is layered on top of `console.log` with the prefix `__MCP__::`. `JsRuntimeImpl.setConsoleCallback` parses these.

| JS emit | Kotlin handler |
|---|---|
| `__MCP__::__rerender__::[]` | `dispatchMcpMessage` → `mainHandler.post` → `onRerenderRequested.invoke()` (host-independent; fires for previews too). Coalesced via `rerenderPosted`. |
| `__MCP__::log::[level, …]` | `host.log(level, payload)` |
| `__MCP__::openLink::[url]` | `host.openLink(url)` |
| `__MCP__::sendMessage::[text]` | `host.sendMessage(text)` |
| `__MCP__::updateModelContext::[obj]` | `host.updateModelContext(obj)` |
| `__MCP__::toolCall::[id, name, args]` | `dispatchToolCall` → suspend call to `host.toolCall(name, args)` (on bindjs's long-lived `toolCallScope = SupervisorJob + Dispatchers.IO`) → result serialized via Gson, then `__resolveToolCall(id, ok, payload)` is `evaluateJavaScriptAsync`'d back into the isolate, resolving the JS-side `Promise` that `host.toolCall(...)` returned. |

If `__pendingToolCalls` ever holds entries and `setMcpHost(false)` is called, all pending promises reject with `mcp host removed`.

### Tool-call error path

Exceptions thrown by `HomeViewModel.callMcpTool` (e.g., `IllegalStateException("tool 'X' failed: …")` when MCP returns `isError`) propagate up to `dispatchToolCall`, which calls `__resolveToolCall(id, false, errorMessage)`. The JS shim rejects the Promise with `new Error(message)`, so BindJS user code recovers via standard `try { await host.toolCall(...) } catch (e) { … }`. The supervisor scope guarantees one failing tool call doesn't kill the channel for the next.

### Why `callMcpTool` flattens to plain Kotlin types

```kotlin
val parsed = Json.parseToJsonElement(text)
return jsonElementToPlain(parsed)   // → Map<String, Any?> / List<Any?> / primitives
```

The MCP `textContent` is parsed via `kotlinx.serialization`, then flattened to plain Kotlin maps/lists/primitives before being returned. The reason is that `bindjs-android` re-serializes the payload through **Gson** when writing `__resolveToolCall(id, ok, payload)` back into the JS isolate, and Gson doesn't roundtrip `kotlinx.serialization.JsonElement` instances cleanly. The flattening keeps both sides happy.

### Reactive re-render mechanism

JS `setState` setters call `runtime.needsRerender()` **synchronously** (the JS isolate has no `queueMicrotask`; using one would tear down `await host.toolCall(...)` chains). Kotlin coalesces bursts via `rerenderPosted`, then invokes the listener registered by `BindJSToolBubble`, which calls `rerender()`:

```kotlin
jsRuntime.willRender()                                  // reset hook paths
renderedComponent = jsRuntime.callComponent(name, args) // re-run body
version++                                               // bumps key on BindJSView
```

`willRender()` is **mandatory** before each `callComponent` — without it, the second render reads `useState` from stale paths and freshly-set state looks like it never landed.

`BindJSToolBubble.LaunchedEffect(content)` wires the listener once via `jsRuntime.setOnRerenderRequested { coroutineScope.launch { rerender() } }`. The runtime is a process-wide singleton (`JsRuntimeImpl.getInstance(context)`), so the **last** bubble to mount wins for both the rerender listener and the McpHost. Multiple BindJS bubbles in the same chat can therefore step on each other's rerender listeners — but in this demo only the most recent tool's UI is interactive, so that's acceptable.

## BindJS modifier processing — known landmine

`BindJSView` translates the JS AST (a tree of `BaseComponent` + `ModifiedComponent` wrappers) into Compose. Each `ModifiedComponent` carries exactly one modifier; nested wrappers represent a `.foo().bar().baz()` chain (outermost wrapper = outermost JS call).

Most modifiers fall through `ModifiedComponent`'s `else` branch, which appends to `updateModifiers` and recurses into the wrapped content. The modifier list is eventually picked up at the leaf by `NonModifiedComponent`, which builds the Compose `Modifier` chain via `buildModifier`.

**A handful of modifiers are special-cased** in `ModifiedComponent`'s `when (modifier)` and handled by a dedicated composable: `OverlayModifier`, `FrameModifier`, `MaskModifier`, `ContextMenuModifier`, and now `OnAppearModifier` / `OnDisappearModifier`.

Several of those composables call `InnerComponents(modifiers = modifiers.modifiersToShareWithChildren(), …)`. `modifiersToShareWithChildren()` only keeps text-formatting modifiers (Font, FontWeight, ForegroundStyle, LineLimit, LineSpacing, MultilineTextAlignment, Bold, AllowsHitTesting) — **everything else is dropped before reaching children**. That means any modifier whose handling lives only at the leaf gets silently lost behind a `FrameModifier` etc.

`OnAppearModifier` / `OnDisappearModifier` are now consumed at the `ModifiedComponent` layer (fire `LaunchedEffect` / `DisposableEffect` there, pass the un-augmented `modifiers` to children) so the same pattern doesn't bite again. If you add a new effect-style modifier, follow this pattern — don't rely on it propagating to the leaf.

The OnAppear → `UiEvent.OnAppear(handlerId)` → `HomeScreen.handleBindJSEvent` → `jsRuntime.callEventHandler(handlerId)` → JS stored function → component closure (e.g. `hydrate()`).

## Dependencies (versions live in `gradle/libs.versions.toml`)

- AGP 9.0.1, Kotlin 2.2.10 (`compileSdk` 36, `minSdk` 26)
- Compose BOM 2026.02.01 (`ui`, `material3`, `ui-tooling`)
- Navigation3 1.1.0
- Hilt 2.59.2 + KSP 2.3.6
- `kotlinx.serialization` 1.8.1
- `com.halilibo.compose-richtext:richtext-commonmark + richtext-ui-material3` 1.0.0-alpha01 (assistant markdown rendering)
- `ai.metabind:metabindai-android` (unified 0.2.0; substituted to the in-tree `:metabindai` module via `includeBuild("../..")` — see chain above)
- BindJS uses `androidx.javascriptengine.JavaScriptSandbox` + `JavaScriptIsolate` for the JS runtime. Requires WebView JavaScript Sandbox to be available on the device.

## Build / run

```sh
./gradlew :app:installDebug         # build + push
adb shell am start -n ai.metabind.assistant.demo/.MainActivity
```

Both GitHub Packages repos in `settings.gradle.kts` need `gpr.user` / `gpr.key` (or `GITHUB_ACTOR` / `GITHUB_TOKEN`) — even with composite-build substitutions active, Gradle still resolves the version metadata.

`local.properties` (gitignored) feeds BuildConfig: `METABIND_ORG_ID`, `METABIND_PROJECT_ID`, optional `METABIND_AGENT_HOST` / `METABIND_MCP_HOST`.

## Logcat tags worth knowing

| Tag | Source | What it tells you |
|---|---|---|
| `HomeViewModel` | this app | Send/receive turns, tool discovery, UI bundle load, MCP tool-call wrapping |
| `MetabindAgentProvider` | `:metabindai` (`metabindai-android`) | SSE frames (`message_start`, `tool_use`, `tool_use_input_partial`, `tool_result`, `message_stop`) |
| `BindJSToolBubble` | this app | Render failures from `callComponent` |
| `JsRuntimeImpl` | `bindjs-android` | `setEnvironment`/`willRender`/`callComponent` lifecycle, **component-tree dumps after each render**, `Unknown MCP method` warnings |
| `JSConsole` | `bindjs-android` | Any JS `console.log` that does **not** start with `__MCP__::` |
| `BindJSHost` | this app | `host.log(level, message)` from BindJS user code (level prefixed) |
| `BindJSView` | `bindjs-android` | Renderer-side errors |

Quick filter while debugging tool UIs:
```sh
adb logcat MetabindAgentProvider:V HomeViewModel:V JsRuntimeImpl:V BindJSHost:V BindJSToolBubble:V JSConsole:V *:S
```

## Releasing (maintainers)

Shipping to consumers now means releasing from the `metabind-android` monorepo root:
publish `bindjs-android` (separate repo) if it changed, then publish the three libraries
(`metabind-content-android`, `mcpappshost-android`, `metabindai-android`) at the unified
version from the monorepo root, then re-pin this sample if you build it standalone. Don't
do that for local verification — the composite-build chain already handles that.
