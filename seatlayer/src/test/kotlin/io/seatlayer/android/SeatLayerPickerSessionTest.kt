package io.seatlayer.android

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatLayerPickerSessionTest {
    @Test
    fun handshakeSendsPickerInitAndPublishesReadySnapshot() = runTest {
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val outbound = mutableListOf<Envelope>()
        val session = SeatLayerPickerSession(holder)
        session.beginHandshake(BridgeChannel(outbound::add))

        session.ingest(
            Envelope(kind = "hello", type = "hello", payload = hello(holder)).encode(),
        )
        val init = outbound.single()
        assertEquals("init", init.kind)
        assertEquals(2, (init.payload as JsonObject).objectValue("protocol")?.int("min"))
        assertEquals(
            "picker",
            (init.payload as JsonObject).objectValue("surface")?.string("kind"),
        )

        session.ingest(
            Envelope(
                kind = "evt",
                type = "sys.ready",
                payload = bridgeJson.parseToJsonElement(
                    """
                    {
                      "protocol":2,
                      "mode":"test",
                      "transport":"android",
                      "chart":{"event":"ev"},
                      "snapshot":${minimalSnapshot(1)}
                    }
                    """.trimIndent(),
                ),
            ).encode(),
        )

        val ready = session.awaitReady()
        assertEquals(2, ready.protocolRevision)
        assertEquals("test", ready.mode.raw)
        assertEquals(1, holder.state.value.snapshot?.revision)
        assertTrue(holder.state.value.isReady)
    }

    @Test
    fun handshakePreservesMonotonicTimingAndPublishesAdvertisedChartLoad() = runTest {
        var now = 1_000_000_000L
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val outbound = mutableListOf<Envelope>()
        val session = SeatLayerPickerSession(holder) { now }
        session.beginHandshake(BridgeChannel(outbound::add))

        now += 60_000_000L
        val advertised = hello(holder).toMutableMap().apply {
            this["capabilities"] = JsonArray(
                (holder.bridgeProfile.requiredCapabilities + "chart-load-trace-v1")
                    .map(::JsonPrimitive),
            )
            this["events"] = JsonArray(
                (holder.bridgeProfile.requiredEvents + "telemetry.chartLoad")
                    .map(::JsonPrimitive),
            )
        }
        session.ingest(
            Envelope("hello", "hello", payload = JsonObject(advertised)).encode(),
        )

        now += 90_000_000L
        session.ingest(
            Envelope(
                kind = "evt",
                type = "sys.ready",
                payload = bridgeJson.parseToJsonElement(
                    """{"protocol":2,"mode":"test","transport":"android","chart":{"event":"ev"},"snapshot":${minimalSnapshot(1)}}""",
                ),
            ).encode(),
        )
        val ready = session.awaitReady()
        val timing = (holder.state.value.phase as SeatLayerPickerPhase.Ready).timing
        assertEquals(60L, timing.timeToHelloMs)
        assertEquals(150L, timing.timeToReadyMs)
        assertTrue(timing.raw is JsonObject)

        val nextLoad = async(start = CoroutineStart.UNDISPATCHED) {
            holder.chartLoads.first()
        }
        session.ingest(
            Envelope(
                kind = "evt",
                type = "telemetry.chartLoad",
                payload = bridgeJson.parseToJsonElement(
                    """{"trace":{"outcome":"success","bootMs":120,"ms":40,"future":"kept"}}""",
                ),
            ).encode(),
        )

        val load = nextLoad.await()
        assertEquals(150L, load.tapToReadyMs)
        assertEquals(30L, load.hostMs)
        assertEquals(ready, load.ready)
        assertEquals(timing, load.readyTiming)
        assertEquals("kept", load.trace.raw.string("future"))
    }

    @Test
    fun missingRequiredSupportFailsBeforeInit() {
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val outbound = mutableListOf<Envelope>()
        val session = SeatLayerPickerSession(holder)
        session.beginHandshake(BridgeChannel(outbound::add))
        val incomplete = hello(holder).toMutableMap().apply {
            this["commands"] = JsonArray(
                holder.bridgeProfile.requiredCommands
                    .filterNot { it == "picker.continue" }
                    .map(::JsonPrimitive),
            )
        }

        session.ingest(
            Envelope(
                kind = "hello",
                type = "hello",
                payload = JsonObject(incomplete),
            ).encode(),
        )

        assertTrue(outbound.isEmpty())
        assertTrue(holder.state.value.phase is SeatLayerPickerPhase.Failed)
        val error = (holder.state.value.phase as SeatLayerPickerPhase.Failed).error
        assertTrue(error.message.orEmpty().contains("picker.continue"))
    }

    @Test
    fun privateAccessFailsBeforeInitWithoutNativeProviderCapability() {
        val holder = SeatLayerPickerStateHolder(
            SeatLayerConfiguration(
                event = "ev",
                buyerAccessTokenProvider = { BuyerAccessToken("private") },
            ),
        )
        val outbound = mutableListOf<Envelope>()
        val session = SeatLayerPickerSession(holder)
        session.beginHandshake(BridgeChannel(outbound::add))

        session.ingest(
            Envelope(kind = "hello", type = "hello", payload = hello(holder)).encode(),
        )

        assertTrue(outbound.isEmpty())
        val phase = holder.state.value.phase as SeatLayerPickerPhase.Failed
        assertTrue(phase.error.message.orEmpty().contains("private buyer access"))
    }

    @Test
    fun readyRequiresTheConfiguredEventAndAValidInitialSnapshot() {
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val outbound = mutableListOf<Envelope>()
        val session = SeatLayerPickerSession(holder)
        session.beginHandshake(BridgeChannel(outbound::add))
        session.ingest(
            Envelope(kind = "hello", type = "hello", payload = hello(holder)).encode(),
        )

        session.ingest(
            Envelope(
                kind = "evt",
                type = "sys.ready",
                payload = bridgeJson.parseToJsonElement(
                    """{"protocol":2,"chart":{"event":"other"},"snapshot":${minimalSnapshot(1)}}""",
                ),
            ).encode(),
        )

        val phase = holder.state.value.phase as SeatLayerPickerPhase.Failed
        assertEquals("event_mismatch", phase.error.code)
    }

    @Test
    fun readyRejectsMissingInitialSnapshot() {
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val outbound = mutableListOf<Envelope>()
        val session = SeatLayerPickerSession(holder)
        session.beginHandshake(BridgeChannel(outbound::add))
        session.ingest(
            Envelope(kind = "hello", type = "hello", payload = hello(holder)).encode(),
        )

        session.ingest(
            Envelope(
                kind = "evt",
                type = "sys.ready",
                payload = bridgeJson.parseToJsonElement(
                    """{"protocol":2,"chart":{"event":"ev"}}""",
                ),
            ).encode(),
        )

        val phase = holder.state.value.phase as SeatLayerPickerPhase.Failed
        assertEquals("invalid_snapshot", phase.error.code)
    }

    @Test
    fun originComparisonNormalizesOnlyDefaultHttpsPort() {
        assertTrue(isAllowedSeatLayerOrigin("https://cdn.seatlayer.io"))
        assertTrue(isAllowedSeatLayerOrigin("https://cdn.seatlayer.io:443"))
        assertEquals(false, isAllowedSeatLayerOrigin("http://cdn.seatlayer.io"))
        assertEquals(false, isAllowedSeatLayerOrigin("https://cdn.seatlayer.io:444"))
        assertEquals(false, isAllowedSeatLayerOrigin("https://cdn.seatlayer.io.evil.test"))
        assertEquals(false, isAllowedSeatLayerOrigin("https://cdn.seatlayer.io/path"))
        assertEquals(false, isAllowedSeatLayerOrigin("https://user@cdn.seatlayer.io"))
    }

    @Test
    fun pageNavigationAllowsOnlyThePinnedVersionedDocument() {
        assertTrue(isAllowedSeatLayerPage(SEATLAYER_MOBILE_PAGE_URL))
        assertEquals(false, isAllowedSeatLayerPage("$SEATLAYER_MOBILE_PAGE_URL#other"))
        assertEquals(false, isAllowedSeatLayerPage("https://cdn.seatlayer.io/mobile.html"))
        assertEquals(false, isAllowedSeatLayerPage("https://example.com"))
        assertEquals(false, isAllowedSeatLayerPage(null))
    }

    private fun hello(holder: SeatLayerPickerStateHolder): JsonObject = JsonObject(
        mapOf(
            "bundle" to JsonPrimitive("0.71.5"),
            "protocol" to holder.bridgeProfile.protocolRange.toJson(),
            "capabilities" to JsonArray(
                holder.bridgeProfile.requiredCapabilities.map(::JsonPrimitive),
            ),
            "events" to JsonArray(
                holder.bridgeProfile.requiredEvents.map(::JsonPrimitive),
            ),
            "commands" to JsonArray(
                holder.bridgeProfile.requiredCommands.map(::JsonPrimitive),
            ),
        ),
    )

    private fun minimalSnapshot(revision: Int): String =
        """{"schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA","sessionId":"session-a","revision":$revision,"event":{"key":"ev"}}"""
}
