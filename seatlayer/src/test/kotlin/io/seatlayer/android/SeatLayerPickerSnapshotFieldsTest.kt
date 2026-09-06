package io.seatlayer.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The additive snapshot fields, each read three ways: reported, absent, and
 * malformed. Absent is the one that matters — every field here is present-only,
 * and reading absent as zero or false is how a control dims forever or a
 * bookable category is struck through.
 */
class SeatLayerPickerSnapshotFieldsTest {
    @Test
    fun venueFitIsPresentOnlyAndAbsenceIsNotFalse() {
        assertEquals(true, snapshot("""{"atVenueFit":true}""").map.atVenueFit)
        assertEquals(false, snapshot("""{"atVenueFit":false}""").map.atVenueFit)
        assertNull(snapshot("{}").map.atVenueFit)
        assertNull(snapshot("""{"atVenueFit":"yes"}""").map.atVenueFit)
    }

    @Test
    fun accessibleSpacesAreCountedOnlyWhenTheRuntimeCountsThem() {
        val counted = snapshot(
            sections = """[{"id":"s1","accessibleFree":{"wheelchair":2,"companion":1}}]""",
        )
        assertEquals(3, counted.sections.single().accessibleFree)

        assertNull(snapshot(sections = """[{"id":"s1","accessibleFree":{}}]""").sections.single().accessibleFree)
        assertNull(snapshot(sections = """[{"id":"s1"}]""").sections.single().accessibleFree)
        assertNull(
            snapshot(sections = """[{"id":"s1","accessibleFree":"lots"}]""")
                .sections.single().accessibleFree,
        )
    }

    @Test
    fun aCategoryWithNoCountIsNotSoldOut() {
        assertEquals(4, snapshot(categories = """[{"key":"a","free":4}]""").categories.single().free)
        assertFalse(snapshot(categories = """[{"key":"a","free":4}]""").categories.single().isSoldOut)
        assertTrue(snapshot(categories = """[{"key":"a","free":0}]""").categories.single().isSoldOut)

        val uncounted = snapshot(categories = """[{"key":"a"}]""").categories.single()
        assertNull(uncounted.free)
        assertFalse(uncounted.isSoldOut)
    }

    @Test
    fun anAccessNeedWithNoCountStaysOffered() {
        val needs = snapshot("""{"accessNeeds":[{"key":"wheelchair"},{"key":"companion","count":0},{"key":"hearing","count":3}]}""")
            .map.accessNeeds

        assertNull(needs[0].count)
        assertTrue(needs[0].isAvailable)
        assertEquals(0, needs[1].count)
        assertFalse(needs[1].isAvailable)
        assertEquals(3, needs[2].count)
        assertTrue(needs[2].isAvailable)
    }

    @Test
    fun screenPointArrivesWholeOrNotAtAll() {
        val seat = seat("""{"id":"a","label":"A-1","screenPoint":{"x":120.5,"y":88.0}}""")
        assertEquals(SeatLayerPickerScreenPoint(120.5, 88.0), seat.screenPoint)

        assertNull(seat("""{"id":"a","label":"A-1"}""").screenPoint)
        assertNull(seat("""{"id":"a","label":"A-1","screenPoint":{"x":1}}""").screenPoint)
    }

    @Test
    fun onlyARealPhotographIsOfferedAsTheViewFromASeat() {
        assertEquals(
            SeatLayerPickerSeatViewThumbnail("asset-1"),
            seat("""{"id":"a","label":"A-1","seatViewThumb":{"reference":"asset-1","kind":"real"}}""")
                .seatViewThumbnail,
        )
        assertNull(
            seat("""{"id":"a","label":"A-1","seatViewThumb":{"reference":"asset-1","kind":"generated"}}""")
                .seatViewThumbnail,
        )
        assertNull(
            seat("""{"id":"a","label":"A-1","seatViewThumb":{"kind":"real"}}""").seatViewThumbnail,
        )
    }

