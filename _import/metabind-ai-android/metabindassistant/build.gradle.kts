plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

android {
    namespace = "ai.metabind.assistant"
    compileSdk {
        version = release(libs.versions.androidCompileSdk.get().toInt()) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    publishing {
        multipleVariants {
            includeBuildTypeValues("release", "debug")
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "ai.metabind"
            artifactId = "metabind-assistant-android"
            version = "0.1.7"

            afterEvaluate {
                from(components["default"])
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/metabindai/bindjs-android-binary")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

configurations.all {
    exclude(group = "com.atlassian.commonmark", module = "commonmark")
}

dependencies {
    api(project(":mcpappshost"))
    api(libs.bindjs)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.foundation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.richtext.commonmark)
    implementation(libs.richtext.ui.material3)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
}
