plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

android {
    namespace = "ai.metabind.mcpappshost"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions { unitTests.isReturnDefaultValues = true }

    publishing {
        multipleVariants {
            includeBuildTypeValues("release", "debug")
        }
    }
}

publishing {
    // group, version and the GitHub Packages repository come from the root build.
    publications {
        register<MavenPublication>("release") {
            artifactId = "mcpappshost-android"

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

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    testImplementation("junit:junit:4.13.2")
    // Match OkHttp selected transitively by the current BindJS dependency.
    testImplementation("com.squareup.okhttp3:mockwebserver:5.0.0-alpha.14")
    testImplementation("com.squareup.okhttp3:okhttp-tls:5.0.0-alpha.14")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
