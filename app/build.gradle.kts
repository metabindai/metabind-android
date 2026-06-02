plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "ai.metabind.metabind.sample"
    compileSdk {
        version = release(libs.versions.android.compile.sdk.get().toInt()) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ai.metabind.metabind.sample"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["METABIND_URL"] = "https://api.metabind.ai/graphql"
        manifestPlaceholders["METABIND_WS_URL"] = "wss://ws-api.metabind.ai"

        // Credentials — supply via Gradle properties (e.g. ~/.gradle/gradle.properties
        // or -PmetabindApiKey=...) so secrets stay out of version control.
        manifestPlaceholders["METABIND_API_KEY"] =
            (project.findProperty("metabindApiKey") as String?).orEmpty()
        manifestPlaceholders["METABIND_ORGANIZATION_ID"] =
            (project.findProperty("metabindOrganizationId") as String?).orEmpty()
        manifestPlaceholders["METABIND_PROJECT_ID"] =
            (project.findProperty("metabindProjectId") as String?).orEmpty()
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":metabind"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.android.compose.ui)
    implementation(libs.android.compose.material)
}
