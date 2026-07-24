plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.com.google.devtools.ksp.gradle.plugin)
    implementation(libs.androidx.hilt.gradle.plugin)
    // Javapoet currently required when using Hilt alongside Kotlin Gradle DSL plugins. See https://github.com/google/dagger/issues/3068#issuecomment-999118496
    implementation(libs.square.javapoet)
}

// just a helper to get a syntax similar to the plugins {} block:
fun plugin(id: String, version: String) = "$id:$id.gradle.plugin:$version"
