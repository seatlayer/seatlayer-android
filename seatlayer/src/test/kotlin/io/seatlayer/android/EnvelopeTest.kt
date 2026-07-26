package io.seatlayer.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvelopeTest {
    @Test
    fun roundTripsCorrelatedCommand() {
        val original = Envelope(
            kind = "cmd",
            type = "bestAvailable",
            id = "a4",
            payload = JsonObject(mapOf("qty" to JsonPrimitive(4))),
        )
        assertEquals(original, Envelope.decode(original.encode()))
    }

    @Test
    fun rejectsMalformedTraffic() {
        listOf(
            "not-json",
            "null",
            "[]",
            """{"sl":2,"k":"evt","t":"ready"}""",
            """{"sl":1,"k":"evt","t":""}""",
            """{"sl":1,"k":"evt","t":"ready","n":1.5}""",
        ).forEach { assertNull(Envelope.decode(it)) }
    }

    @Test
    fun preservesUnknownFutureKind() {
        val envelope = Envelope.decode(
            """{"sl":1,"k":"snapshot","t":"future.snapshot"}""",
        )
        assertEquals("snapshot", envelope?.kind)
    }
}
