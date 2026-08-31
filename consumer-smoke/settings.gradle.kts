pluginManagement {
    val consumerAgpVersion = providers.gradleProperty("consumerAgpVersion")
        .orElse("9.2.1")
    val consumerKotlinVersion = providers.gradleProperty("consumerKotlinVersion")
        .orElse("2.3.10")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version consumerAgpVersion.get()
        id("org.jetbrains.kotlin.android") version consumerKotlinVersion.get()
        id("org.jetbrains.kotlin.plugin.compose") version consumerKotlinVersion.get()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven {
            url = uri("../build/consumer-smoke-repository")
            content { includeGroup("io.seatlayer") }
        }
        mavenCentral()
    }
}

rootProject.name = "seatlayer-android-consumer-smoke"
include(":raw-consumer")
include(":compose-consumer")
