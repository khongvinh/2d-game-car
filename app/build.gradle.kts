plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.khongvinh.carsurvival"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.khongvinh.carsurvival"
        minSdk = 24
        targetSdk = 35
        versionCode = 24
        versionName = "7.6"
    }

    signingConfigs {
        // Fixed debug key committed to the repo so every CI build can be
        // installed over the previous one without uninstalling.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
