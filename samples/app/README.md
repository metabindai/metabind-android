# Metabind App Android

An Android demo app that renders dynamic UI components from the Metabind service. The app uses the [Metabind library](https://github.com/yapstudios/bindjs-android-binary) to interpret and display SwiftUI-like declarative component descriptions natively with Jetpack Compose.

## Features

- **Dynamic UI rendering** via the Metabind library with real-time subscription support
- **QR code scanning** to load components by link (CameraX + ML Kit)
- **Recents list** with local Room database persistence and swipe-to-dismiss
- **Deep linking** support (`ai.metabind://app/`)
- **Edge-to-edge** UI with Material3 theming

## Architecture

Multi-module Gradle project:

```
app/                  → Main activity, navigation, app entry point
├── base-ui/          → Shared UI components and utilities
├── base-theme/       → Jetpack Compose theming (Material3)
├── feature-home/     → Screens: Recents, Detail, Preview, ScanLink
├── data-home/        → Room database, repositories, models
└── dynamicfeature/   → Dynamic feature module
```

## Building

> **Note**: JAVA_HOME must point to a JDK 21+ installation. Check `local.properties` or environment variables if you encounter errors.

```bash
./gradlew assembleDebug       # Debug build
./gradlew assembleRelease     # Release build
./gradlew test                # Unit tests
./gradlew connectedAndroidTest # Instrumentation tests
```

## Key Technologies

- **Kotlin 2.3** / **Jetpack Compose 1.9** / **Material3**
- **Metabind** library for dynamic component rendering
- **Dagger Hilt** for dependency injection
- **Room** for local persistence
- **CameraX** + **ML Kit** for barcode scanning
- **Coil** for image loading
- **Media3/ExoPlayer** for video playback
