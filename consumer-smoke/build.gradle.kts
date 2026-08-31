plugins {
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}

tasks.register("verifyConsumers") {
    group = "verification"
    description = "Compiles clean coordinate consumers and verifies the raw dependency boundary."
    dependsOn(
        ":raw-consumer:assembleRelease",
        ":raw-consumer:verifyNoComposeDependencies",
        ":compose-consumer:assembleRelease",
    )
}
