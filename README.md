# Metabind for Android

The native Android SDK for Metabind. Embed a governed agent in your Android app, and render Metabind-managed content as native Jetpack Compose.

## What this is

Metabind builds agents that answer in your product's own UI, not in a chat window: interactive interfaces that take customers straight to what they came for, in the brand they already know. It's built from the UI, design system, and APIs the app already has. No rewrite. It's governed, rendering only components you've approved, enforced on every render. And it's hosted: you define the tools, we run the server. The same agent runs inside your own iOS, Android, and web apps, and across Claude, ChatGPT, and every MCP host, on the open [MCP](https://modelcontextprotocol.io) standard.

This repository is the Android side. It ships three libraries you can adopt independently:

| Library | Artifact | Use it to |
|---|---|---|
| `:metabindai` | `ai.metabind:metabindai-android` | Embed the agent in your app. The Assistant SDK drops in a conversational view that runs the agent and renders its Interactive Tool responses as native Compose. |
| `:mcpappshost` | `ai.metabind:mcpappshost-android` | Render a single MCP tool result without the conversational layer. The low-level building blocks (`MCPAppsClient`) that `:metabindai` is built on. |
| `:metabind-content` | `ai.metabind:metabind-content-android` | Fetch and render content from Metabind's content platform. A Compose view, an Apollo GraphQL client, SQLite-backed caching, and real-time updates over WebSocket. |

Everything renders through BindJS as real native Jetpack Compose, not web views. The three libraries have different dependency footprints, so you depend only on the ones you use: a content-only app doesn't link the assistant, and an assistant-only app doesn't link the GraphQL client.

> [!NOTE]
> BindJS is the open component language for agent UI, rendered natively as SwiftUI, Jetpack Compose, and React; the Compose rendering engine is its Android half. This SDK links it as a precompiled binary (`ai.metabind:bindjs-android`, published to GitHub Packages). All of BindJS is open source under Apache 2.0: the runtime and React renderer, and the native SwiftUI and Jetpack Compose engines.

## The Metabind SDKs

