package io.seatlayer.android.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The old names kept for one release, proved to still read the new ones.
 *
 * A forwarder that compiles but answers a stale field is worse than a removal:
 * a host upgrading sees no error and a chrome switch that quietly stops
 * working. Each case here reads the old name after setting only the new one.
 */
@Suppress("DEPRECATION")
class SeatLayerPickerRenamedApiTest {
    @Test
    fun everyChromeSwitchAnswersUnderBothNames() {
        val chrome = SeatLayerPickerChromeOptions(
            showHeader = false,
            showPriceRail = false,
            showFloorSelector = false,
            showFloorStrip = false,
            showMapControls = false,
            showOverviewControl = false,
            showZoomControls = false,
            showColorblindControl = false,
            showViewModeControl = false,
            showAccessibilityControl = false,
            showTicketPanel = false,
            showConfirmCard = false,
            showHoldPill = false,
            showAttribution = false,
        )

        assertFalse(chrome.header)
        assertFalse(chrome.priceLegend)
        assertFalse(chrome.floorSelector)
        assertFalse(chrome.floorStrip)
        assertFalse(chrome.mapControls)
        assertFalse(chrome.overview)
        assertFalse(chrome.zoom)
        assertFalse(chrome.colorblind)
        assertFalse(chrome.map3D)
        assertFalse(chrome.accessibility)
        assertFalse(chrome.cartSheet)
        assertFalse(chrome.confirmCard)
        assertFalse(chrome.holdCountdown)
        assertFalse(chrome.attribution)

        val defaults = SeatLayerPickerChromeOptions()
        assertTrue(defaults.header)
        assertTrue(defaults.showVenue3DChrome)
        assertTrue(defaults.showSeatViewChrome)
    }

    @Test
    fun theRetiredSwitchesAnswerOffWhateverTheRestOfTheChromeSays() {
        // Both are gone rather than renamed, so a host still reading them is
        // told what the picker now does rather than what it used to.
        val chrome = SeatLayerPickerChromeOptions()
        assertFalse(chrome.fit)
        assertFalse(chrome.dock)
    }

    @Test
    fun theDockBarAndTheExtendHoldPromptAreOffUntilAHostAsks() {
        val options = SeatLayerPickerOptions()
        assertEquals(null, options.showDockBar)
        assertEquals(null, options.showExtendHoldPrompt)
        assertTrue(options.showBookedOverlay)
        assertTrue(options.seatCardGlass)
        assertTrue(options.persistColorblindPreference)
        assertTrue(options.manageSystemOverlays)
    }

    @Test
    fun theVenue3DSwitchAndTheHoldLengthAnswerUnderBothNames() {
        val options = SeatLayerPickerOptions(enable3D = false, holdTtl = 7.minutes)

        assertFalse(options.enableVenue3D)
        assertEquals(420_000, options.holdTtlMillis)
        assertEquals(null, SeatLayerPickerOptions().holdTtlMillis)
    }

    @Test
    fun aHoldLengthReachesTheCoreAsTheMillisecondsTheBridgeSpeaks() {
        val behavior = SeatLayerPickerOptions(enable3D = false, holdTtl = 2.minutes).behavior()

        assertFalse(behavior.enable3D)
        assertFalse(behavior.enableVenue3D)
        assertEquals(120_000, behavior.holdTtlMillis)
        assertEquals(null, SeatLayerPickerOptions().behavior().holdTtlMillis)
    }

    @Test
    fun everyRenamedThemeInkAnswersUnderBothNames() {
        val theme = SeatLayerPickerTheme.light().copy(
            text = Color.Red,
            mutedText = Color.Green,
            radius = 11.dp,
        )

        assertEquals(Color.Red, theme.onSurface)
        assertEquals(Color.Green, theme.muted)
        assertEquals(11.dp, theme.cornerRadius)
    }

    @Test
    fun theDarkThemeCarriesItsOwnCautionAndPremiumInks() {
        val dark = SeatLayerPickerTheme.dark()
        val light = SeatLayerPickerTheme.light()

        assertEquals(Color(SeatLayerDarkTokens.WARN_TEXT), dark.warnText)
        assertEquals(Color(SeatLayerDarkTokens.PREMIUM), dark.premium)
        assertEquals(Color(SeatLayerDarkTokens.PREMIUM_TEXT), dark.premiumText)
        assertEquals(Color(SeatLayerLightTokens.WARN_TEXT), light.warnText)
        assertEquals(null, light.fontFamily)
        assertEquals(null, light.logo)
    }

    @Test
    fun theMoneyFormattersOldNameIsTheSameTypeAsItsNewOne() {
        val formatter: SeatLayerMoneyFormatter =
            SeatLayerPickerMoneyFormatter { amount, currency -> "$currency ${amount.toInt()}" }

        assertEquals("EUR 12", formatter.format(12.0, "EUR"))
    }

    @Test
    fun aCurrencyThisDeviceDoesNotKnowKeepsItsCodeBesideTheNumber() {
        val formatted = SeatLayerMoneyFormatter.localized(java.util.Locale.US)
            .format(12.5, "XZZ")

        assertTrue(formatted, "XZZ" in formatted)
        assertTrue(formatted, "12.5" in formatted)
    }
}
