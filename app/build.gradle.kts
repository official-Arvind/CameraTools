plugins {
    id("com.android.application")
}

android {
    namespace = "com.jigar.cameratools"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.official_arvind.cameratools"
        minSdk = 29
        targetSdk = 34
        versionCode = 101
        versionName = "1.1.0"
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
