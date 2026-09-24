// © 2025 Yap Studios LLC. All rights reserved.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // BindJS and the Metabind SDKs are published to GitHub Packages under bindjs-android-binary.
        maven {
            url = uri("https://maven.pkg.github.com/metabindai/bindjs-android-binary")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name="metabind-app-android"
include(
    ":app",
    ":base-theme",
    ":base-ui",
    ":data-home",
    ":feature-home",
)
include(":dynamicfeature")

// Build the content SDK from the monorepo sources instead of the published artifact.
// To build this sample standalone against the published SDK, comment this out and set
// the `metabind` version in gradle/libs.versions.toml to the desired published version.
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("ai.metabind:metabind-content-android")).using(project(":metabind-content"))
        substitute(module("ai.metabind:metabindai-android")).using(project(":metabindai"))
        substitute(module("ai.metabind:mcpappshost-android")).using(project(":mcpappshost"))
    }
}
