plugins {
    id("com.android.application")
}

android {
    namespace = "com.ira.controller"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ira.controller"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation("androidx.activity:activity:1.13.0")
}
