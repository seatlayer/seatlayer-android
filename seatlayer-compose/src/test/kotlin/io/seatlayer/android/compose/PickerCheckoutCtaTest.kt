package io.seatlayer.android.compose

import io.seatlayer.android.SelectionValidity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checkout ladder is ONE rule, so it is tested as one rule.
 *
 * Every rung, and the precedence between them: a ladder tested rung by rung
 * still lets two rungs both think they are in charge.
 */
public class PickerCheckoutCtaTest {
    private val strings = SeatLayerPickerStrings()

    @Test
    public fun salesClosedBeatsEverything() {
        val cta = cta(salesClosed = true, ticketCount = 2, holdActive = true, canOfferFind = true)
        assertEquals(strings.salesClosedCta, cta.label)
        assertFalse(cta.enabled)
        assertTrue(cta.statesReason)
        assertFalse(cta.findsBestSeats)
    }

    @Test
    public fun anUnansweredPromptComesNext() {
        val cta = cta(promptUnanswered = true, ticketCount = 0, canOfferFind = true)
        assertEquals(strings.confirmYourTickets, cta.label)
        assertFalse(cta.enabled)
        assertFalse(cta.findsBestSeats)
    }

    @Test
    public fun aSeatCardKeepsTheLabelAndHoldsTheButtonDown() {
        val open = cta(seatCardOpen = true, ticketCount = 2, holdActive = true, canCheckout = true)
        val closed = cta(ticketCount = 2, holdActive = true, canCheckout = true)
        assertEquals(closed.label, open.label)
        assertFalse(open.enabled)
        assertFalse(open.statesReason)
    }

    @Test
    public fun aSeatCardOverAnEmptyCartOffersNoFinderEither() {
        val cta = cta(seatCardOpen = true, ticketCount = 0, canOfferFind = true)
        assertEquals(strings.findBestSeatsCta, cta.label)
        assertFalse(cta.enabled)
        assertFalse(cta.findsBestSeats)
    }

    @Test
    public fun workInFlightSaysWhatItIsDoing() {
        val securing = cta(busy = SeatLayerPickerCheckoutBusy.SecuringSeats, ticketCount = 2)
        assertEquals(strings.securingSeats, securing.label)
        assertTrue(securing.busy)
        assertFalse(securing.enabled)

        val opening = cta(busy = SeatLayerPickerCheckoutBusy.OpeningCheckout, ticketCount = 2)
        assertEquals(strings.openingCheckout, opening.label)
        assertTrue(opening.busy)
    }

    @Test
    public fun aBrokenSelectionSaysWhichWayToMend() {
        assertEquals(
            strings.chooseMore(2),
            cta(validity = validity(count = 1, required = 3, remaining = 2)).label,
        )
        assertEquals(
            strings.removeTickets(2),
            cta(validity = validity(count = 5, required = 3, remaining = 0)).label,
        )
        assertEquals(
            strings.adjustSelection,
            cta(validity = validity(count = 3, required = 3, remaining = 0)).label,
        )
        assertTrue(cta(validity = validity(count = 1, required = 3, remaining = 2)).statesReason)
    }

    @Test
    public fun anEmptyCartWithAFinderGetsADoor_notARefusal() {
        val cta = cta(ticketCount = 0, canOfferFind = true, holdActive = false)
        assertEquals(strings.findBestSeatsCta, cta.label)
        assertTrue(cta.enabled)
        assertTrue(cta.findsBestSeats)
        assertFalse(cta.statesReason)
    }

    @Test
    public fun aHoldWithNothingPendingContinuesToCheckout() {
        val cta = cta(ticketCount = 2, holdActive = true, canCheckout = true)
        assertEquals(strings.continueToCheckout, cta.label)
        assertTrue(cta.enabled)
    }

    @Test
    public fun aHoldWithSeatsStillToSecureSaysSo() {
        val cta = cta(ticketCount = 3, pendingCount = 1, holdActive = true, canCheckout = true)
        assertEquals(strings.secureMoreAndCheckout(1), cta.label)
    }

    @Test
    public fun anEmptyCartWithNoFinderStatesTheReason() {
        val cta = cta(ticketCount = 0, canOfferFind = false)
        assertEquals(strings.selectSeats, cta.label)
        assertFalse(cta.enabled)
        assertTrue(cta.statesReason)
    }

    @Test
    public fun ticketsWithNoHoldCarryTheCallersOwnLabel() {
        val cta = cta(ticketCount = 2, holdActive = false, canCheckout = true)
        assertEquals(strings.holdAndCheckout, cta.label)
        assertTrue(cta.enabled)
        val refused = cta(ticketCount = 2, holdActive = false, canCheckout = false)
        assertFalse(refused.enabled)
    }

    @Test
    public fun theFinderIsWithheldWhereTheFormWouldBeRefused() {
        assertEquals(strings.selectSeats, cta(ticketCount = 0, canOfferFind = false).label)
        // A hold already exists: an empty cart with a live hold is not an
        // invitation to search again.
        assertEquals(
            strings.continueToCheckout,
            cta(ticketCount = 0, canOfferFind = true, holdActive = true).label,
        )
    }

    @Suppress("LongParameterList")
    private fun cta(
        salesClosed: Boolean = false,
        promptUnanswered: Boolean = false,
        seatCardOpen: Boolean = false,
        busy: SeatLayerPickerCheckoutBusy = SeatLayerPickerCheckoutBusy.None,
        validity: SelectionValidity? = null,
        ticketCount: Int = 0,
        pendingCount: Int = 0,
        holdActive: Boolean = false,
        canOfferFind: Boolean = false,
        canCheckout: Boolean = true,
    ): SeatLayerPickerCheckoutCtaState = seatLayerCheckoutCtaState(
        strings = strings,
        label = strings.holdAndCheckout,
        canCheckout = canCheckout,
        salesClosed = salesClosed,
        promptUnanswered = promptUnanswered,
        seatCardOpen = seatCardOpen,
        busy = busy,
        selectionValidity = validity,
        ticketCount = ticketCount,
        pendingCount = pendingCount,
        holdActive = holdActive,
        canOfferFind = canOfferFind,
    )

    private fun validity(count: Int, required: Int, remaining: Int) = SelectionValidity(
        isValid = false,
        count = count,
        required = required,
        remaining = remaining,
        seats = emptyList(),
        violations = emptyList(),
    )
}
