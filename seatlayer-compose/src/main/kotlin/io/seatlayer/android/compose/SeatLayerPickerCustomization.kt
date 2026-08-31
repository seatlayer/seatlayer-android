package io.seatlayer.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SeatLayerChartLoad
import io.seatlayer.android.SeatLayerPickerHold
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerBehavior
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff
import io.seatlayer.android.SeatLayerPickerController
import io.seatlayer.android.SeatLayerPickerPresentationState
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerPickerState
import io.seatlayer.android.SeatLayerPickerThemeMode

public enum class SeatLayerPickerLayoutMode { Adaptive, Compact, Wide }

@Immutable
public data class SeatLayerPickerChromeOptions(
    val header: Boolean = true,
    val priceLegend: Boolean = true,
    val floorSelector: Boolean = true,
    val floorStrip: Boolean = true,
    val mapControls: Boolean = true,
    val overview: Boolean = true,
    val zoom: Boolean = true,
    val colorblind: Boolean = true,
    val fit: Boolean = true,
    val map3D: Boolean = true,
    val accessibility: Boolean = true,
    val cartSheet: Boolean = true,
    val dock: Boolean = true,
    val confirmCard: Boolean = true,
    val holdCountdown: Boolean = true,
    val attribution: Boolean = true,
)

@Immutable
public data class SeatLayerPickerOptions(
    val layout: SeatLayerPickerLayoutMode = SeatLayerPickerLayoutMode.Adaptive,
    val chrome: SeatLayerPickerChromeOptions = SeatLayerPickerChromeOptions(),
    val readOnly: Boolean = false,
    val confirmSelection: Boolean = true,
    val enableBestAvailable: Boolean = true,
    val enableVenue3D: Boolean = true,
    val enableSeatView: Boolean = true,
    val holdTtlMillis: Int? = null,
    val initialHoldId: String? = null,
    val max3DSeats: Int? = null,
    val hideEventDetails: Boolean = false,
    val panelInitiallyCollapsed: Boolean = true,
    val refreshOnResume: Boolean = true,
    val announceHoldLapse: Boolean = true,
    val haptics: Boolean = true,
    val languages: List<String> = emptyList(),
) {
    internal fun behavior(): SeatLayerPickerBehavior = SeatLayerPickerBehavior(
        readOnly = readOnly,
        confirmSelection = confirmSelection,
        enableBestAvailable = enableBestAvailable,
        enableVenue3D = enableVenue3D,
        enableSeatView = enableSeatView,
        holdTtlMillis = holdTtlMillis,
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
    val buttonRadius: Dp = SeatLayerPickerTokens.RADIUS_BUTTON.dp,
    val cardRadius: Dp = SeatLayerPickerTokens.RADIUS_CARD.dp,
    val sheetRadius: Dp = SeatLayerPickerTokens.RADIUS_SHEET.dp,
    val horizontalGutter: Dp = SeatLayerPickerTokens.SIZE_CONFIRM_CARD_GUTTER.dp,
    val panelWidth: Dp = SeatLayerPickerTokens.SIZE_CONFIRM_CARD_MAX_WIDTH.dp,
    val wideBreakpoint: Dp = SeatLayerPickerTokens.SIZE_WIDE_BREAKPOINT.dp,
    val parts: Map<SeatLayerPickerPart, SeatLayerPickerPartStyle> = emptyMap(),
) {
    public operator fun get(part: SeatLayerPickerPart): SeatLayerPickerPartStyle =
        parts[part] ?: SeatLayerPickerPartStyle()
}

public data class SeatLayerPickerCallbacks(
    val onCheckout: suspend (SeatLayerPickerCheckoutHandoff) -> Unit = {},
    val onClose: () -> Unit = {},
    val onError: (SeatLayerException) -> Unit = {},
    val onReady: (ReadyInfo) -> Unit = {},
    val onSnapshot: (SeatLayerPickerSnapshot) -> Unit = {},
    val onSelectionChanged: (List<SeatLayerPickerSelectedSeat>) -> Unit = {},
    val onHoldChanged: (SeatLayerPickerHold) -> Unit = {},
    val onChartLoad: (SeatLayerChartLoad) -> Unit = {},
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
