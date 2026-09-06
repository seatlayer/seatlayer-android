package io.seatlayer.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeatLayerPickerClosurePlanningTest {
    @Test
    fun `cart motion starts only after the pending seat is authoritatively retained`() {
        val pending = seat()
        val retained = snapshot(
            cart = """{"items":[{"label":"A-2","seatId":"seat-2"}]}""",
        )
        val removed = snapshot(cart = """{"items":[]}""")

        assertEquals(pending, SeatLayerPickerMotion.newlyConfirmedSeat(pending, null, retained))
        assertNull(SeatLayerPickerMotion.newlyConfirmedSeat(pending, null, removed))
        assertNull(SeatLayerPickerMotion.newlyConfirmedSeat(pending, pending, retained))
    }

    private fun seat(): SeatLayerPickerSelectedSeat = SeatLayerPickerSelectedSeat(
        id = "seat-2",
        label = "A-2",
        displayLabel = null,
        displayType = null,
        objectId = null,
        objectType = "seat",
        bookingMode = null,
        sectionLabel = null,
        rowLabel = "A",
        seatNumber = "2",
        categoryKey = "standard",
        price = 20.0,
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

    private fun snapshot(cart: String): SeatLayerPickerSnapshot =
        decodeSeatLayerPickerSnapshot(
            bridgeJson.parseToJsonElement(
                """
                {
                  "schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA",
                  "sessionId":"motion",
                  "revision":1,
                  "event":{"key":"ev"},
                  "cart":$cart
                }
                """.trimIndent(),
            ),
        )!!
}
