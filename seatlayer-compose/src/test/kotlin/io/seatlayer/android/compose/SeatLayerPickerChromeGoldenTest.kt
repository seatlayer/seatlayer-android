package io.seatlayer.android.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.seatlayer.android.SeatLayerPickerThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Reference images for the shipped chrome at the phone reference viewport.
 *
 * Every surface renders from the deterministic evidence fixture, so a change in
 * layout, spacing, type or colour shows up as an image difference rather than as
 * a reviewer's recollection.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SeatLayerPickerChromeGoldenTest {
    @get:Rule
    public val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    public fun overviewLight() {
        capture(PickerEvidenceScenario.Overview, SeatLayerPickerThemeMode.Light, "overview-light")
    }

    @Test
    public fun overviewDark() {
        capture(PickerEvidenceScenario.Overview, SeatLayerPickerThemeMode.Dark, "overview-dark")
    }

    @Test
    public fun seatCardLight() {
        capture(
            PickerEvidenceScenario.Confirmation,
            SeatLayerPickerThemeMode.Light,
            "seat-card-light",
        )
    }

    @Test
    public fun seatCardDark() {
        capture(
            PickerEvidenceScenario.Confirmation,
            SeatLayerPickerThemeMode.Dark,
            "seat-card-dark",
        )
    }

    @Test
    public fun cartSheetExpandedLight() {
        capture(PickerEvidenceScenario.Cart, SeatLayerPickerThemeMode.Light, "cart-sheet-light")
    }

    @Test
    public fun cartSheetExpandedDark() {
        capture(PickerEvidenceScenario.Cart, SeatLayerPickerThemeMode.Dark, "cart-sheet-dark")
    }

    private fun capture(
        scenario: PickerEvidenceScenario,
        themeMode: SeatLayerPickerThemeMode,
        name: String,
    ) {
        val scope = pickerEvidenceScope(scenario, themeMode, SeatLayerPickerOptions())
        val builders = SeatLayerPickerBuilders(
            map = { _, _ -> PickerEvidenceRenderer(scenario) },
        )
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = themeMode,
                explicitTheme = scope.theme,
                branding = scope.state.snapshot?.branding,
            ) {
                CompositionLocalProvider(
                    LocalSeatLayerPickerScope provides scope,
                    // Pinned so the hold pill reads the same eight minutes in
                    // every capture, whatever the composition cost.
                    LocalSeatLayerPickerClock provides { SEATLAYER_EVIDENCE_NOW_MILLIS.toLong() },
                ) {
                    Box(Modifier.fillMaxSize().background(scope.theme.background)) {
                        with(scope) {
                            SeatLayerReadyMadePicker(
                                modifier = Modifier.fillMaxSize(),
                                builders = builders,
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$SNAPSHOT_DIRECTORY/$name.png")
    }

    private companion object {
        const val SNAPSHOT_DIRECTORY = "src/test/snapshots"
    }
}
