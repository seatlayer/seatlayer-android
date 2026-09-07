package io.seatlayer.android.compose

import io.seatlayer.android.EventMode
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SEATLAYER_PICKER_SNAPSHOT_SCHEMA
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerPickerAccessNeed
import io.seatlayer.android.SeatLayerPickerBranding
import io.seatlayer.android.SeatLayerPickerEventDetails
import io.seatlayer.android.SeatLayerPickerHold
import io.seatlayer.android.SeatLayerPickerMapState
import io.seatlayer.android.SeatLayerPickerPhase
import io.seatlayer.android.SeatLayerPickerPresentationState
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerPickerState
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerThemeMode
import io.seatlayer.android.TransportName
import kotlinx.serialization.json.JsonObject

/**
 * A deterministic scope for the buyer-facing states and the accessibility
 * sheet.
 *
 * Everything a state gates on is a parameter, so a test says what the runtime
 * reported rather than reaching into a fixture that reports one thing.
 */
internal fun pickerAccessScope(
    needs: List<SeatLayerPickerAccessNeed> = emptyList(),
    active: List<String> = emptyList(),
    hideLimitedView: Boolean = false,
    colorblindSafe: Boolean = false,
    hold: SeatLayerPickerHold = SeatLayerPickerHold(false, null, null),
    salesClosed: Boolean = false,
    accessStatus: String = "ready",
    accessReason: String? = null,
    presentation: SeatLayerPickerPresentationState = SeatLayerPickerPresentationState(),
    options: SeatLayerPickerOptions = SeatLayerPickerOptions(),
    themeMode: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode.Light,
    capabilities: Set<String> = setOf("accessibilityFilter", "limitedViewFilter"),
): SeatLayerPickerScope {
    val holder = SeatLayerPickerStateHolder(SeatLayerConfiguration(event = FIXTURE_EVENT))
    val snapshot = SeatLayerPickerSnapshot(
        schema = SEATLAYER_PICKER_SNAPSHOT_SCHEMA,
        sessionId = FIXTURE_SESSION,
        revision = 1,
        event = SeatLayerPickerEventDetails(
            key = FIXTURE_EVENT,
            name = "Fixture Arena",
            mode = EventMode.Live,
            currency = "USD",
            venue = "North Hall",
            startsAt = null,
            timezone = null,
            locale = "en",
            posterUrl = null,
            salesClosed = salesClosed,
        ),
        branding = FIXTURE_BRANDING,
        categories = emptyList(),
        zones = emptyList(),
        sections = emptyList(),
        generalAdmissionAreas = emptyList(),
        bestAvailableZones = emptyList(),
        map = SeatLayerPickerMapState(
            rung = "sections",
            viewMode = "flat",
            buyerView = "map",
            view3DNavigationMode = "orbit",
            view3DTargetSeatId = null,
            activeFloorId = null,
            focusedSectionId = null,
            focusedSection = null,
            colorblindSafe = colorblindSafe,
            hideLimitedView = hideLimitedView,
            canZoomIn = true,
            canZoomOut = true,
            categoryFilter = emptyList(),
            accessibilityFilter = active,
            accessNeeds = needs,
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
        hold = hold,
        accessConfigured = true,
        accessStatus = accessStatus,
        accessReason = accessReason,
        capabilities = capabilities,
        raw = JsonObject(emptyMap()),
    )
    val state = SeatLayerPickerState(
        phase = SeatLayerPickerPhase.Ready(
            ReadyInfo(
                protocolRevision = 2,
                mode = EventMode.Live,
                transport = TransportName.Android,
                eventKey = FIXTURE_EVENT,
            ),
        ),
        snapshot = snapshot,
        presentation = presentation,
    )
    return FixtureScope(holder, state, themeMode, options)
}

private class FixtureScope(
    override val stateHolder: SeatLayerPickerStateHolder,
    override val state: SeatLayerPickerState,
    override val themeMode: SeatLayerPickerThemeMode,
    override val options: SeatLayerPickerOptions,
) : SeatLayerPickerScope {
    override val controller = stateHolder.controller
    override val theme: SeatLayerPickerTheme = if (themeMode == SeatLayerPickerThemeMode.Dark) {
        SeatLayerPickerTheme.dark()
    } else {
        SeatLayerPickerTheme.light()
    }
    override val strings: SeatLayerPickerStrings = SeatLayerPickerStrings.localized("en")
    override val styles: SeatLayerPickerStyles = SeatLayerPickerStyles()
    override val moneyFormatter: SeatLayerMoneyFormatter =
        SeatLayerMoneyFormatter { amount, currency -> "$currency ${amount.toInt()}" }
    override val callbacks: SeatLayerPickerCallbacks = SeatLayerPickerCallbacks()
}

private val FIXTURE_BRANDING = SeatLayerPickerBranding(
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
)

private const val FIXTURE_EVENT = "ev_states_fixture"
private const val FIXTURE_SESSION = "fixture-session"
