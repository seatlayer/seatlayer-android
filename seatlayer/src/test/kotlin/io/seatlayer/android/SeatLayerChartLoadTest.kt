package io.seatlayer.android

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatLayerChartLoadTest {
    @Test
    fun `open trace retains additive fields and ignores invalid metrics`() {
        val trace = decodeSeatLayerChartLoadEvent(
            bridgeJson.parseToJsonElement(
                """
                {
                  "trace":{
                    "event":"ev",
                    "outcome":"failed",
                    "bootMs":820,
                    "ms":-1,
                    "chartBytes":4096,
                    "futureMetric":{"value":4}
                  }
                }
                """.trimIndent(),
            ),
        )!!

        assertFalse(trace.succeeded)
        assertNull(trace.ms)
        assertEquals(820, trace.bootMs)
        assertEquals(4096, trace.chartBytes)
        assertTrue((trace.raw["futureMetric"] as JsonObject).containsKey("value"))
    }

    @Test
    fun `host timing is derived without reporting a negative span`() {
        val trace = SeatLayerChartLoadTrace(
            raw = JsonObject(emptyMap()),
            bootMs = 1_020,
        )

        assertEquals(0L, SeatLayerChartLoad(trace, 1_000, null).hostMs)
        assertEquals(80L, SeatLayerChartLoad(trace, 1_100, null).hostMs)
        assertTrue(SeatLayerChartLoadTrace(JsonObject(emptyMap())).succeeded)
    }
}
