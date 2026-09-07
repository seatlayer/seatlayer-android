package io.seatlayer.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SeatLayerChartLoad
import io.seatlayer.android.SeatLayerPickerHold
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerBehavior
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff
import io.seatlayer.android.SeatLayerPickerController
import io.seatlayer.android.SeatLayerPickerPresentationState
import io.seatlayer.android.SeatLayerPickerSeatConfidence
import io.seatlayer.android.SeatLayerPickerSectionSummary
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SelectionValidity
import io.seatlayer.android.SeatLayerPickerState
import io.seatlayer.android.SeatLayerPickerThemeMode

public enum class SeatLayerPickerLayoutMode { Adaptive, Compact, Wide }

/**
 * Which pieces of native chrome the ready-made picker draws.
 *
 * The names are the ones every SeatLayer buyer SDK uses, so a team porting a
 * screen between platforms is reading the same switch. Each control also
 * remains a standalone public composable for hosts that build their own layout.
 */
@Immutable
public data class SeatLayerPickerChromeOptions(
    val showHeader: Boolean = true,
    val showPriceRail: Boolean = true,
    val showFloorSelector: Boolean = true,
    val showFloorStrip: Boolean = true,
    val showMapControls: Boolean = true,
    /**
     * Back to the venue from a framed section. Null is off on a phone, where
     * the whole-venue disc in the map's own column already releases a section.
     */
    val showOverviewControl: Boolean? = null,
    /** The `+` disc. Null is on at both widths. */
    val showZoomControls: Boolean? = null,
    /** The whole-venue disc. Null is on at both widths. */
    val showZoomToFitControl: Boolean? = null,
    /**
     * The colourblind-safe disc. Null is off on a phone, where the choice lives
     * inside the accessibility sheet rather than in a corner of the map.
     */
    val showColorblindControl: Boolean? = null,
    val showViewModeControl: Boolean = true,
    val showAccessibilityControl: Boolean = true,
    val showTicketPanel: Boolean = true,
    val showConfirmCard: Boolean = true,
    val showVenue3DChrome: Boolean = true,
    val showSeatViewChrome: Boolean = true,
    val showHoldPill: Boolean = true,
    // Retained for source compatibility. Built-in layouts intentionally ignore
    // this value; API branding is authoritative for attribution visibility.
    val showAttribution: Boolean = true,
) {
    @Deprecated("Renamed to showHeader.", ReplaceWith("showHeader"))
    val header: Boolean get() = showHeader

    @Deprecated("Renamed to showPriceRail.", ReplaceWith("showPriceRail"))
    val priceLegend: Boolean get() = showPriceRail

    @Deprecated("Renamed to showFloorSelector.", ReplaceWith("showFloorSelector"))
    val floorSelector: Boolean get() = showFloorSelector

    @Deprecated("Renamed to showFloorStrip.", ReplaceWith("showFloorStrip"))
    val floorStrip: Boolean get() = showFloorStrip

    @Deprecated("Renamed to showMapControls.", ReplaceWith("showMapControls"))
    val mapControls: Boolean get() = showMapControls

    @Deprecated("Renamed to showOverviewControl.", ReplaceWith("showOverviewControl"))
    val overview: Boolean get() = overviewControlFor(phone = false)

    @Deprecated("Renamed to showZoomControls.", ReplaceWith("showZoomControls"))
    val zoom: Boolean get() = zoomControlsFor(phone = false)

    @Deprecated("Renamed to showColorblindControl.", ReplaceWith("showColorblindControl"))
    val colorblind: Boolean get() = colorblindControlFor(phone = false)

    @Deprecated("Renamed to showViewModeControl.", ReplaceWith("showViewModeControl"))
    val map3D: Boolean get() = showViewModeControl

    @Deprecated(
        "Renamed to showAccessibilityControl.",
        ReplaceWith("showAccessibilityControl"),
    )
    val accessibility: Boolean get() = showAccessibilityControl

    @Deprecated("Renamed to showTicketPanel.", ReplaceWith("showTicketPanel"))
    val cartSheet: Boolean get() = showTicketPanel

    @Deprecated("Renamed to showConfirmCard.", ReplaceWith("showConfirmCard"))
    val confirmCard: Boolean get() = showConfirmCard

    @Deprecated("Renamed to showHoldPill.", ReplaceWith("showHoldPill"))
    val holdCountdown: Boolean get() = showHoldPill

    @Deprecated("Renamed to showAttribution.", ReplaceWith("showAttribution"))
    val attribution: Boolean get() = showAttribution

    /** Whether the `+` disc is drawn at this width. */
    public fun zoomControlsFor(phone: Boolean): Boolean = showZoomControls ?: true

    /** Whether the whole-venue disc is drawn at this width. */
    public fun zoomToFitControlFor(phone: Boolean): Boolean = showZoomToFitControl ?: true

    /**
     * Whether the back-to-venue disc is drawn at this width.
     *
     * Off on a phone by default: the whole-venue disc at the foot of the map's
     * own column releases a framed section with the same press that fits the
     * chart, and a second control for one move reads as a puzzle.
     */
    public fun overviewControlFor(phone: Boolean): Boolean = showOverviewControl ?: !phone

    /**
     * Whether the colourblind-safe disc is drawn at this width.
     *
     * Off on a phone by default: it lives in the accessibility sheet, where the
     * rest of the display choices are, rather than alone in a map corner.
     */
    public fun colorblindControlFor(phone: Boolean): Boolean = showColorblindControl ?: !phone

    /**
     * The dock bar moved to [SeatLayerPickerOptions.showDockBar] and is off by
     * default on every width: the section it named is already on the map.
     */
    @Deprecated(
        "Moved to SeatLayerPickerOptions.showDockBar, default off.",
        ReplaceWith("SeatLayerPickerOptions().showDockBar"),
    )
    val dock: Boolean get() = false

    /**
     * The zoom-to-fit control is retired: the whole-venue control does the same
     * thing without going stale under a pinch.
     */
    @Deprecated("Retired; use the whole-venue control.", ReplaceWith("showOverviewControl"))
    val fit: Boolean get() = false
}

