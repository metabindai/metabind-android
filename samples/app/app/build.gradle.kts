plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
    alias(libs.plugins.compose.compiler)
}

android {

    // bindjs-android compiles against 36.1 and requires consumers to do the same, so this
    // has to be the block form — `defaultConfig.compileSdk` can't express a minor API level.
    compileSdk {
        version = release(libs.versions.android.compile.sdk.get().toInt()) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ai.metabind.app"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "9").toInt()
        versionName = "1.0.0.$versionCode"
        manifestPlaceholders["METABIND_URL"] = "https://api-dev.metabind.ai/graphql"
        manifestPlaceholders["METABIND_WS_URL"] = "wss://ws-api-dev.metabind.ai"
    }

    signingConfigs {
        // Sets a specific keystore to be used for debug builds. This means that everyone
        // building debug versions of the app will all be using the same keystore. This is
        // very helpful because some services (such as Firebase and Google Play Services)
        // require that builds have a specific signing signature in order to properly
        // communicate / authenticate with their services.
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeFile = file("debug_keystore.keystore")
            storePassword = "android"
            enableV1Signing = true
            enableV2Signing = true
        }
        // Note that in CI, release builds are signed separately and do not use this
        // configuration.
        create("release") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeFile = file("debug_keystore.keystore")
            storePassword = "android"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        create("debugRelease") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".debug"
            matchingFallbacks += listOf("debug")
        }

        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile(
                    "proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
            manifestPlaceholders["METABIND_URL"] = "https://api.metabind.ai/graphql"
            manifestPlaceholders["METABIND_WS_URL"] = "wss://ws-api.metabind.ai"
        }

        getByName("debugRelease") {
            manifestPlaceholders["METABIND_URL"] = "https://api.metabind.ai/graphql"
            manifestPlaceholders["METABIND_WS_URL"] = "wss://ws-api.metabind.ai"
        }
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_21
        sourceCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        defaultConfig {
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    namespace = "ai.metabind"
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
    dynamicFeatures += setOf(":dynamicfeature")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.startupruntime)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.timber)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    implementation(project(":base-ui"))
    implementation(project(":base-theme"))
    implementation(project(":feature-home"))
}
