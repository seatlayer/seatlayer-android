package io.seatlayer.android

import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatLayerPickerNeutralProtocolTest {
    @Test
    fun `frozen protocol lock matches Android picker profile`() {
        val fixtureBytes = requireNotNull(
            javaClass.getResourceAsStream(FIXTURE_RESOURCE),
        ) { "Missing $FIXTURE_RESOURCE" }.use { it.readBytes() }
        assertEquals(FIXTURE_SHA256, fixtureBytes.sha256())

        val contract = bridgeJson.parseToJsonElement(fixtureBytes.decodeToString()).jsonObject
        val range = contract.objectValue("protocolRange")
        val base = SeatLayerPickerBridgeProfile.create(
            enable3D = false,
            enableSeatView = false,
        )
        assertEquals(range.integer("min"), base.protocolRange.min)
        assertEquals(range.integer("max"), base.protocolRange.max)
        assertEquals(contract.string("snapshotSchema"), SEATLAYER_PICKER_SNAPSHOT_SCHEMA)
        assertEquals(contract.stringSet("requiredCapabilities"), base.requiredCapabilities)
        assertEquals(contract.stringSet("requiredCommands"), base.requiredCommands)
        assertEquals(contract.stringSet("requiredEvents"), base.requiredEvents)
        assertEquals(contract.stringSet("optionalCapabilities"), base.optionalCapabilities)

        val enabled = SeatLayerPickerBridgeProfile.create()
        val conditionals = contract.objectValue("conditionalRequirements")
        assertConditionalRequirements(conditionals.objectValue("enable3D"), base, enabled)
        assertConditionalRequirements(
            conditionals.objectValue("enableSeatView"),
            base,
            enabled,
        )
        assertAndroidInitShape(enabled)
    }

    private fun assertConditionalRequirements(
        conditional: JsonObject,
        base: SeatLayerPickerBridgeProfile,
        enabled: SeatLayerPickerBridgeProfile,
    ) {
        assertTrue(conditional.boolean("default"))
        assertTrue(
            enabled.requiredCapabilities.containsAll(conditional.stringSet("capabilities")),
        )
        assertTrue(enabled.requiredCommands.containsAll(conditional.stringSet("commands")))
        assertTrue(
            base.requiredCapabilities.intersect(conditional.stringSet("capabilities")).isEmpty(),
        )
        assertTrue(base.requiredCommands.intersect(conditional.stringSet("commands")).isEmpty())
    }

    private fun assertAndroidInitShape(profile: SeatLayerPickerBridgeProfile) {
        val bundle = BundleInfo(
            bundle = "fixture",
            protocolRange = profile.protocolRange,
            capabilities = (profile.requiredCapabilities + profile.optionalCapabilities).toList(),
            events = (profile.requiredEvents + "seatView.changed").toList(),
            commands = profile.requiredCommands.toList(),
        )
        val payload = profile.initPayload(
            configuration = SeatLayerConfiguration(event = "fixture-event"),
            bundle = bundle,
        )

        assertEquals("android", payload.objectValue("host").string("platform"))
        assertEquals("picker", payload.objectValue("surface").string("kind"))
        assertEquals(1, payload.objectValue("surface").integer("stateContract"))
        assertEquals("native", payload.objectValue("surface").string("chromeOwner"))
        assertEquals("native", payload.objectValue("chrome").string("owner"))
        assertEquals(false, payload.objectValue("chrome").boolean("seatTooltip"))
        assertEquals(false, payload.objectValue("chrome").boolean("testModeIndicator"))
        assertEquals(false, payload.objectValue("chrome").boolean("attribution"))
        assertEquals(
            profile.requiredCapabilities,
            payload.objectValue("requirements").stringSet("capabilities"),
        )
    }

    private fun JsonObject.objectValue(key: String): JsonObject =
        requireNotNull(this[key] as? JsonObject) { "Missing fixture object $key" }

    private fun JsonObject.required(key: String): JsonElement =
        requireNotNull(this[key]) { "Missing fixture field $key" }

    private fun JsonObject.string(key: String): String = required(key).jsonPrimitive.content

    private fun JsonObject.integer(key: String): Int = required(key).jsonPrimitive.int

    private fun JsonObject.boolean(key: String): Boolean = required(key).jsonPrimitive.boolean

    private fun JsonObject.stringSet(key: String): Set<String> =
        (required(key) as JsonArray).mapTo(linkedSetOf()) { it.jsonPrimitive.content }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val FIXTURE_RESOURCE =
            "/io/seatlayer/android/seatlayer-picker-protocol-v2.json"
        const val FIXTURE_SHA256 =
            "cb0e0ae7c14a52b4af9e8420b1a1921b977e284753ef6c0ccc31408fc5c40ffe"
    }
}
