package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.seatlayer.android.SeatLayerPickerAccessNeed
import io.seatlayer.android.SeatLayerPickerAccessibilityAvailability
import io.seatlayer.android.SeatLayerPickerAccessibleStep
import io.seatlayer.android.SeatLayerPickerThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The accessibility sheet's row anatomy, its gates and its bound.
 *
 * Every switch applies as it is flipped, so the thing under test is what the
 * sheet SAYS about each provision — a figure in a column of figures, a row
 * that dims only on an affirmative zero, and no apply step anywhere.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SeatLayerPickerAccessSheetTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun rowsCarryTheirNameAndFigureAndTheWholeLineIsTheControl() {
        showSheet(
            listOf(
                SeatLayerPickerAccessNeed("wheelchair", 8),
                SeatLayerPickerAccessNeed("companion", 8),
                SeatLayerPickerAccessNeed("step-free", 4),
            ),
            active = listOf("wheelchair"),
        )

        // The label is the SHORT name from the SDK's own table, and the figure
        // keeps a column of its own rather than riding on the label.
        composeRule.onNodeWithText(WHEELCHAIR)
            .assertHasClickAction()
            .assertHeightIsAtLeast(PICKER_ACCESS_ROW_HEIGHT.dp)
        composeRule.onAllNodesWithText("8 free").assertCountEquals(2)
        composeRule.onNodeWithContentDescription("$WHEELCHAIR, 8 free").assertIsOn()
        composeRule.onNodeWithContentDescription("Step-free, 4 free").assertIsOff()
    }

    @Test
    public fun anAccommodationWithNoNameInTheTableKeepsItsWireKey() {
        showSheet(listOf(SeatLayerPickerAccessNeed("quiet-room", 3)))

        composeRule.onNodeWithText("quiet-room").assertExists()
    }

    @Test
    public fun aSoldOutProvisionStaysOnTheSheetReadsZeroAndDims() {
        showSheet(listOf(SeatLayerPickerAccessNeed("wheelchair", 0)))

        // A figure, not a sentence: "Not available" is the picker's word for a
        // seat nobody can take on the legend and the seat card, and the retired
        // `accessNoneLeft` is not what a count column says.
        composeRule.onNodeWithText("0", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("$WHEELCHAIR, 0").assertIsNotEnabled()
    }

    @Test
    public fun anUncountedProvisionIsNotSoldOut() {
        showSheet(listOf(SeatLayerPickerAccessNeed("wheelchair", null)))

        // Null is "not counted", which is not zero: no figure is drawn and the
        // row stays live.
        composeRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(WHEELCHAIR).assertIsEnabled()
    }

    @Test
    public fun aSoldOutProvisionThatIsStillOnCanBeTurnedOff() {
        showSheet(listOf(SeatLayerPickerAccessNeed("wheelchair", 0)), active = listOf("wheelchair"))

        // The buyer holding the last space is the one who emptied it; a filter
        // they cannot turn off traps them on a map with nothing left to show.
        composeRule.onNodeWithContentDescription("$WHEELCHAIR, 0").assertIsEnabled()
    }

    @Test
    public fun thereIsNoApplyStepAnywhereOnTheSheet() {
        showSheet(
            listOf(
                SeatLayerPickerAccessNeed("wheelchair", 8),
                SeatLayerPickerAccessNeed("step-free", 4),
            ),
        )

        composeRule.onAllNodesWithText("Apply").assertCountEquals(0)
        composeRule.onAllNodesWithText("Apply filters").assertCountEquals(0)
    }

    @Test
    public fun theMapSwitchesSitUnderTheirOwnGroupHeading() {
        showSheet(listOf(SeatLayerPickerAccessNeed("wheelchair", 8)))

        composeRule.onNodeWithText("VIEW").assertExists()
        composeRule.onNodeWithContentDescription("Hide limited-view seats").assertExists()
        composeRule.onNodeWithContentDescription("Colourblind-friendly colours").assertExists()
    }

    @Test
    public fun aWithheldCapabilityWithdrawsItsRowSilently() {
        showSheet(
            listOf(SeatLayerPickerAccessNeed("wheelchair", 8)),
            availability = SeatLayerPickerAccessibilityAvailability(
                accessibility = true,
                limitedView = false,
                colorblindSafe = false,
            ),
        )

        composeRule.onNodeWithText(WHEELCHAIR).assertExists()
        // No heading over an empty group, and no error where a feature was
        // simply never offered.
        composeRule.onAllNodesWithText("VIEW").assertCountEquals(0)
        composeRule.onAllNodesWithText("Hide limited-view seats").assertCountEquals(0)
    }

    @Test
    public fun theWholeVocabularyScrollsInsideTheSheetsBound() {
        showSheet(SeatLayerPickerStrings.defaultAccessNeeds.keys.mapIndexed { index, key ->
            SeatLayerPickerAccessNeed(key, index + 1)
        })

        // Twelve 50-point rows do not fit the bound, so the last one is reached
        // by scrolling rather than by the sheet taking the screen.
        composeRule.onNodeWithText("Lift-up armrest").performScrollTo().assertExists()
    }

    @Test
    public fun theSheetIsBoundedToAFractionOfTheScreenAndFloored() {
        val tall = seatLayerAccessSheetMaxHeight(844f)
        assertEquals(844f * SeatLayerSizeTokens.ACCESS_SHEET_MAX_HEIGHT_FRACTION, tall, 0.001f)
        // A short viewport gets a window worth scrolling rather than a sliver…
        assertEquals(
            SeatLayerSizeTokens.ACCESS_SHEET_MIN_HEIGHT,
            seatLayerAccessSheetMaxHeight(300f),
            0.001f,
        )
        // …but never a sheet taller than the screen it is bounded by.
        assertEquals(200f, seatLayerAccessSheetMaxHeight(200f), 0.001f)
        assertTrue(tall < 844f)
    }

    @Test
    public fun theTourStepIsPrintedFromOneEvenThoughTheWireCountsFromZero() {
        val strings = SeatLayerPickerStrings.localized("en")
        val first = SeatLayerPickerAccessibleStep(
            id = "sec-1",
            label = "Section 102",
            free = 4,
            index = 0,
            total = 6,
        )

        assertEquals("1 of 6", seatLayerAccessibleStepLabel(strings, first))
        assertEquals("2 of 6", seatLayerAccessibleStepLabel(strings, first.copy(index = 1)))
    }

    private companion object {
        /**
         * The name `localized("en")` resolves for the wheelchair provision.
         *
         * The reference's own English locale block still carries the runtime's
         * LONG taxonomy label here rather than the short one in `tokens.json`;
         * the SDK prints whatever the table says, so the test does too.
         */
        const val WHEELCHAIR = "Wheelchair space"
    }

    private fun showSheet(
        needs: List<SeatLayerPickerAccessNeed>,
        active: List<String> = emptyList(),
        availability: SeatLayerPickerAccessibilityAvailability =
            SeatLayerPickerAccessibilityAvailability(
                accessibility = true,
                limitedView = true,
                colorblindSafe = true,
            ),
    ) {
        val scope = pickerAccessScope(needs = needs, active = active)
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = SeatLayerPickerThemeMode.Light,
                explicitTheme = scope.theme,
                branding = null,
            ) {
                CompositionLocalProvider(LocalSeatLayerPickerScope provides scope) {
                    Column(Modifier.fillMaxSize().background(scope.theme.background)) {
                        SeatLayerPickerAccessibilityFiltersForEvidence(
                            availability = availability,
                            initiallyExpanded = true,
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }
}
