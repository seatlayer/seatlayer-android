package io.seatlayer.android.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.seatlayer.android.SeatLayerPickerCartLanding
import io.seatlayer.android.SeatLayerPickerThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The ticket in the air, and the beats that wait on it.
 *
 * The chip is the only surface that knows when the flight is over, so it is the
 * chip that advances the landing. Nothing here may be advanced by a clock: a
 * slow frame must delay the swell, never lose it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PickerSelectionFlightTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theChipGrowsIntoItsFlightAndShrinksAsItLands() {
        assertEquals(0.6f, seatLayerFlightScale(0f), TOLERANCE)
        assertEquals(1f, seatLayerFlightScale(0.22f), TOLERANCE)
        assertEquals(0.55f, seatLayerFlightScale(1f), TOLERANCE)
        assertEquals(0f, seatLayerFlightOpacity(0f), TOLERANCE)
        assertEquals(1f, seatLayerFlightOpacity(0.22f), TOLERANCE)
        assertTrue(seatLayerFlightOpacity(1f) < 0.2f)
    }

    @Test
    fun theLandingIsTheChipsToReportAndTheSwellWaitsForIt() {
        val scope = flightScope()
        val state = SeatLayerPickerFlightState()
        mount(scope, state)

        scope.controller.setCartLanding(SeatLayerPickerCartLanding.Flying)
        composeRule.runOnUiThread { state.launch("A-12", Color(0xFF3A8C83)) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(FRAME)
        composeRule.waitForIdle()

        // Still in the air: the footer must not have swelled and the map must
        // not have given its lift back.
        assertEquals(SeatLayerPickerCartLanding.Flying, scope.state.presentation.cartLanding)
        assertNotNull(state.flight)

        val seen = pumpLanding(scope)
        // The chip reports its own arrival, and the tray acknowledges it before
        // the map moves: land, swell, then release.
        assertEquals(
            listOf(
                SeatLayerPickerCartLanding.Flying,
                SeatLayerPickerCartLanding.Landed,
                SeatLayerPickerCartLanding.Released,
            ),
            seen,
        )
        assertNull(state.flight)
    }

    @Test
    fun aChipTakenOffScreenMidFlightDoesNotStrandTheBeatsBehindIt() {
        val scope = flightScope()
        val state = SeatLayerPickerFlightState()
        var mounted by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = SeatLayerPickerThemeMode.Light,
                explicitTheme = scope.theme,
                branding = null,
            ) {
                CompositionLocalProvider(
                    LocalSeatLayerPickerScope provides scope,
                    LocalSeatLayerPickerFlight provides state,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (mounted) {
                            SeatLayerSelectionFlight(wide = false, bottomPadding = 0.dp)
                        }
                    }
                }
            }
        }

        scope.controller.setCartLanding(SeatLayerPickerCartLanding.Flying)
        composeRule.runOnUiThread { state.launch("A-12", Color(0xFF3A8C83)) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(FRAME * 2)
        composeRule.waitForIdle()
        composeRule.runOnUiThread { mounted = false }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(FRAME)
        composeRule.waitForIdle()

        // A lift the map can never give back is worse than a flight nobody saw.
        assertEquals(SeatLayerPickerCartLanding.Released, scope.state.presentation.cartLanding)
    }

    @Test
    fun theChipTravelsBetweenTheTwoMeasuredAnchors() {
        val state = SeatLayerPickerFlightState()
        state.cardCentre = androidx.compose.ui.geometry.Offset(120f, 900f)
        state.cartAnchor = androidx.compose.ui.geometry.Offset(180f, 1_400f)
        state.launch("A-12", Color.Red)

        val flight = requireNotNull(state.flight)
        assertEquals(120f, flight.from?.x)
        assertEquals(1_400f, flight.to?.y)

        // A later ticket never has an earlier one's landing cleared out from
        // under it.
        val first = flight
        state.launch("A-13", Color.Red)
        state.landed(first)
        assertEquals("A-13", state.flight?.label)
    }

    /**
     * Pump frames until the landing settles, and report every step it passed
     * through — the ORDER is the thing under test, not the wall clock.
     */
    private fun pumpLanding(scope: SeatLayerPickerScope): List<SeatLayerPickerCartLanding> {
        val seen = mutableListOf(scope.state.presentation.cartLanding)
        var frames = 0
        while (seen.last() != SeatLayerPickerCartLanding.Released && frames < MAX_FRAMES) {
            composeRule.mainClock.advanceTimeBy(FRAME)
            composeRule.waitForIdle()
            frames += 1
            val landing = scope.state.presentation.cartLanding
            if (landing != seen.last()) seen += landing
        }
        return seen
    }

    private fun mount(scope: SeatLayerPickerScope, state: SeatLayerPickerFlightState) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = SeatLayerPickerThemeMode.Light,
                explicitTheme = scope.theme,
                branding = null,
            ) {
                CompositionLocalProvider(
                    LocalSeatLayerPickerScope provides scope,
                    LocalSeatLayerPickerFlight provides state,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        SeatLayerSelectionFlight(wide = false, bottomPadding = 0.dp)
                    }
                }
            }
        }
    }

    private fun flightScope() = pickerEvidenceScope(
        scenario = PickerEvidenceScenario.Confirmation,
        themeMode = SeatLayerPickerThemeMode.Light,
    )

    private companion object {
        const val TOLERANCE = 0.001f
        const val FRAME = 16L

        /** Long enough for the flight and the swell, short enough to fail fast. */
        const val MAX_FRAMES = 200
    }
}
