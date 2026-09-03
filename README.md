# Metabind for Android

The native Android SDK for Metabind. Embed a governed agent in your Android app, and render Metabind-managed content as native Jetpack Compose.

## What this is

Metabind is the hosted platform for [Model Context Protocol (MCP)](https://modelcontextprotocol.io) Apps: you define the tools, and Metabind runs the server. It turns your existing UI and APIs into a governed agent — a standards-compliant MCP App that understands what each customer came for, renders interactive UI instead of plain text, and runs both inside your own app and across Claude, ChatGPT, and every MCP host. The agent is governed, not autonomous. It follows the system prompt you author, and it can only render components you approved, validated against each tool's schema on every render.

This repository is the Android side. It ships three libraries you can adopt independently:

| Library | Artifact | Use it to |
|---|---|---|
| `:metabindai` | `ai.metabind:metabindai-android` | Embed the agent in your app. The Assistant SDK drops in a conversational view that runs the agent and renders its Interactive Tool responses as native Compose. |
| `:mcpappshost` | `ai.metabind:mcpappshost-android` | Render a single MCP tool result without the conversational layer. The low-level building blocks (`MCPAppsClient`) that `:metabindai` is built on. |
| `:metabind-content` | `ai.metabind:metabind-content-android` | Fetch and render content from Metabind's content platform. A Compose view, an Apollo GraphQL client, SQLite-backed caching, and real-time updates over WebSocket. |

Everything renders through BindJS as real native Jetpack Compose, not web views. The three libraries have different dependency footprints, so you depend only on the ones you use: a content-only app doesn't link the assistant, and an assistant-only app doesn't link the GraphQL client.

> [!NOTE]
> BindJS is Metabind's rendering engine. This SDK links it as a precompiled binary (`ai.metabind:bindjs-android`, published to GitHub Packages). All of BindJS is open source under Apache 2.0: the runtime and React renderer, and the native SwiftUI and Jetpack Compose engines.

## The Metabind SDKs

| Platform | Repository |
|---|---|
| iOS, macOS, visionOS | [`metabind-apple`](https://github.com/metabindai/metabind-apple) |
| Android | `metabind-android` — this repository |
| Web (React) | [`metabind-web`](https://github.com/metabindai/metabind-web) |

One MCP App serves all three: the same tools, components, and agent configuration from a single publish, so the SDKs compose — ship the Android assistant, the iOS assistant, and the web chat surface together.

**[🚀 Start free at metabind.ai](https://metabind.ai)** · **[📖 Read the docs](https://docs.metabind.ai)**

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

All Metabind libraries — and their BindJS dependency — are published to GitHub Packages, which requires authentication to resolve. Provide credentials via environment variables:

```bash
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<your-github-token>
```

Or in `~/.gradle/gradle.properties`:

```properties
gpr.user=<your-github-username>
gpr.key=<your-github-token>
```

Add the repository in your `settings.gradle.kts`:

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

Then add the libraries you need to your module's `build.gradle.kts`. All three ship at one unified version:

```kotlin
dependencies {
    implementation("ai.metabind:metabindai-android:0.2.9")        // embed the agent
    implementation("ai.metabind:mcpappshost-android:0.2.9")       // low-level rendering
    implementation("ai.metabind:metabind-content-android:0.2.9")  // content SDK
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

One Metabind API key authenticates both the MCP server and the agent proxy.

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
