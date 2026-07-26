package io.seatlayer.android

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeClientTest {
    private class RecordingChannel : BridgeChannel {
        val sent = mutableListOf<Envelope>()
        override fun send(envelope: Envelope) {
            sent += envelope
        }
    }

    @Test
    fun correlatesResponseById() = runTest {
        val channel = RecordingChannel()
        val client = BridgeClient(channel, timeoutMillis = 1_000)
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            client.command("getSelection")
        }
        val command = channel.sent.single()
        client.ingest(
            Envelope(
                kind = "res",
                type = command.type,
                id = command.id,
                payload = JsonObject(mapOf("ok" to JsonPrimitive(true))),
            ),
        )
        assertEquals(true, (result.await() as JsonObject).boolean("ok"))
    }

    @Test
    fun turnsOutOfBandHoldErrorIntoCommandFailure() = runTest {
        supervisorScope {
            val channel = RecordingChannel()
            val client = BridgeClient(channel, timeoutMillis = 1_000)
            val result = async(start = CoroutineStart.UNDISPATCHED) {
                client.command("bestAvailable")
            }
            client.ingest(
                Envelope(
                    kind = "evt",
                    type = "error",
                    sequence = 1,
                    payload = JsonObject(
                        mapOf(
                            "code" to JsonPrimitive("sold_out"),
                            "message" to JsonPrimitive("No seats remain."),
                        ),
                    ),
                ),
            )
            val failure = runCatching { result.await() }.exceptionOrNull()
            assertTrue(failure is SeatLayerException.Bridge)
            assertEquals("sold_out", (failure as SeatLayerException.Bridge).code)
        }
    }

    @Test
    fun dropsStaleEventsPerTopic() {
        val client = BridgeClient(RecordingChannel(), timeoutMillis = 1_000)
        val seen = mutableListOf<Int?>()
        client.signalHandler = {
            if (it is BridgeSignal.Event) seen += it.sequence
        }
        client.ingest(Envelope("evt", "selection.changed", sequence = 2))
        client.ingest(Envelope("evt", "selection.changed", sequence = 1))
        client.ingest(Envelope("evt", "hold.changed", sequence = 1))
        assertEquals(listOf(2, 1), seen)
    }

    @Test
    fun lateReplyAfterTimeoutIsIgnored() = runTest {
        supervisorScope {
            val channel = RecordingChannel()
            val client = BridgeClient(channel, timeoutMillis = 10)
            val result = async(start = CoroutineStart.UNDISPATCHED) {
                client.command("hold")
            }
            val id = channel.sent.single().id
            val failure = runCatching { result.await() }.exceptionOrNull()
            assertTrue(failure is SeatLayerException.Timeout)
            client.ingest(Envelope("res", "hold", id = id))
        }
    }
}
