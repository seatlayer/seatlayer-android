package io.seatlayer.android.compose

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Samples the generated constants against the design source they came from.
 *
 * The Gradle design check proves the generator ran on the locked files; this
 * proves the generator read them the way the chrome does — a colour as ARGB, a
 * size as dp, a duration as whole milliseconds, a curve as four numbers.
 */
class SeatLayerPickerDesignTokensTest {
    private val tokens: JsonObject = Json.parseToJsonElement(
        File(
            System.getProperty("seatlayer.designDirectory")
                ?: error("seatlayer.designDirectory is not set"),
            "tokens.json",
        ).readText(),
    ).jsonObject

    private fun group(name: String): JsonObject = tokens.getValue(name).jsonObject

    private fun number(vararg path: String): Double {
        var node = tokens
        path.dropLast(1).forEach { node = node.getValue(it).jsonObject }
        return node.getValue(path.last()).jsonPrimitive.content.toDouble()
    }

    private fun text(vararg path: String): String {
        var node = tokens
        path.dropLast(1).forEach { node = node.getValue(it).jsonObject }
        return node.getValue(path.last()).jsonPrimitive.content
    }

    private fun argb(value: String): Long {
        val raw = value.removePrefix("#")
        return (if (raw.length == 6) "FF$raw" else raw).uppercase().toLong(radix = 16)
    }

    @Test
    fun coloursCarryTheDesignAlphaAndChannels() {
        assertEquals(argb(text("color", "light", "background")), SeatLayerLightTokens.BACKGROUND)
        assertEquals(argb(text("color", "light", "chrome")), SeatLayerLightTokens.CHROME)
        assertEquals(argb(text("color", "light", "premiumText")), SeatLayerLightTokens.PREMIUM_TEXT)
        assertEquals(argb(text("color", "dark", "surface")), SeatLayerDarkTokens.SURFACE)
        assertEquals(
            argb(text("color", "dark", "immersiveGlass")),
            SeatLayerDarkTokens.IMMERSIVE_GLASS,
        )
    }

    @Test
    fun geometryIsGeneratedInDesignUnits() {
        assertEquals(number("size", "headerHeight").toFloat(), SeatLayerSizeTokens.HEADER_HEIGHT, 0f)
        assertEquals(number("size", "peekHeight").toFloat(), SeatLayerSizeTokens.PEEK_HEIGHT, 0f)
        assertEquals(
            number("size", "sheetMaxHeightFraction").toFloat(),
            SeatLayerSizeTokens.SHEET_MAX_HEIGHT_FRACTION,
            0f,
        )
        assertEquals(number("radius", "button").toFloat(), SeatLayerRadiusTokens.BUTTON, 0f)
        assertEquals(
            number("radius", "confirmCard").toFloat(),
            SeatLayerRadiusTokens.CONFIRM_CARD,
            0f,
        )
        assertEquals(number("elevation", "sheet").toFloat(), SeatLayerElevationTokens.SHEET, 0f)
        assertEquals(
            number("opacity", "confirmScrim").toFloat(),
            SeatLayerOpacityTokens.CONFIRM_SCRIM,
            0f,
        )
        assertEquals(
            number("opacity", "mapControlDisabled").toFloat(),
            SeatLayerOpacityTokens.MAP_CONTROL_DISABLED,
            0f,
        )
    }

    @Test
    fun typeCarriesSizeWeightAndTheClampsThatKeepLayoutsIntact() {
        assertEquals(number("type", "cartCardName", "size").toFloat(), SeatLayerTypeTokens.CART_CARD_NAME_SIZE, 0f)
        assertEquals(number("type", "cartCardName", "weight").toInt(), SeatLayerTypeTokens.CART_CARD_NAME_WEIGHT)
        assertEquals(number("type", "peekSummary", "size").toFloat(), SeatLayerTypeTokens.PEEK_SUMMARY_SIZE, 0f)
        assertEquals(number("type", "scaleClamp", "rail").toFloat(), SeatLayerTypeTokens.SCALE_CLAMP_RAIL, 0f)
        assertEquals(number("type", "scaleClamp", "sheet").toFloat(), SeatLayerTypeTokens.SCALE_CLAMP_SHEET, 0f)
    }

    @Test
    fun motionPhysicsAndCurvesArriveAsNumbersNotNames() {
        assertEquals(number("motion", "duration", "sheet").toInt(), SeatLayerMotionTokens.SHEET)
        assertEquals(number("motion", "durationOutsideBudget", "toastDwell").toInt(), SeatLayerMotionTokens.TOAST_DWELL)
        assertEquals(number("motion", "budgetMs").toInt(), SeatLayerMotionTokens.BUDGET_MS)
        assertEquals(
            number("motion", "physics", "sheetSpringStiffness").toFloat(),
            SeatLayerPhysicsTokens.SHEET_SPRING_STIFFNESS,
            0f,
        )
        val spring = group("motion").getValue("curve").jsonObject
            .getValue("spring").jsonObject
            .getValue("cubicBezier")
        val control = spring.toString().trim('[', ']').split(',').map { it.trim().toFloat() }
        assertEquals(control[0], SeatLayerCurveTokens.SPRING_X1, 0f)
        assertEquals(control[1], SeatLayerCurveTokens.SPRING_Y1, 0f)
        assertEquals(control[2], SeatLayerCurveTokens.SPRING_X2, 0f)
        assertEquals(control[3], SeatLayerCurveTokens.SPRING_Y2, 0f)
    }

    @Test
    fun hapticCuesAndStringDefaultsMatchTheSource() {
        assertEquals(text("haptics", "holdExpired"), SeatLayerHapticTokens.HOLD_EXPIRED)
        assertEquals(text("haptics", "sectionFocused"), SeatLayerHapticTokens.SECTION_FOCUSED)
        assertEquals(text("strings", "removeSeat"), SeatLayerStringTokens.REMOVE_SEAT)
        assertEquals(text("strings", "fitWholeVenue"), SeatLayerStringTokens.FIT_WHOLE_VENUE)
        assertEquals(text("strings", "notAvailable"), SeatLayerStringTokens.NOT_AVAILABLE)
    }

    @Test
    fun everyDesignTokenReachedAGeneratedConstant() {
        assertEquals(171, group("size").size)
        assertEquals(210, group("strings").size)
        assertEquals(9, group("opacity").size)
        // Retired with the staged accessibility "Apply" button; a stale copy of
        // this token is how a deleted surface stays half-built.
        assertTrue("applyFilters" !in group("strings"))
    }
}
