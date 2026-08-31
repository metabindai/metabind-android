# CLAUDE.md

Context for agents working in this repo. Read together with `README.md` (user-facing) — this file is the developer/internals view.

This is the `assistant-demo` sample inside the `metabind-android` monorepo
(`samples/assistant-demo`). It is its own Gradle build; see the composite-build wiring below.

## What this app is

A Jetpack Compose chat app demoing the **`metabindai-android`** SDK (module `:metabindai`, package `ai.metabind.ai`). The user types a message, an LLM (via the Metabind Agent proxy) streams a reply and emits MCP tool calls; for tools that declare a `ui` resource, the resource is fetched and rendered natively via **BindJS** (Compose-native JS-driven UI) — or in a sandboxed `WebView` for `text/html` resources.

No upstream LLM credentials ship in the binary. One Metabind API key authenticates both the MCP server and the agent proxy.

**The app itself is thin on purpose.** Everything above — the conversation loop, tool discovery, resource fetching, the host bridge, the renderer — lives in `:metabindai`. This sample exists to show the *drop-in* path: build a `MetabindAssistant`, hand it to `MetabindAssistantView`, done. Its whole UI is ~35 lines.

If you are looking for the **custom-surface** path — observing the assistant's flows and placing tool cards yourself — that is `samples/finance-demo`, not this one. Read its `CLAUDE.md` for the routing concerns a custom surface has to handle.

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
| `:metabindai` | `../../metabindai` | `metabindai-android`: `MetabindAssistant` (the conversation loop), `MetabindAssistantView` (this app's whole UI), `MetabindToolView` (the tool renderer), `MetabindAgentProvider` (SSE client), `ToolUIContent`. Package `ai.metabind.ai`. |
| `:mcpappshost` | `../../mcpappshost` | `mcpappshost-android`: the `MCPAppsClient` JSON-RPC client over MCP. |
| `:metabind-content` | `../../metabind-content` | `metabind-content-android`, the content SDK (not consumed by this demo). |
| `bindjs-android` | published artifact | The BindJS Kotlin runtime + Compose renderer (`JsRuntimeImpl`, `BindJSView`) and the JS isolate script. Consumed from GitHub Packages, own repo. |

## Module / package layout

```
app/src/main/java/ai/metabind/assistant/demo/
  MainActivity.kt                  — single-activity Compose host; picks the start route
                                     from whether a key is already stored
  MetabindAssistantDemoApp.kt      — Hilt @HiltAndroidApp entry
  data/ApiKeyRepository.kt         — the API key in Jetpack DataStore (Preferences).
                                     All access suspends; there is no in-memory cache.
  navigation/
    AppNavigation.kt               — Navigation3 NavDisplay, KeyEntry ⇄ Home
    Routes.kt                      — @Serializable NavKey objects
  ui/screens/
    KeyEntryScreen.kt + ViewModel  — paste API key, persist, hand off
    HomeScreen.kt                  — builds nothing; renders MetabindAssistantView
    HomeViewModel.kt               — constructs the MetabindAssistant from the stored key
```

DI: Hilt. `HomeViewModel(apiKeyRepository)` reads the key back from DataStore and
constructs `MetabindAssistant(apiKey, orgId, projectId, agentHost)`; `close()` on
`onCleared`. That is the entire integration.

## End-to-end tool-rendering flow

This all happens inside `:metabindai` — the sample never sees it. It is documented here
because this is where you will be standing when you need to debug it.

When the user sends a message:

1. **`MetabindAssistant.send`** appends a `ChatMessage(role = USER)` for display, then an
   `LLMMessage.User(...)` to its own `llmHistory`. Any context buffered via
   `mergePendingContext` (from a rendered component calling `host.updateModelContext`, or
   from a custom surface) is consumed here and prefixed as `<context>{…}</context>` on the
   **model-visible text only** — the user-facing bubble stays clean.
2. **`MetabindAgentProvider.streamMessage`** streams SSE frames, translated into
   `LLMStreamEvent`s. The agent proxy runs the tool-use loop server-side, so one `send`
   can produce several tool calls and several bursts of prose in one stream.
3. On `ToolCallStart`:
   - A `ChatMessage(role = TOOL, toolStatus = LOADING)` is appended, **keyed by the
     tool-call id** — that same id is the key into `toolUIContent`.
   - If `toolUIMap[event.name]` has a `resourceUri`, `fetchToolUIContent` reads the MCP
     resource via `client.readResource(uri)` and parses it through
     `ToolUIContent.fromResource`. Mime containing `bindjs` (or exactly
     `application/json`) yields `ToolUIContent.BindJS`; mime containing `html` yields
     `ToolUIContent.Html`. A tool with no `ui` resource never gets an entry, which is how
     data-only tools stay invisible.
   - Arguments arrive **whole** with `ToolCallStart`. `ToolCallArgumentDelta` is ignored,
     so there is no partial-argument rendering on Android.
4. On `ToolResult` the stored `ToolUIContent` is replaced via `withResult(...)`, which is
   what feeds `environment.toolResult` to the component. Note this makes the content a
   *new instance*, which re-triggers `LaunchedEffect(content)` in the renderer — expected,
   not a bug.
5. **`MetabindToolView`** mounts for any `TOOL` message that has content. Its
   `LaunchedEffect(content)` runs, in order:
   - `jsRuntime.setOnRerenderRequested { … }` — wires the JS→native rerender signal.
   - `jsRuntime.setMcpHost(host)` — JS-side this sets `runtime.mcpHost` so user code's
     `useMCPHost()` returns truthy.
   - `awaitReady()`, `setComponents(bundle)`, `setEnvironment(...)`.
   - `renderComponent(layoutName, props)` — returns a `BaseComponent<*>` tree.
   - `renderedComponent = next; version++` causes `BindJSView` to compose.
6. **BindJS host bridge** (the `McpHost` impl inside `MetabindToolView`): `openLink` opens
   an external `ACTION_VIEW` intent (overridable via `onOpenLink`); `sendMessage` calls
   `onSendMessage`, which defaults to `assistant.send` (a custom surface overrides it to
   route the turn); `updateModelContext` → `assistant.mergePendingContext`;
   `toolCall(name, args)` → `assistant.callMcpTool` → `MCPAppsClient.callTool` → parses
   `textContent` as JSON → `jsonElementToPlain` → returned to JS.

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

Anything else logs `Unknown MCP method: <name>` at warn — a useful first thing to grep for
when a component's host call silently does nothing.

If `__pendingToolCalls` ever holds entries when the host is detached — Kotlin
`setMcpHost(null)`, which evaluates `setMcpHost(false)` in the isolate — all pending
promises reject with `mcp host removed`.

### Tool-call error path

Exceptions thrown by `MetabindAssistant.callMcpTool` (e.g., `IllegalStateException("tool 'X' failed: …")` when MCP returns `isError`) propagate up to `dispatchToolCall`, which calls `__resolveToolCall(id, false, errorMessage)`. The JS shim rejects the Promise with `new Error(message)`, so BindJS user code recovers via standard `try { await host.toolCall(...) } catch (e) { … }`. The supervisor scope guarantees one failing tool call doesn't kill the channel for the next.

### Why `callMcpTool` flattens to plain Kotlin types

```kotlin
val parsed = Json.parseToJsonElement(text)
return jsonElementToPlain(parsed)   // → Map<String, Any?> / List<Any?> / primitives
```

The MCP `textContent` is parsed via `kotlinx.serialization`, then flattened to plain Kotlin maps/lists/primitives before being returned. The reason is that `bindjs-android` re-serializes the payload through **Gson** when writing `__resolveToolCall(id, ok, payload)` back into the JS isolate, and Gson doesn't roundtrip `kotlinx.serialization.JsonElement` instances cleanly. The flattening keeps both sides happy.

### Reactive re-render mechanism

JS `setState` setters call `runtime.needsRerender()` **synchronously** (the JS isolate has no `queueMicrotask`; using one would tear down `await host.toolCall(...)` chains). Kotlin coalesces bursts via `rerenderPosted`, then invokes the listener registered by `MetabindToolView`, which calls `rerender()`:

```kotlin
val next = jsRuntime.renderComponent(name, args)   // atomic willRender + callComponent
renderedComponent = next
version++                                          // bumps key on BindJSView
```

Use **`renderComponent`**, not `willRender()` + `callComponent()` as separate calls. The
renderer walks one shared, mutable hook state that `willRender` resets and the component
call consumes; splitting the pair lets a concurrent render or event handler interleave and
corrupt it, leaving the tree bound to stale handler ids — taps and drags silently stop
firing. Skipping `willRender` entirely is worse: the second render reads `useState` from
stale paths and freshly-set state looks like it never landed.

**Each tool card gets its own isolate.** `MetabindToolView` calls
`JsRuntimeImpl.create(applicationContext)` and `close()`s it on dispose — *not*
`JsRuntimeImpl.getInstance(...)`. With a shared process-wide runtime, sibling cards
overwrite each other's handler table, hook state, rerender listener and `mcpHost`, so
rendering a new card freezes every older one. `getInstance` still exists for hosts that
genuinely want one runtime (a single-component preview); don't reach for it here.

## BindJS modifier processing — known landmine

This section is about `bindjs-android`, not this sample, but it is the most common source
of "my modifier does nothing" reports.

`BindJSView` translates the JS AST (a tree of `BaseComponent` + `ModifiedComponent` wrappers) into Compose. Each `ModifiedComponent` carries exactly one modifier; nested wrappers represent a `.foo().bar().baz()` chain (outermost wrapper = outermost JS call).

Most modifiers fall through `ModifiedComponent`'s `else` branch, which appends to `updateModifiers` and recurses into the wrapped content. The modifier list is eventually picked up at the leaf by `NonModifiedComponent`, which builds the Compose `Modifier` chain via `buildModifier`.

**A handful of modifiers are special-cased** in `ModifiedComponent`'s `when (modifier)` and handled by a dedicated composable: `OverlayModifier`, `FrameModifier`, `MaskModifier`, `ContextMenuModifier`, `OnAppearModifier`, `OnDisappearModifier`.

Several of those composables call `InnerComponents(modifiers = modifiers.modifiersToShareWithChildren(), …)`. That filter keeps only text-formatting modifiers (`Bold`, `Font`, `FontWeight`, `LineLimit`, `LineSpacing`, `ForegroundStyle`, `AllowsHitTesting`, `MultilineTextAlignment`) plus anything `ChartCollector.isChartLevelModifier` accepts — **everything else is dropped before reaching children**. So any modifier whose handling lives only at the leaf gets silently lost behind a `FrameModifier` etc.

Chart-level modifiers are in that allowlist for exactly this reason:
`Chart(...).chartLegend(...).frame(...).chartXSelection(...)` puts a frame between the
selection and the chart, and dropping the modifier there left the chart with no selection
handler at all.

`OnAppearModifier` / `OnDisappearModifier` are consumed at the `ModifiedComponent` layer (fire `LaunchedEffect` / `DisposableEffect` there, pass the un-augmented `modifiers` to children) so the same pattern doesn't bite again. If you add a new effect-style modifier, follow this pattern — don't rely on it propagating to the leaf.

The OnAppear → `UiEvent.OnAppear(handlerId)` → `MetabindToolView.handleBindJSEvent` → `jsRuntime.callEventHandler(handlerId)` → JS stored function → component closure (e.g. `hydrate()`).

## Dependencies (versions live in `gradle/libs.versions.toml`)

- AGP 9.3.1, Kotlin 2.3.10 (`compileSdk` 36, `minSdk` 26). AGP must match the monorepo and
  `bindjs-android` exactly — Gradle refuses two AGP versions in one build and this sample
  composes both.
- Compose BOM 2026.02.01 (`ui`, `material3`, `ui-tooling`)
- Navigation3 1.1.0
- Hilt 2.59.2 + KSP 2.3.6
- Jetpack DataStore (Preferences) 1.1.1
- `kotlinx.serialization` 1.8.1
- `com.halilibo.compose-richtext:richtext-commonmark + richtext-ui-material3` 1.0.0-alpha01 (assistant markdown rendering)
- `ai.metabind:metabindai-android` (unified `versions.metabindAssistant`; substituted to the in-tree `:metabindai` module via `includeBuild("../..")` — see chain above, so the pinned version only matters for a standalone build)
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
| `MetabindAssistant` | `:metabindai` | `Sending message`, tool discovery (`Loaded N tools, M with UI: […]`), per-tool UI-content loads, stream failures |
| `MetabindAgentProvider` | `:metabindai` | SSE frames (`message_start`, `tool_use`, `tool_use_input_partial`, `tool_result`, `message_stop`) |
| `MetabindToolView` | `:metabindai` | Render failures from `renderComponent` |
| `JsRuntimeImpl` | `bindjs-android` | `setEnvironment`/`willRender`/`callComponent` lifecycle, **component-tree dumps after each render**, `Unknown MCP method` warnings |
| `JSConsole` | `bindjs-android` | Any JS `console.log` that does **not** start with `__MCP__::` |
| `BindJSHost` | `:metabindai` | `host.log(level, message)` from BindJS user code (level prefixed) |
| `BindJSView` | `bindjs-android` | Renderer-side errors |

Quick filter while debugging tool UIs:
```sh
adb logcat MetabindAssistant:V MetabindAgentProvider:V MetabindToolView:V \
  JsRuntimeImpl:V BindJSHost:V JSConsole:V '*:S'
```

## Releasing (maintainers)

Shipping to consumers now means releasing from the `metabind-android` monorepo root:
publish `bindjs-android` (separate repo) if it changed, then publish the three libraries
(`metabind-content-android`, `mcpappshost-android`, `metabindai-android`) at the unified
version from the monorepo root, then re-pin this sample if you build it standalone. Don't
do that for local verification — the composite-build chain already handles that.
