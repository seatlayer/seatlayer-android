plugins {
    id("com.android.library")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "io.seatlayer.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        aarMetadata {
            minCompileSdk = 24
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnit()
        }
    }
}

dependencies {
    api("androidx.webkit:webkit:1.16.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(
        groupId = "io.seatlayer",
        artifactId = "seatlayer-android",
        version = providers.gradleProperty("VERSION_NAME").get(),
    )

    pom {
        name.set("SeatLayer Android SDK")
        description.set(
            "Official native Kotlin SDK for interactive SeatLayer reserved-seating maps.",
        )
        inceptionYear.set("2026")
        url.set("https://github.com/seatlayer/seatlayer-android")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("seatlayer")
                name.set("SeatLayer")
                url.set("https://seatlayer.io")
            }
        }
        scm {
            url.set("https://github.com/seatlayer/seatlayer-android")
            connection.set("scm:git:git://github.com/seatlayer/seatlayer-android.git")
            developerConnection.set(
                "scm:git:ssh://git@github.com/seatlayer/seatlayer-android.git",
            )
        }
    }
}
