package io.seatlayer.android

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A seat chosen after the hold was made. A runtime up to the pinned version
 * treats the live hold as checkout's, so it reports only that hold's lines and
 * refuses to hand off a selection the hold does not cover. Both covers are here.
 */
class SeatLayerPickerPostCheckoutTest {
    @Test
    fun aSeatSelectedAfterTheHoldJoinsTheCartFromTheSelection() {
        val snapshot = requireNotNull(decodeSeatLayerPickerSnapshot(json(SNAPSHOT)))

        assertTrue(snapshot.cartCompletedFromSelection)
        assertEquals(listOf("A-1", "A-2"), snapshot.cartLines.map { it.label })
        assertEquals(2, snapshot.ticketCount)
        assertEquals(55.0, snapshot.cartTotal, 0.0)
        assertEquals("seat-a-2", snapshot.cartLines.last().seatId)
        assertEquals("Stalls", snapshot.cartLines.last().sectionLabel)
    }

    @Test
    fun aCartTheRuntimeAlreadyCoversIsNotSecondGuessed() {
        val snapshot = requireNotNull(
            decodeSeatLayerPickerSnapshot(
                json(
                    """
                    {"schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA","sessionId":"s","revision":1,
                     "event":{"key":"ev","currency":"EUR"},
                     "cart":{"quantity":9,"total":999.0,"items":[{"label":"A-1","unitPrice":30,"quantity":1}]},
                     "selection":{"seats":[{"id":"seat-a-1","label":"A-1"}]}}
                    """.trimIndent(),
                ),
            ),
        )

        assertFalse(snapshot.cartCompletedFromSelection)
        assertEquals(9, snapshot.ticketCount)
        assertEquals(999.0, snapshot.cartTotal, 0.0)
    }

    @Test
    fun aHoldThatNoLongerCoversTheSelectionIsReplacedOnContinue() = runTest {
        var mismatched = false
        val fixture = CheckoutFixture { command, _ ->
            when {
                command == "picker.continue" && !mismatched -> {
                    mismatched = true
                    throw SeatLayerException.Bridge(
                        BridgeErrorDetails(
                            code = SeatLayerPickerErrorCodes.HOLD_SELECTION_MISMATCH,
                            message = "the hold does not cover the selection",
                            retryable = null,
                            conflicts = emptyList(),
                            metadata = null,
                        ),
                    )
                }
                command == "picker.continue" -> json(HANDOFF)
                else -> json("{}")
            }
        }

        val handoff = fixture.controller.checkout()

        assertEquals(
            listOf("picker.continue", "hold", "picker.continue"),
            fixture.commandNames,
        )
        assertEquals("hold-9", handoff.holdId)
        assertNull(fixture.holder.state.value.presentation.lastActionError)
    }

    @Test
    fun anyOtherRefusalIsStillARefusal() = runTest {
        val fixture = CheckoutFixture { _, _ ->
            throw SeatLayerException.Bridge(
                BridgeErrorDetails(
                    code = "sold_out",
                    message = "gone",
                    retryable = null,
                    conflicts = emptyList(),
                    metadata = null,
                ),
            )
        }

        val error = assertThrows(SeatLayerException.Bridge::class.java) {
            kotlinx.coroutines.runBlocking { fixture.controller.checkout() }
        }
        assertEquals("sold_out", error.code)
        assertEquals(listOf("picker.continue"), fixture.commandNames)
    }

    @Test
    fun aHoldLengthTravelsOnlyWhenTheHostNamedOne() = runTest {
        val without = CheckoutFixture { _, _ -> json(HANDOFF) }
        without.controller.checkout()
        assertFalse("ttlMs" in (without.payloads.single() as JsonObject))

        val with = CheckoutFixture { _, _ -> json(HANDOFF) }
        with.controller.checkout(ttlMillis = 90_000)
        assertEquals(90_000, (with.payloads.single() as JsonObject).int("ttlMs"))
    }

    @Test
    fun aHandedOffHoldThatVanishesWithoutExpiringIsASale() {
        val fixture = CheckoutFixture { _, _ -> json(HANDOFF) }
        fixture.holder.setCheckoutHandoff(
            requireNotNull(decodeSeatLayerPickerCheckoutHandoff(json(HANDOFF).jsonObjectValue("handoff"))),
        )

        fixture.holder.acceptSnapshot(json(holdSnapshot(revision = 2, active = false)))

        assertNotNull(fixture.holder.state.value.presentation.bookedHandoff)
    }

    @Test
    fun anExpiryTheBuyerWasToldAboutIsNotASale() {
        val fixture = CheckoutFixture { _, _ -> json(HANDOFF) }
        fixture.holder.setCheckoutHandoff(
            requireNotNull(decodeSeatLayerPickerCheckoutHandoff(json(HANDOFF).jsonObjectValue("handoff"))),
        )

        fixture.holder.acceptHoldExpired()
        fixture.holder.acceptSnapshot(json(holdSnapshot(revision = 2, active = false)))

        assertNull(fixture.holder.state.value.presentation.bookedHandoff)
    }

    private fun JsonElement.jsonObjectValue(name: String): JsonElement? =
        (this as JsonObject)[name]

    private class CheckoutFixture(handler: suspend (String, JsonElement?) -> JsonElement?) {
        val commandNames = mutableListOf<String>()
        val payloads = mutableListOf<JsonElement?>()
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val controller = holder.controller

        init {
            val profile = holder.bridgeProfile
            val bundle = BundleInfo(
                bundle = "test",
                protocolRange = profile.protocolRange,
                capabilities = profile.requiredCapabilities.toList(),
                events = profile.requiredEvents.toList(),
                commands = (profile.requiredCommands + "hold").toList(),
            )
            val transport = SeatLayerPickerCommandTransport { command, payload ->
                commandNames += command
                payloads += payload
                handler(command, payload)
            }
            holder.beginLoading()
            holder.connect(transport, bundle)
            holder.acceptSnapshot(json(holdSnapshot(revision = 1, active = true)))
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
        val SNAPSHOT = """
            {"schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA","sessionId":"s","revision":1,
             "event":{"key":"ev","currency":"EUR"},
             "cart":{"quantity":1,"total":30.0,
                     "items":[{"label":"A-1","objectId":"seat-a-1","unitPrice":30,"quantity":1}]},
             "selection":{"seats":[
                {"id":"seat-a-1","label":"A-1","price":30,"currency":"EUR"},
                {"id":"seat-a-2","label":"A-2","price":25,"currency":"EUR","sectionLabel":"Stalls"}]}}
        """.trimIndent()

        val HANDOFF = """
            {"handoff":{"holdId":"hold-9","expiresAt":1,"currency":"EUR","total":55.0,
                        "lineItems":[{"label":"A-1","unitPrice":30,"quantity":1},
                                     {"label":"A-2","unitPrice":25,"quantity":1}]}}
        """.trimIndent()

        fun holdSnapshot(revision: Int, active: Boolean): String =
            """{"schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA","sessionId":"s","revision":$revision,
                "event":{"key":"ev"},"hold":{"active":$active}}"""

        fun json(raw: String): JsonElement = bridgeJson.parseToJsonElement(raw)
    }
}
