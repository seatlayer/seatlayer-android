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

val verifyPickerDesign = tasks.register("verifyPickerDesign") {
    group = "verification"
    description = "Verifies Design/, source-lock.json and the generated Kotlin agree."
    val designDirectory = rootProject.layout.projectDirectory.dir("Design")
    val generatedDirectory = layout.projectDirectory.dir(
        "src/main/kotlin/io/seatlayer/android/compose",
    )
    val locked = listOf(
        "tokens.json" to "tokensSha256",
        "locale_strings.json" to "localeStringsSha256",
        "components.md" to "componentsSha256",
        "picker-spec.md" to "pickerSpecSha256",
    )
    val generated = listOf(
        "SeatLayerPickerTokens.g.kt",
        "SeatLayerPickerSizeTokens.g.kt",
        "SeatLayerPickerStringTokens.g.kt",
        "SeatLayerPickerStrings.g.kt",
        "SeatLayerPickerLocaleData.g.kt",
    )
    inputs.files(
        locked.map { (file, _) -> designDirectory.file(file) } +
            designDirectory.file("source-lock.json") +
            generated.map(generatedDirectory::file),
    )
    doLast {
        fun digest(file: java.io.File): String =
            MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }

        val lock = designDirectory.file("source-lock.json").asFile.readText()
        val digests = locked.associate { (file, field) ->
            val actual = digest(designDirectory.file(file).asFile)
            val declared = Regex("\"$field\"\\s*:\\s*\"([a-f0-9]{64})\"")
                .find(lock)
                ?.groupValues
                ?.get(1)
            check(declared == actual) {
                "Design/$file does not match Design/source-lock.json.$field. " +
                    "Re-copy the design source, then run the generators."
            }
            file to actual
        }

        // Every generated file repeats the three digests in its header, so a
        // regeneration that was never run cannot pass as a fresh one.
        val expected = listOf(
            "tokens.json" to "tokens.json         SHA-256: ",
            "locale_strings.json" to "locale_strings.json SHA-256: ",
            "components.md" to "components.md       SHA-256: ",
            "picker-spec.md" to "picker-spec.md      SHA-256: ",
        )
        generated.forEach { name ->
            val header = generatedDirectory.file(name).asFile.readText().take(1_024)
            expected.forEach { (file, marker) ->
                check("$marker${digests.getValue(file)}" in header) {
                    "$name was generated from a different design source. " +
                        "Run node tool/generate-picker-tokens.mjs and " +
                        "node tool/generate-picker-locales.mjs."
                }
            }
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyPickerDesign)
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "seatlayer.designDirectory",
        rootProject.layout.projectDirectory.dir("design").asFile.absolutePath,
    )
}
