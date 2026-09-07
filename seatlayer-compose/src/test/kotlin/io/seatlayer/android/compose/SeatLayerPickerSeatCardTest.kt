package io.seatlayer.android.compose

import androidx.compose.ui.graphics.Color
import io.seatlayer.android.SeatLayerPickerSeatNoteTone
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card's pure decisions: what it prints, what ink it prints in, and how far
 * the map has to move to keep the seat clear of it.
 */
class SeatLayerPickerSeatCardTest {
    private val strings = SeatLayerPickerStrings()

    @Test
    fun theBandChoosesItsInkAgainstTheColourTheOrganizerAuthored() {
        // White survives on a deep teal; a pale yellow keeps its name only in
        // the dark ink. The two candidates are fixed.
        assertEquals(SEATLAYER_BAND_INK_LIGHT, seatLayerPickerConfirmBandInk(Color(0xFF3A8C83)))
        assertEquals(SEATLAYER_BAND_INK_DARK, seatLayerPickerConfirmBandInk(Color(0xFFF6E27A)))
        assertTrue(
            pickerContrastRatio(
                seatLayerPickerConfirmBandInk(Color(0xFFF6E27A)),
                Color(0xFFF6E27A),
            ) >= 3f,
        )
    }

    @Test
    fun everyNoteToneStaysReadableInBothThemes() {
        listOf(SeatLayerPickerTheme.light(), SeatLayerPickerTheme.dark()).forEach { theme ->
            SeatLayerPickerSeatNoteTone.entries.forEach { tone ->
                val colors = seatLayerSeatNoteColors(tone, theme)
                val ratio = pickerContrastRatio(colors.title, colors.band)
                assertTrue(
                    "$tone title measured $ratio on its own band",
                    ratio >= 4.5f,
                )
            }
        }
    }

    @Test
    fun theToneChangesTheGroundAsWellAsTheInk() {
        val theme = SeatLayerPickerTheme.light()
        val warn = seatLayerSeatNoteColors(SeatLayerPickerSeatNoteTone.Warn, theme)
        val premium = seatLayerSeatNoteColors(SeatLayerPickerSeatNoteTone.Premium, theme)
        val access = seatLayerSeatNoteColors(SeatLayerPickerSeatNoteTone.Access, theme)

        assertEquals(theme.warnText, warn.title)
        assertEquals(theme.premiumText, premium.title)
        assertEquals(theme.text, access.title)
        assertTrue(!pickerColorsAgree(warn.band, access.band))
        assertTrue(!pickerColorsAgree(premium.band, warn.band))
    }

    @Test
    fun theCellSetIsChosenBeforeAnyValueIsPrinted() {
        val cells = seatLayerIdentityCells(
            seat(sectionLabel = "Stalls", rowLabel = "  ", seatNumber = "12"),
            strings,
        )
        assertEquals(listOf("Section", "Row", "Seat"), cells.map { it.eyebrow })
        // Present but empty prints the dash rather than losing its column and
        // shifting the other two under the buyer's eye.
        assertEquals(listOf("Stalls", "—", "12"), cells.map { it.value })
    }

    @Test
    fun aSeatWithNoSectionKeepsTwoEqualCells() {
        val cells = seatLayerIdentityCells(seat(rowLabel = "A", seatNumber = "4"), strings)
        assertEquals(listOf("Row", "Seat"), cells.map { it.eyebrow })
    }

    @Test
    fun onlyALongSectionGivesUpTheBigType() {
        assertTrue(
            seatLayerIdentityCells(seat(sectionLabel = "Upper Grand Circle"), strings)
                .first().longSection,
        )
        assertTrue(
            !seatLayerIdentityCells(seat(sectionLabel = "209"), strings).first().longSection,
        )
    }

    @Test
    fun aBoothHoldsPlacesRatherThanSeats() {
        val cells = seatLayerIdentityCells(seat(objectType = "booth", seatNumber = "3"), strings)
        assertEquals("Place", cells.last().eyebrow)
    }

