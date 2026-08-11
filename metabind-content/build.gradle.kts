plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.apollo)
    `maven-publish`
}

android {
    namespace = "ai.metabind.metabind"
    compileSdk {
        version = release(libs.versions.android.compile.sdk.get().toInt()) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
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

    publishing {
        multipleVariants {
            includeBuildTypeValues("release", "debug")
        }
    }
}

publishing {
    // group, version and the GitHub Packages repository are configured once in the
    // root build.gradle.kts. Only the artifactId differs per module.
    publications {
        register<MavenPublication>("release") {
            artifactId = "metabind-content-android"

            pom {
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }

            afterEvaluate {
                from(components["default"])
            }
        }
    }
}

apollo {
    service("service") {
        packageName.set("ai.metabind.metabind")
        generateFragmentImplementations.set(true)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.android.compose.ui)
    implementation(libs.android.compose.material)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.gson)
    // `api`, not `implementation`: every read on ComponentRepository hands back an
    // Apollo-generated fragment (PackageFields, ContentFields, …) and those implement
    // `com.apollographql.apollo.api.Fragment.Data`. As `implementation` the supertype is
    // off a consumer's compile classpath, so merely touching a return value fails with
    // "Cannot access 'Fragment.Data' … check your module classpath" and every consumer
    // has to re-declare Apollo at a version that matches ours.
    api(libs.apollo.runtime)
    implementation(libs.apollo.cache)
    implementation(libs.apollo.normalized.cache)
    api(libs.bindjs)
}