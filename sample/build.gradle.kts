import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

private fun loadLocalEnvironment(file: File?): Map<String, String> {
    if (file?.isFile != true) return emptyMap()
    return file.useLines { lines ->
        lines.mapNotNull { source ->
            val line = source.trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
            val separator = line.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val key = line.substring(0, separator).removePrefix("export ").trim()
            val value = line.substring(separator + 1).trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
            key.takeIf { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }
                ?.let { it to value }
        }.toMap()
    }
}

private fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val androidLocalProperties = Properties().apply {
    rootProject.file("local.properties").takeIf(File::isFile)?.inputStream()?.use { input ->
        load(input)
    }
}
val configuredDesiPassEnvFile =
    providers.environmentVariable("DESIPASS_ENV_FILE").orNull
        ?: androidLocalProperties.getProperty("desipass.envFile")
val desiPassEnvironment = loadLocalEnvironment(
    configuredDesiPassEnvFile?.let(::file)
        ?: file(".env.local").takeIf(File::isFile),
)
val desiPassGraphqlUrl =
    desiPassEnvironment["DESIPASS_GRAPHQL_URL"]
        ?: desiPassEnvironment["EXPO_PUBLIC_DESIPASS_GRAPHQL_URL"]
        ?: ""
val desiPassApiKey =
    desiPassEnvironment["DESIPASS_API_KEY"]
        ?: desiPassEnvironment["EXPO_PUBLIC_DESIPASS_API_KEY"]
        ?: ""

android {
    namespace = "io.seatlayer.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.seatlayer.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = providers.gradleProperty("VERSION_NAME").get()
        buildConfigField("String", "DESIPASS_GRAPHQL_URL", "\"\"")
        buildConfigField("String", "DESIPASS_API_KEY", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "DESIPASS_GRAPHQL_URL",
                desiPassGraphqlUrl.asBuildConfigString(),
            )
            buildConfigField(
                "String",
                "DESIPASS_API_KEY",
                desiPassApiKey.asBuildConfigString(),
            )
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":seatlayer"))
    implementation(project(":seatlayer-compose"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
