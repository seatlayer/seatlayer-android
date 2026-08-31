package io.seatlayer.android.compose

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic native-chrome semantics and screenshot evidence fixture. */
@RunWith(AndroidJUnit4::class)
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
                            SeatLayerPickerZoomControls(
                                layout = SeatLayerPickerMapControlsLayout.Horizontal,
                            )
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
        composeRule.onNodeWithText("Standard")
            .assertHasClickAction()
            .assertIsOn()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Standard, USD 40–USD 60")
            .assertHasClickAction()
            .assertIsOn()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Accessibility and colour options")
            .performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Wheelchair · 8")
            .assertHasClickAction()
            .assertIsOn()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Aisle Seat · 0")
            .assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Zoom in")
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
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

        val image = composeRule.onNodeWithText("Fixture Arena")
            .captureToImage()
        assertTrue(image.width > 0)
        assertTrue(image.height > 0)

        val rootImage = composeRule.onNodeWithText("TEST MODE")
            .captureToImage()
        assertTrue(rootImage.width > 0)

        val outputDirectory = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getExternalFilesDir("evidence")
            ?: error("No external evidence directory")
        val output = outputDirectory.resolve("accessibility-sheet-light-api35.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .takeScreenshot()
            .let { bitmap ->
                output.outputStream().use { stream ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
            }
        assertTrue(output.length() > 1_000)
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
            override val moneyFormatter: SeatLayerPickerMoneyFormatter =
                SeatLayerPickerMoneyFormatter { amount, currency ->
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
