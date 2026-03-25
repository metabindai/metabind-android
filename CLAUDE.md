# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Metabind Android is a GraphQL integration layer for rendering JavaScript-driven UI components natively on Android via Jetpack Compose. It sits on top of the `bindjs` library (consumed as a Maven dependency), fetching component definitions from the Metabind API and rendering them natively.

## Modules

- **`:metabind`** — Core library. Apollo GraphQL client, repository, ViewModels, and Composable entry points. Published as `com.yapstudios:metabind-android`.
- **`:app`** — Sample app demonstrating MetabindView usage.

The `bindjs` rendering engine is an external dependency (`com.yapstudios:bindjs-android`), not part of this repo.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew :metabind:assembleDebug # Build library only
./gradlew publishToMavenLocal    # Install to ~/.m2 for local consumption
./gradlew publish                # Publish to GitHub Packages (needs auth)
```

**GitHub Packages auth** — requires `gpr.user`/`gpr.key` Gradle properties or `GITHUB_ACTOR`/`GITHUB_TOKEN` env vars. This is needed both for publishing and for resolving the `bindjs` dependency.

## Build Configuration

- Gradle 9.0.1 with Kotlin DSL, version catalog at `gradle/libs.versions.toml`
- Compile SDK 36, Min SDK 26, Java 21
- Apollo GraphQL code generation: schemas in `metabind/src/main/graphql/`, generated code in `build/generated/source/apollo/`

## Architecture

**MVVM with Repository pattern:**

```
Composable (MetabindView / PreviewView / ThumbnailView)
  → ViewModel (StateFlow-based state: Loading | Success | Error)
    → ComponentRepository (singleton, Apollo client + normalized cache)
      → Metabind GraphQL API (queries + WebSocket subscriptions)
```

**Key classes in `com.yapstudios.metabind`:**

- `ComponentRepository` — Apollo client setup, GraphQL queries/subscriptions, dual-layer cache (10MB memory + SQLite). Singleton via `ComponentRepository.getInstance(context)`.
- `MetabindView` / `MetabindViewModel` — Main entry point. Loads a component by content ID, optionally subscribes to real-time updates.
- `PreviewView` / `PreviewViewModel` — Preview variant using token-based queries.
- `ThumbnailView` / `ThumbnailViewModel` — Offscreen bitmap rendering via virtual display + PixelCopy.
- `Component.kt` — Data models (`PreviewComponent`, `DesignerComponent`, etc.)

**Data flow:** GraphQL responses → `PreviewComponent` → BindJS `JsRuntime` executes JS component code → native Compose rendering.

## GraphQL

Schema files in `metabind/src/main/graphql/com/yapstudios/metabind/`:
- `Queries.graphql` — `ContentsQuery`, `ContentQuery`, `PreviewQuery`, `ResolvedPackageDataQuery`
- `Subscriptions.graphql` — `ContentUpdatedSubscription`, `PreviewUpdatedSubscription`
- `Fragments.graphql` — Shared field definitions

API endpoints are configured as manifest placeholders: `METABIND_URL` (HTTPS) and `METABIND_WS_URL` (WSS).

## Testing

No test infrastructure exists yet. No test directories or test dependencies are configured.
