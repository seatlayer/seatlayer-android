package io.seatlayer.android.compose

import io.seatlayer.android.SeatLayerPickerCartLine
import io.seatlayer.android.SeatLayerPickerProjections
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sheet's arithmetic, with no screen in the way. */
public class PickerCartRulesTest {
    @Test
    public fun aReleaseBelowTheFlingSpeedTakesTheNearestDetent() {
        val detents = PickerSheetDetents(content = 200f, full = 400f)
        assertEquals(PickerSheetRest.Peek, detents.settle(40f, velocity = 10f))
        assertEquals(PickerSheetRest.Content, detents.settle(180f, velocity = -10f))
        assertEquals(PickerSheetRest.Full, detents.settle(390f, velocity = 0f))
    }

    @Test
    public fun aFlingIsAnInstruction_notAMeasurement() {
        val detents = PickerSheetDetents(content = 200f, full = 400f)
        val fling = SeatLayerPhysicsTokens.SHEET_FLING_VELOCITY + 1f
        // Thrown up from almost nothing: the next detent, not the nearest.
        assertEquals(PickerSheetRest.Content, detents.settle(5f, velocity = fling))
        assertEquals(PickerSheetRest.Full, detents.settle(205f, velocity = fling))
        assertEquals(PickerSheetRest.Peek, detents.settle(195f, velocity = -fling))
    }

    @Test
    public fun aFullBelowContentIsNotADetent() {
        val detents = PickerSheetDetents(content = 300f, full = 120f)
        assertFalse(detents.offersFull)
        assertEquals(300f, detents.resolvedFull, 0.001f)
        assertEquals(listOf(PickerSheetRest.Peek, PickerSheetRest.Content), detents.positions)
    }

    @Test
    public fun aShortDeliberateDragStillAnswers() {
        val detents = PickerSheetDetents(content = 200f, full = 200f)
        val travelled = detents.settleWithFloor(
            height = 20f,
            velocity = 0f,
            started = PickerSheetRest.Peek,
            travel = PICKER_SHEET_DRAG_THRESHOLD,
        )
        assertEquals(PickerSheetRest.Content, travelled)
        val wobble = detents.settleWithFloor(
            height = 4f,
            velocity = 0f,
            started = PickerSheetRest.Peek,
            travel = PICKER_SHEET_DRAG_THRESHOLD - 1f,
        )
        assertEquals(PickerSheetRest.Peek, wobble)
    }

    @Test
    public fun theEndsGiveRatherThanStop() {
        assertEquals(0f, pickerRubberBand(0f, 0f, 200f), 0.001f)
        assertEquals(
            -100f * SeatLayerPhysicsTokens.RUBBER_BAND,
            pickerRubberBand(-100f, 0f, 200f),
            0.001f,
        )
        assertEquals(
            200f + 100f * SeatLayerPhysicsTokens.RUBBER_BAND,
            pickerRubberBand(300f, 0f, 200f),
            0.001f,
        )
    }

    @Test
    public fun theSpringIsTheDesignSourcesOwn() {
        // mass 1, stiffness 420, damping 34 → an under-damped ratio below one.
        assertEquals(0.8295f, pickerSheetDampingRatio(), 0.001f)
    }

    @Test
    public fun theCollapsedSheetIsZeroHigh() {
        val detents = pickerSheetDetents(
            screenHeight = 844f,
            footHeight = 120f,
            cartNatural = 400f,
            extrasNatural = 0f,
            hasTickets = true,
        )
        assertEquals(0f, detents.peek, 0.001f)
    }

    @Test
    public fun theOpenCartStopsAtThreeCardsAndASliver() {
        val sixCards = 6 * SeatLayerSizeTokens.CART_CARD_MIN_HEIGHT +
            5 * SeatLayerSizeTokens.CART_CARD_GAP
        val detents = pickerSheetDetents(
            screenHeight = 844f,
            footHeight = 120f,
            cartNatural = sixCards,
            extrasNatural = 0f,
            hasTickets = true,
        )
        val tray = SeatLayerSizeTokens.CART_TRAY_PAD_TOP + SeatLayerSizeTokens.CART_TRAY_PAD_BOTTOM
        assertEquals(SeatLayerSizeTokens.CART_PEEK_MAX_HEIGHT + tray, detents.content, 0.001f)
        val threeCards = 3 * SeatLayerSizeTokens.CART_CARD_MIN_HEIGHT +
            2 * SeatLayerSizeTokens.CART_CARD_GAP
        assertTrue(
            "three cards fit under the cap",
            SeatLayerSizeTokens.CART_PEEK_MAX_HEIGHT > threeCards,
        )
        assertTrue(
            "the fourth does not",
            SeatLayerSizeTokens.CART_PEEK_MAX_HEIGHT <
                threeCards + SeatLayerSizeTokens.CART_CARD_MIN_HEIGHT,
        )
    }

