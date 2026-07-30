# Metabind Assistant Demo (Android)

A Jetpack Compose chat app showcasing the `metabindai-android` Assistant SDK (the `:metabindai` module of [`metabind-android`](https://github.com/metabindai/metabind-android)) — the high-level conversational wrapper that orchestrates an LLM + MCP tool execution + native Compose rendering of tool UIs.

The Apple counterpart lives at [`metabind-apple/Samples/MetabindAI/AssistantDemo`](https://github.com/metabindai/metabind-apple/tree/main/Samples/MetabindAI/AssistantDemo).

## What it does

1. Asks for your Metabind API key on launch (in-memory only, never persisted).
2. Connects to the Metabind Agent proxy and the configured project's MCP server.
3. Streams agent responses, executes MCP tool calls server-side, and renders the returned `ui` resources as live Compose UI via BindJS — or as a sandboxed `WebView` for MCP-app HTML tools.
4. Wires `useMCPHost()` so BindJS components can `openLink`, `sendMessage`, and `updateModelContext` back into the chat.

No LLM provider keys ship in the binary — the Metabind Agent proxy holds the upstream credentials and runs the tool-use loop. One Metabind API key authenticates both the MCP server and the agent.

## Requirements

- Android Studio Narwhal+ (AGP 9.0.1, Kotlin 2.2.10)
- `compileSdk` 36, `minSdk` 26
- A Metabind project API key — create one at [metabind.ai](https://metabind.ai)
- A GitHub personal access token with `read:packages` scope (to fetch the SDK from GitHub Packages)

## Setup

### 1. GitHub Packages credentials

Add credentials in `~/.gradle/gradle.properties`:

```properties
gpr.user=<your-github-username>
gpr.key=<github-pat-with-read:packages>
```

…or export them as environment variables `GITHUB_ACTOR` / `GITHUB_TOKEN`.

### 2. Metabind project configuration

`local.properties` (gitignored) supplies the org/project IDs and host overrides. They're surfaced as `BuildConfig` fields by `app/build.gradle.kts`:

```properties
METABIND_ORG_ID=<your-org-id>
METABIND_PROJECT_ID=<your-project-id>

# Optional — default to production:
# METABIND_AGENT_HOST=https://agent-dev.metabind.ai
# METABIND_MCP_HOST=https://mcp-dev.metabind.ai
```

Each key also accepts an environment variable of the same name (CI-friendly). Defaults are `https://agent.metabind.ai` and `https://mcp.metabind.ai`.

### 3. Run

```sh
./gradlew :app:installDebug
```

Paste your Metabind API key into the launch field, hit Start, and chat. Try prompts like:

- "Show me a sofa, then a matching armchair"
- "Compare two of your bestselling sofas"
- "Build a mood board for a Japandi living room"

## How it works

The interesting part is `app/src/main/java/ai/metabind/assistant/demo/ui/screens/HomeViewModel.kt`:

```kotlin
val agent = MetabindAgentProvider()
val mcp = MCPAppsClient(
    url = "$MCP_HOST/$ORG_ID/projects/$PROJECT_ID",
    headers = mapOf("authorization" to "Bearer $apiKey")
)

agent.streamMessage(
    baseUrl = AGENT_HOST,
    apiKey = apiKey,
    orgId = ORG_ID,
    projectId = PROJECT_ID,
    messages = llmHistory
).collect { event ->
    when (event) {
        is LLMStreamEvent.TextDelta -> /* stream into assistant bubble */
        is LLMStreamEvent.ToolCallStart -> {
            val resourceUri = toolUIMap[event.name] ?: return@collect
            val resource = mcp.readResource(resourceUri)
            val ui = ToolUIContent.fromResource(resource, event.arguments)
            // render `ui` as a BindJSView or AndroidView { WebView }
        }
        // …
    }
}
```

`HomeScreen.kt` wires the runtime via `JsRuntimeImpl.setMcpHost(...)` so BindJS components running inside a tool's UI can talk back to the host:

| Capability | Wired to |
|---|---|
| `openLink(url)` | `Intent(ACTION_VIEW, …)` with `FLAG_ACTIVITY_NEW_TASK`. |
| `sendMessage(text)` | Re-enters `HomeViewModel.sendMessage`, scrolling the chat to the new turn. |
| `updateModelContext(map)` | Buffered into `pendingContext` and prefixed onto the next user turn as a `<context>{…}</context>` block — the user-visible chat bubble stays clean. |

For MCP-app HTML (the `WebView` path), `HomeScreen.buildMcpAppHostHtml` wraps the content in a tiny iframe host that answers `ui/initialize` with a minimal `hostInfo`/`hostContext` and forwards `ui/notifications/size-changed` to a `JavascriptInterface` so the WebView can size to its content.

## Local development against the SDK

This sample builds against the in-tree SDK sources by default: `settings.gradle.kts` uses a composite build onto the monorepo root, substituting the published artifacts for the local modules:

```kotlin
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("ai.metabind:mcpappshost-android")).using(project(":mcpappshost"))
        substitute(module("ai.metabind:metabindai-android")).using(project(":metabindai"))
    }
}
```

Edits in `../../metabindai` or `../../mcpappshost` flow into the next `:app:installDebug` build without publishing. BindJS (`ai.metabind:bindjs-android`) stays a published artifact from its own repo and isn't substituted.

## Logging

Diagnostic output uses `android.util.Log`:

| Tag | Contents |
|---|---|
| `MetabindAgentProvider` | SSE frames from the agent proxy (`message_start`, `tool_use`, `tool_result`, `message_stop`). |
| `HomeViewModel` | Tool discovery, UI bundle loading, per-turn lifecycle. |
| `BindJSToolBubble` | BindJS render failures. |

Tail with `adb logcat MetabindAgentProvider:V HomeViewModel:V BindJSToolBubble:V *:S`.

## License

See `LICENSE`.
