
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.rsho.daftarhisabati"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.rsho.daftarhisabati"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
