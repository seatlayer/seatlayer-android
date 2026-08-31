package io.seatlayer.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatLayerPickerImmersiveTest {
    @Test
    fun `authored 3D row boundaries and explicit target beat selection fallback`() {
        val snapshot = snapshot(
            map = """
                {
                  "buyerView":"venue3d",
                  "view3dTargetSeatId":"row-seat-7",
                  "view3dTargetSeat":{"id":"row-seat-7","label":"G-7","rowLabel":"G","seatNumber":"7"},
                  "view3dPreviousSeatId":null,
                  "view3dNextSeatId":"row-seat-8",
                  "view3dFocusedSectionId":"gallery"
                }
            """.trimIndent(),
            selection = """[{"id":"selected-1","label":"A-1"}]""",
        )

        val neighbours = SeatLayerPickerImmersive.neighbours(snapshot)

        assertTrue(snapshot.map.reportsView3DPosition)
        assertNull(neighbours.previousSeatId)
        assertEquals("row-seat-8", neighbours.nextSeatId)
        assertEquals("row-seat-7", neighbours.target?.id)
        assertEquals("G", neighbours.target?.rowLabel)
        assertTrue(SeatLayerPickerImmersive.hasFocusedView(snapshot))
    }

    @Test
    fun `older runtime gets bounded selection fallback without inventing overview target`() {
        val focused = snapshot(
            map = """{"buyerView":"venue3d","view3dTargetSeatId":"s2"}""",
            selection = """[{"id":"s1","label":"A-1"},{"id":"s2","label":"A-2"},{"id":"s3","label":"A-3"}]""",
        )
        val overview = snapshot(
            map = """{"buyerView":"venue3d"}""",
            selection = """[{"id":"s1","label":"A-1"}]""",
        )

        val fallback = SeatLayerPickerImmersive.neighbours(focused)
        assertFalse(focused.map.reportsView3DPosition)
        assertEquals("s1", fallback.previousSeatId)
        assertEquals("s3", fallback.nextSeatId)
        assertEquals("s2", fallback.target?.id)
        assertNull(SeatLayerPickerImmersive.neighbours(overview).target)
    }

    @Test
    fun `3D action plans preserve target and distinguish focus from overview`() {
        val focused = snapshot(
            map = """
                {"buyerView":"venue3d","view3dTargetSeatId":"s2","view3dPreviousSeatId":"s1","view3dNextSeatId":null,"view3dFocusedSectionId":"gallery"}
            """.trimIndent(),
            selection = """[{"id":"s2","label":"A-2"}]""",
        )
        val overview = snapshot(
            map = """
                {"buyerView":"venue3d","view3dPreviousSeatId":null,"view3dNextSeatId":null,"view3dFocusedSectionId":null}
            """.trimIndent(),
        )

        assertEquals(
            SeatLayerPickerVenue3DActionPlan("venue3d", resetView = true),
            SeatLayerPickerImmersive.plan(SeatLayerPickerVenue3DAction.Back, focused),
        )
        assertEquals(
            SeatLayerPickerVenue3DActionPlan(
                view = "venue3d",
                flyToSeatId = "s2",
                resetView = true,
            ),
            SeatLayerPickerImmersive.plan(SeatLayerPickerVenue3DAction.Recenter, focused),
        )
        assertNull(SeatLayerPickerImmersive.plan(SeatLayerPickerVenue3DAction.Next, focused))
        assertFalse(SeatLayerPickerImmersive.hasFocusedView(overview))
        assertEquals(
            SeatLayerPickerVenue3DActionPlan("map"),
            SeatLayerPickerImmersive.plan(SeatLayerPickerVenue3DAction.Back, overview),
        )
    }

    private fun snapshot(
        map: String,
        selection: String = "[]",
    ): SeatLayerPickerSnapshot = decodeSeatLayerPickerSnapshot(
        bridgeJson.parseToJsonElement(
            """
            {
              "schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA",
              "sessionId":"session-3d",
              "revision":1,
              "event":{"key":"ev"},
              "map":$map,
              "selection":{"seats":$selection}
            }
            """.trimIndent(),
        ),
    )!!
}
