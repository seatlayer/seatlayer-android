package io.seatlayer.android.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.seatlayer.android.SeatLayerPickerThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/** Full native-chrome evidence with only renderer-owned pixels substituted. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SeatLayerPickerVisualEvidenceTest {
    @get:Rule
    public val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    public fun overviewTestModeCompactLight() {
        render(PickerEvidenceScenario.Overview)
        composeRule.onNodeWithText("Fixture Arena").assertIsDisplayed()
        composeRule.onNodeWithText("TEST MODE").assertIsDisplayed()
        // The zoom-to-fit control is retired from the built-in layouts; the
        // whole-venue control does the same job without going stale.
        composeRule.onAllNodesWithContentDescription("Fit to screen").assertCountEquals(0)
    }

    @Test
    public fun sectionFocusFloorsAndStepOut() {
        render(PickerEvidenceScenario.SectionFocus)
        composeRule.onNodeWithText("Lower").assertIsDisplayed()
        composeRule.onNodeWithText("Upper").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to venue").assertIsDisplayed()
    }

    @Test
    public fun visibleAdultChildTierConfirmation() {
        render(PickerEvidenceScenario.Confirmation)
        composeRule.onNodeWithText("Adult").assertIsDisplayed()
        composeRule.onNodeWithText("Child").assertIsDisplayed()
        composeRule.onNodeWithText("Companion").assertIsDisplayed()
    }

    @Test
    public fun generalAdmissionQuantityAndTiers() {
        render(PickerEvidenceScenario.GeneralAdmission)
        composeRule.onNodeWithText("Standing floor").assertIsDisplayed()
        composeRule.onNodeWithText("Quantity").assertIsDisplayed()
    }

    @Test
    public fun tableQuantityDark() {
        render(
            PickerEvidenceScenario.Table,
            themeMode = SeatLayerPickerThemeMode.Dark,
        )
        composeRule.onNodeWithText("Terrace table 7").assertIsDisplayed()
        composeRule.onNodeWithText("Choose the number of guests for this table")
            .assertIsDisplayed()
    }

    @Test
    public fun expandedCartWithHold() {
        render(PickerEvidenceScenario.Cart)
        // Every held seat is its own line: the collapsed run is retired.
        listOf("Section 102 · A · 12", "Section 102 · A · 13", "Section 102 · A · 14")
            .forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText("Hold seats & checkout").assertIsDisplayed()
    }

    @Test
    public fun holdLapseAndUndoNotices() {
        render(PickerEvidenceScenario.Recovery)
        composeRule.onNodeWithText("Your hold expired", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    public fun venue3DTargetAndNeighboursDark() {
        render(
            PickerEvidenceScenario.Venue3D,
            themeMode = SeatLayerPickerThemeMode.Dark,
        )
        composeRule.onNodeWithContentDescription("Previous seat").assertIsDisplayed()
        composeRule.onNodeWithText("View from here").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next seat").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Recentre on the stage").assertIsDisplayed()
    }

    @Test
    public fun panoramaRestoresTargetChromeDark() {
        render(
            PickerEvidenceScenario.Panorama,
            themeMode = SeatLayerPickerThemeMode.Dark,
        )
        composeRule.onNodeWithText("Section 102 · Row A · Seat 12").assertIsDisplayed()
        composeRule.onNodeWithText("Real view").assertIsDisplayed()
    }

    @Test
    public fun panoramaUnavailableIsActionable() {
        render(PickerEvidenceScenario.PanoramaUnavailable)
        composeRule.onNodeWithText("Seat view is unavailable for this seat.").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").assertIsDisplayed()
    }

    @Test
    public fun loadingState() {
        render(PickerEvidenceScenario.Loading)
        composeRule.onNodeWithContentDescription("Loading seat map…").assertIsDisplayed()
    }

    @Test
    public fun failureRetryState() {
        render(PickerEvidenceScenario.Error)
        composeRule.onNodeWithText("Choose your seats").assertIsDisplayed()
        composeRule.onNodeWithText("The seat map didn’t load").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    public fun emptyInventoryState() {
        render(PickerEvidenceScenario.Empty)
        composeRule.onNodeWithText("Sold out").assertIsDisplayed()
    }

    @Test
    public fun salesClosedState() {
        render(PickerEvidenceScenario.SalesClosed)
        composeRule.onNodeWithText("Sold out").assertIsDisplayed()
    }

    @Test
    public fun confirmationAtLargeFontScale() {
        render(
            PickerEvidenceScenario.Confirmation,
            fontScale = 1.5f,
        )
        // A seat is added, not "selected": the generic word is what a booth,
        // a table or a general-admission unit gets.
        composeRule.onNodeWithText("Add seat").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    public fun rtlCompactChrome() {
        render(
            PickerEvidenceScenario.Overview,
            layoutDirection = LayoutDirection.Rtl,
        )
        composeRule.onNodeWithText("Fixture Arena").assertIsDisplayed()
    }

    @Test
    public fun narrowSplitScreenChrome() {
        render(
            PickerEvidenceScenario.Overview,
            viewportWidthDp = 320,
        )
        composeRule.onNodeWithText("Fixture Arena").assertIsDisplayed()
    }

    @Test
    public fun compactLandscapeChrome() {
        RuntimeEnvironment.setQualifiers("+w844dp-h390dp-land")
        render(
            PickerEvidenceScenario.Overview,
            options = SeatLayerPickerOptions(layout = SeatLayerPickerLayoutMode.Compact),
        )
        composeRule.onNodeWithText("Fixture Arena").assertIsDisplayed()
        composeRule.onNodeWithText("TEST MODE").assertIsDisplayed()
    }

    @Test
    public fun wideLandscapeChrome() {
        RuntimeEnvironment.setQualifiers("+w844dp-h390dp-land")
        render(
            PickerEvidenceScenario.Cart,
            options = SeatLayerPickerOptions(layout = SeatLayerPickerLayoutMode.Wide),
        )
        // Each cart line names its own section now, so the label repeats.
        composeRule.onAllNodesWithText("Section 102", substring = true)
            .onFirst()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    private fun render(
        scenario: PickerEvidenceScenario,
        themeMode: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode.Light,
        options: SeatLayerPickerOptions = SeatLayerPickerOptions(),
        fontScale: Float = 1f,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        viewportWidthDp: Int? = null,
    ) {
        val scope = pickerEvidenceScope(scenario, themeMode, options)
        val builders = SeatLayerPickerBuilders(
            map = { _, _ -> PickerEvidenceRenderer(scenario) },
        )
        composeRule.setContent {
            val platformDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(platformDensity.density, fontScale),
                LocalLayoutDirection provides layoutDirection,
            ) {
                SeatLayerPickerThemeProvider(
                    mode = themeMode,
                    explicitTheme = scope.theme,
                    branding = scope.state.snapshot?.branding,
                ) {
                    CompositionLocalProvider(LocalSeatLayerPickerScope provides scope) {
                        Box(
                            Modifier.fillMaxSize().background(Color(0xFF252B38)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = if (viewportWidthDp == null) {
                                    Modifier.fillMaxSize()
                                } else {
                                    Modifier.width(viewportWidthDp.dp).fillMaxHeight()
                                },
                            ) {
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
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
    }
}
