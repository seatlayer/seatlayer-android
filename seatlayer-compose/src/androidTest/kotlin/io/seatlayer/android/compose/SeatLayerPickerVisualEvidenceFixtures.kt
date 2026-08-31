package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.BundleInfo
import io.seatlayer.android.EventMode
import io.seatlayer.android.ProtocolRange
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SEATLAYER_PICKER_SNAPSHOT_SCHEMA
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerAccessNeed
import io.seatlayer.android.SeatLayerPickerBranding
import io.seatlayer.android.SeatLayerPickerCartLine
import io.seatlayer.android.SeatLayerPickerCategory
import io.seatlayer.android.SeatLayerPickerCategoryTier
import io.seatlayer.android.SeatLayerPickerEventDetails
import io.seatlayer.android.SeatLayerPickerFloorInfo
import io.seatlayer.android.SeatLayerPickerGeneralAdmissionArea
import io.seatlayer.android.SeatLayerPickerHold
import io.seatlayer.android.SeatLayerPickerHoldLapse
import io.seatlayer.android.SeatLayerPickerMapState
import io.seatlayer.android.SeatLayerPickerPhase
import io.seatlayer.android.SeatLayerPickerPresentationState
import io.seatlayer.android.SeatLayerPickerPrompt
import io.seatlayer.android.SeatLayerPickerRemovalPhase
import io.seatlayer.android.SeatLayerPickerRemovalUndo
import io.seatlayer.android.SeatLayerPickerSectionSummary
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerPickerState
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerThemeMode
import io.seatlayer.android.SeatLayerPickerZone
import io.seatlayer.android.SeatLayerSeatView
import io.seatlayer.android.TransportName
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject

internal enum class PickerEvidenceScenario {
    Overview,
    SectionFocus,
    Confirmation,
    GeneralAdmission,
    Table,
    Cart,
    Recovery,
    Venue3D,
    Panorama,
    PanoramaUnavailable,
    Loading,
    Error,
    Empty,
    SalesClosed,
}

internal fun pickerEvidenceScope(
    scenario: PickerEvidenceScenario,
    themeMode: SeatLayerPickerThemeMode,
    options: SeatLayerPickerOptions = SeatLayerPickerOptions(),
): SeatLayerPickerScope {
    val holder = SeatLayerPickerStateHolder(
        configuration = SeatLayerConfiguration(event = EVIDENCE_EVENT),
    )
    val bundle = BundleInfo(
        bundle = "visual-evidence",
        protocolRange = ProtocolRange(2, 2),
        capabilities = listOf(
            "picker-session-v2",
            "native-chrome-contract-v1",
            "native-seat-view-chrome-v1",
            "venue-3d-v1",
            "venue-3d-controls-v1",
            "seat-view-v1",
            "floor-stack-v1",
            "viewport-insets-v1",
            "access-needs-v1",
            "colorblind-safe",
        ),
        events = listOf("picker.snapshot", "seatView.changed"),
        commands = listOf(
            "picker.setViewportInsets",
            "picker.setInteractionEnabled",
            "picker.setFloor",
            "picker.setAccessibilityFilter",
            "picker.setLimitedViewFilter",
            "picker.setColorblindSafe",
            "picker.setBuyerView",
            "picker.setVenue3DNavigationMode",
            "picker.openSeatView",
            "picker.closeSeatView",
            "picker.zoomIn",
            "picker.zoomOut",
            "picker.zoomToFit",
            "picker.continue",
            "picker.lifecycle",
            "picker.abort",
            "picker.destroy",
        ),
    )
    val snapshot = if (scenario == PickerEvidenceScenario.Loading ||
        scenario == PickerEvidenceScenario.Error
    ) null else evidenceSnapshot(scenario)
    val ready = ReadyInfo(
        protocolRevision = 2,
        mode = if (scenario == PickerEvidenceScenario.Overview) EventMode.Test else EventMode.Live,
        transport = TransportName.Android,
        eventKey = EVIDENCE_EVENT,
    )
    val error = if (scenario == PickerEvidenceScenario.Error) {
        SeatLayerException.Transport(
            "The secure buyer session could not be opened. Check your connection and try again.",
        )
    } else {
        null
    }
    val presentation = evidencePresentation(scenario)
    val state = SeatLayerPickerState(
        phase = when (scenario) {
            PickerEvidenceScenario.Loading -> SeatLayerPickerPhase.Loading
            PickerEvidenceScenario.Error -> SeatLayerPickerPhase.Failed(requireNotNull(error))
            else -> SeatLayerPickerPhase.Ready(ready)
        },
        snapshot = snapshot,
        seatView = if (scenario == PickerEvidenceScenario.Panorama) {
            SeatLayerSeatView(
                seatId = "seat-a12",
                title = "Section 102 · Row A · Seat 12",
                caption = "Drag to look around from this seat",
                badge = null,
                real = true,
                generated = false,
                dragHint = "Drag to explore",
            )
        } else {
            null
        },
        lastError = error,
        holdLapse = if (scenario == PickerEvidenceScenario.Recovery) {
            SeatLayerPickerHoldLapse(
                lapsedLabels = listOf("A-12", "A-13"),
                recoverableLabels = listOf("A-12"),
                heldForMillis = 300_000,
            )
        } else {
            null
        },
        presentation = presentation,
        bundleInfo = bundle,
    )
    installEvidenceState(holder, state, bundle)
    return EvidenceScope(holder, themeMode, options)
}

