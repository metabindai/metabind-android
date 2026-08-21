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
        maven {
            url = uri("https://maven.pkg.github.com/metabindai/bindjs-android-binary")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).get()
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).get()
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/yapstudios/bindjs-android-binary")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).get()
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).get()
            }
        }
    }
}

rootProject.name = "metabind-finance-demo"
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
