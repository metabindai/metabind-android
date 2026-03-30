plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.apollo)
    `maven-publish`
}

android {
    namespace = "com.yapstudios.metabind"
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
    publications {
        register<MavenPublication>("release") {
            groupId = "com.yapstudios"
            artifactId = "metabind-android"
            version = "0.0.4"

            afterEvaluate {
                from(components["default"])
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/yapstudios/bindjs-android-binary")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

apollo {
    service("service") {
        packageName.set("com.yapstudios.metabind")
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
    implementation(libs.apollo.runtime)
    implementation(libs.apollo.cache)
    implementation(libs.apollo.normalized.cache)
    implementation(libs.bindjs)
}