    @Test
    fun theRowIsPrintedWithItsSectionPrefixStripped() {
        assertEquals("C", pickerRowLabel("Stalls D C", "Stalls D"))
        assertEquals("H", pickerRowLabel("GALL-H", "Gallery"))
        assertEquals("H", pickerRowLabel("GALL-H", "Grand Hall", sectionCode = "GALL"))
        // A row sharing no prefix keeps its whole name, and a bare letter is
        // never mistaken for a code.
        assertEquals("A", pickerRowLabel("A", "Stalls"))
        assertEquals("Balcony B", pickerRowLabel("Balcony B", "Stalls"))
        assertEquals("Stalls", pickerRowLabel("Stalls", "Stalls"))
    }

    @Test
    fun theIdentityReadsAsOneSentence() {
        val cells = seatLayerIdentityCells(
            seat(sectionLabel = "Stalls", rowLabel = "A", seatNumber = "12"),
            strings,
        )
        assertEquals("Stalls · Row A · Seat 12", seatLayerIdentitySentence(cells, strings))
    }

    @Test
    fun theTwoAnswersAreNotEquallyWide() {
        assertEquals(0.34f, SEATLAYER_CONFIRM_CANCEL_SHARE, 0.0001f)
    }

    @Test
    fun theMapIsAskedToPanRatherThanToZoom() {
        // A phone-sized map with a card at its foot: the seat is asked to sit
        // partway into the band the sheet leaves clear, never at a fixed point.
        val fraction = seatLayerSheetLiftFraction(
            mapHeight = 800.0,
            cardHeight = 260.0,
            bottomBand = 0.0,
            restInset = 14.0,
            seatGap = 12.0,
        )
        assertNotNull(fraction)
        assertTrue(fraction!! > 0.0 && fraction < SEATLAYER_SHEET_LIFT_FRACTION)
    }

    @Test
    fun aCardTallerThanItsMapAsksForNoPan() {
        assertNull(
            seatLayerSheetLiftFraction(
                mapHeight = 200.0,
                cardHeight = 400.0,
                bottomBand = 0.0,
                restInset = 14.0,
                seatGap = 12.0,
            ),
        )
        assertNull(
            seatLayerSheetLiftFraction(
                mapHeight = 0.0,
                cardHeight = 260.0,
                bottomBand = 0.0,
                restInset = 14.0,
                seatGap = 12.0,
            ),
        )
    }

    @Test
    fun theDockBandMovesTheCardsHomeAndSoTheFractionWithIt() {
        val flat = seatLayerSheetLiftFraction(800.0, 260.0, 0.0, 14.0, 12.0)!!
        val docked = seatLayerSheetLiftFraction(800.0, 260.0, 56.0, 14.0, 12.0)!!
        assertTrue(docked < flat)
    }

    @Test
    fun theAccentIsWalkedTowardTheInkUntilItIsReadable() {
        val theme = SeatLayerPickerTheme.light()
        val ground = pickerWash(theme.accent, 0.07f, theme.surface)
        val readable = pickerReadableAccent(theme.accent, theme.text, theme.surface, ground)
        assertTrue(pickerContrastRatio(readable, theme.surface) >= 4.5f)
        assertTrue(pickerContrastRatio(readable, ground) >= 4.5f)
    }

    private fun seat(
        sectionLabel: String? = null,
        rowLabel: String? = null,
        seatNumber: String? = null,
        objectType: String? = "seat",
    ) = SeatLayerPickerSelectedSeat(
        id = "seat-1",
        label = "A-1",
        displayLabel = null,
        displayType = null,
        objectId = null,
        objectType = objectType,
        bookingMode = null,
        sectionLabel = sectionLabel,
        rowLabel = rowLabel,
        seatNumber = seatNumber,
        categoryKey = "standard",
        price = 54.0,
        currency = "USD",
        tiers = emptyList(),
        tierId = null,
        accessibility = emptyList(),
        wheelchairSpaceType = null,
        quantity = null,
        capacity = null,
        minOccupancy = null,
        maxOccupancy = null,
    )
}
