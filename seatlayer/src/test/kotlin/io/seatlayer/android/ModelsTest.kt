package io.seatlayer.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun hostedAndFixtureVersionsRemainDistinctAndPinned() {
        assertEquals("0.66.0", SEATLAYER_HOSTED_WEB_VERSION)
        assertEquals("0.59.0", SEATLAYER_LEGACY_FIXTURE_WEB_VERSION)
        assertEquals(
            "https://cdn.seatlayer.io/seatlayer-js@$SEATLAYER_HOSTED_WEB_VERSION/mobile.html",
            SEATLAYER_MOBILE_PAGE_URL,
        )
    }

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

    @Test
    fun privateSelectionConfigurationMatchesMobileBridgeContract() {
        val configuration = SeatLayerConfiguration(
            event = "ev_private",
            buyerAccessToken = BuyerAccessToken("bse_seed", 123.0),
            buyerAccessTokenProvider = BuyerAccessTokenProvider {
                BuyerAccessToken("bse_${it.reason.raw}")
            },
            selectedObjects = listOf("A-1"),
            selectableObjects = listOf("A-1", "A-2"),
            numberOfPlacesToSelect = 2,
            selectionValidators = listOf(
                SelectionValidator.MinimumSelectedPlaces(2),
                SelectionValidator.ConsecutiveSeats,
            ),
        )
        val config = configuration.initPayload().objectValue("config")!!
        assertEquals("bse_seed", config.objectValue("buyerAccessToken")?.string("token"))
        assertEquals(true, config.boolean("nativeAccessProvider"))
        assertEquals(2, config.int("numberOfPlacesToSelect"))
        assertEquals(listOf("A-1"), config.array("selectedObjects")?.map { it.jsonPrimitive.content })
        assertEquals("minimumSelectedPlaces", config.array("selectionValidators")
            ?.firstOrNull()?.let { it as JsonObject }?.string("type"))
        assertTrue(configuration.usesPrivateAccess)
        assertTrue(configuration.usesSelectionPolicy)
    }

    @Test
    fun decodesTypedSelectionAndAccessEventsLossTolerantly() {
        val validity = decodeSelectionValidity(
            bridgeJson.parseToJsonElement(
                """{"isValid":false,"count":1,"required":2,"remaining":1,"seats":[{"id":"s1","label":"A-1"}],"violations":["futureRule"]}""",
            ),
        )!!
        assertEquals("A-1", validity.seats.single().label)
        assertEquals("futureRule", validity.violations.single().raw)

        val unavailable = decodeBuyerAccessUnavailable(
            bridgeJson.parseToJsonElement(
                """{"reason":"future_access_state","retryable":false}""",
            ),
        )!!
        assertEquals("future_access_state", unavailable.reason.raw)
    }
}