| Platform | Repository |
|---|---|
| iOS, macOS, visionOS | [`metabind-apple`](https://github.com/metabindai/metabind-apple) |
| Android | `metabind-android` — this repository |
| Web (React) | [`metabind-web`](https://github.com/metabindai/metabind-web) |

One [MCP App](https://github.com/modelcontextprotocol/ext-apps) serves all three: the same tools, components, and agent configuration from a single publish, so the SDKs compose — ship the Android assistant, the iOS assistant, and the web chat surface together.

**[Start free at metabind.ai](https://www.metabind.ai/signup)** · **[Read the docs](https://docs.metabind.ai)**

## Documentation

The full guides live on [docs.metabind.ai](https://docs.metabind.ai):

- [Android SDK guide](https://docs.metabind.ai/guides/assistant-sdk/android-sdk) — install, the chat surface, the `MetabindAssistant` API, streaming and custom UIs
- [LLM provider configuration](https://docs.metabind.ai/guides/assistant-sdk/llm-provider-configuration) — key custody and how the Agent proxy runs the tool loop
- [Content SDK guide](https://docs.metabind.ai/content/mobile-sdks/android-sdk) — `metabind-content-android` end to end
- [BindJS reference](https://docs.metabind.ai/bindjs/introduction) — the component language tool UIs are written in

## Requirements

- Android Gradle Plugin 9.0+, Gradle 9.x
- JDK 21
- `compileSdk` 36, `minSdk` 26
- A Metabind account. Create one at [metabind.ai](https://metabind.ai).

## Installation

All Metabind libraries — and their BindJS dependency — are published to GitHub Packages, which requires authentication even for public packages. Use a personal access token
(classic) with `read:packages`, or a GitHub Actions `GITHUB_TOKEN` with package read
access. Repository access alone does not grant package access. Provide credentials via environment variables:

```bash
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<your-github-token>
```

Or in `~/.gradle/gradle.properties`:

```properties
gpr.user=<your-github-username>
gpr.key=<your-github-token>
```

The next releases are prepared for publishing from their public source
repositories: BindJS `0.0.31` from `bindjs-android`, and SDK `0.2.10` from
`metabind-android`. **The registry migration has not happened yet.** See the
[proposed migration guide](docs/PACKAGE_MIGRATION.md) before switching an
existing project. After the cutover, configure both repositories in your
`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/metabindai/bindjs-android")
            content { includeModule("ai.metabind", "bindjs-android") }
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/metabindai/metabind-android")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).get()
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).get()
            }
        }
    }
}
```

Then add the libraries you need to your module's `build.gradle.kts`. All three ship at one unified version:

```kotlin
dependencies {
    implementation("ai.metabind:metabindai-android:0.2.11")        // embed the agent
    implementation("ai.metabind:mcpappshost-android:0.2.11")       // low-level rendering
    implementation("ai.metabind:metabind-content-android:0.2.11")  // content SDK
}
```

---

## MetabindAI: embed the agent

`:metabindai` is the Assistant SDK. It embeds your Metabind agent inside your own app, calling real tools and rendering interactive UI as native Compose, governed by the same MCP App you publish to Claude, ChatGPT, and every other MCP host.

When a tool returns a `ui` resource, the SDK fetches the BindJS bundle and renders it as native Compose, the same interface a person sees in Claude or ChatGPT, running natively inside your app.

`MetabindAgentProvider` routes the conversation through `agent.metabind.ai`. The proxy holds your language model credentials server-side, runs the tool-use loop, and streams normalized events back, so your app ships no Anthropic or OpenAI keys:

```kotlin
import ai.metabind.ai.MetabindAssistant
import ai.metabind.ai.MetabindAssistantView

@Composable
fun AssistantScreen(apiKey: String, org: String, project: String) {
    val assistant = remember {
        MetabindAssistant(apiKey = apiKey, orgId = org, projectId = project)
    }
    MetabindAssistantView(assistant = assistant)
}
```

The assistant derives the MCP server URL from your org and project ids; the optional `agentHost` and `mcpHost` parameters override the production endpoints.

> [!NOTE]
> Retain the `MetabindAssistant` instance at an appropriate scope — inside a ViewModel, for example — and call `close()` when you discard it. The Android SDK is Agent-proxy only: there's no bring-your-own-key provider, so no LLM credential ever ships in your APK.

A supplied Metabind API key authenticates both the MCP server and the agent proxy.

### Public chat and account tokens

For a public published project, omit `apiKey` when constructing
`MetabindAssistant`. No Authorization header is sent; the Agent receives a random
private guest-session identifier. Guest chat requires the matching Agent deployment.
Draft and private access still requires credentials.

For account sign-in, supply `accessTokenProvider = { ... }`, a suspend callback
that returns a fresh access token. Both MCP requests and Agent turns resolve it
when needed. The host app owns browser login, encrypted storage, and refresh.
Anonymous drafts are rejected before network access. Never put tokens or guest
session identifiers in shared project URLs.

## MCPAppsHost: render a single tool result

`:mcpappshost` is the low-level layer `:metabindai` is built on. Use `MCPAppsClient` directly when you want to drive the MCP connection and render a single tool's UI resource yourself, without the conversational layer.

## MetabindContent: fetch and render content

`:metabind-content` fetches content from Metabind's content platform and renders it natively.

### 1. Configure credentials

Metabind authenticates every request with an API key scoped to an organization and project. Configure the endpoints and credentials as `meta-data` entries in your app's `AndroidManifest.xml`. The endpoint entries (`url`, `ws.url`) are **required**; the credential entries are **optional** and default to an empty string when omitted:

```xml
<application ...>
    <meta-data android:name="ai.metabind.metabind.url" android:value="${METABIND_URL}" />
    <meta-data android:name="ai.metabind.metabind.ws.url" android:value="${METABIND_WS_URL}" />
    <meta-data android:name="ai.metabind.metabind.api.key" android:value="${METABIND_API_KEY}" />
    <meta-data android:name="ai.metabind.metabind.organization.id" android:value="${METABIND_ORGANIZATION_ID}" />
    <meta-data android:name="ai.metabind.metabind.project.id" android:value="${METABIND_PROJECT_ID}" />
</application>
```

Supply the values through `manifestPlaceholders` in your `build.gradle.kts`, reading secrets from Gradle properties so they stay out of version control:

```kotlin
defaultConfig {
    manifestPlaceholders["METABIND_URL"] = "https://api.metabind.ai/graphql"
    manifestPlaceholders["METABIND_WS_URL"] = "wss://ws-api.metabind.ai"
    manifestPlaceholders["METABIND_API_KEY"] =
        (project.findProperty("metabindApiKey") as String?).orEmpty()
    manifestPlaceholders["METABIND_ORGANIZATION_ID"] =
        (project.findProperty("metabindOrganizationId") as String?).orEmpty()
    manifestPlaceholders["METABIND_PROJECT_ID"] =
        (project.findProperty("metabindProjectId") as String?).orEmpty()
}
```

### 2. Render content with MetabindView

`MetabindView` reads the configured credentials automatically. It handles fetching, caching, and rendering:

```kotlin
import ai.metabind.metabind.view.MetabindView

@Composable
fun ContentScreen() {
    MetabindView(contentId = "cont_123")
}
```

> The content module keeps the `ai.metabind.metabind` package (and Apollo-generated
> code) it has always used, so upgrading from `metabind-android` is a coordinate change
> (`metabind-android` → `metabind-content-android`), not a source change.

---

## Samples

Runnable sample apps live under [`samples/`](samples/):

| Sample | Path | Demonstrates |
|---|---|---|
| App | [`samples/app`](samples/app) | A multi-module Compose app rendering Metabind content with `MetabindView` / `ThumbnailView`. |
| Assistant Demo | [`samples/assistant-demo`](samples/assistant-demo) | A chat app driving the agent and rendering Interactive Tool responses with `MetabindAssistantView`. |
| Retail | [`samples/retail`](samples/retail) | Placeholder for a retail content sample (stub). |

Each sample is its own Gradle build and, by default, builds against the **in-tree SDK
sources** via a composite build (`includeBuild("../..")`) with dependency substitution —
so editing SDK source flows straight into the next sample build, no publishing required:

```bash
cd samples/app            && ./gradlew :app:assembleDebug
cd samples/assistant-demo && ./gradlew :app:assembleDebug
```

### Building a sample against the published SDK

To use a sample standalone against the published coordinates instead of in-tree sources,
comment out the `includeBuild("../..") { … }` block in that sample's `settings.gradle.kts`
and set the SDK version alias in its `gradle/libs.versions.toml` (`metabind` for the app,
`metabindAssistant` for the assistant demo) to the published version you want. Gradle will
then resolve `ai.metabind:*-android` from GitHub Packages like any other dependency.

## Building from source

```bash
./gradlew build                 # build + test all three libraries
./gradlew test                  # unit tests
./gradlew publishToMavenLocal   # install all three artifacts to ~/.m2 at the unified version
./gradlew publish               # publish to GitHub Packages (needs auth)
```

GraphQL schema and operations for the content module live in
`metabind-content/src/main/graphql/`; Apollo generates the client into
`metabind-content/build/generated/`.

## License

Apache License 2.0. See [`LICENSE`](LICENSE).


## Saved-draft assistant previews

Pass `draft = true` to `MetabindAssistant` to use the MCP draft endpoint and request
saved draft configuration from the Agent service. The default remains published
mode. Preview hosts can call `awaitReady()` before showing chat to validate MCP
access without sending a message or executing a tool.

Call `refreshPreviewResources()` from a lifecycle-aware coroutine while the host
is resumed (the preview sample uses a three-second interval). It refreshes tool
definitions and existing UI resources between turns while preserving messages,
tool arguments, results, and the conversation. Identical resources do not recreate
cards, and an intervening turn or reset discards stale refresh results. Refresh
never replays tool calls. `close()` releases the assistant when its host is removed.

The `samples/app` preview host supports importing scoped access from a QR or URL,
secure local credential storage, and reopening saved projects. See its README
for the link format and local test workflow.
