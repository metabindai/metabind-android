# CLAUDE.md

Context for agents working in this repo. Read together with `README.md` (user-facing) —
this file is the developer/internals view.

This is the `finance-demo` sample inside the `metabind-android` monorepo
(`samples/finance-demo`). It is its own Gradle build; see the composite-build wiring
below. It is the Android counterpart to
`metabind-apple/Samples/MetabindAI/FinanceDemo` — when in doubt about *intent*, read
the Swift original; the comments there carry the reasoning behind most of the
behavior, and this port preserves the behavior while translating the mechanics.

## What this app is

A two-surface Compose app on the **`metabindai-android`** SDK (module `:metabindai`,
package `ai.metabind.ai`). One question is asked on launch and its rendered MCP card
*is* the home screen; every later question is answered in a sheet over the top.
There is no transcript and no navigation stack.

It exists to demo a **custom** surface over `MetabindAssistant` —
`samples/assistant-demo` is the drop-in `MetabindAssistantView` instead. Everything
interesting here is in how a linear conversation gets routed onto two surfaces.

## SDK source (live source, do NOT decompile JARs)

`settings.gradle.kts` has an active `includeBuild` substitution onto the monorepo
root:

```
samples/finance-demo              (this build)
  └── includeBuild("../..")       (the metabind-android monorepo root)
        ├── substitutes ai.metabind:metabindai-android  → :metabindai
        └── substitutes ai.metabind:mcpappshost-android → :mcpappshost
```

BindJS stays a **published** GitHub Packages artifact (`ai.metabind:bindjs-android`)
— not substituted. So editing `../../metabindai` or `../../mcpappshost` flows into
the next `:app:installDebug`; BindJS changes still need a published binary. Trust the
in-tree SDK sources over the resolved artifact when investigating runtime behavior.

## Module / package layout

```
app/src/main/java/ai/metabind/finance/demo/
  MainActivity.kt                — single-activity host + the FinanceApp switch
  MetabindFinanceDemoApp.kt      — Hilt @HiltAndroidApp entry
  FinanceViewModel.kt            — owns the MetabindAssistant and the AnswerRouter
  data/ApiKeyRepository.kt       — DataStore, with the configured-key + sticky-reset rules
  ui/
    AnswerRouter.kt              — THE core file; turns a conversation into two surfaces
    HomeScreen.kt                — home surface, accent wash, title bar + overflow menu
    AnswerSheet.kt               — the thread sheet, chips, fading header
    AnswerCards.kt               — card frame, multi-card tabs, loading/empty cards
    QuestionBar.kt               — rail / composer / in-flight bubble, all three states
    AISweepBorder.kt             — the rotating "AI is working" border
    BlurRevealText.kt            — word-by-word blur-in prose + inline markdown parser
    Suggestions.kt               — fixed prompts, SF-Symbol-name → drawable table
    KeyEntryScreen.kt
    theme/Theme.kt               — FinancePalette (explicit light/dark) + the `glass` modifier
```

DI: Hilt. `FinanceViewModel` is the only injection point that matters.

## AnswerRouter — the part to understand first

The router owns Compose snapshot state (`home`, `thread`, `selected`, `pending`,
`isAnswerPresented`) and mutates it from one collector over
`combine(messages, isLoading, toolUIContent)`. Every routing decision reads all
three, because any of the three changing can be what makes a turn deliverable.

**A turn is identified by `startIndex`** — the index of its user message in
`assistant.messages`. Prose is "the first non-blank ASSISTANT message between this
turn's question and the next one"; cards are "the TOOL messages in that range that
have an entry in `toolUIContent`".

### Three non-obvious invariants

1. **Delivered answers are snapshots, not views.** `Answer.prose` / `Answer.cards`
   hold copies. `assistant.reset()` — which is exactly what closing the sheet does —
   empties `messages` and `toolUIContent`, so an answer that re-derived itself on
   every recomposition would be hollowed out the moment the sheet closed, *home card
   included*. Only the single answer named by `liveAnswerId` is refreshed from the
   conversation, and even then `refreshed()` falls back to what it already holds
   rather than to nothing.

