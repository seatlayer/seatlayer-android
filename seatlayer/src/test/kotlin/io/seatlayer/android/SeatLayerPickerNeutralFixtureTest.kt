package io.seatlayer.android

import java.security.MessageDigest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SeatLayerPickerNeutralFixtureTest {
    @Test
    fun `frozen neutral pure-helper fixtures pass on JVM`() {
        val fixtureBytes = requireNotNull(
            javaClass.getResourceAsStream(FIXTURE_RESOURCE),
        ) { "Missing $FIXTURE_RESOURCE" }.use { it.readBytes() }

        assertEquals(FIXTURE_SHA256, fixtureBytes.sha256())
        val root = bridgeJson.parseToJsonElement(fixtureBytes.decodeToString()).jsonObject
        assertEquals(1, root.required("version").jsonPrimitive.int)
        val cases = root.required("cases").jsonArray
        assertEquals(EXPECTED_CASE_IDS, cases.map { it.jsonObject.string("id") })

        cases.forEach { verifyCase(it.jsonObject) }
    }

    private fun verifyCase(case: JsonObject) {
        val input = case.required("input").jsonObject
        val expected = case.required("expected").jsonObject
        when (case.string("helper")) {
            "ticketIdentity" -> verifyTicketIdentity(input, expected)
            "confirmedCart" -> verifyConfirmedCart(input, expected)
            "totals" -> verifyTotals(input, expected)
            "denseRuns" -> verifyDenseRuns(input, expected)
            "seatRunLabel" -> verifySeatRunLabel(input, expected)
            "canUndoRemoval" -> verifyUndo(input, expected)
            "seatIdentity" -> verifySeatIdentity(input, expected)
            else -> error("Unknown frozen helper in ${case.string("id")}")
        }
    }

    private fun verifyTicketIdentity(input: JsonObject, expected: JsonObject) {
        val actual = SeatLayerPickerProjections.ticketIdentity(
            input.required("line").jsonObject.toCartLine(),
        )
        assertEquals(expected.stringOrNull("lineKey"), actual.lineKey)
        assertEquals(expected.stringOrNull("removalLabel"), actual.removalLabel)
        assertEquals(expected.stringOrNull("objectId"), actual.objectId)
        assertEquals(expected.stringOrNull("seatId"), actual.seatId)
    }

    private fun verifyConfirmedCart(input: JsonObject, expected: JsonObject) {
        val actual = SeatLayerPickerProjections.confirmedCart(
            items = input.required("items").jsonArray.map { it.jsonObject.toCartLine() },
            pending = input.required("pending").jsonObject.toSelectedSeat(),
        )
        assertEquals(expected.stringList("lineKeys"), actual.items.map { it.lineKey })
        assertTotals(expected, actual.totals)
    }

    private fun verifyTotals(input: JsonObject, expected: JsonObject) {
        val actual = SeatLayerPickerProjections.totals(
            input.required("items").jsonArray.map { it.jsonObject.toCartLine() },
        )
        assertTotals(expected, actual)
    }

    private fun verifyDenseRuns(input: JsonObject, expected: JsonObject) {
        val actual = SeatLayerPickerProjections.denseRuns(
            input.required("items").jsonArray.map { it.jsonObject.toCartLine() },
        )
        val expectedRuns = expected.required("runs").jsonArray
        assertEquals(expectedRuns.size, actual.size)
        expectedRuns.zip(actual).forEach { (expectedValue, actualRun) ->
            val expectedRun = expectedValue.jsonObject
            assertEquals(expectedRun.stringList("memberLineKeys"), actualRun.memberLineKeys)
            assertEquals(
                expectedRun.stringList("orderedMemberLineKeys"),
                actualRun.orderedMemberLineKeys,
            )
            assertEquals(expectedRun.string("seatsLabel"), actualRun.seatsLabel)
            assertEquals(expectedRun.integer("quantity"), actualRun.quantity)
            assertEquals(expectedRun.number("total"), actualRun.total, 0.0)
        }
    }

    private fun verifySeatRunLabel(input: JsonObject, expected: JsonObject) {
        assertEquals(
            expected.string("label"),
            SeatLayerPickerProjections.seatRunLabel(input.stringList("labels")),
        )
    }

    private fun verifyUndo(input: JsonObject, expected: JsonObject) {
        val actual = input.required("checks").jsonArray.map { value ->
            val check = value.jsonObject
            SeatLayerPickerProjections.canUndoRemoval(
                phase = SeatLayerPickerRemovalPhase(check.string("phase")),
                sameSession = check.boolean("sameSession"),
                stillAbsent = check.boolean("stillAbsent"),
            )
        }
        assertEquals(expected.booleanList("values"), actual)
    }

    private fun verifySeatIdentity(input: JsonObject, expected: JsonObject) {
        assertEquals(
            expected.string("identity"),
            SeatLayerPickerProjections.seatIdentity(
                input.required("seat").jsonObject.toSelectedSeat(),
            ),
        )
    }

    private fun assertTotals(
        expected: JsonObject,
        actual: SeatLayerPickerCartTotals,
    ) {
        assertEquals(expected.integer("quantity"), actual.quantity)
        assertEquals(expected.number("total"), actual.total, 0.0)
        assertEquals(expected.stringOrNull("currency"), actual.currency)
        assertEquals(expected.boolean("hasMixedCurrencies"), actual.hasMixedCurrencies)
    }

    private fun JsonObject.toCartLine(): SeatLayerPickerCartLine =
        SeatLayerPickerCartLine(
            lineKey = string("lineKey"),
            label = string("label"),
            displayLabel = stringOrNull("displayLabel"),
            displayType = stringOrNull("displayType"),
            objectId = string("objectId"),
            objectType = string("objectType"),
            categoryKey = string("categoryKey"),
            tierId = stringOrNull("tierId"),
            unitPrice = number("unitPrice"),
            currency = string("currency"),
            quantity = integer("quantity"),
            seatId = stringOrNull("seatId"),
            sectionLabel = stringOrNull("sectionLabel"),
            rowLabel = stringOrNull("rowLabel"),
            seatNumber = stringOrNull("seatNumber"),
        )

    private fun JsonObject.toSelectedSeat(): SeatLayerPickerSelectedSeat =
        SeatLayerPickerSelectedSeat(
            id = string("id"),
            label = string("label"),
            displayLabel = stringOrNull("displayLabel"),
            displayType = stringOrNull("displayType"),
            objectId = stringOrNull("objectId"),
            objectType = stringOrNull("objectType"),
            bookingMode = stringOrNull("bookingMode"),
            sectionLabel = stringOrNull("sectionLabel"),
            rowLabel = stringOrNull("rowLabel"),
            seatNumber = stringOrNull("seatNumber"),
            categoryKey = stringOrNull("categoryKey").orEmpty(),
            price = this["price"]?.jsonPrimitive?.double ?: 0.0,
            currency = stringOrNull("currency").orEmpty(),
            tiers = emptyList(),
            tierId = stringOrNull("tierId"),
            accessibility = emptyList(),
            wheelchairSpaceType = stringOrNull("wheelchairSpaceType"),
            quantity = this["quantity"]?.jsonPrimitive?.int,
            capacity = this["capacity"]?.jsonPrimitive?.int,
            minOccupancy = this["minOccupancy"]?.jsonPrimitive?.int,
            maxOccupancy = this["maxOccupancy"]?.jsonPrimitive?.int,
        )

    private fun JsonObject.required(key: String): JsonElement =
        requireNotNull(this[key]) { "Missing fixture field $key" }

    private fun JsonObject.string(key: String): String = required(key).jsonPrimitive.content

    private fun JsonObject.stringOrNull(key: String): String? {
        val value = this[key] ?: return null
        return if (value is JsonNull) null else value.jsonPrimitive.contentOrNull
    }

    private fun JsonObject.integer(key: String): Int = required(key).jsonPrimitive.int

    private fun JsonObject.number(key: String): Double = required(key).jsonPrimitive.double

    private fun JsonObject.boolean(key: String): Boolean = required(key).jsonPrimitive.boolean

    private fun JsonObject.stringList(key: String): List<String> =
        required(key).jsonArray.map { it.jsonPrimitive.content }

    private fun JsonObject.booleanList(key: String): List<Boolean> =
        required(key).jsonArray.map { it.jsonPrimitive.boolean }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val FIXTURE_RESOURCE =
            "/io/seatlayer/android/picker-pure-helpers.v1.json"
        const val FIXTURE_SHA256 =
            "ab9fae5445a07f8d8c6053828b09dd60350ccdbc3a86bdde87e918593e383522"
        val EXPECTED_CASE_IDS = listOf(
            "ticket-identity-addressed-v1",
            "confirmed-cart-per-line-addressing-v1",
            "totals-mixed-currency-v1",
            "dense-runs-adjacent-fold-and-order-v1",
            "seat-run-label-never-invents-gaps-v1",
            "undo-requires-same-session-absence-v1",
            "structural-seat-identity-v1",
        )
    }
}