/** A price override for one category. */
@Immutable
public data class SeatLayerCategoryPricing(
    /** Price when the category has no tiers. */
    val base: Double? = null,
    /** Price per tier id. */
    val tiers: Map<String, Double> = emptyMap(),
)

/** Host-supplied pricing and money formatting. */
@Immutable
public data class SeatLayerPickerPricing(
    val categories: Map<String, SeatLayerCategoryPricing> = emptyMap(),
    /** How every amount in the native chrome is rendered. */
    val formatter: SeatLayerMoneyFormatter? = null,
)

@Immutable
public data class SeatLayerPickerOptions(
    val layout: SeatLayerPickerLayoutMode = SeatLayerPickerLayoutMode.Adaptive,
    val chrome: SeatLayerPickerChromeOptions = SeatLayerPickerChromeOptions(),
    val readOnly: Boolean = false,
    val confirmSelection: Boolean = true,
    val enableBestAvailable: Boolean = true,
    val enable3D: Boolean = true,
    val enableSeatView: Boolean = true,
    /**
     * How long a hold this picker makes should last.
     *
     * Left unset, the server's own window applies and the SDK never names a
     * number: a countdown the SDK invents is a promise the server did not make.
     */
    val holdTtl: Duration? = null,
    val initialHoldId: String? = null,
    val max3DSeats: Int? = null,
    val hideEventDetails: Boolean = false,
    val panelInitiallyCollapsed: Boolean = true,
    val refreshOnResume: Boolean = true,
    val announceHoldLapse: Boolean = true,
    val haptics: Boolean = true,
    val languages: List<String> = emptyList(),
    /** The event's name, where the host would rather say it than the chart. */
    val eventName: String? = null,
    /** Whether a hold that leaves without expiring raises the booked overlay. */
    val showBookedOverlay: Boolean = true,
    /**
     * The dock bar naming the focused section. Null is off on every width: the
     * section it names is already on the map, under the buyer's thumb.
     */
    val showDockBar: Boolean? = null,
    /**
     * The card offering more time on a hold. Null is off on a phone, where the
     * header's own clock already carries the hold.
     */
    val showExtendHoldPrompt: Boolean? = null,
    /** Whether the seat card is drawn on glass over the map. */
    val seatCardGlass: Boolean = true,
    /** Whether a colourblind choice outlives the session on this device. */
    val persistColorblindPreference: Boolean = true,
    /** Whether the picker takes over the status and navigation bars. */
    val manageSystemOverlays: Boolean = true,
    val pricing: SeatLayerPickerPricing? = null,
) {
    @Deprecated("Renamed to enable3D.", ReplaceWith("enable3D"))
    val enableVenue3D: Boolean get() = enable3D

    @Deprecated("Renamed to holdTtl, which takes a Duration.", ReplaceWith("holdTtl"))
    val holdTtlMillis: Int? get() = holdTtl?.inWholeMilliseconds?.toInt()

    internal fun behavior(): SeatLayerPickerBehavior = SeatLayerPickerBehavior(
        readOnly = readOnly,
        confirmSelection = confirmSelection,
        enableBestAvailable = enableBestAvailable,
        enable3D = enable3D,
        enableSeatView = enableSeatView,
        holdTtlMillis = holdTtl?.inWholeMilliseconds?.toInt(),
        initialHoldId = initialHoldId,
        max3DSeats = max3DSeats,
        hideEventDetails = hideEventDetails,
        panelInitiallyCollapsed = panelInitiallyCollapsed,
        refreshOnResume = refreshOnResume,
        announceHoldLapse = announceHoldLapse,
        languages = languages,
    )
}

