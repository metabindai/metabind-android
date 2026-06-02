# Metabind Android

Android SDK for the Metabind API. Fetch and render dynamic content natively in your Android apps via Jetpack Compose, powered by the [BindJS](https://github.com/metabindai/bindjs-android) rendering engine.

## Installation

Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.metabind:metabind-android:0.0.12")
}
```

The library is published to GitHub Packages, which requires authentication to
resolve. Provide credentials via environment variables:

```bash
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<your-github-token>
```

Or in `~/.gradle/gradle.properties`:

```properties
gpr.user=<your-github-username>
gpr.key=<your-github-token>
```

## Quick Start

### 1. Configure Credentials

Metabind authenticates every request with an API key scoped to an organization and
project. Configure the endpoints and credentials as `meta-data` entries in your app's
`AndroidManifest.xml`. The endpoint entries (`url`, `ws.url`) are **required**; the
credential entries (`api.key`, `organization.id`, `project.id`) are **optional** and
default to an empty string when omitted:

```xml
<application ...>
    <meta-data android:name="ai.metabind.metabind.url" android:value="${METABIND_URL}" />
    <meta-data android:name="ai.metabind.metabind.ws.url" android:value="${METABIND_WS_URL}" />
    <meta-data android:name="ai.metabind.metabind.api.key" android:value="${METABIND_API_KEY}" />
    <meta-data android:name="ai.metabind.metabind.organization.id" android:value="${METABIND_ORGANIZATION_ID}" />
    <meta-data android:name="ai.metabind.metabind.project.id" android:value="${METABIND_PROJECT_ID}" />
</application>
```

Supply the values through `manifestPlaceholders` in your `build.gradle.kts`, reading
secrets from Gradle properties so they stay out of version control:

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

```properties
# ~/.gradle/gradle.properties
metabindApiKey=your-api-key
metabindOrganizationId=your-org-id
metabindProjectId=your-project-id
```

The library sends these as an `x-api-key: <org>:<project>:<apiKey>` header on HTTP
requests and in the WebSocket connection init payload. Any credential left unset is
sent as an empty value.

### 2. Render Content with MetabindView

`MetabindView` reads the configured credentials automatically. It handles fetching,
caching, and rendering:

```kotlin
import ai.metabind.metabind.view.MetabindView

@Composable
fun ContentScreen() {
    MetabindView(contentId = "cont_123")
}
```

**Features:**

- Automatic loading, error, and success states
- SQLite-backed caching with cache-then-network updates
- Optional real-time subscriptions via WebSocket
- ViewModel lifecycle tied to the composable (automatic cancellation)

**With Real-time Updates:**

```kotlin
// Enable WebSocket subscription for live updates
MetabindView(contentId = "cont_123", enableSubscription = true)
```

When `enableSubscription = true`, the view watches the cached content for the
initial load and a WebSocket subscription for real-time updates from the CMS.

## GraphQL APIs

For more control, use `ComponentRepository` directly. Obtain the shared instance
with `ComponentRepository.get(context)`.

For complete API documentation, see the
[Metabind GraphQL Documentation](https://docs.metabind.ai/graphql/overview). You can
also view the [GraphQL schema](metabind/src/main/graphql/schema.graphqls) in this
repository.

The APIs follow a consistent pattern:

- **`get*()`** — single `suspend` request. Cache-first by default; pass `refresh = true`
  for a network-first fetch.
- **List queries** — return a cursor-paginated [`Page<T>`](metabind/src/main/kotlin/ai/metabind/metabind/CmsModels.kt)
  (`data`, `cursor`, `hasMore`, `limit`).
- **`watch*()` / `subscribeTo*()`** — `Flow`s that emit cache-then-network updates and
  real-time WebSocket updates (preview rendering).

Single-item lookups return the generated Apollo fragment type (or `null` when not
found); list queries return `Page<FragmentType>`.

### Content

Fetch and render individual content entries by preview token.

```kotlin
val repo = ComponentRepository.get(context)

// Fetch a renderable content/component by token (Result<PreviewComponent>)
val result = repo.getPreviewByToken("cont_123")
result.onSuccess { component -> /* render */ }

// Fetch resolved content directly by token (PreviewComponent?)
val content = repo.getContentByToken("cont_123")

// Watch for cache-then-network updates
repo.watchPreviewByToken("cont_123").collect { result ->
    result.onSuccess { component -> /* update UI */ }
}

// Subscribe to real-time updates over WebSocket
repo.subscribeToPreviewByToken("cont_123").collect { component ->
    // Handle live update
}
```

### Contents (List)

Fetch paginated lists of content with filtering and sorting.

```kotlin
val contents = repo.getContents(
    typeId = "type_123",            // Filter by content type
    tags = listOf("featured"),      // Filter by tags
    locale = "en-US",               // Filter by locale
    search = "hello",               // Text search
    filter = ContentFilter(...),    // Advanced filtering
    sort = listOf(SortCriteria(...)), // Sorting
    cursor = null,                  // Pagination cursor
    limit = 20,                     // Page size
)

val items = contents.data
val hasMore = contents.hasMore
val nextCursor = contents.cursor
```

### Components

Fetch component definitions.

```kotlin
// Fetch a single component
val component = repo.getComponent("comp_123")

// Fetch a component list with search
val components = repo.getComponents(search = "Button", limit = 20)
```

### Content Types

Fetch content type schemas.

```kotlin
// Fetch a single content type
val contentType = repo.getContentType("type_123")

