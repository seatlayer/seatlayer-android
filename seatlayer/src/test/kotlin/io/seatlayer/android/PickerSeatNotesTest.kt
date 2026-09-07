package io.seatlayer.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One row model, shared by every surface that answers "what is special here?". */
class PickerSeatNotesTest {
    @Test
    fun everyAttributeAtOnceProducesOneFixedOrder() {
        val rows = seatLayerPickerSeatNotes(
            accessibility = listOf("hearing", "step-free", "wheelchair"),
            wheelchairSpaceType = "no-seat",
            commercial = SeatLayerPickerSeatCommercial(
                restrictedView = true,
                obstructedView = true,
                premium = true,
                organizerNote = "Pillar at the aisle end.",
            ),
        )

        assertEquals(
            listOf(
                "hearing",
                "step-free",
                "emptyWheelchairSpace",
                "restrictedView",
                "obstructedView",
                "premiumSeat",
            ),
            rows.map { it.labelKey },
        )
        assertEquals(
            listOf(
                SeatLayerPickerSeatNoteTone.Access,
                SeatLayerPickerSeatNoteTone.Access,
                SeatLayerPickerSeatNoteTone.Access,
                SeatLayerPickerSeatNoteTone.Warn,
                SeatLayerPickerSeatNoteTone.Warn,
                SeatLayerPickerSeatNoteTone.Premium,
            ),
            rows.map { it.tone },
        )
    }

    @Test
    fun restrictedAndObstructedNeverCollapseIntoEachOther() {
        // A seat behind both a rail and a pillar used to report only the rail.
        val rows = seatLayerPickerSeatNotes(
            commercial = SeatLayerPickerSeatCommercial(
                restrictedView = true,
                obstructedView = true,
            ),
        )
        assertEquals(listOf("restrictedView", "obstructedView"), rows.map { it.labelKey })
    }

    @Test
    fun aWheelchairAccommodationWithAProvisionYieldsTheProvisionAlone() {
        val rows = seatLayerPickerSeatNotes(
            accessibility = listOf("wheelchair"),
            wheelchairSpaceType = "seat-present",
        )
        assertEquals(listOf("accessiblePhysicalSeat"), rows.map { it.labelKey })
        assertEquals(listOf("wheelchair"), rows.map { it.iconKey })
    }

    @Test
    fun theOrganizersSentenceHangsOnTheFirstSellingMark() {
        val rows = seatLayerPickerSeatNotes(
            commercial = SeatLayerPickerSeatCommercial(
                obstructedView = true,
                premium = true,
                organizerNote = "Speaker stack in front.",
            ),
        )
        assertEquals("Speaker stack in front.", rows.first().note)
        assertNull(rows.last().note)
    }

    @Test
    fun withNoMarkToExplainTheSentenceBecomesItsOwnRow() {
        val rows = seatLayerPickerSeatNotes(
            accessibility = listOf("companion"),
            commercial = SeatLayerPickerSeatCommercial(organizerNote = "Ask staff on arrival."),
        )
        assertEquals(listOf("companion", "organizerNote"), rows.map { it.labelKey })
        assertEquals("Ask staff on arrival.", rows.last().note)
        assertEquals(SeatLayerPickerSeatNoteTone.Note, rows.last().tone)
    }

    @Test
    fun aKeyThisBuildHasNoNameForIsDropped() {
        val rows = seatLayerPickerSeatNotes(accessibility = listOf("teleportation"))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun twoSeatsWithTheSameAttributesAlwaysProduceTheSameList() {
        val first = seatLayerPickerSeatNotes(accessibility = listOf("cart", "low-vision"))
        val second = seatLayerPickerSeatNotes(accessibility = listOf("cart", "low-vision"))
        assertEquals(first, second)
    }
}
