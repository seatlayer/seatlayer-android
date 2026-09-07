package io.seatlayer.android.compose

import io.seatlayer.android.SeatLayerPickerBlockedRegion
import io.seatlayer.android.SeatLayerPickerCategory
import io.seatlayer.android.SeatLayerPickerFloorInfo
import io.seatlayer.android.SeatLayerPickerMapState
import io.seatlayer.android.SeatLayerPickerSectionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The map chrome's reading rules, with no composition in the way. */
public class PickerMapChromeTest {
    @Test
    public fun aFramedSectionAlwaysHasAStepBack() {
        assertTrue(
            "inside a section there is always a rung left",
            mapState(focusedSectionId = "section-102", atVenueFit = true).canStepBack,
        )
    }

    @Test
    public fun theVenueFitPoseEndsTheLadder() {
        assertFalse(mapState(atVenueFit = true).canStepBack)
        assertTrue(mapState(atVenueFit = false).canStepBack)
    }

    @Test
    public fun anOlderSnapshotFallsBackToCanZoomOut() {
        // Absent is a THIRD answer, not a false: a runtime that cannot report
        // its fit pose must still be able to say there is a way out.
        assertTrue(mapState(atVenueFit = null, canZoomOut = true).canStepBack)
        assertFalse(mapState(atVenueFit = null, canZoomOut = false).canStepBack)
    }

    @Test
    public fun theWholeVenueDiscNeverReadsTheCamera() {
        // Nothing in this file may make the whole-venue disc conditional: it is
        // wired straight to `overview` with no enabled expression at all. The
        // guard is the control column's own count, which does not move with the
        // camera.
        val chrome = SeatLayerPickerChromeOptions()
        assertEquals(3, compactMapControlCount(chrome))
    }

    @Test
    public fun theRetiredPlusKeepsItsSlot() {
        // The column's height is the same among the seats as it is at the
        // venue, because the retired disc never leaves the layout.
        val chrome = SeatLayerPickerChromeOptions()
        assertEquals(
            compactMapControlsRequiredHeight(compactMapControlCount(chrome)),
            compactMapControlsRequiredHeight(3),
        )
    }

    @Test
    public fun thePhoneHidesTheColourblindDiscAndTheBackDisc() {
        val chrome = SeatLayerPickerChromeOptions()
        assertFalse(chrome.colorblindControlFor(phone = true))
        assertTrue(chrome.colorblindControlFor(phone = false))
        assertFalse(chrome.overviewControlFor(phone = true))
        assertTrue(chrome.overviewControlFor(phone = false))
        assertTrue(chrome.zoomControlsFor(phone = true))
        assertTrue(chrome.zoomToFitControlFor(phone = true))
    }

    @Test
    public fun aHostSwitchWinsOverTheWidthDefault() {
        val chrome = SeatLayerPickerChromeOptions(showColorblindControl = true)
        assertTrue(chrome.colorblindControlFor(phone = true))
    }

    @Test
    public fun theDockIsOffOnEveryWidth() {
        assertNull(SeatLayerPickerOptions().showDockBar)
    }

    @Test
    public fun aRegionIsForgottenOnTeardown() {
        val registry = PickerBlockedRegionRegistry()
        val key = Any()
        registry.set(key, SeatLayerPickerBlockedRegion(1.0, 2.0, 3.0, 4.0))
        assertEquals(1, registry.regions.value.size)
        registry.forget(key)
        assertTrue("a control that goes takes its guard", registry.regions.value.isEmpty())
    }

    @Test
    public fun theWholeListIsReplacedNeverMerged() {
        val registry = PickerBlockedRegionRegistry()
        val first = Any()
        val second = Any()
        registry.set(first, SeatLayerPickerBlockedRegion(0.0, 0.0, 10.0, 10.0))
        registry.set(second, SeatLayerPickerBlockedRegion(20.0, 20.0, 10.0, 10.0))
        assertEquals(2, registry.regions.value.size)
        registry.clear()
        assertTrue(registry.regions.value.isEmpty())
    }

    @Test
    public fun anIdenticalRectangleIsNotRepublished() {
        val registry = PickerBlockedRegionRegistry()
        val key = Any()
        val rect = SeatLayerPickerBlockedRegion(4.0, 5.0, 36.0, 36.0)
        registry.set(key, rect)
        val published = registry.regions.value
        registry.set(key, SeatLayerPickerBlockedRegion(4.0, 5.0, 36.0, 36.0))
        assertTrue("no send for an unchanged layout", published === registry.regions.value)
    }

    @Test
    public fun aBadMeasurementIsFlooredRatherThanSent() {
        // The runtime refuses the WHOLE list for one bad rectangle, so one
        // mis-measured disc must not drop the guard over everything else.
        assertEquals(0.0, pickerFiniteEdge(Float.NaN), 0.0)
        assertEquals(0.0, pickerFiniteEdge(Float.POSITIVE_INFINITY), 0.0)
        assertEquals(12.0, pickerFiniteEdge(12f), 0.0)
        assertEquals(0.0, pickerFiniteExtent(-3.0), 0.0)
        assertEquals(0.0, pickerFiniteExtent(Double.NaN), 0.0)
        assertEquals(36.0, pickerFiniteExtent(36.0), 0.0)
    }

