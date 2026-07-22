plugins {
    id("common-feature")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "ai.metabind.ui.theme"

}

dependencies {
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.core.splashscreen)
}