2. **`isSending` exists because of `Dispatchers.Main.immediate`.**
   `MetabindAssistant.send` appends the user message and *then* raises
   `isLoading`. The router collects on the ViewModel's main-immediate scope, so
   appending the message re-enters `sync()` synchronously — in the one window where
   the conversation holds a question and the assistant still reads as idle. Without
   the flag, that first `sync()` concludes "no cards, no prose, not processing" and
   delivers an empty answer; the real one then has to overwrite it a second later.
   This was a real bug, not a theoretical one.

3. **`adoptUntrackedTurn` is load-bearing.** A rendered component calling
   `host.sendMessage` lands straight on the assistant without going through `ask()`.
   Adopting it is what makes a component-driven question extend the thread like a
   tapped pill instead of running invisibly.

### Presentation timing vs iOS

iOS polls `session.phase` and waits for the first parsed argument before presenting,
because its SDK streams tool arguments into a live session. Android has no
equivalent wait: `ToolCallStart` carries the arguments whole, and a card only enters
`toolUIContent` once its `ui://` resource has been read and parsed. So the first
entry for a turn is already worth showing — `sync()` delivers on it directly and
there is no presentation task.

### nextSteps

Model-written follow-ups ride along as a `nextSteps` array argument on a card's tool
call — no extra round trip. iOS reads them into `assistant.nextSteps` (a single
current list); here they're extracted in `AnswerRouter.nextSteps` and stored **per
answer**, so the chips offer the suggestions belonging to the answer on screen and
the home answer keeps its own across the reset that ends a thread.

The argument must be declared in the tool's published input schema — a server
validating `additionalProperties: false` rejects a call carrying an argument it never
advertised.

## Tool rendering

Cards render through `MetabindToolView(assistant, toolName, content, …)` from
`:metabindai` — the same renderer `MetabindAssistantView` mounts, so the host bridge,
per-card JS isolate, hook state and re-render plumbing are identical to the drop-in
view. `onSendMessage` is overridden to `router::ask` so a component-driven turn is
routed rather than silently sent.

For how the JS ↔ Kotlin bridge itself works (the `__MCP__::` console channel,
`willRender` / `renderComponent` atomicity, the modifier landmines), see
`samples/assistant-demo/CLAUDE.md` — it documents the same machinery and applies
verbatim.

## Compose translations worth knowing

- **`palette`, not Material tokens.** iOS builds on `systemGroupedBackground` /
  `secondarySystemGroupedBackground`, where a card is lighter than the page in light
  mode and the relationship inverts in dark. Material's tonal tokens don't invert
  that way, so `FinancePalette` spells both schemes out. Use `palette.card` /
  `palette.page`, not `colorScheme.surface`.
- **`glass()`** is a flat translucency + hairline border, not a real blur. Android has
  no backdrop-sampling equivalent that works below API 31.
- **`matchParentSize`, never `fillMaxHeight`, for overlay decorations.** The chip
  row's trailing fade used `fillMaxHeight()` and inflated the whole sheet header to
  full-screen height, pushing the title and the answer off the bottom. Anything
  drawn *over* a sibling must not contribute to measurement.
- **`onFocusChanged` reports the initial state.** The composer's "lost focus on an
  empty field → back to the rail" check needs a `hadFocus` guard, or it fires on the
  first composition and bounces the composer shut before the focus request lands.
  SwiftUI's `onChange(of:)` doesn't have this problem.
- **The ask bar is measured, not inset.** It floats over the scrolling content
  (`align(BottomCenter)` + `onSizeChanged`), and the content pads its bottom by that
  measured height. Its window-insets padding goes on the *content* of the bar rather
  than the whole bar, so the scrim still runs the full height and finishes under the
  navigation bar instead of at a visible edge.
