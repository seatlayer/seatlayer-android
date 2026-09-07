package io.seatlayer.android

import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatLayerPickerPresentationModelTest {
    @Test
    fun aSecondTapOnACartedSeatAsksBeforeTakingItAway() {
        val holder = holder(events = setOf("seat.retap"))
        holder.acceptSnapshot(json(snapshot(revision = 2, seats = """{"id":"seat-1","label":"A-1"}""")))
        holder.answer(holder.state.value.snapshot!!.selection.single())

        holder.acceptSeatRetap(json("""{"seat":{"id":"seat-1","label":"A-1"}}"""))

        val presentation = holder.state.value.presentation
        assertEquals(SeatLayerPickerCardIntent.Remove, presentation.cardIntent)
        assertEquals("A-1", presentation.candidateSeat?.label)
        assertEquals("A-1", presentation.seatAwaitingRemoval?.label)
    }

    @Test
    fun aRuntimeThatDoesNotAnnounceRetapsNeverRaisesARemoveCard() {
        val holder = holder(events = emptySet())
        holder.acceptSnapshot(json(snapshot(revision = 2, seats = """{"id":"seat-1","label":"A-1"}""")))
        holder.answer(holder.state.value.snapshot!!.selection.single())

        holder.acceptSeatRetap(json("""{"seat":{"id":"seat-1","label":"A-1"}}"""))

        assertNull(holder.state.value.presentation.candidateSeat)
    }

    @Test
    fun aSeatOnItsCardIsInNoTicketCountAndNoTotal() {
        val cart = listOf(
            line("A-1", seatId = "seat-1", price = 30.0),
            line("A-2", seatId = "seat-2", price = 25.0),
        )
        val candidate = seat("seat-2", "A-2")

        val asking = SeatLayerPickerPresentationState(
            candidateSeat = candidate,
            cardIntent = SeatLayerPickerCardIntent.Add,
        ).confirmedCart(snapshotWith(cart))
        assertEquals(listOf("A-1"), asking.items.map { it.label })
        assertEquals(1, asking.totals.quantity)
        assertEquals(30.0, asking.totals.total, 0.0)

        // A Remove card asks about a seat the buyer already owns, so it stays
        // counted until it is actually given up.
        val removing = SeatLayerPickerPresentationState(
            candidateSeat = candidate,
            cardIntent = SeatLayerPickerCardIntent.Remove,
        ).confirmedCart(snapshotWith(cart))
        assertEquals(listOf("A-1", "A-2"), removing.items.map { it.label })
        assertEquals(55.0, removing.totals.total, 0.0)
    }

    @Test
    fun continueDoesNotGreyOutWhileASeatIsJoiningALiveHold() {
        val state = SeatLayerPickerPresentationState(actionInFlight = true)

        assertTrue(
            state.canCheckout(
                snapshot = snapshotWith(listOf(line("A-1", seatId = "seat-1", price = 30.0))),
                ready = true,
                readOnly = false,
            ),
        )
    }

    @Test
    fun theSheetReportsItsDetentNotJustABoolean() {
        assertEquals(
            SeatLayerPickerSheetDetent.Collapsed,
            SeatLayerPickerPresentationState().sheetDetent,
        )
        assertEquals(
            SeatLayerPickerSheetDetent.Open,
            SeatLayerPickerPresentationState(cartExpanded = true).sheetDetent,
        )
    }

    @Test
    fun theCartLandingIsAdvancedByItsSurfacesAndNotByAClock() {
        val holder = holder()
        assertEquals(SeatLayerPickerCartLanding.Idle, holder.state.value.presentation.cartLanding)

        listOf(
            SeatLayerPickerCartLanding.Flying,
            SeatLayerPickerCartLanding.Landed,
            SeatLayerPickerCartLanding.Released,
        ).forEach { step ->
            holder.setCartLanding(step)
            assertEquals(step, holder.state.value.presentation.cartLanding)
        }

        // A new session starts the tray at rest, whatever the last one was mid-way through.
        holder.acceptSnapshot(json(snapshot(revision = 1, sessionId = "next")))
        assertEquals(SeatLayerPickerCartLanding.Idle, holder.state.value.presentation.cartLanding)
    }

    @Test
    fun seatsAHoldArrivesWithAreAnsweredAndEverySeatAfterThemIsAsked() {
        val holder = holder()

        holder.acceptSnapshot(
            json(
                snapshot(
                    revision = 1,
                    seats = """{"id":"seat-1","label":"A-1"}""",
                    hold = true,
                ),
            ),
        )
        assertNull(holder.state.value.presentation.candidateSeat)

        holder.acceptSnapshot(
            json(
                snapshot(
                    revision = 2,
                    seats = """{"id":"seat-1","label":"A-1"},{"id":"seat-2","label":"A-2"}""",
                    hold = true,
                ),
            ),
        )
        assertEquals("A-2", holder.state.value.presentation.candidateSeat?.label)
    }

    @Test
    fun aReadOnlyPickerNeverAsksAnything() {
        val holder = SeatLayerPickerStateHolder(
            SeatLayerConfiguration(event = "ev"),
            SeatLayerPickerBehavior(readOnly = true),
        )
        holder.beginLoading()
        holder.acceptSnapshot(json(snapshot(revision = 1, seats = """{"id":"seat-1","label":"A-1"}""")))

        assertNull(holder.state.value.presentation.candidateSeat)
    }

    @Test
    fun aSeatLiftKnowsWhatItHasToGiveBack() {
        val lift = SeatLayerPickerSeatLift(seatId = "seat-1", dy = 120.0)

        assertEquals(-120.0, lift.restore, 0.0)
        assertFalse(SeatLayerPickerPresentationState().blockedRegions.isNotEmpty())
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

    private fun seat(id: String, label: String) = SeatLayerPickerSelectedSeat(
        id = id,
        label = label,
        displayLabel = null,
        displayType = null,
        objectId = null,
        objectType = null,
        bookingMode = null,
        sectionLabel = null,
        rowLabel = null,
        seatNumber = null,
        categoryKey = "",
        price = 0.0,
        currency = "EUR",
        tiers = emptyList(),
        tierId = null,
        accessibility = emptyList(),
        wheelchairSpaceType = null,
        quantity = null,
        capacity = null,
        minOccupancy = null,
        maxOccupancy = null,
    )

    private fun line(label: String, seatId: String, price: Double) = SeatLayerPickerCartLine(
        lineKey = seatId,
        label = label,
        displayLabel = null,
        displayType = null,
        objectId = seatId,
        objectType = "seat",
        categoryKey = "",
        tierId = null,
        unitPrice = price,
        currency = "EUR",
        quantity = 1,
        seatId = seatId,
        sectionLabel = null,
        rowLabel = null,
        seatNumber = null,
    )

    private fun snapshotWith(lines: List<SeatLayerPickerCartLine>): SeatLayerPickerSnapshot =
        requireNotNull(decodeSeatLayerPickerSnapshot(json(snapshot(revision = 1))))
            .copy(cartLines = lines)

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
