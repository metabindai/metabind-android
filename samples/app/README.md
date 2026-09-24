# Metabind App Android

An Android demo app that renders dynamic UI components from the Metabind service. The app uses the in-tree Metabind Content and Assistant SDKs to interpret and display SwiftUI-like declarative component descriptions natively with Jetpack Compose.

## Features

- **Dynamic UI rendering** via the Metabind library with real-time subscription support
- **QR code scanning** to load components by link (CameraX + ML Kit)
- **Recents list** with local Room database persistence and swipe-to-dismiss
- **Deep linking** support (`ai.metabind://app/`)
- **Edge-to-edge** UI with Material3 theming

## MCP project chat

Studio's Connect screen has separate published-project and draft-preview links.
Scan a QR in the app, open the HTTPS link, or paste it into Preview. Both modes use
`https://www.metabind.ai/preview/mcp?url=<encoded endpoint>&name=<optional title>`.
Only the draft MCP endpoint has `/draft`. Development uses the corresponding
`dev.metabind.ai` and `mcp-dev.metabind.ai` hosts. Only canonical project IDs and
trusted HTTPS endpoints are accepted.

Public published projects open ready to chat without sign-in. The SDK sends a
random, private guest-session identifier to isolate conversation history. This
requires the shared Agent guest-chat deployment. Private projects remain protected.
Published mode does not run draft polling.

Draft links show **Sign in to Metabind**. AppAuth opens the system browser and
uses authorization code flow with S256 PKCE and random state. The app validates
discovery hosts, project, callback, and granted scopes. It refreshes access tokens
for MCP requests and Agent turns without resetting the conversation. OAuth state
is encrypted with an Android Keystore key and excluded from backups. Old QR keys
are ignored and legacy stored keys are removed when the corresponding draft is opened.
**Disconnect this project** and removing a recent clear the device's grant, not
browser sign-in or another device's access. Revoke old server API keys in Studio.

Saved draft tools and existing cards refresh every three seconds while foregrounded
and between turns, preserving conversation and results without replaying tools.
Structural edits can reset card state. Draft tools can still change real data.
The project must have its assistant configured.

Emulators and devices without cameras open manual URL entry. Physical devices
retain scanning and manual entry. Existing content preview links still work.
HTTPS links require the release app's signing certificate in each website's
`/.well-known/assetlinks.json` to open directly without user link-handler setup.
OAuth uses the package-specific `<applicationId>.oauth://callback` scheme;
debug and release callback schemes are separate.

### Local validation

```sh
./gradlew :app:assembleDebug :data-home:testDebugUnitTest \
  :metabind-android:metabindai:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Keystore instrumentation checks persistence, environment isolation, ciphertext,
and tamper rejection. Sign-in checks cover credential-free draft entry, activity
recreation, PKCE/state generation, and rejection of a mismatched OAuth response.
SDK tests cover anonymous discovery/chat/card loading, guest-session isolation,
anonymous draft rejection, token rotation for both MCP and Agent, and draft refresh.

The opt-in live Finance test requires `mcp-preview-test-link` in the app's cache
and a previously completed draft login. It checks a production draft and deletes
the input after import. Published chat is tested separately. Physical camera scanning, real account
login/refresh/revocation, deployed Studio links, and live guest chat require end-to-end
validation. Local fixtures are not proof that deployed services support guest chat.

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
