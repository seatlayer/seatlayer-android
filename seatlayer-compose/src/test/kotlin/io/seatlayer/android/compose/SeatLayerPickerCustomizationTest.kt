package io.seatlayer.android.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SEATLAYER_ALL_FLOORS
import io.seatlayer.android.SeatLayerPickerFloorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SeatLayerPickerCustomizationTest {
    @Test
    fun exposesExactlyTheCanonicalTwentyFiveReplacementPoints() {
        assertEquals(25, SeatLayerPickerPart.entries.size)
    }

    @Test
    fun builderResolutionDoesNotAliasIndependentParts() {
        val header: SeatLayerPickerPartBuilder = { _, defaultChild -> defaultChild() }
        val builders = SeatLayerPickerBuilders(header = header)

        assertSame(header, builders[SeatLayerPickerPart.Header])
        assertNull(builders[SeatLayerPickerPart.Legend])
    }

    @Test
    fun partStylesRemainIndependentAndUnspecifiedPartsUseNeutralDefaults() {
        val checkout = SeatLayerPickerPartStyle(
            containerColor = Color(0xFF112233),
            cornerRadius = 18.dp,
        )
        val styles = SeatLayerPickerStyles(
            parts = mapOf(SeatLayerPickerPart.CheckoutBar to checkout),
        )

        assertEquals(checkout, styles[SeatLayerPickerPart.CheckoutBar])
        assertEquals(SeatLayerPickerPartStyle(), styles[SeatLayerPickerPart.CartSheet])
    }

    @Test
    fun compactMapControlsAccountForEveryVisibleButton() {
        // Three discs on the phone: the accessibility disc at the head of the
        // column, `+`, and the whole venue. There is no `−` — a control that
        // only sometimes had a step to take read as one that sometimes worked
        // — and the colourblind choice lives in the accessibility sheet.
        assertEquals(3, compactMapControlCount(SeatLayerPickerChromeOptions()))
        assertEquals(120.dp, compactMapControlsRequiredHeight(3))
        assertEquals(
            36.dp,
            compactMapControlsRequiredHeight(
                compactMapControlCount(
                    SeatLayerPickerChromeOptions(
                        showAccessibilityControl = false,
                        showZoomControls = false,
                        showZoomToFitControl = true,
                        showOverviewControl = true,
                        showColorblindControl = false,
                    ),
                ),
            ),
        )
    }

    @Test
    fun floorChoicesOwnOneAllFloorsSentinelAndDeduplicateAuthoredIds() {
        val floors = listOf(
            SeatLayerPickerFloorInfo(SEATLAYER_ALL_FLOORS, "All floors", null),
            SeatLayerPickerFloorInfo("lower", "Lower", 1),
            SeatLayerPickerFloorInfo("lower", "Duplicate lower", 1),
            SeatLayerPickerFloorInfo("upper", "Upper", 2),
        )

        assertEquals(
            listOf("lower", "upper"),
            floors.pickerAuthoredFloors().map(SeatLayerPickerFloorInfo::id),
        )
    }

    @Test
    fun themeColorParsingAcceptsSixAndEightDigitHexOnly() {
        assertEquals(Color(0xFF112233), parseColor("#112233"))
        assertEquals(Color(0x80112233), parseColor("80112233"))
        assertNull(parseColor("#123"))
        assertNull(parseColor("not-a-color"))
    }
}