private fun evidencePresentation(
    scenario: PickerEvidenceScenario,
): SeatLayerPickerPresentationState {
    val seat = evidenceSeat()
    val table = evidenceTable()
    return when (scenario) {
        PickerEvidenceScenario.Confirmation -> SeatLayerPickerPresentationState(
            pendingSeat = seat,
        )
        PickerEvidenceScenario.GeneralAdmission -> SeatLayerPickerPresentationState(
            activePrompt = SeatLayerPickerPrompt.GeneralAdmission(
                SeatLayerPickerGeneralAdmissionArea(
                    id = "ga-floor",
                    label = "Standing floor",
                    capacity = 300,
                    available = 48,
                    categoryKey = "standard",
                    price = 54.0,
                    currency = "USD",
                    tiers = seat.tiers.take(2),
                ),
            ),
        )
        PickerEvidenceScenario.Table -> SeatLayerPickerPresentationState(
            pendingTable = table,
            activePrompt = SeatLayerPickerPrompt.Table(table),
        )
        PickerEvidenceScenario.Cart -> SeatLayerPickerPresentationState(cartExpanded = true)
        PickerEvidenceScenario.Recovery -> SeatLayerPickerPresentationState(
            removalUndo = SeatLayerPickerRemovalUndo(
                line = evidenceCartLines().last(),
                sessionId = EVIDENCE_SESSION,
                phase = SeatLayerPickerRemovalPhase.UndoWindow,
            ),
        )
        PickerEvidenceScenario.PanoramaUnavailable -> SeatLayerPickerPresentationState(
            lastActionError = SeatLayerException.Transport(
                "Seat view is unavailable for this seat.",
            ),
        )
        else -> SeatLayerPickerPresentationState()
    }
}

@Suppress("UNCHECKED_CAST")
private fun installEvidenceState(
    holder: SeatLayerPickerStateHolder,
    state: SeatLayerPickerState,
    bundle: BundleInfo,
) {
    val stateField = SeatLayerPickerStateHolder::class.java.getDeclaredField("mutableState")
    stateField.isAccessible = true
    (stateField.get(holder) as MutableStateFlow<SeatLayerPickerState>).value = state

    val controller = holder.controller
    val bundleField = controller.javaClass.getDeclaredField("bundleInfo")
    bundleField.isAccessible = true
    bundleField.set(controller, bundle)

    val transportType = Class.forName(
        "io.seatlayer.android.SeatLayerPickerCommandTransport",
    )
    val transport = Proxy.newProxyInstance(
        transportType.classLoader,
        arrayOf(transportType),
    ) { proxy, method, _ ->
        when (method.name) {
            "command" -> JsonObject(emptyMap())
            "toString" -> "VisualEvidenceTransport"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> false
            else -> null
        }
    }
    val transportField = controller.javaClass.getDeclaredField("transport")
    transportField.isAccessible = true
    transportField.set(controller, transport)
}

