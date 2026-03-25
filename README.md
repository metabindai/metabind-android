# BindJS Android

A JavaScript-driven UI rendering engine for Android using Jetpack Compose. BindJS lets you define UI components that are rendered natively on Android, with optional GraphQL-based content loading via the Metabind platform.

## Modules

### `bindjs`
Core rendering engine. Deserializes a component tree from JSON and renders it using Jetpack Compose. Includes 30+ composable views (Box, Row, Column, Button, Text, Image, Video, Model3D, etc.), a modifier system for styling/layout, gradient support, and a JavaScript runtime for event handlers and dynamic logic.

**Key dependencies:** Compose, Coil (images), Media3/ExoPlayer (video), SceneView (3D models), androidx.javascriptengine.

### `metabind`
GraphQL integration layer built on top of `bindjs`. Fetches component definitions from the Metabind API using Apollo GraphQL, supports real-time updates via subscriptions, and provides normalized caching (SQL + memory). Exposes `MetabindView` — a single composable entry point with ViewModel-based state management.

### `app`
Sample application demonstrating `MetabindView` usage with hardcoded content IDs.

## Publishing

Both `bindjs` and `metabind` are published as Maven artifacts:

| Module     | ArtifactId           |
|------------|----------------------|
| bindjs     | `com.yapstudios:bindjs-android`   |
| metabind   | `com.yapstudios:metabind-android` |

### Publish to GitHub Packages

Authentication requires a GitHub personal access token with `write:packages` scope.

**Option 1 — environment variables:**

```bash
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<your-github-token>
./gradlew publish
```

**Option 2 — Gradle properties:**

```bash
./gradlew publish -Pgpr.user=<your-github-username> -Pgpr.key=<your-github-token>
```

You can also add these to your `~/.gradle/gradle.properties`:

```properties
gpr.user=<your-github-username>
gpr.key=<your-github-token>
```

Artifacts are published to `https://maven.pkg.github.com/yapstudios/bindjs-android-binary`.

### Consuming GitHub Packages artifacts

GitHub Packages requires authentication even for reading packages. You need a personal access token with `read:packages` scope.

Add the repository to your project's `settings.gradle.kts` (or root `build.gradle.kts`):

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/yapstudios/bindjs-android-binary")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Then add the dependency:

```kotlin
dependencies {
    implementation("com.yapstudios:bindjs-android:0.0.2")
    // or
    implementation("com.yapstudios:metabind-android:0.0.2")
}
```

Set credentials via `~/.gradle/gradle.properties` or environment variables as described above.

### Publish to local Maven repository

To install artifacts to your local Maven cache (`~/.m2/repository`):

```bash
./gradlew publishToMavenLocal
```

Then in consuming projects, add `mavenLocal()` to your repositories block:

```kotlin
repositories {
    mavenLocal()
    // ...
}
```

And add the dependency:

```kotlin
dependencies {
    implementation("com.yapstudios:bindjs-android:0.0.2")
    // or
    implementation("com.yapstudios:metabind-android:0.0.2")
}
```
