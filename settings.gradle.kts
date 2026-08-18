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
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        // BindJS is consumed as a published GitHub Packages artifact and kept in its
        // own repo (bindjs-android / bindjs-android-binary), exactly like bindjs-apple-binary.
        maven {
            url = uri("https://maven.pkg.github.com/metabindai/bindjs-android-binary")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).get()
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).get()
            }
        }
    }
}

rootProject.name = "metabind-android"

// Published libraries
include(":metabind-content")
include(":mcpappshost")
include(":metabindai")

// To develop against a local checkout of BindJS, uncomment and point at ../bindjs-android:
includeBuild("../bindjs-android") {
    dependencySubstitution {
        substitute(module("ai.metabind:bindjs-android")).using(project(":bindjs"))
    }
}
