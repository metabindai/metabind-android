# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`metabind-android` is the consolidated Android monorepo. It ships three independent
libraries from one build, and hosts the sample apps. It sits on top of the `bindjs`
library (consumed as a published GitHub Packages dependency, kept in its own repo).

## Modules (published libraries)

- **`:metabind-content`** — Content SDK. Apollo GraphQL client, repository, ViewModels, and Composable entry points (`MetabindView`). Published as `ai.metabind:metabind-content-android`. Namespace/package `ai.metabind.metabind` (unchanged; formerly `:metabind` / `metabind-android`).
- **`:mcpappshost`** — Low-level MCP tool-rendering building blocks (`MCPAppsClient`). Published as `ai.metabind:mcpappshost-android`. Namespace `ai.metabind.mcpappshost`.
- **`:metabindai`** — Assistant SDK (`MetabindAssistant`, `MetabindAssistantView`). Published as `ai.metabind:metabindai-android`. Namespace/package `ai.metabind.ai` (formerly `:metabindassistant` / `metabind-assistant-android`, namespace `ai.metabind.assistant`).

All three publish at one **unified version** defined once in `gradle/libs.versions.toml`
(`versions.metabind`) and inherited via the root `build.gradle.kts` (group + version +
GitHub Packages repository configured once for every `maven-publish` subproject).

The `bindjs` rendering engine is an external dependency (`ai.metabind:bindjs-android`), not part of this repo.

## Samples

Sample apps live under `samples/` (`app`, `assistant-demo`, `retail`). Each is its own
Gradle build and, by default, builds against the in-tree SDK sources via
`includeBuild("../..")` + dependency substitution. `retail` is a stub.

## Build Commands

```bash
./gradlew build                          # Build + test all three libraries
./gradlew :metabind-content:assembleDebug # Build one library
./gradlew publishToMavenLocal            # Install all 3 artifacts to ~/.m2 at the unified version
./gradlew publish                        # Publish to GitHub Packages (needs auth)

# Samples are separate composite builds:
cd samples/app            && ./gradlew :app:assembleDebug
cd samples/assistant-demo && ./gradlew :app:assembleDebug
```

**GitHub Packages auth** — requires `gpr.user`/`gpr.key` Gradle properties or `GITHUB_ACTOR`/`GITHUB_TOKEN` env vars. This is needed both for publishing and for resolving the `bindjs` dependency.

## Build Configuration

- Gradle 9.0.1 with Kotlin DSL, version catalog at `gradle/libs.versions.toml`
- Compile SDK 36, Min SDK 26, Java 21
- Apollo GraphQL code generation: schemas in `metabind-content/src/main/graphql/`, generated code in `metabind-content/build/generated/`

## Architecture

**MVVM with Repository pattern:**

```
Composable (MetabindView / PreviewView / ThumbnailView)
  → ViewModel (StateFlow-based state: Loading | Success | Error)
    → ComponentRepository (singleton, Apollo client + normalized cache)
      → Metabind GraphQL API (queries + WebSocket subscriptions)
```

**Key classes in `ai.metabind.metabind`:**

- `ComponentRepository` — Apollo client setup, GraphQL queries/subscriptions, dual-layer cache (10MB memory + SQLite). Singleton via `ComponentRepository.getInstance(context)`.
- `MetabindView` / `MetabindViewModel` — Main entry point. Loads a component by content ID, optionally subscribes to real-time updates.
- `PreviewView` / `PreviewViewModel` — Preview variant using token-based queries.
- `ThumbnailView` / `ThumbnailViewModel` — Offscreen bitmap rendering via virtual display + PixelCopy.
- `Component.kt` — Data models (`PreviewComponent`, `DesignerComponent`, etc.)

**Data flow:** GraphQL responses → `PreviewComponent` → BindJS `JsRuntime` executes JS component code → native Compose rendering.

## GraphQL

Schema files in `metabind-content/src/main/graphql/`:
- `Queries.graphql` — `ContentsQuery`, `ContentQuery`, `PreviewQuery`, `ResolvedPackageDataQuery`
- `Subscriptions.graphql` — `ContentUpdatedSubscription`, `PreviewUpdatedSubscription`
- `Fragments.graphql` — Shared field definitions

API endpoints are configured as manifest placeholders: `METABIND_URL` (HTTPS) and `METABIND_WS_URL` (WSS).

## Testing

No test infrastructure exists yet. No test directories or test dependencies are configured.
