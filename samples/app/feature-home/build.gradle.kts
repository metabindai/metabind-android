plugins {
    id("common-feature")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "ai.metabind.feature.home"
}

dependencies {
    implementation(project(":base-theme"))
    implementation(project(":base-ui"))
    implementation(project(":data-home"))

    implementation(libs.guava.android)
    implementation(libs.accompanist.permissions)

    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.camera.mlkit.vision)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.hilt.android)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.metabind)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.javascriptengine)
    implementation(libs.kotlin.coroutines.guava)
}
