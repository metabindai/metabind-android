plugins {
    id("common-android-library")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ai.metabind.data.home"
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.gson)
    implementation(libs.metabind)
    ksp(libs.room.compiler)
}
