package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.seatlayer.android.EventMode
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerPickerAccessNeed
import io.seatlayer.android.SeatLayerPickerAccessibilityAvailability
import io.seatlayer.android.SeatLayerPickerBranding
import io.seatlayer.android.SeatLayerPickerCategory
import io.seatlayer.android.SeatLayerPickerEventDetails
import io.seatlayer.android.SeatLayerPickerHold
import io.seatlayer.android.SeatLayerPickerMapState
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerPickerState
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerThemeMode
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Deterministic native-chrome semantics and screenshot evidence fixture. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SeatLayerPickerAccessibilityEvidenceTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun criticalControlsExposeStateLabelsAndMinimumTargets() {
        val scope = fixtureScope()
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = SeatLayerPickerThemeMode.Light,
                explicitTheme = scope.theme,
                branding = scope.state.snapshot?.branding,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSeatLayerPickerScope provides scope,
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .background(scope.theme.background),
                    ) {
                        SeatLayerPickerHeader(onClose = {})
                        SeatLayerPickerTestModeIndicator()
                        SeatLayerPriceLegend()
                        SeatLayerPriceLegend(compact = true)
                        SeatLayerPickerAccessibilityFiltersForEvidence(
                            SeatLayerPickerAccessibilityAvailability(
                                accessibility = true,
                                limitedView = true,
                                colorblindSafe = true,
                            ),
                        )
                        Row {
                            SeatLayerPickerZoomInButton()
                            SeatLayerPickerZoomOutButton()
                            SeatLayerPickerShowWholeVenueButton()
                            SeatLayerPickerColorblindControl()
                            SeatLayerPickerLimitedViewControl()
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Close")
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        // The wide chip is name · price; the phone chip is the price alone.
        composeRule.onNodeWithText("Standard · USD 40+").assertHasClickAction()
        composeRule.onNodeWithText("USD 40+")
            .assertHasClickAction()
            .assertHeightIsAtLeast(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
        // The NAME is spoken on both, and a spread becomes a floor rather than
        // a range no chip can hold.
        composeRule.onAllNodesWithContentDescription("Standard — USD 40+")
            .assertCountEquals(2)
        // The control names how many filters are on: a screen reader that says
        // only "accessibility" over a filtered map has hidden the filtering.
        composeRule.onNodeWithContentDescription("Accessibility and colour options · 2")
            .performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        // The design source's own minimum, not Android's older 48: every
        // number in this chrome comes from the generated tokens.
        composeRule.onNodeWithText("Wheelchair · 8")
            .assertHasClickAction()
            .assertIsOn()
            .assertHeightIsAtLeast(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
        composeRule.onNodeWithText("Aisle Seat · 0")
            .assertIsNotEnabled()
            .assertHeightIsAtLeast(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
        // Among the seats `+` has retired: it is out of the reading order, but
        // it KEEPS ITS SLOT, so the disc above it never moves out from under a
        // thumb already reaching for it.
        composeRule.onAllNodesWithContentDescription("Zoom in").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Zoom in", useUnmergedTree = true)
            .assertWidthIsAtLeast(SeatLayerSizeTokens.MAP_CONTROL_SIZE.dp)
            .assertHeightIsAtLeast(SeatLayerSizeTokens.MAP_CONTROL_SIZE.dp)
        // The map discs are the design source's own 36 dp, drawn on the map
        // rather than on a panel: a 48 dp disc column takes a fifth of a phone
        // map, and the corner it stands in is not a toolbar.
        //
        // The whole-venue disc is ALWAYS live, whatever the camera last said:
        // a pinch changes no state, so dimming it on a stale reading strands a
        // buyer with no way out.
        composeRule.onNodeWithContentDescription("Show whole venue")
            .assertHasClickAction()
            .assertIsEnabled()
            .assertWidthIsAtLeast(SeatLayerSizeTokens.MAP_CONTROL_SIZE.dp)
            .assertHeightIsAtLeast(SeatLayerSizeTokens.MAP_CONTROL_SIZE.dp)
        // `−` is live only among the seats, where there is still a rung.
        composeRule.onNodeWithContentDescription("Zoom out")
            .assertHasClickAction()
            .assertIsEnabled()
        composeRule.onNodeWithContentDescription("Colourblind-friendly colours")
            .assertHasClickAction()
            .assertIsOn()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Hide limited-view seats")
            .assertHasClickAction()
            .assertIsOff()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithText("Fixture Arena").assertIsDisplayed()
        composeRule.onNodeWithText("TEST MODE").assertIsDisplayed()
    }

    private fun fixtureScope(): SeatLayerPickerScope {
        val holder = SeatLayerPickerStateHolder(
            SeatLayerConfiguration(event = FIXTURE_EVENT_KEY),
        )
        val theme = SeatLayerPickerTheme.light()
        return object : SeatLayerPickerScope {
            override val stateHolder: SeatLayerPickerStateHolder = holder
            override val state: SeatLayerPickerState = SeatLayerPickerState(
                snapshot = fixtureSnapshot(),
            )
            override val controller = holder.controller
            override val theme: SeatLayerPickerTheme = theme
            override val themeMode: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode.Light
            override val strings: SeatLayerPickerStrings = SeatLayerPickerStrings.localized("en")
            override val options: SeatLayerPickerOptions = SeatLayerPickerOptions()
            override val styles: SeatLayerPickerStyles = SeatLayerPickerStyles()
            override val moneyFormatter: SeatLayerMoneyFormatter =
                SeatLayerMoneyFormatter { amount, currency ->
                    "$currency ${amount.toInt()}"
                }
            override val callbacks: SeatLayerPickerCallbacks = SeatLayerPickerCallbacks()
        }
    }

    private fun fixtureSnapshot(): SeatLayerPickerSnapshot = SeatLayerPickerSnapshot(
        schema = "seatlayer.picker.snapshot.v1",
        sessionId = "fixture-session",
        revision = 1,
        event = SeatLayerPickerEventDetails(
            key = FIXTURE_EVENT_KEY,
            name = "Fixture Arena",
            mode = EventMode.Test,
            currency = "USD",
            venue = "North Hall",
            startsAt = null,
            timezone = null,
            locale = "en",
            posterUrl = null,
            salesClosed = false,
        ),
        branding = SeatLayerPickerBranding(
            brandName = "SeatLayer",
            logoUrl = null,
            attributionRequired = false,
            accent = null,
            accentInk = null,
            background = null,
            surface = null,
            text = null,
            muted = null,
            line = null,
            fontFamily = null,
            radius = null,
        ),
        categories = listOf(
            SeatLayerPickerCategory(
                key = "standard",
                label = "Standard",
                color = "#006C67",
                priceMin = 40.0,
                priceMax = 60.0,
                available = 120,
                notForSale = false,
                tiers = emptyList(),
            ),
        ),
        zones = emptyList(),
        sections = emptyList(),
        generalAdmissionAreas = emptyList(),
        bestAvailableZones = emptyList(),
        map = SeatLayerPickerMapState(
            rung = "seats",
            viewMode = "flat",
            buyerView = "map",
            view3DNavigationMode = "orbit",
            view3DTargetSeatId = null,
            activeFloorId = null,
            focusedSectionId = null,
            focusedSection = null,
            colorblindSafe = true,
            hideLimitedView = false,
            canZoomIn = true,
            canZoomOut = true,
            categoryFilter = listOf("standard"),
            accessibilityFilter = listOf("wheelchair"),
            accessNeeds = listOf(
                SeatLayerPickerAccessNeed("wheelchair", 8),
                SeatLayerPickerAccessNeed("companion", 8),
                SeatLayerPickerAccessNeed("aisle-seat", 0),
                SeatLayerPickerAccessNeed("step-free", 4),
                SeatLayerPickerAccessNeed("hearing-support", 2),
            ),
            floors = emptyList(),
            floorMode = null,
            floorLabelStyle = null,
            viewportInsets = null,
        ),
        selection = emptyList(),
        selectionValidity = null,
        maxSelection = 6,
        ticketCount = 0,
        cartLines = emptyList(),
        cartTotal = 0.0,
        currency = "USD",
        hold = SeatLayerPickerHold(active = false, expiresAt = null, owner = null),
        accessConfigured = true,
        accessStatus = "ready",
        accessReason = null,
        capabilities = setOf("accessibilityFilter", "limitedViewFilter"),
        raw = JsonObject(emptyMap()),
    )

    private companion object {
        const val FIXTURE_EVENT_KEY = "ev_accessibility_fixture"
    }
}
