package io.seatlayer.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who owns a hold, and what the native chrome may do to it.
 *
 * Two owners, and they behave differently: a hold this picker made carries the
 * countdown, may be extended and is released with the seats it holds; a hold
 * handed in by the host is never released or mutated by native controls, and
 * the buyer's time is still running whoever holds it.
 */
class SeatLayerPickerHoldOwnershipTest {
    @Test
    fun aHostOwnedHoldIsNeverReleasedByTheNativeChrome() = runTest {
        val holder = holderWith(hold = """{"active":true,"expiresAt":99,"ownership":"host"}""")

        assertFalse(holder.controller.releasePickerOwnedHold())
    }

    @Test
    fun aPickerOwnedHoldIsGivenBackWhenTheBuyerLeaves() = runTest {
        var aborted = false
        val holder = holderWith(
            hold = """{"active":true,"expiresAt":99,"ownership":"picker"}""",
            transport = { command, _ ->
                if (command == "picker.abort") aborted = true
                null
            },
        )

        assertTrue(holder.controller.releasePickerOwnedHold())
        assertTrue(aborted)
    }

    @Test
    fun anInactiveHoldIsNothingToGiveBack() = runTest {
        val holder = holderWith(hold = """{"active":false,"ownership":"picker"}""")

        assertFalse(holder.controller.releasePickerOwnedHold())
    }

    @Test
    fun theHoldIdCrossesOnlyAtTheCheckoutHandoff() {
        val snapshot = requireNotNull(
            decodeSeatLayerPickerSnapshot(
                bridgeJson.parseToJsonElement(
                    snapshotJson(
                        """{"active":true,"expiresAt":99,"ownership":"picker","holdId":"leaked"}""",
                    ),
                ),
            ),
        )

        // An ordinary snapshot describes the hold without ever naming it. Even
        // a runtime that puts an id on the wire finds nowhere to put it: the id
        // is a booking capability and stays off the device until checkout.
        assertEquals(
            SeatLayerPickerHold(active = true, expiresAt = 99.0, owner = "picker"),
            snapshot.hold,
        )

        val handoff = requireNotNull(
            decodeSeatLayerPickerCheckoutHandoff(
                bridgeJson.parseToJsonElement(
                    """{"holdId":"h1","expiresAt":99,"lineItems":[]}""",
                ),
            ),
        )
        assertEquals("h1", handoff.holdId)
    }

    @Test
    fun aHandoffTheHostRefusesIsGivenBackRatherThanStranded() = runTest {
        var rejected: String? = null
        val holder = holderWith(
            hold = """{"active":true,"expiresAt":99,"ownership":"picker"}""",
            transport = { command, payload ->
                if (command == "picker.rejectHandoff") {
                    rejected = payload?.let { obj ->
                        (obj as? kotlinx.serialization.json.JsonObject)
                            ?.get("holdId")
                            ?.let { value ->
                                (value as? kotlinx.serialization.json.JsonPrimitive)?.content
                            }
                    }
                }
                null
            },
        )

        holder.controller.rejectCheckoutHandoff(
            SeatLayerPickerCheckoutHandoff(
                holdId = "h1",
                expiresAt = 99.0,
                currency = "USD",
                lineItems = emptyList(),
                total = 0.0,
            ),
        )

        assertEquals("h1", rejected)
    }

    @Test
    fun aHoldPresentOnArrivalIsAnsweredAndEverySeatAfterItIsAsked() {
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val arrived = requireNotNull(
            decodeSeatLayerPickerSnapshot(
                bridgeJson.parseToJsonElement(
                    snapshotJson(
                        hold = """{"active":true,"expiresAt":99,"ownership":"picker"}""",
                        revision = 1,
                        seats = """{"id":"s1","label":"A-1"}""",
                    ),
                ),
            ),
        )
        assertTrue(holder.acceptSnapshot(arrived))
        // Adopted: the seat was answered before this screen existed.
        assertNull(holder.state.value.presentation.candidateSeat)

        val added = requireNotNull(
            decodeSeatLayerPickerSnapshot(
                bridgeJson.parseToJsonElement(
                    snapshotJson(
                        hold = """{"active":true,"expiresAt":99,"ownership":"picker"}""",
                        revision = 2,
                        seats = """{"id":"s1","label":"A-1"},{"id":"s2","label":"A-2"}""",
                    ),
                ),
            ),
        )
        assertTrue(holder.acceptSnapshot(added))
        // A live hold never silences the card: every seat tapped after arrival
        // is asked about, including one that joins a hold already running.
        assertEquals("A-2", holder.state.value.presentation.candidateSeat?.label)
    }

    private fun holderWith(
        hold: String,
        transport: (
            String,
            kotlinx.serialization.json.JsonElement?,
        ) -> kotlinx.serialization.json.JsonElement? = { _, _ -> null },
    ): SeatLayerPickerStateHolder {
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val profile = holder.bridgeProfile
        holder.beginLoading()
        holder.connect(
            SeatLayerPickerCommandTransport { command, payload -> transport(command, payload) },
            BundleInfo(
                bundle = "test",
                protocolRange = profile.protocolRange,
                capabilities = profile.requiredCapabilities.toList(),
                events = profile.requiredEvents.toList(),
                commands = profile.requiredCommands.toList(),
            ),
        )
        holder.acceptSnapshot(
            requireNotNull(
                decodeSeatLayerPickerSnapshot(
                    bridgeJson.parseToJsonElement(snapshotJson(hold)),
                ),
            ),
        )
        holder.markReady(
            ReadyInfo(
                protocolRevision = 2,
                mode = EventMode.Live,
                transport = TransportName.Android,
                eventKey = "ev",
            ),
        )
        return holder
    }

    private fun snapshotJson(
        hold: String,
        revision: Int = 1,
        seats: String = "",
    ): String = """
        {"schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA","sessionId":"session-a",
         "revision":$revision,"event":{"key":"ev"},"hold":$hold,
         "selection":{"seats":[$seats]}}
    """.trimIndent()
}
