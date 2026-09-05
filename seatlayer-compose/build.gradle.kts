import java.security.MessageDigest

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "io.seatlayer.android.compose"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        aarMetadata {
            minCompileSdk = 24
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
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

val sdkVersion = providers.gradleProperty("VERSION_NAME").get()

dependencies {
    api(project(":seatlayer"))
    constraints {
        api("io.seatlayer:seatlayer-android:$sdkVersion") {
            version { strictly(sdkVersion) }
            because("SeatLayer core and Compose artifacts publish as one release train")
        }
    }

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    api(composeBom)
    api("androidx.compose.runtime:runtime")
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    api("androidx.lifecycle:lifecycle-runtime:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(
        groupId = "io.seatlayer",
        artifactId = "seatlayer-android-compose",
        version = sdkVersion,
    )

    pom {
        name.set("SeatLayer Jetpack Compose Seating Chart and Seat Picker")
        description.set(
            "Official SeatLayer Jetpack Compose UI for Android seating charts and reserved seating. " +
                "Add a customizable seat picker with native filters, seat details, cart, hold countdown " +
                "and checkout callbacks around the shared venue map.",
        )
        inceptionYear.set("2026")
        url.set("https://docs.seatlayer.io/buyer-sdk/android/")
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
                url.set("https://seatlayer.io/developers/")
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

val verifyPickerLocales = tasks.register("verifyPickerLocales") {
    group = "verification"
    description = "Verifies the generated 37-locale picker catalogue source lock."
    val source = layout.projectDirectory.file(
        "src/main/resources/io/seatlayer/android/compose/locale_strings.json",
    )
    val generated = layout.projectDirectory.file(
        "src/main/kotlin/io/seatlayer/android/compose/SeatLayerPickerLocaleData.kt",
    )
    inputs.files(source, generated)
    doLast {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.asFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        val declared = Regex("SOURCE_SHA256: String = \"([a-f0-9]{64})\"")
            .find(generated.asFile.readText())
            ?.groupValues
            ?.get(1)
        check(declared == digest) {
            "Picker locale catalogue is stale. Run " +
                "scripts/generate-picker-locales.py <source.json> <output.kt>."
        }
        val lineCount = generated.asFile.useLines { lines -> lines.count() }
        check(lineCount <= 800) {
            "Generated picker locale source exceeds the 800-line source limit: $lineCount"
        }
    }
}

val verifyPickerTokens = tasks.register("verifyPickerTokens") {
    group = "verification"
    description = "Verifies the generated native picker design-token source lock."
    val source = layout.projectDirectory.file(
        "src/main/resources/io/seatlayer/android/compose/picker_tokens.json",
    )
    val generated = layout.projectDirectory.file(
        "src/main/kotlin/io/seatlayer/android/compose/SeatLayerPickerTokens.kt",
    )
    inputs.files(source, generated)
    doLast {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.asFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        val declared = Regex("SOURCE_SHA256: String = \"([a-f0-9]{64})\"")
            .find(generated.asFile.readText())
            ?.groupValues
            ?.get(1)
        check(declared == digest) {
            "Picker token catalogue is stale. Run scripts/generate-picker-tokens.py."
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyPickerLocales, verifyPickerTokens)
}
