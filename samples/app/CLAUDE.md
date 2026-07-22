# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is the `app` sample inside the `metabind-android` monorepo (`samples/app`). It is its
own Gradle build and, by default, builds against the in-tree content SDK via
`includeBuild("../..")` + a dependency substitution of `ai.metabind:metabind-content-android`
onto `:metabind-content` (see `settings.gradle.kts`).

## Project Overview

Metabind App Android is an Android demo app that renders dynamic UI components from the Metabind service. It uses the Metabind content library (`ai.metabind:metabind-content-android`) to interpret and display SwiftUI-like declarative component descriptions natively with Jetpack Compose.

## Build Commands

**Important**: JAVA_HOME must point to a JDK 21+ installation. If you encounter JAVA_HOME errors, check the local.properties file or environment variables.

```bash
# Build the app (debug variant)
./gradlew assembleDebug

# Build release variant
./gradlew assembleRelease

# Clean build
./gradlew clean

# Run tests
./gradlew test

# Run instrumentation tests
./gradlew connectedAndroidTest
```

## Architecture

### Module Structure

The project uses a multi-module architecture organized by feature and layer:

- **app**: Main application module with navigation setup and app entry point
- **base-ui**: Shared UI components and utilities
- **base-theme**: Jetpack Compose theming (Material3 defaults)
- **data-home**: Data layer for home feature (Room database, repositories, models)
- **feature-home**: Home feature screens and ViewModels
- **dynamicfeature**: Dynamic feature module

### Metabind Integration

The app uses the `ai.metabind:metabind-content-android` library (built from the monorepo's `:metabind-content` module via composite build; resolvable from GitHub Packages when built standalone) to render dynamic UI components:

- **`MetabindView`** (feature-home/DetailScreen.kt): Renders a full component by content ID with real-time subscription support
- **`ThumbnailView`** (feature-home/RecentsScreen.kt): Renders component thumbnails in the recents list
- Metabind URLs are configured via build config and injected into the manifest

### Navigation

Navigation uses Jetpack Compose Navigation with a graph-based structure:

- **ComposeApp.kt**: Sets up NavHost and observes NavigationConductor flow
- **Screens.kt**: Route definitions (`recents`, `preview`, `scanLink`, `detail/{itemId}`)
- **NavigationConductor**: Flow-based navigation events for programmatic navigation
- **MainGraph**: Root navigation graph; `recents` is the start destination
- **Deep links**: URI prefix `ai.metabind://app/` for preview, scanLink, detail, recents

Bottom navigation bar with two tabs: Recents and Preview.

### Dependency Injection

Dagger Hilt is used throughout with these key modules:

- `HomeModule`: Provides MetabindDatabase, RecentsDao, RecentsRepository

### Convention Plugins

Custom Gradle convention plugins in `buildSrc/`:

- **common-android-library.gradle.kts**: Base setup for library modules (Hilt, Kotlin, coroutines)
- **common-feature.gradle.kts**: Extends common-android-library with Compose dependencies

### Data Layer

Room database in `data-home`:

- **MetabindDatabase**: Single database with RecentsDao
- **RecentItem**: Entity storing id, name, token (content ID), timestamp
- **RecentsRepository**: Data access layer for recent items

### ViewModel Pattern

ViewModels follow a consistent pattern with delegates:

- `ViewStateProviderDelegate`: Manages immutable state with SavedStateHandle
- `AnalyticsDelegate`: Provides analytics tracking (screen name registration)
- ViewModels expose `viewState: StateFlow<ViewState>` for observing UI state
- Use `updateState()` to modify state immutably

### Key Screens

- **RecentsScreen**: Lists recently viewed components with swipe-to-dismiss deletion, uses `ThumbnailView` for previews
- **DetailScreen**: Full component rendering via `MetabindView` with subscription support
- **PreviewScreen / ScanLinkScreen**: Camera-based QR code scanning to load components

## Key Technologies

- **Kotlin 2.3.10** with coroutines for async operations
- **Jetpack Compose** (1.9.4) for UI with **Material3**
- **Compose Compiler Plugin** for Compose integration
- **Dagger Hilt** (2.58) for dependency injection
- **Metabind Content** (`metabind-content-android`, unified 0.2.0) for dynamic component rendering
- **Room** (2.8.3) for local persistence
- **Gson** for JSON serialization
- **Timber** for logging
- **CameraX** (1.4.1) and ML Kit barcode scanning
- **Coil** (2.7.0) for image loading
- **Media3/ExoPlayer** (1.5.0) for video playback

## Development Notes

### Module Dependencies

```
app
├── base-ui
├── base-theme
└── feature-home
    ├── base-ui
    ├── base-theme
    ├── data-home (Room database)
    ├── metabind-content (SDK library, in-tree via composite build)
    ├── CameraX / ML Kit / Coil
    └── Media3
```

Always add feature dependencies in feature module build.gradle.kts files.

### Build Variants

- **Debug**: Uses debug keystore (app/debug_keystore.keystore) with standard debug key
- **Release**: Minification enabled, release keystore configuration
- **debugRelease**: Custom variant for testing
- Dynamic features are included via `dynamicFeatures += setOf(":dynamicfeature")`

### Git Worktrees

Create worktrees in the sibling directory `../yap-content-builder-android-worktrees/`.

### Testing

- Unit tests: Use JUnit, placed in `src/test/`
- Instrumentation tests: Use Espresso and Compose testing, placed in `src/androidTest/`
- Main test file: `app/src/androidTest/java/.../MainActivityTest.kt`
