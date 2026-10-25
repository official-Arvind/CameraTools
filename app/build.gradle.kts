plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.official_arvind.cameratools"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.official_arvind.cameratools"
        minSdk = 29
        targetSdk = 34
        versionCode = 132
        versionName = "1.3.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
