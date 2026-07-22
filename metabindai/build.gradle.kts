plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

android {
    namespace = "ai.metabind.ai"
    compileSdk {
        version = release(libs.versions.android.compile.sdk.get().toInt()) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
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
    lint {
        // Captures a pre-existing finding in the HTML-fallback WebView bridge
        // (missing @JavascriptInterface on MBAndroidHost) carried over unchanged from
        // metabind-ai-android. Tracked as a follow-up; baselined so `build` stays green
        // and new lint regressions are still caught.
        baseline = file("lint-baseline.xml")
    }

    publishing {
        multipleVariants {
            includeBuildTypeValues("release", "debug")
        }
    }
}

publishing {
    // group, version and the GitHub Packages repository come from the root build.
    publications {
        register<MavenPublication>("release") {
            artifactId = "metabindai-android"

            afterEvaluate {
                from(components["default"])
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
