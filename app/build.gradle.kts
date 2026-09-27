plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wallisland.walllock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wallisland.walllock"
        minSdk = 26
        targetSdk = 34
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }

    signingConfigs {
        // One fixed key so each build installs over the previous one. It lives in the repo on purpose:
        // this is a sideloaded hobby app. Move it to a CI secret if the app is ever distributed more widely.
        create("walllock") {
            storeFile = file("walllock.keystore")
            storePassword = "walllock"
            keyAlias = "walllock"
            keyPassword = "walllock"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("walllock")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("walllock")
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
        lintConfig = file("lint.xml")
        abortOnError = true
        warningsAsErrors = false
    }
}
