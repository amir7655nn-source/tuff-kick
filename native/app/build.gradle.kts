plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tuff.kick"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tuff.kick.native"
        minSdk = 24
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = System.getenv("APP_VERSION_NAME") ?: "dev"
        buildConfigField("String", "KICK_CLIENT_ID", "\"01M5S8X89RGBSH22VT02MVMCNP\"")
        // the secret comes from the build environment (GitHub Actions secret), never from the repository
        val kickSecret = (System.getenv("KICK_CLIENT_SECRET") ?: "").replace("\"", "").trim()
        buildConfigField("String", "KICK_CLIENT_SECRET", "\"" + kickSecret + "\"")
        buildConfigField("String", "KICK_WORKER_URL", "\"" + (System.getenv("KICK_WORKER_URL") ?: "").trim() + "\"")
    }

    // fixed signing key (from GitHub secrets) so every build is the same app and updates install over each other
    val ksPath = System.getenv("KEYSTORE_PATH") ?: ""
    signingConfigs {
        if (ksPath.isNotEmpty()) {
            create("tuff") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (ksPath.isNotEmpty()) signingConfig = signingConfigs.getByName("tuff")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")
    implementation("io.coil-kt:coil-svg:2.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("com.amazonaws:ivs-player:1.38.0")
}
