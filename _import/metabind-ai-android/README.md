# Metabind AI for Android

Native host SDK for rendering interactive [MCP](https://modelcontextprotocol.io) tool results as [Jetpack Compose](https://developer.android.com/jetpack/compose) views.

MCP servers return "apps" — resources containing BindJS components (or MCP-app HTML) — alongside tool results. This SDK discovers those tools, streams an agent conversation through the Metabind proxy, decodes the returned UI bundles, and exposes them as plain Kotlin data classes ready for native rendering. Native rendering is delegated to the separate [`bindjs-android`](https://github.com/metabindai/bindjs-android) library.

## Modules

| Module | Maven coordinates | Purpose |
|---|---|---|
| **`mcpappshost`** | `ai.metabind:mcpappshost-android` | Low-level MCP client + LLM transport types. `MCPAppsClient`, `LLMMessage`, `LLMStreamEvent`. |
| **`metabindassistant`** | `ai.metabind:metabind-assistant-android` | Higher-level agent-proxy provider + `ToolUIContent` dispatcher (BindJS / HTML) + `ChatMessage`. Depends on `mcpappshost`. |

The split mirrors the Apple counterpart at [`metabind-ai-apple`](https://github.com/metabindai/metabind-ai-apple) (`MCPAppsHost` + `MetabindAssistant`).

## Requirements

- Android Studio Narwhal+ (AGP 9.0.1, Kotlin 2.2.10)
- `compileSdk` 36, `minSdk` 26
- A Metabind project API key (see [metabind.ai](https://metabind.ai))
- A GitHub personal access token with `read:packages` scope to fetch published artifacts from GitHub Packages

## Install

The SDK and its `bindjs-android` runtime are both published to GitHub Packages. Add the repo and credentials in your consumer project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/metabindai/bindjs-android-binary")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).get()
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).get()
            }
        }
    }
}
```

Set credentials in `~/.gradle/gradle.properties` (or `GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables):

```properties
gpr.user=<your-github-username>
gpr.key=<github-pat-with-read:packages>
```

Then in your app/module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.metabind:metabind-assistant-android:0.1.0")
    // or, for the low-level layer only:
    // implementation("ai.metabind:mcpappshost-android:0.1.0")
}
```

`metabind-assistant-android` re-exports `mcpappshost` and `bindjs-android` as `api` dependencies — pulling in the assistant gives you everything needed to stream a conversation and render its tool UIs.

## Quick start — stream a conversation through the Metabind Agent proxy

`MetabindAgentProvider` POSTs to `agent.metabind.ai` and emits normalized SSE events. The proxy holds upstream provider credentials server-side and runs the full tool-use loop — your app ships no Anthropic/OpenAI keys.

```kotlin
val agent = MetabindAgentProvider()

agent.streamMessage(
    baseUrl = MetabindAgentProvider.PRODUCTION_HOST,
    apiKey = metabindApiKey,
    orgId = "<org>",
    projectId = "<project>",
    messages = listOf(LLMMessage.User("Show me a sofa"))
).collect { event ->
    when (event) {
        is LLMStreamEvent.TextDelta -> append(event.text)
        is LLMStreamEvent.ToolCallStart -> beginTool(event.name, event.id)
        is LLMStreamEvent.ToolResult -> completeTool(event.toolCallId, event.content)
        is LLMStreamEvent.Done -> finishTurn(event.stopReason)
        is LLMStreamEvent.Error -> showError(event.message)
        else -> {}
    }
}
```

One Metabind API key authenticates both the MCP server (used to fetch tool UIs) and the agent proxy.

## Quick start — render a tool's UI

For tools that return a `_meta.ui.resourceUri`, fetch the resource via `MCPAppsClient` and decode it with `ToolUIContent.fromResource`:

```kotlin
val client = MCPAppsClient(
    url = "https://mcp.metabind.ai/<org>/projects/<project>",
    headers = mapOf("authorization" to "Bearer $metabindApiKey")
)

val resource = client.readResource("ui://promotion-form")
val ui = ToolUIContent.fromResource(resource, toolArguments)

when (ui) {
    is ToolUIContent.BindJS -> {
        // hand `ui.designerComponent` to BindJSView from bindjs-android
    }
    is ToolUIContent.Html -> {
        // load `ui.html` into a Compose AndroidView { WebView }
    }
}
```

`MCPAppsClient` transparently handles:

- The `initialize` / `notifications/initialized` handshake (mutex-guarded so concurrent first-callers don't race)
- `Mcp-Session-Id` header round-tripping, with automatic re-init on `404` / `410`
- `text/event-stream` responses (it extracts the last `data:` line)
- Exponential-backoff retries on `500/502/503/504` and socket timeouts

## Build the SDK

```sh
./gradlew :mcpappshost:assembleRelease :metabindassistant:assembleRelease
```

## Publish

Both modules are published to `https://maven.pkg.github.com/metabindai/bindjs-android-binary`. Authentication requires a GitHub personal access token with `write:packages` scope.

```sh
# Environment variables
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<your-github-token>
./gradlew :mcpappshost:publish :metabindassistant:publish

# Or via Gradle properties
./gradlew :mcpappshost:publish :metabindassistant:publish \
    -Pgpr.user=<your-github-username> -Pgpr.key=<your-github-token>
```

For local development, publish to your local Maven cache:

```sh
./gradlew :mcpappshost:publishToMavenLocal :metabindassistant:publishToMavenLocal
```

Consumers then add `mavenLocal()` to their repositories.

## Local development against a consuming app

The demo app at [`metabind-assistant-demo-android`](https://github.com/metabindai/metabind-assistant-demo-android) can be built against a local checkout of this SDK via Gradle composite builds. Uncomment the `includeBuild("../metabind-ai-android")` block in the demo's `settings.gradle.kts`.

## Example app

A runnable showcase lives in its own repo:

**[metabind-assistant-demo-android](https://github.com/metabindai/metabind-assistant-demo-android)** — Compose chat app wired to the Metabind Agent proxy, rendering real interactive product components.

## License

See `LICENSE`.
