plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mp3tag.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mp3tag.mobile"
        minSdk = 31          // Android 12
        targetSdk = 36
        versionCode = 8
        versionName = "0.4.0"
        base.archivesName.set("mp3tag-mobile-v$versionName")
    }

    // Shared debug keystore so local and CI builds are inter-installable
    // (the same signature lets a GitHub-built APK update the sideloaded app).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Material 3 Expressive (MaterialExpressiveTheme, MotionScheme, LoadingIndicator...)
    implementation("androidx.compose.material3:material3:1.5.0-alpha01")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Tagging engine: MP3 (ID3v1/v2.3/v2.4), FLAC, MP4/M4A, Ogg, WAV (RIFF INFO + ID3), DSF (DSD)
    implementation("net.jthink:jaudiotagger:3.0.1")
}
