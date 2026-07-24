plugins {
    id("com.android.dynamic-feature")
}
android {
    namespace = "ai.metabind.dynamicfeature"

    defaultConfig {
        compileSdk = libs.versions.android.compile.sdk.get().toInt()
        minSdk = libs.versions.android.min.sdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        create("debugRelease") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
    }
}

dependencies {
    implementation(project(":app"))
    implementation(libs.androidx.core.ktx)
}