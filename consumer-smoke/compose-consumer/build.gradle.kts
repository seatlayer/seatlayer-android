plugins {
    id("com.android.library")
}

if (!providers.gradleProperty("consumerBuiltInKotlin").orElse("true").get().toBoolean()) {
    pluginManager.apply("org.jetbrains.kotlin.android")
}
pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

android {
    namespace = "io.seatlayer.consumer.compose"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val seatlayerVersion = providers.gradleProperty("seatlayerVersion").get()
    implementation("io.seatlayer:seatlayer-android:$seatlayerVersion")
    implementation("io.seatlayer:seatlayer-android-compose:$seatlayerVersion")
}
