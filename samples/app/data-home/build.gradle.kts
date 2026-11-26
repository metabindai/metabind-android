plugins {
    id("common-android-library")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ai.metabind.data.home"
}

dependencies {
    api("net.openid:appauth:0.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.16.1")
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.gson)
    implementation(libs.metabind)
    ksp(libs.room.compiler)
}
