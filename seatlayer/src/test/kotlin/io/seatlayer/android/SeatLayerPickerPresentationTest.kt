package io.seatlayer.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatLayerPickerPresentationTest {
    @Test
    fun `confirmed cart removes the exact addressed pending line and label fallback`() {
        val lines = listOf(
            line("one", "same", "seat-1", 25.0),
            line("two", "same", "seat-2", 30.0),
            line("three", "same", null, 20.0),
            line("four", "other", null, 15.0),
        )
        val projection = SeatLayerPickerProjections.confirmedCart(
            items = lines,
            pending = seat(id = "seat-2", label = "same"),
        )

        assertEquals(listOf("one", "four"), projection.items.map { it.lineKey })
        assertEquals(2, projection.totals.quantity)
        assertEquals(40.0, projection.totals.total, 0.0)
        assertEquals("EUR", projection.totals.currency)
    }

    @Test
    fun `mixed currencies never claim one aggregate currency`() {
        val totals = SeatLayerPickerProjections.totals(
            listOf(
                line("ga", "GA", null, 12.0, quantity = 3, currency = "EUR"),
                line("table", "Table 1", null, 20.0, quantity = 4, currency = "USD"),
            ),
        )

        assertEquals(7, totals.quantity)
        assertEquals(116.0, totals.total, 0.0)
        assertNull(totals.currency)
        assertTrue(totals.hasMixedCurrencies)
    }

    @Test
    fun `dense runs order addressed seats without changing source identity`() {
        val lines = listOf(
            line("three", "A-3", "seat-3", 25.0, seatNumber = "3"),
            line("one", "A-1", "seat-1", 25.0, seatNumber = "1"),
            line("two", "A-2", "seat-2", 25.0, seatNumber = "2"),
        )

        val run = SeatLayerPickerProjections.denseRuns(lines).single()

        assertEquals(listOf("three", "one", "two"), run.memberLineKeys)
        assertEquals(listOf("one", "two", "three"), run.orderedMemberLineKeys)
        assertEquals("1–3", run.seatsLabel)
        assertEquals(3, run.quantity)
        assertEquals(75.0, run.total, 0.0)
    }

    @Test
    fun `seat labels never invent a range across gaps`() {
        assertEquals(
            "1, 2, 4 +2",
            SeatLayerPickerProjections.seatRunLabel(listOf("1", "2", "4", "5", "6")),
        )
    }

    @Test
    fun `undo requires its window the same session and continued absence`() {
        assertTrue(
            SeatLayerPickerProjections.canUndoRemoval(
                SeatLayerPickerRemovalPhase.UndoWindow,
                sameSession = true,
                stillAbsent = true,
            ),
        )
        assertFalse(
            SeatLayerPickerProjections.canUndoRemoval(
                SeatLayerPickerRemovalPhase.AwaitingRemove,
                sameSession = true,
                stillAbsent = true,
            ),
        )
        assertFalse(
            SeatLayerPickerProjections.canUndoRemoval(
                SeatLayerPickerRemovalPhase.UndoWindow,
                sameSession = false,
                stillAbsent = true,
            ),
        )
        assertFalse(
            SeatLayerPickerProjections.canUndoRemoval(
                SeatLayerPickerRemovalPhase.UndoWindow,
                sameSession = true,
                stillAbsent = false,
            ),
        )
    }

    @Test
    fun `back ladder selects exactly the top presentation layer`() {
        val pending = seat(id = "seat-2", label = "A-2")
        val ga = SeatLayerPickerGeneralAdmissionArea(
            id = "ga",
            label = "Standing",
            capacity = null,
            available = 20,
            categoryKey = null,
            price = null,
            currency = null,
            tiers = emptyList(),
        )
        val section = snapshot(map = """{"focusedSectionId":"section-a"}""")
        val venue3D = snapshot(map = """{"buyerView":"venue3d"}""")
        val uninitializedSeats = snapshot(map = """{"rung":"seats"}""")
        val navigableSeats = snapshot(
            map = """{"rung":"seats"}""",
            catalog =
                """{"categories":[{"key":"live","available":1,"notForSale":false}]}""",
        )

        assertEquals(
            SeatLayerPickerBackStep.Prompt,
            SeatLayerPickerPresentationState(
                activePrompt = SeatLayerPickerPrompt.GeneralAdmission(ga),
                cartExpanded = true,
                pendingSeat = pending,
            ).nextBackStep(section),
        )
        assertEquals(
            SeatLayerPickerBackStep.Cart,
            SeatLayerPickerPresentationState(
                cartExpanded = true,
                pendingSeat = pending,
            ).nextBackStep(section),
        )
        assertEquals(
            SeatLayerPickerBackStep.Confirmation,
            SeatLayerPickerPresentationState(pendingSeat = pending).nextBackStep(section),
        )
        assertEquals(
            SeatLayerPickerBackStep.Section,
            SeatLayerPickerPresentationState().nextBackStep(section),
        )
        assertEquals(
            SeatLayerPickerBackStep.Venue3D,
            SeatLayerPickerPresentationState().nextBackStep(venue3D),
        )
        assertEquals(
            SeatLayerPickerBackStep.Close,
            SeatLayerPickerPresentationState().nextBackStep(uninitializedSeats),
        )
        assertEquals(
            SeatLayerPickerBackStep.Section,
            SeatLayerPickerPresentationState().nextBackStep(navigableSeats),
        )
        assertEquals(
            SeatLayerPickerBackStep.Close,
            SeatLayerPickerPresentationState().nextBackStep(snapshot()),
        )
        assertEquals(
            SeatLayerPickerBackStep.Venue3D,
            SeatLayerPickerPresentationState(pendingSeat = pending).nextBackStep(venue3D),
        )
        assertEquals(
            SeatLayerPickerBackStep.Panorama,
            SeatLayerPickerPresentationState(pendingSeat = pending).nextBackStep(
                venue3D,
                SeatLayerSeatView(
                    seatId = "seat-2",
                    title = "A-2",
                    caption = null,
                    badge = null,
                    real = true,
                    generated = false,
                    dragHint = null,
                ),
            ),
        )
    }

    @Test
    fun `empty state requires affirmative inventory evidence`() {
        assertFalse(SeatLayerPickerProjections.isProvenEmpty(snapshot()))
        assertTrue(
            SeatLayerPickerProjections.isProvenEmpty(
                snapshot(event = """{"key":"ev","salesClosed":true}"""),
            ),
        )
        assertTrue(
            SeatLayerPickerProjections.isProvenEmpty(
                snapshot(
                    catalog =
                        """{"categories":[{"key":"sold","available":0,"notForSale":true}]}""",
                ),
            ),
        )
        assertFalse(
            SeatLayerPickerProjections.isProvenEmpty(
                snapshot(
                    catalog =
                        """{"categories":[{"key":"live","available":1,"notForSale":false}]}""",
                ),
            ),
        )
    }

    private fun line(
        key: String,
        label: String,
        seatId: String?,
        price: Double,
        quantity: Int = 1,
        currency: String = "EUR",
        seatNumber: String? = null,
    ): SeatLayerPickerCartLine = SeatLayerPickerCartLine(
        lineKey = key,
        label = label,
        displayLabel = null,
        displayType = null,
        objectId = "object-$key",
        objectType = "seat",
        categoryKey = "standard",
        tierId = null,
        unitPrice = price,
        currency = currency,
        quantity = quantity,
        seatId = seatId,
        sectionLabel = "Gallery",
        rowLabel = "A",
        seatNumber = seatNumber,
    )

    private fun seat(id: String, label: String): SeatLayerPickerSelectedSeat =
        SeatLayerPickerSelectedSeat(
            id = id,
            label = label,
            displayLabel = null,
            displayType = null,
            objectId = "row-a",
            objectType = "seat",
            bookingMode = null,
            sectionLabel = "Gallery",
            rowLabel = "A",
            seatNumber = "2",
            categoryKey = "standard",
            price = 30.0,
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

    private fun snapshot(
        event: String = """{"key":"ev"}""",
        map: String = "{}",
        catalog: String = "{}",
    ): SeatLayerPickerSnapshot = decodeSeatLayerPickerSnapshot(
        bridgeJson.parseToJsonElement(
            """
            {
              "schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA",
              "sessionId":"session-a",
              "revision":1,
              "event":$event,
              "map":$map,
              "catalog":$catalog
            }
            """.trimIndent(),
        ),
    )!!
}