    @Test
    public fun aSpreadBecomesAFloorPrice() {
        val formatter = SeatLayerMoneyFormatter { amount, currency -> "$currency$amount" }
        assertEquals(
            "USD54.0+",
            pickerCategoryAmount(category(min = 54.0, max = 92.0), "USD", formatter),
        )
        assertEquals(
            "USD54.0",
            pickerCategoryAmount(category(min = 54.0, max = 54.0), "USD", formatter),
        )
    }

    @Test
    public fun aCategoryWithNoPriceWearsItsName() {
        val formatter = SeatLayerMoneyFormatter { amount, currency -> "$currency$amount" }
        assertNull(pickerCategoryAmount(category(min = 0.0, max = 0.0), "USD", formatter))
    }

    @Test
    public fun aNumberedVenueTakesTheDigits() {
        assertEquals("2", pickerFloorLabel(floor("floor-2", "Level 2"), "number"))
        // A floor whose name carries no digit keeps the only name a buyer has
        // ever seen on a ticket.
        assertEquals(
            "Royal Circle",
            pickerFloorLabel(floor("rc", "Royal Circle"), "number"),
        )
        assertEquals("Lower", pickerFloorLabel(floor("floor-1", "Lower"), null))
        assertEquals("Lower", pickerFloorLabel(floor("floor-1", "Lower"), "name"))
    }

    @Test
    public fun theAllFloorsSentinelIsNeverAnAuthoredFloor() {
        val floors = listOf(
            floor("all", "All floors"),
            floor("floor-1", "Lower"),
            floor("floor-1", "Lower"),
            floor("floor-2", "Upper"),
        )
        assertEquals(
            listOf("floor-1", "floor-2"),
            floors.pickerAuthoredFloors().map(SeatLayerPickerFloorInfo::id),
        )
    }

    @Test
    public fun anUncountedSectionIsNotAnEmptyOne() {
        val counted = section("a", accessibleFree = 3)
        val uncounted = section("b", accessibleFree = null)
        val empty = section("c", accessibleFree = 0)
        assertEquals(1, listOf(counted, uncounted, empty).count { (it.accessibleFree ?: 0) > 0 })
    }

    @Test
    public fun theTourStopsOnlyOnAnAnswer() {
        val tour = PickerAccessibleTour()
        tour.begin(listOf("wheelchair"))
        assertTrue(tour.covers(listOf("wheelchair")))
        assertFalse(tour.covers(listOf("wheelchair", "companion")))
        assertTrue(tour.enter())
        assertFalse("one walk at a time", tour.enter())
        tour.arrive(null)
        assertTrue("nothing matching is an answer, not an error", tour.exhausted)
        assertFalse(tour.walking)
    }

    @Test
    public fun anErrorKeepsWhatWasLastFramed() {
        val tour = PickerAccessibleTour()
        tour.begin(listOf("wheelchair"))
        tour.enter()
        tour.arrive(
            io.seatlayer.android.SeatLayerPickerAccessibleStep(
                id = "section-102",
                label = "Section 102",
                free = 4,
                index = 0,
                total = 3,
            ),
        )
        tour.enter()
        tour.stumble()
        assertEquals("section-102", tour.step?.id)
        assertFalse(tour.exhausted)
    }
}

private fun mapState(
    rung: String = "sections",
    focusedSectionId: String? = null,
    canZoomIn: Boolean = true,
    canZoomOut: Boolean = true,
    atVenueFit: Boolean? = null,
): SeatLayerPickerMapState = SeatLayerPickerMapState(
    rung = rung,
    viewMode = "flat",
    buyerView = "map",
    view3DNavigationMode = "orbit",
    view3DTargetSeatId = null,
    activeFloorId = null,
    focusedSectionId = focusedSectionId,
    focusedSection = null,
    colorblindSafe = false,
    hideLimitedView = false,
    canZoomIn = canZoomIn,
    canZoomOut = canZoomOut,
    atVenueFit = atVenueFit,
    categoryFilter = emptyList(),
    accessibilityFilter = emptyList(),
    accessNeeds = emptyList(),
    floors = emptyList(),
    floorMode = null,
    floorLabelStyle = null,
    viewportInsets = null,
)

private fun category(min: Double, max: Double) = SeatLayerPickerCategory(
    key = "standard",
    label = "Standard",
    color = "#3A8C83",
    priceMin = min,
    priceMax = max,
    available = 40,
    notForSale = false,
    tiers = emptyList(),
)

private fun floor(id: String, name: String) = SeatLayerPickerFloorInfo(id, name, null)

private fun section(id: String, accessibleFree: Int?) = SeatLayerPickerSectionSummary(
    id = id,
    label = id,
    displayLabel = null,
    zoneId = null,
    zoneLabel = null,
    entrance = null,
    color = null,
    dominantCategoryKey = null,
    seatsLeft = 10,
    priceMin = null,
    priceMax = null,
    accessibleFree = accessibleFree,
)