private class EvidenceScope(
    override val stateHolder: SeatLayerPickerStateHolder,
    override val themeMode: SeatLayerPickerThemeMode,
    override val options: SeatLayerPickerOptions,
) : SeatLayerPickerScope {
    override val state: SeatLayerPickerState get() = stateHolder.state.value
    override val controller = stateHolder.controller
    override val theme: SeatLayerPickerTheme = if (themeMode == SeatLayerPickerThemeMode.Dark) {
        SeatLayerPickerTheme.dark()
    } else {
        SeatLayerPickerTheme.light()
    }
    override val strings: SeatLayerPickerStrings = SeatLayerPickerStrings.localized("en-US")
    override val styles: SeatLayerPickerStyles = SeatLayerPickerStyles()
    override val moneyFormatter: SeatLayerPickerMoneyFormatter =
        SeatLayerPickerMoneyFormatter { amount, currency ->
            "$currency ${"%.2f".format(java.util.Locale.US, amount)}"
        }
    override val callbacks: SeatLayerPickerCallbacks = SeatLayerPickerCallbacks()
}

@Composable
internal fun PickerEvidenceRenderer(scenario: PickerEvidenceScenario) {
    val scope = LocalSeatLayerPickerScope.current
    val immersive = scenario == PickerEvidenceScenario.Venue3D ||
        scenario == PickerEvidenceScenario.Panorama
    val background = if (immersive) {
        Brush.verticalGradient(listOf(Color(0xFF243147), Color(0xFF080D17)))
    } else {
        Brush.verticalGradient(
            listOf(
                parseColor(scope.theme.mapTheme.background) ?: scope.theme.background,
                if (scope.themeMode == SeatLayerPickerThemeMode.Dark) {
                    Color(0xFF141C2A)
                } else {
                    Color(0xFFE0E6EF)
                },
            ),
        )
    }
    Box(Modifier.fillMaxSize().background(background)) {
        if (immersive) {
            EvidenceImmersiveStage()
        } else {
            EvidenceSeatMap()
        }
        Surface(
            modifier = Modifier.align(Alignment.Center).padding(bottom = 78.dp),
            shape = RoundedCornerShape(999.dp),
            color = scope.theme.surface.copy(alpha = 0.9f),
            contentColor = scope.theme.muted,
        ) {
            Text(
                "Renderer-owned test canvas",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EvidenceSeatMap() {
    Column(
        modifier = Modifier.alignEvidenceMap(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.width(180.dp).height(54.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFCAD3E1),
        ) { Box(contentAlignment = Alignment.Center) { Text("STAGE", fontSize = 11.sp) } }
        Spacer(Modifier.height(10.dp))
        repeat(6) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(12) { seat ->
                    val selected = row == 2 && seat in 5..6
                    Box(
                        Modifier
                            .size(if (selected) 13.dp else 11.dp)
                            .background(
                                if (selected) Color(0xFF66509B) else Color(0xFF3A8C83),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

private fun Modifier.alignEvidenceMap(): Modifier = this
    .fillMaxSize()
    .padding(top = 142.dp, bottom = 118.dp)

@Composable
private fun EvidenceImmersiveStage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("FIXTURE ARENA", color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp)
        Spacer(Modifier.height(24.dp))
        repeat(5) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(9) { seat ->
                    Box(
                        Modifier
                            .size((18 - row).dp)
                            .background(
                                if (row == 1 && seat == 4) Color(0xFFB9A6F5)
                                else Color.White.copy(alpha = 0.2f + row * 0.05f),
                                RoundedCornerShape(4.dp),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private fun evidenceSnapshot(scenario: PickerEvidenceScenario): SeatLayerPickerSnapshot {
    val tierSeat = evidenceSeat()
    val tableSeat = evidenceTable()
    val selection = when (scenario) {
        PickerEvidenceScenario.Confirmation -> listOf(tierSeat)
        PickerEvidenceScenario.Table -> listOf(tableSeat)
        else -> emptyList()
    }
    val cartLines = when (scenario) {
        PickerEvidenceScenario.Confirmation -> listOf(evidenceCartLines().first())
        PickerEvidenceScenario.Cart -> evidenceCartLines()
        else -> emptyList()
    }
    val venue3D = scenario == PickerEvidenceScenario.Venue3D ||
        scenario == PickerEvidenceScenario.Panorama
    val soldOut = scenario == PickerEvidenceScenario.Empty ||
        scenario == PickerEvidenceScenario.SalesClosed
    val categories = listOf(
        SeatLayerPickerCategory(
            key = "standard",
            label = "Standard",
            color = "#3A8C83",
            priceMin = 54.0,
            priceMax = 78.0,
            available = if (soldOut) 0 else 84,
            notForSale = soldOut,
            tiers = tierSeat.tiers,
        ),
        SeatLayerPickerCategory(
            key = "premium",
            label = "Premium",
            color = "#B18443",
            priceMin = 110.0,
            priceMax = 145.0,
            available = if (soldOut) 0 else 22,
            notForSale = soldOut,
            tiers = emptyList(),
        ),
    )
    return SeatLayerPickerSnapshot(
        schema = SEATLAYER_PICKER_SNAPSHOT_SCHEMA,
        sessionId = EVIDENCE_SESSION,
        revision = 7,
        event = SeatLayerPickerEventDetails(
            key = EVIDENCE_EVENT,
            name = "Fixture Arena",
            mode = if (scenario == PickerEvidenceScenario.Overview) EventMode.Test else EventMode.Live,
            currency = "USD",
            venue = "North Hall",
            startsAt = null,
            timezone = "America/New_York",
            locale = "en-US",
            posterUrl = null,
            salesClosed = scenario == PickerEvidenceScenario.SalesClosed,
        ),
        branding = SeatLayerPickerBranding(
            brandName = "SeatLayer",
            logoUrl = null,
            attributionRequired = true,
            accent = "#66509B",
            accentInk = "#FFFFFF",
            background = null,
            surface = null,
            text = null,
            muted = null,
            line = null,
            fontFamily = null,
            radius = 14.0,
        ),
        categories = categories,
        zones = listOf(SeatLayerPickerZone("lower", "Lower bowl", "#3A8C83")),
        sections = evidenceSections(),
        generalAdmissionAreas = listOf(
            SeatLayerPickerGeneralAdmissionArea(
                id = "ga-floor",
                label = "Standing floor",
                capacity = 300,
                available = if (soldOut) 0 else 48,
                categoryKey = "standard",
                price = 54.0,
                currency = "USD",
                tiers = tierSeat.tiers,
            ),
        ),
        bestAvailableZones = listOf(SeatLayerPickerZone("lower", "Lower bowl", null)),
        map = evidenceMapState(scenario, venue3D, tierSeat),
        selection = selection,
        selectionValidity = null,
        maxSelection = 8,
        ticketCount = cartLines.sumOf(SeatLayerPickerCartLine::quantity),
        cartLines = cartLines,
        cartTotal = cartLines.sumOf(SeatLayerPickerCartLine::total),
        currency = "USD",
        hold = SeatLayerPickerHold(
            active = scenario == PickerEvidenceScenario.Cart,
            expiresAt = if (scenario == PickerEvidenceScenario.Cart) {
                System.currentTimeMillis() + 8 * 60_000.0
            } else {
                null
            },
            owner = if (scenario == PickerEvidenceScenario.Cart) "picker" else null,
        ),
        accessConfigured = true,
        accessStatus = "ready",
        accessReason = null,
        capabilities = setOf(
            "venue3d",
            "seatView",
            "accessibilityFilter",
            "limitedViewFilter",
        ),
        raw = JsonObject(emptyMap()),
    )
}

private fun evidenceMapState(
    scenario: PickerEvidenceScenario,
    venue3D: Boolean,
    target: SeatLayerPickerSelectedSeat,
): SeatLayerPickerMapState {
    val venueOverview = scenario == PickerEvidenceScenario.Overview
    return SeatLayerPickerMapState(
        rung = if (venueOverview) "venue" else "seats",
        viewMode = if (venue3D) "perspective" else "flat",
        buyerView = if (venue3D) "venue3d" else "map",
        view3DNavigationMode = "orbit",
        view3DTargetSeatId = if (venue3D) target.id else null,
        activeFloorId = "floor-1",
        focusedSectionId = if (venueOverview) null else "section-102",
        focusedSection = if (venueOverview) null else evidenceSections().first(),
        colorblindSafe = false,
        hideLimitedView = false,
        canZoomIn = true,
        canZoomOut = !venueOverview,
        categoryFilter = emptyList(),
        accessibilityFilter = emptyList(),
        accessNeeds = listOf(
            SeatLayerPickerAccessNeed("wheelchair", 8),
            SeatLayerPickerAccessNeed("companion", 8),
            SeatLayerPickerAccessNeed("aisle-seat", 5),
            SeatLayerPickerAccessNeed("step-free", 4),
        ),
        floors = listOf(
            SeatLayerPickerFloorInfo("all", "All floors", null),
            SeatLayerPickerFloorInfo("floor-1", "Lower", 1),
            SeatLayerPickerFloorInfo("floor-2", "Upper", 2),
        ),
        floorMode = null,
        floorLabelStyle = "name",
        viewportInsets = null,
        view3DTargetSeat = if (venue3D) target else null,
        view3DPreviousSeatId = if (venue3D) "seat-a11" else null,
        view3DNextSeatId = if (venue3D) "seat-a13" else null,
        view3DFocusedSectionId = if (venue3D) "section-102" else null,
        reportsView3DPosition = venue3D,
    )
}

private fun evidenceSeat(): SeatLayerPickerSelectedSeat = SeatLayerPickerSelectedSeat(
    id = "seat-a12",
    label = "A-12",
    displayLabel = "Section 102 · Row A · Seat 12",
    displayType = "seat",
    objectId = "seat-a12",
    objectType = "seat",
    bookingMode = "fixed",
    sectionLabel = "Section 102",
    rowLabel = "A",
    seatNumber = "12",
    categoryKey = "standard",
    price = 78.0,
    currency = "USD",
    tiers = listOf(
        SeatLayerPickerCategoryTier("adult", "Adult", 78.0, "USD", null, null),
        SeatLayerPickerCategoryTier(
            "child",
            "Child",
            46.0,
            "USD",
            "age",
            "For guests aged 3–15. ID may be requested.",
        ),
        SeatLayerPickerCategoryTier(
            "companion",
            "Companion",
            0.0,
            "USD",
            "companion",
            "Use with an eligible accessibility ticket.",
        ),
    ),
    tierId = "adult",
    accessibility = listOf("aisle-seat"),
    wheelchairSpaceType = null,
    quantity = 1,
    capacity = null,
    minOccupancy = null,
    maxOccupancy = null,
)

private fun evidenceTable(): SeatLayerPickerSelectedSeat = SeatLayerPickerSelectedSeat(
    id = "table-7",
    label = "T-07",
    displayLabel = "Terrace table 7",
    displayType = "table",
    objectId = "table-7",
    objectType = "table",
    bookingMode = "variable",
    sectionLabel = "Terrace",
    rowLabel = null,
    seatNumber = null,
    categoryKey = "premium",
    price = 42.0,
    currency = "USD",
    tiers = listOf(
        SeatLayerPickerCategoryTier("adult", "Adult", 42.0, "USD", null, null),
        SeatLayerPickerCategoryTier("child", "Child", 25.0, "USD", "age", null),
    ),
    tierId = "adult",
    accessibility = emptyList(),
    wheelchairSpaceType = null,
    quantity = 4,
    capacity = 8,
    minOccupancy = 2,
    maxOccupancy = 8,
)

private fun evidenceCartLines(): List<SeatLayerPickerCartLine> = listOf(
    cartLine("line-a12", "A-12", "12", 78.0),
    cartLine("line-a13", "A-13", "13", 78.0),
    cartLine("line-a14", "A-14", "14", 78.0),
)

private fun cartLine(
    key: String,
    label: String,
    number: String,
    price: Double,
): SeatLayerPickerCartLine = SeatLayerPickerCartLine(
    lineKey = key,
    label = label,
    displayLabel = "Section 102 · Row A · Seat $number",
    displayType = "seat",
    objectId = "seat-$label",
    objectType = "seat",
    categoryKey = "standard",
    tierId = null,
    unitPrice = price,
    currency = "USD",
    quantity = 1,
    seatId = "seat-$label",
    sectionLabel = "Section 102",
    rowLabel = "A",
    seatNumber = number,
)

private fun evidenceSections(): List<SeatLayerPickerSectionSummary> = listOf(
    SeatLayerPickerSectionSummary(
        id = "section-102",
        label = "102",
        displayLabel = "Section 102",
        zoneId = "lower",
        zoneLabel = "Lower bowl",
        entrance = "Gate B",
        color = "#3A8C83",
        dominantCategoryKey = "standard",
        seatsLeft = 21,
        priceMin = 54.0,
        priceMax = 78.0,
    ),
    SeatLayerPickerSectionSummary(
        id = "section-103",
        label = "103",
        displayLabel = "Section 103",
        zoneId = "lower",
        zoneLabel = "Lower bowl",
        entrance = "Gate C",
        color = "#B18443",
        dominantCategoryKey = "premium",
        seatsLeft = 12,
        priceMin = 110.0,
        priceMax = 145.0,
    ),
)

private const val EVIDENCE_EVENT = "ev_android_visual_fixture"
private const val EVIDENCE_SESSION = "android-visual-session"