    @Test
    fun aNegativeSightlineIsAFailedMeasurementNotADistance() {
        assertEquals(7.0, seat("""{"id":"a","label":"A-1","sightlineMetres":7}""").sightlineMetres)
        assertEquals(0.0, seat("""{"id":"a","label":"A-1","sightlineMetres":0}""").sightlineMetres)
        assertNull(seat("""{"id":"a","label":"A-1","sightlineMetres":-3}""").sightlineMetres)
        assertNull(seat("""{"id":"a","label":"A-1","sightlineMetres":"7"}""").sightlineMetres)
    }

    @Test
    fun halfAConfidenceDisclosureIsNoDisclosure() {
        val complete = seat(
            """{"id":"a","label":"A-1","seatViewConfidence":{"headline":"Modelled view",
               "model":"m","reality":"r","coverage":"c","provenance":"p","freshness":"f",
               "limitations":["no lighting"],"modeledTarget":"seat"}}""",
        ).seatViewConfidence

        assertEquals("Modelled view", complete?.headline)
        assertEquals(listOf("no lighting"), complete?.limitations)
        assertEquals("seat", complete?.modeledTarget)

        assertNull(
            seat("""{"id":"a","label":"A-1","seatViewConfidence":{"headline":"h","model":"m"}}""")
                .seatViewConfidence,
        )
        assertNull(
            seat(
                """{"id":"a","label":"A-1","seatViewConfidence":{"headline":"h","model":"",
                   "reality":"r","coverage":"c","provenance":"p","freshness":"f"}}""",
            ).seatViewConfidence,
        )
    }

    @Test
    fun seatNotesReadInOneFixedOrderWhateverTheSeatCarries() {
        val seat = seat(
            """{"id":"a","label":"A-1","accessibility":["wheelchair","companion","hearing"],
               "wheelchairSpaceType":"no-seat",
               "commercial":{"restrictedView":true,"obstructedView":true,"premium":true,
                             "note":"Pillar at the aisle end"}}""",
        )

        assertEquals(
            listOf(
                "access:companion",
                "access:hearing",
                "wheelchair:no-seat",
                "mark:restrictedView",
                "mark:obstructedView",
                "mark:premium",
            ),
            seat.seatNotes().map { it.key },
        )
        // The wheelchair accommodation yields to the more precise provision.
        assertFalse("access:wheelchair" in seat.seatNotes().map { it.key })
        // The organizer's sentence explains the first selling mark, not a row
        // of its own.
        assertEquals(
            "Pillar at the aisle end",
            seat.seatNotes().single { it.key == "mark:restrictedView" }.note,
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
            seat.seatNotes().map { it.tone },
        )
    }

    @Test
    fun anOrganizerSentenceWithNoMarkToExplainIsItsOwnRow() {
        val notes = seat(
            """{"id":"a","label":"A-1","commercial":{"note":"Bring photo ID"}}""",
        ).seatNotes()

        assertEquals(1, notes.size)
        assertEquals("note", notes.single().key)
        assertEquals(SeatLayerPickerSeatNoteTone.Note, notes.single().tone)
        assertEquals("Bring photo ID", notes.single().note)
    }

    @Test
    fun aSeatWithNothingToSayEarnsNoRows() {
        assertEquals(emptyList<SeatLayerPickerSeatNote>(), seat("""{"id":"a","label":"A-1"}""").seatNotes())
        assertNull(seat("""{"id":"a","label":"A-1","commercial":{"note":"  "}}""").commercial)
        // A key this build's taxonomy does not know is skipped, not printed raw.
        assertEquals(
            emptyList<SeatLayerPickerSeatNote>(),
            seat("""{"id":"a","label":"A-1","accessibility":["telepathy"]}""").seatNotes(),
        )
    }

    private fun seat(raw: String): SeatLayerPickerSelectedSeat =
        snapshot(selection = """[$raw]""").selection.single()

    private fun snapshot(
        map: String = "{}",
        sections: String = "[]",
        categories: String = "[]",
        selection: String = "[]",
    ): SeatLayerPickerSnapshot = requireNotNull(
        decodeSeatLayerPickerSnapshot(
            bridgeJson.parseToJsonElement(
                """
                {"schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA","sessionId":"s","revision":1,
                 "event":{"key":"ev"},"map":$map,
                 "catalog":{"sections":$sections,"categories":$categories},
                 "selection":{"seats":$selection}}
                """.trimIndent(),
            ),
        ),
    )
}
