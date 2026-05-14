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
        mavenLocal() // for SDK development against `publishToMavenLocal` builds of metabind-ai-android
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

rootProject.name = "metabind-assistant-demo"
include(":app")

// Uncomment to develop against a local checkout of metabind-ai-android:
//
includeBuild("../metabind-ai-android") {
    dependencySubstitution {
        substitute(module("ai.metabind:mcpappshost-android")).using(project(":mcpappshost"))
        substitute(module("ai.metabind:metabind-assistant-android")).using(project(":metabindassistant"))
    }
}
