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
        mavenLocal() // for SDK development against `publishToMavenLocal` builds of the monorepo SDKs
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

rootProject.name = "metabind-assistant-demo"
include(":app")

// Build the AI SDK from the monorepo sources instead of the published artifacts.
// To build this sample standalone against the published SDK, comment this out and set
// the `metabindAssistant` version in gradle/libs.versions.toml to the desired published version.
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("ai.metabind:mcpappshost-android")).using(project(":mcpappshost"))
        substitute(module("ai.metabind:metabindai-android")).using(project(":metabindai"))
    }
}
