package io.seatlayer.android

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatLayerPickerSnapshotTest {
    @Test
    fun decodesRequiredIdentityAndToleratesMalformedOptionalEntries() {
        val snapshot = decodeSeatLayerPickerSnapshot(
            json(
                """
                {
                  "schema":"seatlayer.picker.snapshot/1",
                  "sessionId":"session-a",
                  "revision":7.0,
                  "event":{"key":"ev_live","mode":"future-mode"},
                  "branding":{"tokens":{"accent":"#123456","radius":12}},
                  "catalog":{
                    "categories":[
                      {"key":"standard","tiers":[{"id":"adult","name":"Adult","price":25,"currency":"GBP","restriction":"companion","buyerMessage":"Must accompany an access ticket"}]},
                      {"label":"missing-key"},
                      "future-entry"
                    ],
                    "zones":[{"id":"floor","label":"Floor"},{}],
                    "sections":[{"id":"a","seatsLeft":4.0},{"id":"bad","seatsLeft":4.5}],
                    "gaAreas":[{"id":"ga","label":"Standing","available":50}],
                    "bestAvailableZones":[{"id":"best"}]
                  },
                  "map":{
                    "projection":"iso",
                    "viewportInsets":{"top":-4,"right":8},
                    "categoryFilter":["standard","standard"],
                    "accessNeeds":[{"key":" wheelchair ","count":3},{"key":"wheelchair","count":9}]
                  },
                  "selection":{
                    "seats":[{"id":"s1","label":"A-1"},{"label":"bad"}],
                    "maxSelection":6
                  },
                  "cart":{
                    "items":[{"label":"A-1","unitPrice":25,"quantity":2}],
                    "currency":"GBP"
                  },
                  "hold":{"active":true,"expiresAt":1234,"ownership":"picker"},
                  "features":{"floorStack":true,"seatView":["native"],"empty":[]},
                  "future":{"anything":true}
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals("session-a", snapshot.sessionId)
        assertEquals(7, snapshot.revision)
        assertEquals("future-mode", snapshot.event.mode.raw)
        assertEquals("USD", snapshot.event.currency)
        assertEquals("#123456", snapshot.branding.accent)
        assertEquals(1, snapshot.categories.size)
        assertEquals(25.0, snapshot.categories.single().priceMin, 0.0)
        assertEquals("GBP", snapshot.categories.single().tiers.single().currency)
        assertEquals("companion", snapshot.categories.single().tiers.single().restriction)
        assertEquals(
            "Must accompany an access ticket",
            snapshot.categories.single().tiers.single().buyerMessage,
        )
        assertEquals(1, snapshot.selection.size)
        assertEquals(50.0, snapshot.cartTotal, 0.0)
        assertEquals("GBP", snapshot.currency)
        assertEquals("picker", snapshot.hold.owner)
        assertEquals("iso", snapshot.map.viewMode)
        assertEquals(0.0, snapshot.map.viewportInsets?.top ?: -1.0, 0.0)
        assertEquals(listOf("standard"), snapshot.map.categoryFilter)
        assertEquals(1, snapshot.map.accessNeeds.size)
        assertEquals(setOf("floorStack", "seatView"), snapshot.capabilities)
        assertTrue((snapshot.raw as JsonObject).containsKey("future"))
    }

    @Test
    fun rejectsOnlyBrokenSnapshotIdentity() {
        val valid = minimalSnapshot(revision = "1")
        assertTrue(decodeSeatLayerPickerSnapshot(json(valid)) != null)

        assertNull(decodeSeatLayerPickerSnapshot(json(minimalSnapshot(revision = "1.25"))))
        assertNull(decodeSeatLayerPickerSnapshot(json(minimalSnapshot(revision = "\"1\""))))
        assertNull(
            decodeSeatLayerPickerSnapshot(
                json(valid.replace("session-a", "")),
            ),
        )
        assertNull(
            decodeSeatLayerPickerSnapshot(
                json(valid.replace(SEATLAYER_PICKER_SNAPSHOT_SCHEMA, "future-schema")),
            ),
        )
        assertNull(
            decodeSeatLayerPickerSnapshot(
                json(valid.replace("\"key\":\"ev\"", "\"name\":\"No key\"")),
            ),
        )
    }

    @Test
    fun stateHolderDropsEqualStaleAndDifferentSessionSnapshots() {
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val first = decodeSeatLayerPickerSnapshot(json(minimalSnapshot("3")))!!
        val equal = decodeSeatLayerPickerSnapshot(json(minimalSnapshot("3")))!!
        val stale = decodeSeatLayerPickerSnapshot(json(minimalSnapshot("2")))!!
        val next = decodeSeatLayerPickerSnapshot(json(minimalSnapshot("4")))!!
        val other = decodeSeatLayerPickerSnapshot(
            json(minimalSnapshot("5").replace("session-a", "session-b")),
        )!!

        assertTrue(holder.acceptSnapshot(first))
        assertFalse(holder.acceptSnapshot(equal))
        assertFalse(holder.acceptSnapshot(stale))
        assertFalse(holder.acceptSnapshot(other))
        assertTrue(holder.acceptSnapshot(next))
        assertEquals(4, holder.state.value.snapshot?.revision)
    }

    @Test
    fun stateHolderRejectsAValidSnapshotForAnotherEvent() {
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val otherEvent = decodeSeatLayerPickerSnapshot(
            json(
                minimalSnapshot("1")
                    .replace("\"key\":\"ev\"", "\"key\":\"other\""),
            ),
        )!!

        assertFalse(holder.acceptSnapshot(otherEvent))
        assertNull(holder.state.value.snapshot)
    }

    @Test
    fun presentationSelectsNewestUnansweredSeatAcrossRevisions() {
        val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = "ev"))
        val first = decodeSeatLayerPickerSnapshot(
            json(
                minimalSnapshot("1").dropLast(1) +
                    ",\"selection\":{\"seats\":[{\"id\":\"s1\",\"label\":\"A-1\"}]}}",
            ),
        )!!
        val second = decodeSeatLayerPickerSnapshot(
            json(
                minimalSnapshot("2").dropLast(1) +
                    ",\"selection\":{\"seats\":[" +
                    "{\"id\":\"s1\",\"label\":\"A-1\"}," +
                    "{\"id\":\"s2\",\"label\":\"A-2\"}]}}",
            ),
        )!!

        assertTrue(holder.acceptSnapshot(first))
        assertEquals("A-1", holder.state.value.presentation.pendingSeat?.label)
        holder.controller.confirmPending()
        assertNull(holder.state.value.presentation.pendingSeat)
        assertTrue(holder.acceptSnapshot(second))
        assertEquals("A-2", holder.state.value.presentation.pendingSeat?.label)
    }

    @Test
    fun checkoutAndAvailabilityResultsAreNormalized() {
        val handoff = decodeSeatLayerPickerCheckoutHandoff(
            json(
                """{"holdId":"h1","expiresAt":99,"lineItems":[{"label":"A-1","unitPrice":20,"quantity":2}]}""",
            ),
        )!!
        assertEquals("USD", handoff.currency)
        assertEquals(40.0, handoff.total, 0.0)

        val outcome = decodeSeatLayerPickerAvailabilityOutcome(
            json(
                """{"refreshed":true,"holdLapsed":true,"lapsed":["A-1","A-2"],"recoverable":["A-2","other"]}""",
            ),
        )!!
        assertEquals(listOf("A-2"), outcome.recoverableLabels)
        assertEquals(SeatLayerPickerRecovery.Partial, outcome.recovery)
    }

    private fun minimalSnapshot(revision: String): String =
        """{"schema":"$SEATLAYER_PICKER_SNAPSHOT_SCHEMA","sessionId":"session-a","revision":$revision,"event":{"key":"ev"}}"""

    private fun json(value: String) = bridgeJson.parseToJsonElement(value)
}
