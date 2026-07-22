plugins {
    id("common-android-library")
}

val vce = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {

    buildFeatures {
        compose = true
    }


}

dependencies {
    implementation(vce.findLibrary("androidx-core-ktx").get())
    implementation(vce.findLibrary("androidx-compose-material").get())
    implementation(vce.findLibrary("androidx-compose-ui").get())
    implementation(vce.findLibrary("androidx-compose-ui-tooling").get())
    implementation(vce.findLibrary("androidx-lifecycle-common-java8").get())
    implementation(vce.findLibrary("androidx-lifecycle-runtime-ktx").get())
    implementation(vce.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    implementation(vce.findLibrary("androidx-navigation-compose").get())
    implementation(vce.findLibrary("timber").get())
}
