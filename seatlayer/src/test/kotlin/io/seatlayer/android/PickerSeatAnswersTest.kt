package io.seatlayer.android

import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which seats raise a card, and which ones the picker leaves alone. */
class PickerSeatAnswersTest {
    @Test
    fun aSeatNobodyCanTakeRaisesNoCard() {
        assertFalse(seatLayerPickerMayAskAboutSeat("booked", ownHoldActive = false))
        assertFalse(seatLayerPickerMayAskAboutSeat("booked", ownHoldActive = true))
        assertFalse(seatLayerPickerMayAskAboutSeat("blocked", ownHoldActive = false))
        assertFalse(seatLayerPickerMayAskAboutSeat("blocked", ownHoldActive = true))
    }

    @Test
    fun aHeldSeatIsAskedAboutOnlyWhileThePickersOwnHoldIsLive() {
        assertFalse(seatLayerPickerMayAskAboutSeat("held", ownHoldActive = false))
        assertTrue(seatLayerPickerMayAskAboutSeat("held", ownHoldActive = true))
    }

    @Test
    fun aStatusThisBuildDoesNotKnowIsLeftAlone() {
        // Every runtime shipped before the field omits it entirely, and an
        // unknown word is not a reason to swallow a seat the runtime selected.
        assertTrue(seatLayerPickerMayAskAboutSeat(null, ownHoldActive = false))
        assertTrue(seatLayerPickerMayAskAboutSeat("reserved-for-review", ownHoldActive = false))
    }

    @Test
    fun theSnapshotPathNeverRaisesACardOverASoldSeat() {
        val holder = holder()
        holder.acceptSnapshot(
            json(
                snapshot(
                    revision = 2,
                    seats = """{"id":"seat-1","label":"A-1","status":"booked"}""",
                ),
            ),
        )

        assertNull(holder.state.value.presentation.candidateSeat)
    }

    @Test
    fun aRetapOverASeatInAnotherBuyersHoldIsIgnored() {
        val holder = holder(events = setOf("seat.retap"))
        holder.acceptSnapshot(json(snapshot(revision = 2)))

        holder.acceptSeatRetap(
            json("""{"seat":{"id":"seat-1","label":"A-1","status":"held"}}"""),
        )

        assertNull(holder.state.value.presentation.candidateSeat)
    }

    @Test
    fun aRetapOverTheBuyersOwnHeldSeatStillAsks() {
        val holder = holder(events = setOf("seat.retap"))
        holder.acceptSnapshot(json(snapshot(revision = 2, hold = true)))

        holder.acceptSeatRetap(
            json("""{"seat":{"id":"seat-1","label":"A-1","status":"held"}}"""),
        )

        val presentation = holder.state.value.presentation
        assertEquals(SeatLayerPickerCardIntent.Remove, presentation.cardIntent)
        assertEquals("A-1", presentation.candidateSeat?.label)
    }

    @Test
    fun anUnansweredAddOutranksARetap() {
        val holder = holder(events = setOf("seat.retap"))
        holder.acceptSnapshot(
            json(snapshot(revision = 2, seats = """{"id":"seat-2","label":"A-2"}""")),
        )
        assertEquals("A-2", holder.state.value.presentation.candidateSeat?.label)

        holder.acceptSeatRetap(json("""{"seat":{"id":"seat-1","label":"A-1"}}"""))

        val presentation = holder.state.value.presentation
        assertEquals(SeatLayerPickerCardIntent.Add, presentation.cardIntent)
        assertEquals("A-2", presentation.candidateSeat?.label)
    }

    @Test
    fun theStatusFieldArrivesWholeOrNotAtAll() {
        val decoded = decodeSeatLayerPickerSnapshot(
            json(
                snapshot(
                    revision = 1,
                    seats = """{"id":"a","label":"A-1","status":"blocked"},
                        {"id":"b","label":"A-2"},{"id":"c","label":"A-3","status":""}""",
                ),
            ),
        )
        val statuses = requireNotNull(decoded).selection.map { it.status }
        assertEquals(listOf("blocked", null, null), statuses)
    }

    private fun holder(events: Set<String> = emptySet()): SeatLayerPickerStateHolder {
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val profile = holder.bridgeProfile
        holder.beginLoading()
        holder.connect(
            SeatLayerPickerCommandTransport { _, _ -> null },
            BundleInfo(
                bundle = "test",
                protocolRange = profile.protocolRange,
                capabilities = profile.requiredCapabilities.toList(),
                events = (profile.requiredEvents + events).toList(),
                commands = profile.requiredCommands.toList(),
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

    private companion object {
        fun snapshot(
            revision: Int,
            sessionId: String = "s",
            seats: String = "",
            hold: Boolean = false,
        ): String =
            """{"schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA","sessionId":"$sessionId",
                "revision":$revision,"event":{"key":"ev"},"hold":{"active":$hold},
                "selection":{"seats":[$seats]}}"""

        fun json(raw: String): JsonElement = bridgeJson.parseToJsonElement(raw)
    }
}
