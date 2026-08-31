package io.seatlayer.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatLayerPickerProtocolTest {
    @Test
    fun pickerProfileDoesNotChangeRawProtocolOneInit() {
        val configuration = SeatLayerConfiguration(
            event = "ev_android",
            showsWebSeatTooltip = true,
        )
        val raw = configuration.initPayload()
        assertEquals(1, raw.objectValue("protocol")?.int("min"))
        assertEquals(1, raw.objectValue("protocol")?.int("max"))
        assertEquals(true, raw.objectValue("chrome")?.boolean("seatTooltip"))
        assertFalse("surface" in raw)

        val profile = SeatLayerPickerBridgeProfile.create()
        val picker = profile.initPayload(configuration, completeBundle(profile))
        assertEquals(2, picker.objectValue("protocol")?.int("min"))
        assertEquals(2, picker.objectValue("protocol")?.int("max"))
        assertEquals("picker", picker.objectValue("surface")?.string("kind"))
        assertEquals(1, picker.objectValue("surface")?.int("stateContract"))
        assertEquals("native", picker.objectValue("surface")?.string("chromeOwner"))
        assertEquals("native", picker.objectValue("chrome")?.string("owner"))
        assertEquals(false, picker.objectValue("chrome")?.boolean("seatTooltip"))
        assertEquals(false, picker.objectValue("chrome")?.boolean("attribution"))
        assertEquals(false, picker.objectValue("chrome")?.boolean("seatViewTitle"))
        assertTrue(
            picker.objectValue("requirements")
                ?.array("capabilities")
                .orEmpty()
                .contains(JsonPrimitive("picker-session-v2")),
        )
    }

    @Test
    fun profileFailsClosedWhenRequiredContractEntryIsMissing() {
        val profile = SeatLayerPickerBridgeProfile.create()
        val incomplete = completeBundle(profile).copy(
            commands = profile.requiredCommands.filterNot { it == "picker.continue" },
        )

        val error = assertThrows(SeatLayerException.Incompatible::class.java) {
            profile.validate(incomplete)
        }
        assertTrue(error.message.orEmpty().contains("picker.continue"))
    }

    @Test
    fun optionalCapabilitiesAreNotRequiredAtHandshake() {
        val profile = SeatLayerPickerBridgeProfile.create(
            enable3D = false,
            enableSeatView = false,
        )
        assertFalse("venue-3d-v1" in profile.requiredCapabilities)
        assertFalse("picker.setBuyerView" in profile.requiredCommands)
        assertFalse("seat-view-v1" in profile.requiredCapabilities)
        assertFalse("picker.openSeatView" in profile.requiredCommands)
        profile.validate(
            BundleInfo(
                bundle = "test",
                protocolRange = profile.protocolRange,
                capabilities = profile.requiredCapabilities.toList(),
                events = profile.requiredEvents.toList(),
                commands = profile.requiredCommands.toList(),
            ),
        )
    }

    @Test
    fun enabledImmersiveFeaturesAreConditionalHandshakeRequirements() {
        val profile = SeatLayerPickerBridgeProfile.create()
        assertTrue("venue-3d-v1" in profile.requiredCapabilities)
        assertTrue("picker.setBuyerView" in profile.requiredCommands)
        assertTrue("seat-view-v1" in profile.requiredCapabilities)
        assertTrue("picker.openSeatView" in profile.requiredCommands)
    }

    private fun completeBundle(profile: SeatLayerPickerBridgeProfile): BundleInfo =
        BundleInfo(
            bundle = "test",
            protocolRange = ProtocolRange(2, 2),
            capabilities = (
                profile.requiredCapabilities + profile.optionalCapabilities +
                    "native-seat-view-chrome-v1"
                ).toList(),
            events = (profile.requiredEvents + "seatView.changed").toList(),
            commands = (
                profile.requiredCommands + setOf(
                    "picker.setViewportInsets",
                    "picker.refreshAvailability",
                    "picker.holdSelection",
                )
                ).toList(),
        )
}
