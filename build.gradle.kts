import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.GradleBuild

plugins {
    id("com.android.library") version "9.2.1" apply false
    id("com.android.application") version "9.2.1" apply false
    id("com.android.test") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
    id("io.github.takahirom.roborazzi") version "1.73.0" apply false
}

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "consumerSmoke"
                    url = rootProject.layout.buildDirectory
                        .dir("consumer-smoke-repository")
                        .get()
                        .asFile
                        .toURI()
                }
            }
        }
    }
}

val verifyKotlinSourceLimits = tasks.register("verifyKotlinSourceLimits") {
    group = "verification"
    description = "Rejects Android Kotlin source files over the 800-line project limit."
    val sources = fileTree(projectDir) {
        include(
            "seatlayer/src/**/*.kt",
            "seatlayer-compose/src/**/*.kt",
            "sample/src/**/*.kt",
            "benchmark/src/**/*.kt",
            "consumer-smoke/*/src/**/*.kt",
        )
        exclude("**/build/**")
    }
    inputs.files(sources)
    doLast {
        val oversized = sources.files
            .map { source -> source to source.useLines { lines -> lines.count() } }
            .filter { (_, lines) -> lines > 800 }
            .sortedBy { (source, _) -> source.path }
        check(oversized.isEmpty()) {
            oversized.joinToString(
                prefix = "Kotlin source exceeds 800 lines:\n",
                separator = "\n",
            ) { (source, lines) -> "${source.relativeTo(projectDir)}: $lines" }
        }
    }
}

val verifyPublicationMetadata = tasks.register("verifyPublicationMetadata") {
    group = "verification"
    description = "Verifies aligned core and Compose Maven publication metadata."
    dependsOn(
        ":seatlayer:generatePomFileForMavenPublication",
        ":seatlayer-compose:generatePomFileForMavenPublication",
    )
    val version = providers.gradleProperty("VERSION_NAME")
    val corePom = project(":seatlayer").layout.buildDirectory.file(
        "publications/maven/pom-default.xml",
    )
    val composePom = project(":seatlayer-compose").layout.buildDirectory.file(
        "publications/maven/pom-default.xml",
    )
    inputs.files(corePom, composePom)
    inputs.property("version", version)
    doLast {
        val expectedVersion = version.get()
        val core = corePom.get().asFile.readText()
        val compose = composePom.get().asFile.readText()
        check("<artifactId>seatlayer-android</artifactId>" in core)
        check("<version>$expectedVersion</version>" in core)
        check(
            Regex(
                "<artifactId>kotlinx-serialization-json</artifactId>\\s*" +
                    "<version>[^<]+</version>\\s*" +
                    "<scope>compile</scope>",
            ).containsMatchIn(core),
        ) {
            "Core publication must expose kotlinx-serialization-json at compile scope"
        }
        check("<artifactId>seatlayer-android-compose</artifactId>" in compose)
        check(
            Regex(
                "<artifactId>seatlayer-android</artifactId>\\s*" +
                    "<version>${Regex.escape(expectedVersion)}</version>",
            ).containsMatchIn(compose),
        ) {
            "Compose publication must depend on seatlayer-android:$expectedVersion"
        }
    }
}

val verifyPublicApi = tasks.register<Exec>("verifyPublicApi") {
    group = "verification"
    description = "Verifies both public JVM API dumps and the raw 0.2.x ABI surface."
    dependsOn(
        ":seatlayer:assembleRelease",
        ":seatlayer-compose:assembleRelease",
    )
    inputs.files(
        layout.projectDirectory.file("api/seatlayer-android.api"),
        layout.projectDirectory.file("api/seatlayer-android-compose.api"),
        layout.projectDirectory.file("api/seatlayer-android-0.2.0.api"),
        layout.projectDirectory.file("api/seatlayer-android-0.2.0.classes"),
    )
    outputs.upToDateWhen { false }
    commandLine("bash", "scripts/verify-public-api.sh", "--check")
}

val verifyExternalConsumers = tasks.register<GradleBuild>("verifyExternalConsumers") {
    group = "verification"
    description = "Builds clean raw and Compose consumers from temporary Maven coordinates."
    dependsOn(
        ":seatlayer:publishMavenPublicationToConsumerSmokeRepository",
        ":seatlayer-compose:publishMavenPublicationToConsumerSmokeRepository",
    )
    val sdkVersion = providers.gradleProperty("VERSION_NAME")
    val consumerProperties = layout.projectDirectory.file("consumer-smoke/gradle.properties")
    inputs.file(consumerProperties)
    inputs.property("sdkVersion", sdkVersion)
    dir = file("consumer-smoke")
    tasks = listOf("verifyConsumers")
    doFirst {
        val properties = java.util.Properties().apply {
            consumerProperties.asFile.inputStream().use(::load)
        }
        check(properties.getProperty("seatlayerVersion") == sdkVersion.get()) {
            "consumer-smoke seatlayerVersion must match VERSION_NAME=${sdkVersion.get()}"
        }
    }
}