/** Visual-only defaults. Geometry cannot reduce interactive targets below 48dp. */
@Immutable
public data class SeatLayerPickerPartStyle(
    val containerColor: Color? = null,
    val contentColor: Color? = null,
    val cornerRadius: Dp? = null,
    val elevation: Dp? = null,
    val horizontalPadding: Dp? = null,
    val verticalPadding: Dp? = null,
)

@Immutable
public data class SeatLayerPickerStyles(
    /**
     * The wash drawn over the map while a card is up.
     *
     * Null takes the design source's own confirm scrim over the theme's
     * background, which is what keeps the wash in step with a rebranded
     * picker; a host that names a colour here owns it outright.
     */
    val scrimColor: Color? = null,
    val buttonRadius: Dp = SeatLayerRadiusTokens.BUTTON.dp,
    val cardRadius: Dp = SeatLayerRadiusTokens.CARD.dp,
    val sheetRadius: Dp = SeatLayerRadiusTokens.SHEET.dp,
    val horizontalGutter: Dp = SeatLayerSizeTokens.CONFIRM_CARD_GUTTER.dp,
    val panelWidth: Dp = SeatLayerSizeTokens.CONFIRM_CARD_MAX_WIDTH.dp,
    val wideBreakpoint: Dp = SeatLayerSizeTokens.WIDE_BREAKPOINT.dp,
    val parts: Map<SeatLayerPickerPart, SeatLayerPickerPartStyle> = emptyMap(),
) {
    public operator fun get(part: SeatLayerPickerPart): SeatLayerPickerPartStyle =
        parts[part] ?: SeatLayerPickerPartStyle()
}

/**
 * Host callbacks the picker raises.
 *
 * The names match every SeatLayer buyer SDK, so the whole shared surface is
 * declared here at once. Thirteen of them — from [onSelectionValidityChanged]
 * through [onContinue] — are additive and not yet raised by this SDK's own
 * chrome; they stay inert (a no-op default that is never invoked) until the
 * screens that own those moments land, and a host may wire them today without
 * a later source change.
 */
public data class SeatLayerPickerCallbacks(
    val onCheckout: suspend (SeatLayerPickerCheckoutHandoff) -> Unit = {},
    val onClose: () -> Unit = {},
    val onError: (SeatLayerException) -> Unit = {},
    val onReady: (ReadyInfo) -> Unit = {},
    val onSnapshot: (SeatLayerPickerSnapshot) -> Unit = {},
    val onSelectionChanged: (List<SeatLayerPickerSelectedSeat>) -> Unit = {},
    val onHoldChanged: (SeatLayerPickerHold) -> Unit = {},
    val onChartLoad: (SeatLayerChartLoad) -> Unit = {},
    val onSelectionValidityChanged: (SelectionValidity?) -> Unit = {},
    val onHoldExpired: () -> Unit = {},
    /** A hold the host took to checkout that left without expiring: a sale. */
    val onBooked: (SeatLayerPickerCheckoutHandoff) -> Unit = {},
    val onAccessExpired: () -> Unit = {},
    val onAccessUnavailable: (String?) -> Unit = {},
    val onSelectedObjectUnavailable: (List<String>) -> Unit = {},
    val onThemeResolved: (SeatLayerPickerTheme) -> Unit = {},
    val onSectionFocused: (SeatLayerPickerSectionSummary?) -> Unit = {},
    val onSeatSelected: (SeatLayerPickerSelectedSeat) -> Unit = {},
    val onSeatRemoved: (SeatLayerPickerSelectedSeat) -> Unit = {},
    val onSeatViewOpened: (String) -> Unit = {},
    /** The buyer asked how a modelled view was produced. */
    val onSeatConfidence: (SeatLayerPickerSeatConfidence) -> Unit = {},
    val onContinue: () -> Unit = {},
)

public enum class SeatLayerPickerPart {
    Header,
    Legend,
    FloorSelector,
    FloorStrip,
    SectionNavigator,
    DockBar,
    AccessibilityFilters,
    Map,
    MapControls,
    BestAvailable,
    SeatConfirmation,
    ConfirmCard,
    GeneralAdmissionPrompt,
    TablePrompt,
    CartList,
    CartSheet,
    Venue3D,
    SeatViewChrome,
    HoldCountdown,
    HoldLapse,
    ActionError,
    CheckoutBar,
    Loading,
    Error,
    Empty,
}