- **The answer sheet's two detents are hand-fitted.** Material gives the sheet an
  explicit height (`window - status bar - 12dp`) rather than letting it fill, because
  the Expanded anchor is `window - sheet height` — sizing it short is what leaves the
  gap at the top, and it also keeps the partially-expanded anchor alive, which
  Material only offers while the sheet is taller than half the window. Material then
  translates the whole sheet down for the partial state, so the ask bar is pushed back
  up by `requireOffset() - topInset` in a `graphicsLayer` block to hold the screen's
  bottom edge at every offset. The scrolling answer is *not* shortened to match: its
  tail hangs below the screen while half-open, and dragging it up expands the sheet
  first (the sheet's nested-scroll connection) so the tail arrives when reached for.
- **`Modifier.blur` is a no-op below API 31.** The prose reveal degrades to a
  staggered fade and the sweep border loses its glow; both still read fine.
- **The sweep gradient rotates by moving its stops.** Compose has no rotatable sweep
  gradient and rotating the canvas would turn the shape with it, so
  `AISweepBorder.sweepStops` re-derives monotonic stop positions each frame. Stops
  that aren't ordered are rejected outright, which is why the wrapped ones are
  emitted before the rest.

## Divergences from the Apple app (deliberate)

- **No `requestFullscreen`.** The sheet has iOS's two detents, but nothing can ask it
  to change one: bindjs on Android has no display-mode channel, so the request has
  nowhere to arrive from.
- **No "Reset Cache".** The Android assistant re-reads each tool's `ui://` resource
  per call, so there is nothing cached to drop.
- **DataStore, not Keychain.** Uninstalling clears the key; there is no
  hardware-backed protection.

## SDK changes this sample required

Both additive, in `:metabindai`, and `samples/assistant-demo` was rebuilt against
them:

- `MetabindToolView.kt` — extracted the previously-private BindJS/HTML tool renderer
  out of `MetabindAssistantView.kt` into a public composable, mirroring Apple's
  `MCPAppView`. `MetabindAssistantView` now calls it. **`metabindai/lint-baseline.xml`
  points at `MetabindToolView.kt`** for the two carried-over WebView findings; if you
  move that code again, retarget the baseline or `lint` fails.
- `MetabindAssistant.mergePendingContext` made public, and `clearPendingContext()`
  added. A custom surface needs both: the first to name which of several on-screen
  answers a follow-up phrased as "that" refers to, the second to drop that context
  when the turn it was gathered for is abandoned.

## Build / run

```sh
./gradlew :app:installDebug
adb shell am start -n ai.metabind.finance.demo/.MainActivity
```

`local.properties` (gitignored) feeds BuildConfig: `FINANCE_DEMO_ORG_ID`,
`FINANCE_DEMO_PROJECT_ID`, optional `FINANCE_DEMO_API_KEY`,
`FINANCE_DEMO_AGENT_HOST`, `FINANCE_DEMO_MCP_HOST`. Both GitHub Packages repos in
`settings.gradle.kts` need `gpr.user` / `gpr.key` (or `GITHUB_ACTOR` /
`GITHUB_TOKEN`) — even with the substitutions active, Gradle still resolves
`bindjs-android`.

## Logcat tags worth knowing

| Tag | Source | What it tells you |
|---|---|---|
| `MetabindAssistant` | `:metabindai` | Tool discovery (`Loaded N tools, M with UI`), send, UI-content loads |
| `MetabindAgentProvider` | `:metabindai` | SSE frames (`tool_use`, `tool_result`, `message_stop`) |
| `MetabindToolView` | `:metabindai` | Render failures from `renderComponent` |
| `JsRuntimeImpl` | `bindjs-android` | Render lifecycle + component-tree dumps |
| `JSConsole` | `bindjs-android` | JS `console.log` not prefixed `__MCP__::` |
| `BindJSHost` | `:metabindai` | `host.log(level, message)` from component code |
| `BindJSView` | `bindjs-android` | Renderer-side errors |

```sh
adb logcat MetabindAssistant:V MetabindAgentProvider:V MetabindToolView:V \
  JsRuntimeImpl:V BindJSHost:V JSConsole:V '*:S'
```

## Not yet exercised

The multi-card tab path (`AnswerCards` with `cards.size > 1`) is a direct port and
has not been seen live — it needs a turn where the model emits two *different*
UI-declaring tool calls in one message.
