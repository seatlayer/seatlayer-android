plugins {
    id("com.android.application")
}

android {
    namespace = "io.seatlayer.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.seatlayer.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":seatlayer"))
}