    @Test
    public fun anEmptyTrayStaysShort() {
        val tall = pickerSheetDetents(844f, 120f, 400f, 300f, hasTickets = true)
        val empty = pickerSheetDetents(844f, 120f, 0f, 300f, hasTickets = false)
        // 0.64 of the height, not 0.72, and capped lower.
        assertTrue(empty.content < tall.content)
    }

    @Test
    public fun aShortDeviceGivesTheSheetLessRoom_neverTheButton() {
        val short = pickerSheetDetents(480f, 200f, 400f, 0f, hasTickets = true)
        val chrome = SeatLayerSizeTokens.SHEET_HEAD_HEIGHT + 200f
        assertTrue(short.content + chrome <= 480f * SeatLayerSizeTokens.SHEET_MAX_HEIGHT_FRACTION)
    }

    @Test
    public fun aSwipeCommitsPastItsShareOrOnAThrow() {
        val width = 300f
        assertFalse(pickerSwipeCommits(slide = 100f, width = width, velocity = 0f))
        assertTrue(
            pickerSwipeCommits(
                slide = width * SeatLayerPhysicsTokens.SWIPE_COMMIT_FRACTION,
                width = width,
                velocity = 0f,
            ),
        )
        assertTrue(
            pickerSwipeCommits(
                slide = 10f,
                width = width,
                velocity = SeatLayerPhysicsTokens.SWIPE_FLING_VELOCITY,
            ),
        )
        assertFalse(pickerSwipeCommits(slide = 0f, width = 0f, velocity = 5_000f))
    }

    @Test
    public fun theSwellGoesOutAndComesBack() {
        assertEquals(0f, pickerSwellCurve(0f), 0.001f)
        assertEquals(1f, pickerSwellCurve(CartSheetLiterals.SWELL_PEAK), 0.001f)
        assertEquals(0f, pickerSwellCurve(1f), 0.001f)
    }

    @Test
    public fun theCountdownSpeaksOnTheMinute_thenEverySecondOfTheLast() {
        val strings = SeatLayerPickerStrings()
        assertNull(seatLayerHoldAnnouncement(0L, strings))
        assertNull(seatLayerHoldAnnouncement(301L, strings))
        assertEquals("5 minutes left", seatLayerHoldAnnouncement(300L, strings))
        assertEquals("1 minute left", seatLayerHoldAnnouncement(60L, strings))
        assertEquals("59 seconds left", seatLayerHoldAnnouncement(59L, strings))
        assertEquals("1 second left", seatLayerHoldAnnouncement(1L, strings))
    }

    @Test
    public fun theClockShowsTheWholeServerHold() {
        val now = 1_000_000L
        assertEquals(600L, seatLayerHoldSecondsLeft(now + 600_000L, now))
        assertEquals("10:00", seatLayerHoldClock(600L))
        assertEquals("0:07", seatLayerHoldClock(7L))
        assertEquals(0L, seatLayerHoldSecondsLeft(now - 1L, now))
        assertTrue(seatLayerHoldIsExpiring(59L))
        assertFalse(seatLayerHoldIsExpiring(60L))
        assertFalse(seatLayerHoldIsExpiring(0L))
    }

    @Test
    public fun theCollapsedSeatLineNamesEverySeat() {
        val line = pickerCartSeatLine(
            listOf(cartLine("204-Q-7"), cartLine("204-U-13")),
        )
        assertEquals("204 · Q · 7,  204 · U · 13", line)
        assertNull(pickerCartSeatLine(emptyList()))
    }

    @Test
    public fun thePositionLineDropsATypeThatIsAlreadyTheName() {
        val line = cartLine("A-12", row = "A", seat = "12")
        assertEquals("A · 12 · Lower Bowl", pickerCardPositionLine(line, "Lower Bowl", "Section 102"))
        assertEquals("A · 12", pickerCardPositionLine(line, "Lower Bowl", "lower bowl"))
    }

