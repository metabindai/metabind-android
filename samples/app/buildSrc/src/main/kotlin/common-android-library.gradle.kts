plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}

val vce = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {

    defaultConfig {
        compileSdk = vce.findVersion("android-compile-sdk").get().requiredVersion.toInt()
        minSdk = vce.findVersion("android-min-sdk").get().requiredVersion.toInt()
    }

    buildTypes {
        create("debugRelease") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_21
        sourceCompatibility = JavaVersion.VERSION_21
    }

}

dependencies {
    implementation(vce.findLibrary("androidx-core-ktx").get())
    implementation(vce.findLibrary("androidx-hilt-android").get())
    ksp(vce.findLibrary("androidx-hilt-compiler").get())
    implementation(vce.findLibrary("kotlin-coroutines").get())
    implementation(vce.findLibrary("timber").get())
}
