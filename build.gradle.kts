plugins {
    id("com.android.library") version "9.2.1" apply false
    id("com.android.application") version "9.2.1" apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

val expectedWebSdkSha256 =
    "89bc29fbccad5d3c30e52cf5381c974b95ac034b32c28b400248b4ebb4ee22a9"

tasks.register("verifyVendoredWebSdk") {
    group = "verification"
    description = "Verifies the pinned SeatLayer Web SDK asset."
    val asset = layout.projectDirectory.file("seatlayer/src/main/assets/seatlayer.js")
    inputs.file(asset)
    doLast {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(asset.asFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(digest == expectedWebSdkSha256) {
            "Unexpected seatlayer.js checksum: $digest"
        }
    }
}

tasks.register("validate") {
    group = "verification"
    description = "Builds, tests, lints, and validates the SDK and sample app."
    dependsOn(
        ":seatlayer:testDebugUnitTest",
        ":seatlayer:lintRelease",
        ":seatlayer:assembleRelease",
        ":seatlayer:generatePomFileForMavenPublication",
        ":sample:assembleDebug",
        "verifyVendoredWebSdk",
    )
}
