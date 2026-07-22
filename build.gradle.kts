import org.gradle.api.publish.PublishingExtension

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.apollo) apply false
}

// Single source of truth for the published library version. Every library module
// (:metabind-content, :mcpappshost, :metabindai) inherits group + version from here,
// and the shared GitHub Packages repository below, so only artifactId differs per module.
val metabindVersion = libs.versions.metabind.get()

subprojects {
    group = "ai.metabind"
    version = metabindVersion

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/metabindai/bindjs-android-binary")
                    credentials {
                        username = (project.findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
                        password = (project.findProperty("gpr.key") as String?) ?: System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }
}
