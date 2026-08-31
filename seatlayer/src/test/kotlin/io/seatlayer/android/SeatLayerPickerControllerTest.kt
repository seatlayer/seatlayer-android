package io.seatlayer.android

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SeatLayerPickerControllerTest {
    @Test
    fun mutationFallsBackToSnapshotWhenEventDoesNotReachTargetRevision() = runTest {
        val fixture = PickerFixture(revisionWaitMillis = 0) { command, _ ->
            when (command) {
                "picker.selectObjects" -> json("""{"revision":2}""")
                "picker.getSnapshot" -> json(
                    """{"snapshot":${minimalSnapshot(2)}}""",
                )
                else -> json("{}")
            }
        }

        val snapshot = fixture.controller.selectObjects(listOf("A-1"))

        assertEquals(2, snapshot?.revision)
        assertEquals(
            listOf("picker.selectObjects", "picker.getSnapshot"),
            fixture.commands,
        )
    }

    @Test
    fun inventoryMutationsAreSerialized() = runTest {
        val inFlight = AtomicInteger()
        val maximum = AtomicInteger()
        val revision = AtomicInteger(1)
        val fixture = PickerFixture { command, _ ->
            if (command == "picker.selectObjects") {
                val active = inFlight.incrementAndGet()
                maximum.updateAndGet { maxOf(it, active) }
                delay(100)
                inFlight.decrementAndGet()
                val next = revision.incrementAndGet()
                json("""{"snapshot":${minimalSnapshot(next)},"revision":$next}""")
            } else {
                json("{}")
            }
        }

        awaitAll(
            async { fixture.controller.selectObjects(listOf("A-1")) },
            async { fixture.controller.selectObjects(listOf("A-2")) },
        )

        assertEquals(1, maximum.get())
        assertEquals(3, fixture.holder.state.value.snapshot?.revision)
    }

    @Test
    fun overlappingCheckoutCallsShareOneHandoff() = runTest {
        val fixture = PickerFixture { command, _ ->
            if (command == "picker.continue") {
                delay(100)
                json(
                    """
                    {
                      "revision":2,
                      "snapshot":${minimalSnapshot(2)},
                      "handoff":{"holdId":"h1","expiresAt":100,"currency":"USD","lineItems":[],"total":0}
                    }
                    """.trimIndent(),
                )
            } else {
                json("{}")
            }
        }

        val handoffs = awaitAll(
            async { fixture.controller.checkout() },
            async { fixture.controller.checkout() },
        )

        assertEquals(1, fixture.commands.count { it == "picker.continue" })
        assertSame(handoffs[0], handoffs[1])
        assertEquals("h1", handoffs[0].holdId)
    }

    @Test
    fun unsupportedOptionalActionDoesNotSendBridgeCommand() = runTest {
        val fixture = PickerFixture(optional = false) { _, _ -> json("{}") }

        fixture.controller.setViewportInsets(
            SeatLayerPickerViewportInsets(top = 10.0),
        )

        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun panoramaBackUsesOptionalCloseCommandWithoutBreakingOlderRuntimes() = runTest {
        val supported = PickerFixture { _, _ -> json("{}") }
        supported.holder.acceptSeatView(
            json(
                """{
                  "seatId":"seat-1","title":"A-1","real":true,"generated":false
                }""",
            ),
        )

        assertEquals(SeatLayerPickerBackStep.Panorama, supported.controller.back())
        assertEquals(listOf("picker.closeSeatView"), supported.commands)

        val legacy = PickerFixture(optional = false) { _, _ -> json("{}") }
        assertFalse(legacy.controller.supportsSeatViewClose)
        assertFalse(legacy.controller.closeSeatView())
        assertTrue(legacy.commands.isEmpty())
    }

    @Test
    fun liveThemeUpdateCarriesResolvedRendererRolesWithoutReload() = runTest {
        val fixture = PickerFixture { _, _ -> json("{}") }

        fixture.controller.setThemeMode(
            SeatLayerPickerThemeMode.Dark,
            SeatLayerPickerMapTheme(
                background = "#000001",
                rowLabel = "#000002",
                text = "#000003",
                selection = "#000004",
            ),
        )

        assertEquals(listOf("picker.setThemeMode"), fixture.commands)
        val payload = fixture.payloads.single() as JsonObject
        assertEquals("dark", (payload["mode"] as kotlinx.serialization.json.JsonPrimitive).content)
        val mapTheme = payload["mapTheme"] as JsonObject
        assertEquals(
            setOf("background", "rowLabelColor", "textColor", "selectionColor"),
            mapTheme.keys,
        )
        assertEquals(
            "#000004",
            (mapTheme["selectionColor"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
    }

    @Test
    fun foregroundLifecycleFallsBackToAvailabilityThenFreshSnapshot() = runTest {
        val fixture = PickerFixture(revisionWaitMillis = 0) { command, _ ->
            when (command) {
                "picker.lifecycle", "picker.refreshAvailability" -> json("{}")
                "picker.getSnapshot" -> json(
                    """{"snapshot":${minimalSnapshot(2)}}""",
                )
                else -> json("{}")
            }
        }

        val result = fixture.controller.lifecycle("foreground")

        assertEquals(2, result?.snapshot?.revision)
        assertEquals(
            listOf(
                "picker.lifecycle",
                "picker.refreshAvailability",
                "picker.getSnapshot",
            ),
            fixture.commands,
        )
        assertEquals(false, fixture.holder.state.value.presentation.actionInFlight)
    }

    @Test
    fun tierConfirmationSendsTierBeforeAcceptanceAndFailureKeepsCardOpen() = runTest {
        lateinit var successful: PickerFixture
        var pendingDuringCommand = false
        successful = PickerFixture(revisionWaitMillis = 0) { command, _ ->
            if (command == "picker.setSeatTier") {
                pendingDuringCommand =
                    successful.holder.state.value.presentation.pendingSeat != null
                json("""{"snapshot":${tierSnapshot(3, "child")}}""")
            } else {
                json("{}")
            }
        }
        successful.holder.acceptSnapshot(json(tierSnapshot(2, "adult")))

        assertTrue(successful.controller.confirmPending("child"))
        assertTrue(pendingDuringCommand)
        assertEquals(listOf("picker.setSeatTier"), successful.commands)
        assertEquals("child", successful.controller.snapshot?.selection?.single()?.tierId)
        assertEquals(60.0, successful.controller.snapshot?.selection?.single()?.price ?: 0.0, 0.0)
        assertEquals(null, successful.holder.state.value.presentation.pendingSeat)

        val failed = PickerFixture(revisionWaitMillis = 0) { command, _ ->
            if (command == "picker.setSeatTier") {
                throw SeatLayerException.Transport("offline")
            }
            json("{}")
        }
        failed.holder.acceptSnapshot(json(tierSnapshot(2, "adult")))

        assertEquals(false, failed.controller.confirmPending("child"))
        assertEquals("adult", failed.holder.state.value.presentation.pendingSeat?.tierId)
        assertEquals(false, failed.holder.state.value.presentation.actionInFlight)
    }

    @Test
    fun overlappingCheckoutHandoffsInvokeHostExactlyOnce() = runTest {
        val callbacks = AtomicInteger()
        val fixture = PickerFixture { command, _ ->
            if (command == "picker.continue") {
                delay(100)
                json(
                    """{"snapshot":${minimalSnapshot(2)},"handoff":{"holdId":"h1","expiresAt":100,"currency":"USD","lineItems":[],"total":0}}""",
                )
            } else {
                json("{}")
            }
        }

        val handoffs = awaitAll(
            async {
                fixture.controller.handoffCheckout { callbacks.incrementAndGet() }
            },
            async {
                fixture.controller.handoffCheckout { callbacks.incrementAndGet() }
            },
        )

        assertEquals(1, callbacks.get())
        assertEquals(1, fixture.commands.count { it == "picker.continue" })
        assertSame(handoffs[0], handoffs[1])
    }

    @Test
    fun hostRejectionReleasesTheExactHandoff() = runTest {
        val fixture = PickerFixture { command, _ ->
            when (command) {
                "picker.continue" -> json(
                    """{"snapshot":${minimalSnapshot(2)},"handoff":{"holdId":"opaque-hold","expiresAt":100,"currency":"USD","lineItems":[],"total":0}}""",
                )
                "picker.rejectHandoff" -> json("{}")
                else -> json("{}")
            }
        }

        try {
            fixture.controller.handoffCheckout { error("host refused") }
            fail("Expected a typed handoff rejection")
        } catch (_: SeatLayerException.Transport) {
            // Expected.
        }

        assertEquals(
            listOf("picker.continue", "picker.rejectHandoff"),
            fixture.commands,
        )
        assertEquals(
            "opaque-hold",
            (fixture.payloads.last() as JsonObject)["holdId"]
                ?.let { (it as kotlinx.serialization.json.JsonPrimitive).content },
        )
        assertEquals(null, fixture.holder.state.value.presentation.checkoutHandoff)
    }

    private class PickerFixture(
        optional: Boolean = true,
        revisionWaitMillis: Long = 2_000,
        handler: suspend (String, JsonElement?) -> JsonElement?,
    ) {
        val commands = mutableListOf<String>()
        val payloads = mutableListOf<JsonElement?>()
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val controller = SeatLayerPickerController(holder, revisionWaitMillis)

        init {
            val profile = holder.bridgeProfile
            val optionalCapabilities = if (optional) profile.optionalCapabilities else emptySet()
            val optionalCommands = if (optional) {
                setOf(
                    "picker.setViewportInsets",
                    "picker.refreshAvailability",
                    "picker.holdSelection",
                    "picker.closeSeatView",
                )
            } else {
                emptySet()
            }
            val bundle = BundleInfo(
                bundle = "test",
                protocolRange = profile.protocolRange,
                capabilities = (profile.requiredCapabilities + optionalCapabilities).toList(),
                events = (
                    profile.requiredEvents + if (optional) setOf("seatView.changed") else emptySet()
                    ).toList(),
                commands = (profile.requiredCommands + optionalCommands).toList(),
            )
            val transport = SeatLayerPickerCommandTransport { command, payload ->
                commands += command
                payloads += payload
                handler(command, payload)
            }
            holder.beginLoading()
            holder.connect(transport, bundle)
            controller.connect(transport, bundle)
            holder.acceptSnapshot(json(minimalSnapshot(1)))
            holder.markReady(
                ReadyInfo(
                    protocolRevision = 2,
                    mode = EventMode.Live,
                    transport = TransportName.Android,
                    eventKey = "ev",
                ),
            )
        }
    }

    private companion object {
        fun minimalSnapshot(revision: Int): String =
            """{"schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA","sessionId":"session-a","revision":$revision,"event":{"key":"ev"}}"""

        fun tierSnapshot(revision: Int, tierId: String): String {
            val price = if (tierId == "child") 60 else 100
            return """
                {
                  "schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA",
                  "sessionId":"session-a",
                  "revision":$revision,
                  "event":{"key":"ev","currency":"EUR"},
                  "selection":{"seats":[{
                    "id":"seat-1","label":"G-1","price":$price,"currency":"EUR",
                    "tierId":"$tierId",
                    "tiers":[
                      {"id":"adult","name":"Adult","price":100,"currency":"EUR"},
                      {"id":"child","name":"Child","price":60,"currency":"EUR"}
                    ]
                  }]},
                  "cart":{"items":[{"label":"G-1","seatId":"seat-1","tierId":"$tierId","unitPrice":$price,"currency":"EUR","quantity":1}]}
                }
            """.trimIndent()
        }

        fun json(value: String): JsonElement = bridgeJson.parseToJsonElement(value)
    }
}