@Immutable
public data class SeatLayerPickerPartContext(
    val part: SeatLayerPickerPart,
    val state: SeatLayerPickerState,
    val snapshot: SeatLayerPickerSnapshot?,
    val presentation: SeatLayerPickerPresentationState,
    val controller: SeatLayerPickerController,
    val themeMode: SeatLayerPickerThemeMode,
    val theme: SeatLayerPickerTheme,
    val strings: SeatLayerPickerStrings,
    val options: SeatLayerPickerOptions,
    val styles: SeatLayerPickerStyles,
    val style: SeatLayerPickerPartStyle,
)

public typealias SeatLayerPickerPartBuilder =
    @Composable (SeatLayerPickerPartContext, @Composable () -> Unit) -> Unit

/** Whole-part replacement points shared by ready-made and custom layouts. */
@Immutable
public data class SeatLayerPickerBuilders(
    val header: SeatLayerPickerPartBuilder? = null,
    val legend: SeatLayerPickerPartBuilder? = null,
    val floorSelector: SeatLayerPickerPartBuilder? = null,
    val floorStrip: SeatLayerPickerPartBuilder? = null,
    val sectionNavigator: SeatLayerPickerPartBuilder? = null,
    val dockBar: SeatLayerPickerPartBuilder? = null,
    val accessibilityFilters: SeatLayerPickerPartBuilder? = null,
    val map: SeatLayerPickerPartBuilder? = null,
    val mapControls: SeatLayerPickerPartBuilder? = null,
    val bestAvailable: SeatLayerPickerPartBuilder? = null,
    val seatConfirmation: SeatLayerPickerPartBuilder? = null,
    val confirmCard: SeatLayerPickerPartBuilder? = null,
    val generalAdmissionPrompt: SeatLayerPickerPartBuilder? = null,
    val tablePrompt: SeatLayerPickerPartBuilder? = null,
    val cartList: SeatLayerPickerPartBuilder? = null,
    val cartSheet: SeatLayerPickerPartBuilder? = null,
    val venue3D: SeatLayerPickerPartBuilder? = null,
    val seatViewChrome: SeatLayerPickerPartBuilder? = null,
    val holdCountdown: SeatLayerPickerPartBuilder? = null,
    val holdLapse: SeatLayerPickerPartBuilder? = null,
    val actionError: SeatLayerPickerPartBuilder? = null,
    val checkoutBar: SeatLayerPickerPartBuilder? = null,
    val loading: SeatLayerPickerPartBuilder? = null,
    val error: SeatLayerPickerPartBuilder? = null,
    val empty: SeatLayerPickerPartBuilder? = null,
) {
    internal operator fun get(part: SeatLayerPickerPart): SeatLayerPickerPartBuilder? =
        when (part) {
            SeatLayerPickerPart.Header -> header
            SeatLayerPickerPart.Legend -> legend
            SeatLayerPickerPart.FloorSelector -> floorSelector
            SeatLayerPickerPart.FloorStrip -> floorStrip
            SeatLayerPickerPart.SectionNavigator -> sectionNavigator
            SeatLayerPickerPart.DockBar -> dockBar
            SeatLayerPickerPart.AccessibilityFilters -> accessibilityFilters
            SeatLayerPickerPart.Map -> map
            SeatLayerPickerPart.MapControls -> mapControls
            SeatLayerPickerPart.BestAvailable -> bestAvailable
            SeatLayerPickerPart.SeatConfirmation -> seatConfirmation
            SeatLayerPickerPart.ConfirmCard -> confirmCard
            SeatLayerPickerPart.GeneralAdmissionPrompt -> generalAdmissionPrompt
            SeatLayerPickerPart.TablePrompt -> tablePrompt
            SeatLayerPickerPart.CartList -> cartList
            SeatLayerPickerPart.CartSheet -> cartSheet
            SeatLayerPickerPart.Venue3D -> venue3D
            SeatLayerPickerPart.SeatViewChrome -> seatViewChrome
            SeatLayerPickerPart.HoldCountdown -> holdCountdown
            SeatLayerPickerPart.HoldLapse -> holdLapse
            SeatLayerPickerPart.ActionError -> actionError
            SeatLayerPickerPart.CheckoutBar -> checkoutBar
            SeatLayerPickerPart.Loading -> loading
            SeatLayerPickerPart.Error -> error
            SeatLayerPickerPart.Empty -> empty
        }
}
