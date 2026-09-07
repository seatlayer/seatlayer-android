package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.seatlayer.android.BundleInfo
import io.seatlayer.android.EventMode
import io.seatlayer.android.ProtocolRange
import io.seatlayer.android.SEATLAYER_PICKER_SNAPSHOT_SCHEMA
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerPickerAccessNeed
import io.seatlayer.android.SeatLayerPickerCategory
import io.seatlayer.android.SeatLayerPickerEventDetails
import io.seatlayer.android.SeatLayerPickerFloorInfo
import io.seatlayer.android.SeatLayerPickerHold
import io.seatlayer.android.SeatLayerPickerMapState
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerPickerState
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The map chrome as it is actually laid out and measured. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PickerMapChromeUiTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun aCaptureAsksEveryEntranceForItsSettledFrame() {
        // The one accessor every animated surface already consults. A capture
        // says so once, and nothing downstream has to know it is being
        // photographed — nor does any golden have to name a frame count.
        var underCapture: Boolean? = null
        var ordinary: Boolean? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalSeatLayerPickerCapture provides true) {
                underCapture = pickerReducedMotion()
            }
            ordinary = pickerReducedMotion()
        }
        composeRule.waitForIdle()
        assertEquals(true, underCapture)
        assertEquals(false, ordinary)
    }

    @Test
    public fun everyControlOnTheMapReportsItsRectangleInTheMapsOwnFrame() {
        val registry = PickerBlockedRegionRegistry()
        composeRule.setContent {
            Box(Modifier.fillMaxSize().padding(20.dp)) {
                Box(Modifier.fillMaxSize().onGloballyPositioned(registry::bindMap)) {
                    CompositionLocalProvider(LocalPickerBlockedRegions provides registry) {
                        PickerMapAnchorRegion(PickerMapAnchor.TopStart) {
                            Box(Modifier.size(36.dp))
                        }
                        PickerMapAnchorRegion(PickerMapAnchor.BottomEnd) {
                            Box(Modifier.size(36.dp))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val regions = registry.regions.value
        assertEquals(2, regions.size)
        // In the MAP's coordinates, not the window's: the surrounding 20 dp of
        // page is not part of the map, and a rectangle measured against the
        // window would guard the wrong strip of it.
        val top = regions.first()
        assertEquals(SeatLayerSizeTokens.MAP_ANCHOR_INSET.toDouble(), top.x, 1.0)
        assertEquals(SeatLayerSizeTokens.MAP_ANCHOR_INSET.toDouble(), top.y, 1.0)
        assertEquals(SeatLayerSizeTokens.MAP_CONTROL_SIZE.toDouble(), top.width, 1.0)
        assertEquals(SeatLayerSizeTokens.MAP_CONTROL_SIZE.toDouble(), top.height, 1.0)
        assertTrue("the foot of the map is further down", regions[1].y > top.y)
    }

    @Test
    public fun aControlThatLeavesTakesItsRectangleWithIt() {
        val registry = PickerBlockedRegionRegistry()
        val mounted = mutableStateOf(true)
        composeRule.setContent {
            Box(Modifier.fillMaxSize().onGloballyPositioned(registry::bindMap)) {
                CompositionLocalProvider(LocalPickerBlockedRegions provides registry) {
                    if (mounted.value) {
                        PickerMapAnchorRegion(PickerMapAnchor.BottomEnd) {
                            Box(Modifier.size(36.dp))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, registry.regions.value.size)
        mounted.value = false
        composeRule.waitForIdle()
        assertTrue(registry.regions.value.isEmpty())
    }

    @Test
    public fun thePhoneColumnIsTheAccessibilityDiscThenPlusThenTheWholeVenue() {
        renderControls(rung = "sections", canZoomIn = true)
        composeRule.onNodeWithContentDescription("Zoom in").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Show whole venue").assertIsEnabled()
        // There is no `−` on the phone.
        composeRule.onAllNodesWithContentDescription("Zoom out").assertCountEquals(0)
    }

    @Test
    public fun plusRetiresAtTheZoomCeilingAndAmongTheSeats() {
        renderControls(rung = "sections", canZoomIn = false)
        composeRule.onAllNodesWithContentDescription("Zoom in").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Zoom in", useUnmergedTree = true)
            .assertIsNotEnabled()
    }

    @Test
    public fun theWholeVenueDiscStaysLiveAtTheVenue() {
        renderControls(rung = "overview", canZoomIn = false, atVenueFit = true)
        composeRule.onNodeWithContentDescription("Show whole venue").assertIsEnabled()
    }

    @Test
    public fun theRailCountsPlacesOnlyWhereTheRuntimeCountsThem() {
        renderRail(counted = false)
        composeRule.onAllNodesWithContentDescription("Standard — USD 40+ · 18")
            .assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Standard — USD 40+").assertIsDisplayed()
    }

    @Test
    public fun theRailSpeaksTheCountWhereTheRuntimeOffersIt() {
        renderRail(counted = true)
        composeRule.onNodeWithContentDescription("Standard — USD 40+ · 18").assertIsDisplayed()
        // The way out is always there, and never inside the scroller.
        composeRule.onNodeWithContentDescription("All prices").assertIsDisplayed()
    }

    @Test
    public fun theFloorStripOffersAllFloorsOnlyOnBothHalvesOfTheRuntimesWord() {
        renderFloors(floorMode = null)
        composeRule.onAllNodesWithText("All floors").assertCountEquals(0)
        composeRule.onAllNodesWithText("Lower").assertCountEquals(1)
    }

    @Test
    public fun theFloorStripOffersAllFloorsWhenTheRuntimeReportsAMode() {
        renderFloors(floorMode = "single")
        composeRule.onAllNodesWithText("All floors").assertCountEquals(1)
    }

    private fun renderControls(
        rung: String,
        canZoomIn: Boolean,
        atVenueFit: Boolean? = null,
    ) {
        render(
            scope = scope(
                map = map(rung = rung, canZoomIn = canZoomIn, atVenueFit = atVenueFit),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                SeatLayerPickerMapControls(compact = true)
            }
        }
    }

    private fun renderRail(counted: Boolean) {
        render(
            scope = scope(
                map = map(),
                capabilities = if (counted) listOf("category-availability-v1") else emptyList(),
            ),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                SeatLayerPriceLegend(compact = true)
            }
        }
    }

    private fun renderFloors(floorMode: String?) {
        render(
            scope = scope(map = map(floorMode = floorMode)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                SeatLayerFloorStrip(compact = true)
            }
        }
    }

    private fun render(
        scope: SeatLayerPickerScope,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = SeatLayerPickerThemeMode.Light,
                explicitTheme = scope.theme,
                branding = io.seatlayer.android.SeatLayerPickerBranding(
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
            ) {
                CompositionLocalProvider(LocalSeatLayerPickerScope provides scope) { content() }
            }
        }
        composeRule.waitForIdle()
    }
}

private const val CHROME_TEST_EVENT = "evt_map_chrome"

private fun map(
    rung: String = "sections",
    canZoomIn: Boolean = true,
    atVenueFit: Boolean? = null,
    floorMode: String? = null,
) = SeatLayerPickerMapState(
    rung = rung,
    viewMode = "flat",
    buyerView = "map",
    view3DNavigationMode = "orbit",
    view3DTargetSeatId = null,
    activeFloorId = "floor-1",
    focusedSectionId = null,
    focusedSection = null,
    colorblindSafe = false,
    hideLimitedView = false,
    canZoomIn = canZoomIn,
    canZoomOut = true,
    atVenueFit = atVenueFit,
    categoryFilter = emptyList(),
    accessibilityFilter = emptyList(),
    accessNeeds = listOf(SeatLayerPickerAccessNeed("wheelchair", 8)),
    floors = listOf(
        SeatLayerPickerFloorInfo("floor-1", "Lower", 1),
        SeatLayerPickerFloorInfo("floor-2", "Upper", 2),
    ),
    floorMode = floorMode,
    floorLabelStyle = "name",
    viewportInsets = null,
)

private fun scope(
    map: SeatLayerPickerMapState,
    capabilities: List<String> = emptyList(),
): SeatLayerPickerScope {
    val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = CHROME_TEST_EVENT))
    val bundle = BundleInfo(
        bundle = "map-chrome-test",
        protocolRange = ProtocolRange(2, 2),
        capabilities = listOf("native-chrome-contract-v1", "floor-stack-v1") + capabilities,
        events = listOf("picker.snapshot"),
        commands = listOf(
            "picker.zoomIn",
            "picker.zoomOut",
            "picker.overview",
            "picker.setFloor",
            "picker.setCategoryFilter",
        ),
    )
    val snapshot = SeatLayerPickerSnapshot(
        schema = SEATLAYER_PICKER_SNAPSHOT_SCHEMA,
        sessionId = "map-chrome-session",
        revision = 1,
        event = SeatLayerPickerEventDetails(
            key = CHROME_TEST_EVENT,
            name = "Fixture Arena",
            mode = EventMode.Live,
            currency = "USD",
            venue = "North Hall",
            startsAt = null,
            timezone = null,
            locale = "en",
            posterUrl = null,
            salesClosed = false,
        ),
        branding = io.seatlayer.android.SeatLayerPickerBranding(
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
                free = 18,
            ),
        ),
        zones = emptyList(),
        sections = emptyList(),
        generalAdmissionAreas = emptyList(),
        bestAvailableZones = emptyList(),
        map = map,
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
        capabilities = setOf("accessibilityFilter"),
        raw = kotlinx.serialization.json.JsonObject(emptyMap()),
    )
    val state = SeatLayerPickerState(
        phase = io.seatlayer.android.SeatLayerPickerPhase.Ready(
            io.seatlayer.android.ReadyInfo(
                protocolRevision = 2,
                mode = EventMode.Live,
                transport = io.seatlayer.android.TransportName.Android,
                eventKey = CHROME_TEST_EVENT,
            ),
        ),
        snapshot = snapshot,
        bundleInfo = bundle,
    )
    val stateField = SeatLayerPickerStateHolder::class.java.getDeclaredField("mutableState")
    stateField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (stateField.get(holder) as kotlinx.coroutines.flow.MutableStateFlow<SeatLayerPickerState>)
        .value = state
    val bundleField = holder.controller.javaClass.getDeclaredField("bundleInfo")
    bundleField.isAccessible = true
    bundleField.set(holder.controller, bundle)
    return object : SeatLayerPickerScope {
        override val stateHolder: SeatLayerPickerStateHolder = holder
        override val state: SeatLayerPickerState get() = holder.state.value
        override val controller = holder.controller
        override val theme: SeatLayerPickerTheme = SeatLayerPickerTheme.light()
        override val themeMode: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode.Light
        override val strings: SeatLayerPickerStrings = SeatLayerPickerStrings.localized("en")
        override val options: SeatLayerPickerOptions = SeatLayerPickerOptions()
        override val styles: SeatLayerPickerStyles = SeatLayerPickerStyles()
        override val moneyFormatter: SeatLayerMoneyFormatter =
            SeatLayerMoneyFormatter { amount, currency -> "$currency ${amount.toInt()}" }
        override val callbacks: SeatLayerPickerCallbacks = SeatLayerPickerCallbacks()
    }
}
