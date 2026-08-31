import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("com.android.library")
}

if (!providers.gradleProperty("consumerBuiltInKotlin").orElse("true").get().toBoolean()) {
    pluginManager.apply("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.seatlayer.consumer.raw"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(
        "io.seatlayer:seatlayer-android:${providers.gradleProperty("seatlayerVersion").get()}",
    )
}

tasks.register("verifyNoComposeDependencies") {
    group = "verification"
    description = "Proves the raw coordinate resolves no Compose runtime artifacts."
    val runtimeClasspath = configurations.named("releaseRuntimeClasspath")
    inputs.files(runtimeClasspath)
    doLast {
        val forbidden = runtimeClasspath.get()
            .incoming
            .resolutionResult
            .allComponents
            .mapNotNull { component -> component.id as? ModuleComponentIdentifier }
            .filter { component ->
                component.group.startsWith("androidx.compose") ||
                    component.module.endsWith("-compose")
            }
            .map { component -> "${component.group}:${component.module}:${component.version}" }
            .sorted()
        check(forbidden.isEmpty()) {
            "Raw seatlayer-android consumer unexpectedly resolved Compose: " +
                forbidden.joinToString()
        }
    }
}
