plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Every commit is a new version: the commit count (CI checks out the full history). A phone only
// takes an update with a higher versionCode, so this only ever goes up.
val commitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

// Releases are signed with the one key in CI's secrets (see README): phones refuse an update signed
// with any other. Without it (a local build), release builds fall back to the debug key.
val signingKeystore: String? = System.getenv("SIGNING_KEYSTORE_FILE")

android {
    namespace = "com.scanner.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scanner.app"
        minSdk = 26
        targetSdk = 35
        versionCode = commitCount
        versionName = "1.0.$commitCount"
    }

    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "release"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ML Kit Document Scanner — on-device capture, edge detection, crop & filters
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // HTTP client for the optional calendar upload (multipart + TLS config)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // On-device OCR + date extraction (text recognition is bundled/offline; entity extraction
    // downloads a small language model on first use). No backend.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:entity-extraction:16.0.0-beta6")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