val verifyOldestExternalConsumers = tasks.register<Exec>("verifyOldestExternalConsumers") {
    group = "verification"
    description =
        "Builds raw and Compose coordinate consumers on the oldest supported toolchain."
    dependsOn(
        ":seatlayer:publishMavenPublicationToConsumerSmokeRepository",
        ":seatlayer-compose:publishMavenPublicationToConsumerSmokeRepository",
    )
    mustRunAfter(verifyExternalConsumers)
    inputs.files(
        layout.projectDirectory.file("scripts/verify-oldest-external-consumers.sh"),
        fileTree("consumer-smoke") {
            exclude("**/build/**", ".gradle/**")
        },
    )
    inputs.property("sdkVersion", providers.gradleProperty("VERSION_NAME"))
    outputs.upToDateWhen { false }
    commandLine("bash", "scripts/verify-oldest-external-consumers.sh")
}

val verifyPickerGenerators = tasks.register<Exec>("verifyPickerGenerators") {
    group = "verification"
    description = "Re-runs the design generators in --check mode; fails on a stale generated file."
    inputs.files(
        fileTree("tool") { include("*.mjs") },
        fileTree("Design") { include("*.json", "*.md") },
        fileTree("seatlayer-compose/src/main/kotlin/io/seatlayer/android/compose") {
            include("*.g.kt")
        },
    )
    outputs.upToDateWhen { false }
    commandLine(
        "bash",
        "-c",
        "node tool/generate-picker-tokens.mjs --check && " +
            "node tool/generate-picker-locales.mjs --check",
    )
}

val verifyPublicRepositoryHygiene = tasks.register<Exec>("verifyPublicRepositoryHygiene") {
    group = "verification"
    description = "Rejects internal process artifacts, unapproved media, local paths, and credentials."
    inputs.file(layout.projectDirectory.file("scripts/check-public-repository.sh"))
    outputs.upToDateWhen { false }
    commandLine("bash", "scripts/check-public-repository.sh")
}

// The release gate is split so a push does not pay for the whole thing.
//
// `checkFast` is what every push and pull request runs: unit and screenshot
// tests, the design lock, the generators in --check mode, the public API dumps, the
// hygiene script and the source-line limit. It answers "is this change
// correct?" and it is the lane that must stay well inside a CI job's time. It
// is deliberately not called `check`: an unqualified `./gradlew check` would
// also match every subproject's own `check`, and AGP hangs lint off that.
//
// `validateRelease` is what a tag runs: lint, both Maven publications, the
// benchmark and sample assemblies, and the external consumer builds on both
// supported toolchains. It answers "does this release ship?" — the questions
// that only matter when an artifact is actually going out, and the ones that
// cost the most minutes.
//
// `validate` remains the union of the two, so every task that was in the gate
// before the split is still in it and one command still runs everything.
val fastCheck = tasks.register("checkFast") {
    group = "verification"
    description = "Correctness gate for every push: tests, design lock, API dumps, hygiene."
    dependsOn(
        ":seatlayer:testDebugUnitTest",
        ":seatlayer-compose:testDebugUnitTest",
        ":seatlayer-compose:verifyRoborazziDebug",
        ":seatlayer-compose:verifyPickerDesign",
        verifyPickerGenerators,
        verifyPublicRepositoryHygiene,
        verifyKotlinSourceLimits,
        verifyPublicApi,
    )
}

val validateRelease = tasks.register("validateRelease") {
    group = "verification"
    description = "Release gate: lint, publications, benchmark and sample builds, consumer smoke."
    dependsOn(
        ":seatlayer:lintRelease",
        ":seatlayer:assembleRelease",
        ":seatlayer:generatePomFileForMavenPublication",
        ":seatlayer-compose:lintRelease",
        ":seatlayer-compose:assembleRelease",
        ":seatlayer-compose:assembleDebugAndroidTest",
        ":seatlayer-compose:generatePomFileForMavenPublication",
        ":benchmark:assembleBenchmark",
        ":sample:lintRelease",
        ":sample:assembleRelease",
        ":sample:assembleBenchmark",
        verifyPublicationMetadata,
        verifyExternalConsumers,
        verifyOldestExternalConsumers,
    )
}

tasks.register("validate") {
    group = "verification"
    description = "Builds, tests, lints, and validates both SDK artifacts and the sample app."
    dependsOn(fastCheck, validateRelease)
}
