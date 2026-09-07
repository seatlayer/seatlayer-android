package io.seatlayer.android

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatLayerPickerBridgeContractTest {
    @Test
    fun theFiveAdditiveCapabilitiesAreOptionalNotHandshakeRequirements() {
        val profile = SeatLayerPickerBridgeProfile.create()
        val additive = setOf(
            "seat-view-thumbnail-v1",
            "accessibility-focus-v1",
            "section-access-counts-v1",
            "category-availability-v1",
            "seat-screen-point-v1",
        )

        assertTrue(profile.optionalCapabilities.containsAll(additive))
        additive.forEach { assertFalse(it in profile.requiredCapabilities) }
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
    fun aWithheldCapabilityWithholdsItsSurfaceInSilence() {
        val bare = fixture(capabilities = emptySet(), commands = emptySet())

        assertFalse(bare.controller.supportsAccessibilityFocus)
        assertFalse(bare.controller.supportsSectionAccessCounts)
        assertFalse(bare.controller.supportsSeatViewThumbnail)
        assertFalse(bare.controller.supportsAccessNeeds)

        val full = fixture(
            capabilities = setOf(
                "accessibility-focus-v1",
                "section-access-counts-v1",
                "seat-view-thumbnail-v1",
                "access-needs-v1",
            ),
            commands = emptySet(),
        )
        assertTrue(full.controller.supportsAccessibilityFocus)
        assertTrue(full.controller.supportsSectionAccessCounts)
        assertTrue(full.controller.supportsSeatViewThumbnail)
        assertTrue(full.controller.supportsAccessNeeds)
    }

    @Test
    fun candidatePaintFramingAndBlockedRegionsGateOnTheCommandTableAlone() = runTest {
        val without = fixture(capabilities = emptySet(), commands = emptySet())
        without.controller.setSelectionFocus("seat-1")
        without.controller.setBlockedRegions(listOf(region()))
        assertNull(without.controller.frameSeat("seat-1"))
        assertEquals(emptyList<String>(), without.commands)

        val with = fixture(
            capabilities = emptySet(),
            commands = setOf(
                "picker.setSelectionFocus",
                "picker.setBlockedRegions",
                "picker.frameSeat",
            ),
        ) { command, _ ->
            if (command == "picker.frameSeat") json("""{"dy":42.5,"gestures":{"tx":1}}""") else json("{}")
        }
        with.controller.setSelectionFocus("seat-1")
        with.controller.setBlockedRegions(listOf(region()))
        val frame = with.controller.frameSeat("seat-1", fraction = 0.5)

        assertEquals(
            listOf("picker.setSelectionFocus", "picker.setBlockedRegions", "picker.frameSeat"),
            with.commands,
        )
        assertEquals(42.5, frame?.dy)
        assertEquals(0.5, (with.payloads[2] as JsonObject).double("fraction"))

        // The gestures value is opaque and travels back exactly as it arrived.
        with.controller.frameSeat("seat-1", gestures = frame?.gestures)
        assertEquals(
            json("""{"tx":1}"""),
            (with.payloads[3] as JsonObject)["gestures"],
        )
    }

    @Test
    fun blockedRegionsReplaceTheWholeListAndNullClearsIt() = runTest {
        val fixture = fixture(commands = setOf("picker.setBlockedRegions"))

        fixture.controller.setBlockedRegions(listOf(region(), region(y = 90.0)))
        fixture.controller.setBlockedRegions(null)

        val first = (fixture.payloads[0] as JsonObject).array("rects")
        assertEquals(2, first?.size)
        assertEquals(90.0, (first?.get(1) as JsonObject).double("y"))
        assertTrue((fixture.payloads[1] as JsonObject)["rects"] is kotlinx.serialization.json.JsonNull)
    }

    @Test
    fun theAccessibleTourNeedsBothTheCapabilityAndTheCommand() = runTest {
        val commandOnly = fixture(
            capabilities = emptySet(),
            commands = setOf("picker.focusAccessibilityFilter", "picker.focusNextAccessibleSection"),
        )
        commandOnly.controller.focusAccessibilityFilter(listOf("wheelchair"))
        assertNull(commandOnly.controller.focusNextAccessibleSection())
        assertEquals(emptyList<String>(), commandOnly.commands)

        val capabilityOnly = fixture(
            capabilities = setOf("accessibility-focus-v1"),
            commands = emptySet(),
        )
        capabilityOnly.controller.focusAccessibilityFilter(listOf("wheelchair"))
        assertNull(capabilityOnly.controller.focusNextAccessibleSection())
        assertEquals(emptyList<String>(), capabilityOnly.commands)

        val offered = fixture(
            capabilities = setOf("accessibility-focus-v1"),
            commands = setOf("picker.focusAccessibilityFilter", "picker.focusNextAccessibleSection"),
        ) { command, _ ->
            if (command == "picker.focusNextAccessibleSection") {
                json("""{"step":{"id":"sec-2","label":"Stalls","free":3,"index":1,"total":6}}""")
            } else {
                json("{}")
            }
        }
        offered.controller.focusAccessibilityFilter(listOf("wheelchair"))
        val step = offered.controller.focusNextAccessibleSection()

        assertEquals(
            listOf("picker.focusAccessibilityFilter", "picker.focusNextAccessibleSection"),
            offered.commands,
        )
        assertEquals(SeatLayerPickerAccessibleStep("sec-2", "Stalls", 3, 1, 6), step)
    }

    @Test
    fun aTourThatRunsOutOfSectionsAnswersNothingRatherThanFailing() = runTest {
        val fixture = fixture(
            capabilities = setOf("accessibility-focus-v1"),
            commands = setOf("picker.focusNextAccessibleSection"),
        ) { _, _ -> json("""{"step":null}""") }

        assertNull(fixture.controller.focusNextAccessibleSection())
    }

    @Test
    fun errorCodesStayStringsSoAConflictReasonSurvives() {
        val details = BridgeErrorDetails.decode(
            json("""{"code":"not_enough_together","message":"no"}"""),
        )

        assertEquals("not_enough_together", details.code)
        assertEquals("hold_selection_mismatch", SeatLayerPickerErrorCodes.HOLD_SELECTION_MISMATCH)
        assertEquals("unsupported_command", SeatLayerPickerErrorCodes.UNSUPPORTED_COMMAND)
        assertEquals("bad_payload", SeatLayerPickerErrorCodes.BAD_PAYLOAD)
    }

    private fun region(y: Double = 12.0) =
        SeatLayerPickerBlockedRegion(x = 8.0, y = y, width = 44.0, height = 44.0)

    private fun fixture(
        capabilities: Set<String> = emptySet(),
        commands: Set<String> = emptySet(),
        handler: suspend (String, JsonElement?) -> JsonElement? = { _, _ -> json("{}") },
    ) = ContractFixture(capabilities, commands, handler)

    private class ContractFixture(
        capabilities: Set<String>,
        commands: Set<String>,
        handler: suspend (String, JsonElement?) -> JsonElement?,
    ) {
        val commandNames = mutableListOf<String>()
        val payloads = mutableListOf<JsonElement?>()
        val commands: List<String> get() = commandNames
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val controller = holder.controller

        init {
            val profile = holder.bridgeProfile
            val bundle = BundleInfo(
                bundle = "test",
                protocolRange = profile.protocolRange,
                capabilities = (profile.requiredCapabilities + capabilities).toList(),
                events = profile.requiredEvents.toList(),
                commands = (profile.requiredCommands + commands).toList(),
            )
            val transport = SeatLayerPickerCommandTransport { command, payload ->
                commandNames += command
                payloads += payload
                handler(command, payload)
            }
            holder.beginLoading()
            holder.connect(transport, bundle)
            holder.acceptSnapshot(json(SNAPSHOT))
            holder.markReady(
                ReadyInfo(
                    protocolRevision = 2,
                    mode = EventMode.Live,
                    transport = TransportName.Android,
                    eventKey = "ev",
                ),
            )
        }
    }

    private companion object {
        const val SNAPSHOT =
            """{"schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA","sessionId":"s","revision":1,"event":{"key":"ev"}}"""

        fun json(raw: String): JsonElement = bridgeJson.parseToJsonElement(raw)
    }
}
