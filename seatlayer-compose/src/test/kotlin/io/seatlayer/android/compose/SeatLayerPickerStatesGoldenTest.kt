package io.seatlayer.android.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.seatlayer.android.SeatLayerPickerAccessNeed
import io.seatlayer.android.SeatLayerPickerAccessibilityAvailability
import io.seatlayer.android.SeatLayerPickerPresentationState
import io.seatlayer.android.SeatLayerPickerThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Reference images for the accessibility sheet and the states that replace the
 * map, in both themes.
 *
 * A change in row anatomy, alignment, type or colour shows up as an image
 * difference rather than as a reviewer's recollection.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SeatLayerPickerStatesGoldenTest {
    @get:Rule
    public val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    public fun accessibilitySheetLight() {
        captureSheet(SeatLayerPickerThemeMode.Light, "access-sheet-light")
    }

    @Test
    public fun accessibilitySheetDark() {
        captureSheet(SeatLayerPickerThemeMode.Dark, "access-sheet-dark")
    }

    @Test
    public fun soldOutLight() {
        capture(
            pickerAccessScope(
                categories = listOf(fixtureCategory(available = 0)),
                themeMode = SeatLayerPickerThemeMode.Light,
            ),
            "sold-out-light",
        ) { SeatLayerPickerSoldOutOverlay() }
    }

    @Test
    public fun soldOutDark() {
        capture(
            pickerAccessScope(
                categories = listOf(fixtureCategory(available = 0)),
                themeMode = SeatLayerPickerThemeMode.Dark,
            ),
            "sold-out-dark",
        ) { SeatLayerPickerSoldOutOverlay() }
    }

    @Test
    public fun bookedLight() {
        capture(bookedScope(SeatLayerPickerThemeMode.Light), "booked-light") {
            SeatLayerPickerBookedOverlay()
        }
    }

    @Test
    public fun bookedDark() {
        capture(bookedScope(SeatLayerPickerThemeMode.Dark), "booked-dark") {
            SeatLayerPickerBookedOverlay()
        }
    }

    @Test
    public fun accessPanelLight() {
        capture(
            pickerAccessScope(
                accessStatus = "unavailable",
                accessReason = "no_token",
                themeMode = SeatLayerPickerThemeMode.Light,
            ),
            "access-panel-light",
        ) { SeatLayerPickerAccessPanel() }
    }

    @Test
    public fun accessPanelDark() {
        capture(
            pickerAccessScope(
                accessStatus = "unavailable",
                accessReason = "no_token",
                themeMode = SeatLayerPickerThemeMode.Dark,
            ),
            "access-panel-dark",
        ) { SeatLayerPickerAccessPanel() }
    }

    private fun bookedScope(themeMode: SeatLayerPickerThemeMode) = pickerAccessScope(
        presentation = SeatLayerPickerPresentationState(bookedHandoff = fixtureHandoff()),
        themeMode = themeMode,
    )

    private fun captureSheet(themeMode: SeatLayerPickerThemeMode, name: String) {
        val needs = SeatLayerPickerStrings.defaultAccessNeeds.keys.take(SHEET_NEEDS)
            .mapIndexed { index, key ->
                // One sold out, one uncounted, the rest with a live figure:
                // three different facts, and the sheet says each differently.
                SeatLayerPickerAccessNeed(
                    key = key,
                    count = when (index) {
                        1 -> 0
                        2 -> null
                        else -> index + 4
                    },
                )
            }
        val scope = pickerAccessScope(
            needs = needs,
            active = listOf(needs.first().key),
            themeMode = themeMode,
        )
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = themeMode,
                explicitTheme = scope.theme,
                branding = null,
            ) {
                CompositionLocalProvider(
                    LocalSeatLayerPickerScope provides scope,
                    // Entrances settle at once: a reference image must not
                    // depend on how many frames the clock was advanced by.
                    LocalSeatLayerPickerCapture provides true,
                ) {
                    Box(Modifier.fillMaxSize().background(scope.theme.background)) {
                        SeatLayerPickerAccessibilityFiltersForEvidence(
                            availability = SeatLayerPickerAccessibilityAvailability(
                                accessibility = true,
                                limitedView = true,
                                colorblindSafe = true,
                            ),
                            initiallyExpanded = true,
                        )
                    }
                }
            }
        }
        settle()
        composeRule.onRoot().captureRoboImage("$SNAPSHOT_DIRECTORY/$name.png")
    }

    private fun capture(
        scope: SeatLayerPickerScope,
        name: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = scope.themeMode,
                explicitTheme = scope.theme,
                branding = null,
            ) {
                CompositionLocalProvider(
                    LocalSeatLayerPickerScope provides scope,
                    // Entrances settle at once: a reference image must not
                    // depend on how many frames the clock was advanced by.
                    LocalSeatLayerPickerCapture provides true,
                ) {
                    Box(Modifier.fillMaxSize().background(scope.theme.background)) { content() }
                }
            }
        }
        settle()
        composeRule.onRoot().captureRoboImage("$SNAPSHOT_DIRECTORY/$name.png")
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
    }

    private companion object {
        const val SNAPSHOT_DIRECTORY = "src/test/snapshots"
        const val SETTLE_MS = 500L
        const val SHEET_NEEDS = 6
    }
}
