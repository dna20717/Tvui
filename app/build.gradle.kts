plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "de.dna.tvui"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.dna.tvui"
        minSdk = 23          // Philips 65PUS6412 läuft mit Android TV 6
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Fester Schlüssel, damit Updates sich über die alte Version installieren lassen.
    signingConfigs {
        create("tvui") {
            storeFile = rootProject.file("signing/tvui.keystore")
            storePassword = "tvuitvui"
            keyAlias = "tvui"
            keyPassword = "tvuitvui"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("tvui")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("tvui")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
}