    @Test
    public fun twoSeatsOfOneRowAreTwoCards_notOneDrawnTwice() {
        // The runtime's own key is ROW-scoped: both seats arrive under it.
        val first = cartLine("102-A-1", row = "102-A", seat = "1")
            .copy(lineKey = "row_102-A", seatId = "seat-1")
        val second = cartLine("102-A-2", row = "102-A", seat = "2")
            .copy(lineKey = "row_102-A", seatId = "seat-2")
        assertEquals(first.lineKey, second.lineKey)
        assertEquals(
            2,
            listOf(first, second)
                .mapIndexed { index, line ->
                    SeatLayerPickerProjections.cartLineKey(line, index)
                }
                .toSet()
                .size,
        )
        assertFalse(SeatLayerPickerProjections.sameTicket(first, second))
        assertTrue(SeatLayerPickerProjections.sameTicket(first, first.copy(unitPrice = 99.0)))
    }

    @Test
    public fun aLineWithNoSeatOfItsOwnStillGetsItsOwnKey() {
        // A best-available result or a resumed hold names no seat at all.
        val first = cartLine("row_102-A", row = "102-A", seat = null)
        val second = cartLine("row_102-A", row = "102-A", seat = null)
        assertEquals(
            2,
            listOf(first, second)
                .mapIndexed { index, line ->
                    SeatLayerPickerProjections.cartLineKey(line, index)
                }
                .toSet()
                .size,
        )
    }

    @Test
    public fun theCardPrintsTheShortRowWord_notTheQualifiedOne() {
        val line = cartLine("211-Q-12", row = "211-Q", seat = "12")
            .copy(sectionLabel = "211")
        val row = pickerRowLabel(line.rowLabel, line.sectionLabel, null)
        assertEquals("Q", row)
        assertEquals(
            "Q · 12 · Lower Bowl",
            pickerCardPositionLine(line, "Lower Bowl", "211", row),
        )
    }

    @Test
    public fun aLineFindsItsSeatBySeatId_thenLabel_thenObject() {
        val seat = seat(id = "seat-1", label = "A-12", objectId = "obj-1")
        assertEquals(seat, pickerSeatBehind(cartLine("A-12").copy(seatId = "seat-1"), listOf(seat)))
        assertEquals(seat, pickerSeatBehind(cartLine("A-12"), listOf(seat)))
        assertEquals(
            seat,
            pickerSeatBehind(cartLine("other").copy(objectId = "obj-1"), listOf(seat)),
        )
        // Best-available results are in no renderer selection: nothing is found,
        // and the card simply offers less.
        assertNull(pickerSeatBehind(cartLine("nothing"), listOf(seat)))
    }

    @Test
    public fun theNoteTitlesComeFromTheStringTable() {
        val strings = SeatLayerPickerStrings()
        assertEquals("Restricted view", pickerSeatNoteTitle(strings, "restrictedView"))
        assertEquals("Obstructed view", pickerSeatNoteTitle(strings, "obstructedView"))
        assertEquals("Premium seat", pickerSeatNoteTitle(strings, "premiumSeat"))
        assertEquals("Empty wheelchair space", pickerSeatNoteTitle(strings, "emptyWheelchairSpace"))
        assertEquals("Wheelchair", pickerSeatNoteTitle(strings, "wheelchair"))
    }

    private fun cartLine(
        label: String,
        row: String? = null,
        seat: String? = null,
    ): SeatLayerPickerCartLine = SeatLayerPickerCartLine(
        lineKey = label,
        label = label,
        displayLabel = null,
        displayType = null,
        objectId = label,
        objectType = "seat",
        categoryKey = "standard",
        tierId = null,
        unitPrice = 40.0,
        currency = "USD",
        quantity = 1,
        seatId = null,
        sectionLabel = "Section 102",
        rowLabel = row,
        seatNumber = seat,
    )

    private fun seat(
        id: String,
        label: String,
        objectId: String,
    ): SeatLayerPickerSelectedSeat = SeatLayerPickerSelectedSeat(
        id = id,
        label = label,
        displayLabel = null,
        displayType = null,
        objectId = objectId,
        objectType = "seat",
        bookingMode = null,
        sectionLabel = "Section 102",
        rowLabel = "A",
        seatNumber = "12",
        categoryKey = "standard",
        price = 40.0,
        currency = "USD",
        tiers = emptyList(),
        tierId = null,
        accessibility = emptyList(),
        wheelchairSpaceType = null,
        quantity = 1,
        capacity = null,
        minOccupancy = null,
        maxOccupancy = null,
    )
}
