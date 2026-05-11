# Metabind Android

A GraphQL integration layer for rendering JavaScript-driven UI components natively on Android via Jetpack Compose. Metabind fetches component definitions from the Metabind API and renders them using the [BindJS](https://github.com/metabindai/bindjs-android) rendering engine.

## Modules

### `:metabind`

Core library providing Apollo GraphQL client integration, real-time updates via WebSocket subscriptions, and normalized caching (memory + SQLite). Exposes `MetabindView`, `PreviewView`, and `ThumbnailView` as Composable entry points with ViewModel-based state management.

Published as `ai.metabind:metabind-android`.

### `:app`

Sample application demonstrating `MetabindView` usage.

## Setup

### Authentication

This project depends on `ai.metabind:bindjs-android` from GitHub Packages, which requires authentication. Provide credentials via environment variables or Gradle properties:

```bash
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<your-github-token>
```

Or in `~/.gradle/gradle.properties`:

```properties
gpr.user=<your-github-username>
gpr.key=<your-github-token>
```

### Build

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew :metabind:assembleDebug # Build library only
./gradlew publishToMavenLocal    # Install to local Maven (~/.m2)
./gradlew publish                # Publish to GitHub Packages
```

## Usage

Add the dependency:

```kotlin
dependencies {
    implementation("ai.metabind:metabind-android:0.0.2")
}
```

Then use `MetabindView` in your Compose UI:

```kotlin
MetabindView(contentId = "your-content-id")
```