// Fetch a content type list
val contentTypes = repo.getContentTypes(search = "Article", limit = 20)
```

### Assets

Fetch media assets (images, videos, files).

```kotlin
// Fetch a single asset
val asset = repo.getAsset("asset_123")

// Fetch an asset list with filtering
val assets = repo.getAssets(
    type = "image/jpeg",            // Filter by MIME type
    tags = listOf("hero"),          // Filter by tags
    search = "logo",                // Text search
    filter = AssetFilter(...),      // Advanced filtering
    sort = listOf(SortCriteria(...)),
    cursor = null,
    limit = 20,
)
```

### Tags

Fetch tags used for organizing content and assets.

```kotlin
// Fetch a single tag
val tag = repo.getTag("tag_123")

// Fetch a tag list
val tags = repo.getTags(search = "category", limit = 20)
```

### Packages

Fetch published component packages (versioned snapshots).

```kotlin
// Fetch a single package by version
val pkg = repo.getPackage("1.0.0")

// Fetch a package list
val packages = repo.getPackages(limit = 20)
```

### Saved Searches

Execute pre-configured searches created in the CMS.

```kotlin
// Fetch a saved search definition
val savedSearch = repo.getSavedSearch("search_123")

// Fetch a saved search list
val savedSearches = repo.getSavedSearches(type = SavedSearchType.CONTENT, limit = 20)

// Execute a saved search and get results (a union of contents or assets)
when (val results = repo.executeSavedSearch("search_123")) {
    is SavedSearchResults.Contents -> results.page.data.forEach { /* ContentFields */ }
    is SavedSearchResults.Assets -> results.page.data.forEach { /* AssetFields */ }
}
```

## Direct Rendering

For advanced use cases, the library exposes additional composables and the
underlying rendering engine.

- **`PreviewView`** — renders a preview by token, the same way `MetabindView` does,
  for use in editor/preview surfaces.
- **`ThumbnailView`** — renders a component offscreen to a bitmap (virtual display +
  `PixelCopy`) for thumbnails and previews.

```kotlin
import ai.metabind.metabind.view.PreviewView
import ai.metabind.metabind.view.ThumbnailView

@Composable
fun PreviewScreen() {
    PreviewView(contentId = "cont_123")
}

@Composable
fun ThumbnailScreen() {
    ThumbnailView(contentId = "cont_123")
}
```

Content fetched via `ComponentRepository` is resolved into a `PreviewComponent`
(id, name, compiled content, and resolved package dependencies), which the BindJS
`JsRuntime` executes to drive native Compose rendering.

## Cache Policies

Each `ComponentRepository` method takes a `refresh` flag that maps to an Apollo
[`FetchPolicy`](https://www.apollographql.com/docs/kotlin/caching/query-information):

| `refresh` | FetchPolicy | Behavior | Use case |
|-----------|-------------|----------|----------|
| `false` (default) | `CacheFirst` | Return cached data if present, else network | Instant display, fewer requests |
| `true` | `NetworkFirst` | Fetch from network, fall back to cache on failure | Force-refresh, pull-to-refresh |

The `watch*()` flows use `CacheAndNetwork` — they emit cached data immediately and
then network updates as they arrive. Responses are cached in a two-layer normalized
cache: a 10 MB in-memory cache backed by SQLite.

## Error Handling

```kotlin
// Result-based APIs (preview rendering)
repo.getPreviewByToken("cont_123")
    .onSuccess { component -> /* render */ }
    .onFailure { error -> /* handle GraphQL or network error */ }

// Browse queries throw on failure
try {
    val assets = repo.getAssets(type = "image")
} catch (e: ApolloException) {
    // Network / transport error
} catch (e: RuntimeException) {
    // GraphQL errors (validation, permissions) or missing data
}
```

## Features

- **SQLite Cache**: Persistent normalized cache (10 MB memory + SQLite)
- **Authentication**: Automatic API key injection for HTTP and WebSocket
- **WebSocket Support**: Real-time subscriptions with auto-reconnect
- **Coroutines & Flow**: Modern Kotlin concurrency throughout
- **Type Safety**: Fully generated types from the GraphQL schema
- **Compose Integration**: Purpose-built composables for Metabind content

## Modules

- **`:metabind`** — Core library. Apollo GraphQL client, `ComponentRepository`,
  ViewModels, and the `MetabindView` / `PreviewView` / `ThumbnailView` composables.
  Published as `ai.metabind:metabind-android`.
- **`:app`** — Sample app demonstrating `MetabindView` usage.

## Building

```bash
./gradlew assembleDebug           # Build the sample APK
./gradlew :metabind:assembleDebug # Build the library only
./gradlew publishToMavenLocal     # Install to local Maven (~/.m2)
./gradlew publish                 # Publish to GitHub Packages
```

## Dependencies

- Apollo Kotlin 4.4.1 (GraphQL client with WebSocket and SQLite support)
- BindJS (component rendering engine)

## Regenerating GraphQL Code

GraphQL operations live in `metabind/src/main/graphql/` (`Queries.graphql`,
`Subscriptions.graphql`, `Fragments.graphql`) against `schema.graphqls`. The Apollo
Kotlin Gradle plugin regenerates typed models on every build:

```bash
./gradlew :metabind:generateServiceApolloSources
```

Generated code is placed under `metabind/build/generated/source/apollo/`. Never edit
these files manually.
</content>
</invoke>
