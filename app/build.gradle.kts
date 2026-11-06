plugins {
    id("com.android.application")
}

android {
    namespace = "com.narezany.calcm3"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.narezany.calcm3"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
