plugins {
    id("common-feature")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "ai.metabind.ui"
}

dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)

    implementation(project(":base-theme"))
}
