plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.calltimer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.calltimer.app"
        // No RoleManager/InCallService dependency in this version, so we can
        // support a much wider range of devices than the earlier design.
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-callguard"
    }

    // Fixed debug-signing key, committed at keystore/debug.keystore, so every
    // build - yours locally, GitHub Actions', anyone else's - produces an
    // APK with the SAME signature. Without this, each machine/CI run falls
    // back to its own auto-generated ~/.android/debug.keystore, and Android
    // refuses to install a differently-signed APK as an "update" to an
    // already-installed app - you'd have to uninstall the old one every
    // single time. This is a standard, low-stakes debug key (not a secret -
    // debug keystores are never used for a real Play Store release), so
    // there's no harm in it being committed to the repo.
    signingConfigs {
        create("debugFixed") {
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debugFixed")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
