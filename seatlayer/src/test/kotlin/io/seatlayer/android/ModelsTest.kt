package io.seatlayer.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test
    fun decodesNestedConflictDetails() {
        val details = BridgeErrorDetails.decode(
            bridgeJson.parseToJsonElement(
                """
                {
                  "code": "hold_conflict",
                  "message": "Some seats are unavailable.",
                  "details": {
                    "status": 409,
                    "conflicts": [{"label":"A-1","status":"held"}]
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals("hold_conflict", details.code)
        assertEquals(HoldConflict("A-1", "held"), details.conflicts.single())
    }

    @Test
    fun preservesUnknownModeAndTransportStrings() {
        val ready = decodeReady(
            JsonObject(
                mapOf(
                    "protocol" to JsonPrimitive(1),
                    "mode" to JsonPrimitive("future-mode"),
                    "transport" to JsonPrimitive("future-transport"),
                ),
            ),
        )
        assertEquals("future-mode", ready.mode.raw)
        assertEquals("future-transport", ready.transport.raw)
    }
}
