package io.seatlayer.android

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The controller members that changed name, proved to still answer under the
 * old one and to send the same command either way.
 *
 * A forwarder is a promise to an existing host that nothing moved under them
 * for one more release. The only way to keep it is to watch what reaches the
 * bridge, which is what each case here records.
 */
@Suppress("DEPRECATION")
class SeatLayerPickerRenamedControllerTest {
    @Test
    fun aRemovalIsTheSameCommandUnderEitherName() = runTest {
        val sent = mutableListOf<String>()
        val controller = fixture(sent)

        controller.removeObject("A-1")
        controller.removeCartLine("A-2")

        // The Kotlin name changed; the wire word did not. A rename that
        // silently renamed the command would take the cart with it.
        assertEquals(listOf("picker.removeCartLine", "picker.removeCartLine"), sent)
    }

    @Test
    fun hidingLimitedViewSeatsIsTheSameCommandUnderEitherName() = runTest {
        val sent = mutableListOf<String>()
        val controller = fixture(sent)

        controller.setLimitedViewHidden(true)
        controller.setLimitedViewFilter(true)

        assertEquals(
            listOf("picker.setLimitedViewFilter", "picker.setLimitedViewFilter"),
            sent,
        )
    }

    @Test
    fun theMapsGesturesAndThe3DNavigationModeKeepTheirOldNamesToo() = runTest {
        val sent = mutableListOf<String>()
        val controller = fixture(sent)

        controller.setMapInteractionEnabled(false)
        controller.setInteractionEnabled(false)
        controller.set3DNavigationMode("orbit")
        controller.setVenue3DNavigationMode("orbit")

        assertEquals(
            listOf(
                "picker.setInteractionEnabled",
                "picker.setInteractionEnabled",
                "picker.setVenue3DNavigationMode",
                "picker.setVenue3DNavigationMode",
            ),
            sent,
        )
    }

    @Test
    fun aGeneralAdmissionQuantityIsTheSameCommandUnderEitherName() = runTest {
        val sent = mutableListOf<String>()
        val controller = fixture(sent)

        controller.setGeneralAdmissionQuantity("ga", 2)
        controller.holdGeneralAdmission("ga", 2)

        assertEquals(listOf("picker.holdGA", "picker.holdGA"), sent)
    }

    @Test
    fun openingTheSceneFromASeatIsTheBuyerViewCommandCarryingThatSeat() = runTest {
        val sent = mutableListOf<String>()
        val payloads = mutableListOf<JsonObject?>()
        val controller = fixture(sent, payloads)

        controller.showSeatIn3D("seat-9")

        assertEquals(listOf("picker.setBuyerView"), sent)
        assertEquals(JsonPrimitive("venue3d"), payloads.single()?.get("view"))
        assertEquals(JsonPrimitive("seat-9"), payloads.single()?.get("flyToSeatId"))
    }

    @Test
    fun handingACheckoutBackTakesTheHandoffAndSendsItsHoldId() = runTest {
        val sent = mutableListOf<String>()
        val payloads = mutableListOf<JsonObject?>()
        val controller = fixture(sent, payloads)

        controller.rejectCheckoutHandoff(
            SeatLayerPickerCheckoutHandoff(
                holdId = "h-1",
                expiresAt = 0.0,
                currency = "USD",
                lineItems = emptyList(),
                total = 0.0,
            ),
        )
        controller.rejectHandoff("h-1")

        assertEquals(listOf("picker.rejectHandoff", "picker.rejectHandoff"), sent)
        assertTrue(payloads.all { it?.get("holdId") == JsonPrimitive("h-1") })
    }

    @Test
    fun theCartSheetAndTheErrorNoticeKeepTheirOldNames() {
        val holder = holder()

        holder.controller.setCartSheetExpanded(true)
        assertTrue(holder.state.value.presentation.cartExpanded)

        holder.controller.setCartExpanded(false)
        assertEquals(false, holder.state.value.presentation.cartExpanded)

        holder.controller.setCartSheetDetent(SeatLayerPickerSheetDetent.Open)
        assertTrue(holder.state.value.presentation.cartExpanded)

        holder.controller.dismissActionError()
        assertNull(holder.state.value.presentation.lastActionError)
    }

    private fun holder(): SeatLayerPickerStateHolder =
        SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))

    private fun fixture(
        sent: MutableList<String>,
        payloads: MutableList<JsonObject?> = mutableListOf(),
    ): SeatLayerPickerController {
        val holder = holder()
        val profile = holder.bridgeProfile
        val transport = SeatLayerPickerCommandTransport { command, payload ->
            sent += command
            payloads += payload as? JsonObject
            json("{}")
        }
        holder.beginLoading()
        holder.connect(
            transport,
            BundleInfo(
                bundle = "test",
                protocolRange = profile.protocolRange,
                capabilities = profile.requiredCapabilities.toList(),
                events = profile.requiredEvents.toList(),
                commands = profile.requiredCommands.toList() + EXTRA_COMMANDS,
            ),
        )
        holder.acceptSnapshot(json(SNAPSHOT))
        holder.markReady(
            ReadyInfo(
                protocolRevision = 2,
                mode = EventMode.Live,
                transport = TransportName.Android,
                eventKey = "ev",
            ),
        )
        sent.clear()
        payloads.clear()
        return holder.controller
    }

    private companion object {
        val EXTRA_COMMANDS = listOf(
            "picker.removeCartLine",
            "picker.setLimitedViewFilter",
            "picker.setInteractionEnabled",
            "picker.setVenue3DNavigationMode",
            "picker.holdGA",
            "picker.setBuyerView",
            "picker.rejectHandoff",
        )

        const val SNAPSHOT =
            """{"schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA","sessionId":"s","revision":1,"event":{"key":"ev"}}"""

        fun json(raw: String): JsonElement = bridgeJson.parseToJsonElement(raw)
    }
}
