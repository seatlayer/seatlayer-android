package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SeatLayerChartLoad
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff
import io.seatlayer.android.SeatLayerPickerHold
import io.seatlayer.android.SeatLayerPickerProjections
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerThemeMode
import io.seatlayer.android.SeatLayerPickerViewportInsets

/** Complete ready-made native picker, composed exclusively from public parts. */
@Composable
public fun SeatLayerPicker(
    configuration: SeatLayerConfiguration,
    modifier: Modifier = Modifier,
    themeMode: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode.Auto,
    theme: SeatLayerPickerTheme? = null,
    strings: SeatLayerPickerStrings = pickerDefaultStrings(),
    options: SeatLayerPickerOptions = SeatLayerPickerOptions(),
    styles: SeatLayerPickerStyles = SeatLayerPickerStyles(),
    moneyFormatter: SeatLayerMoneyFormatter = SeatLayerMoneyFormatter.localized(),
    builders: SeatLayerPickerBuilders = SeatLayerPickerBuilders(),
    stateHolder: SeatLayerPickerStateHolder = rememberSeatLayerPickerStateHolder(
        configuration,
        options,
    ),
    onCheckout: suspend (SeatLayerPickerCheckoutHandoff) -> Unit = {},
    onError: (SeatLayerException) -> Unit = {},
    onReady: (ReadyInfo) -> Unit = {},
    onSnapshot: (SeatLayerPickerSnapshot) -> Unit = {},
    onSelectionChanged: (List<SeatLayerPickerSelectedSeat>) -> Unit = {},
    onHoldChanged: (SeatLayerPickerHold) -> Unit = {},
    onClose: () -> Unit,
    onChartLoad: (SeatLayerChartLoad) -> Unit = {},
) {
    val callbacks = remember(
        onCheckout,
        onError,
        onReady,
        onChartLoad,
        onSnapshot,
        onSelectionChanged,
        onHoldChanged,
        onClose,
    ) {
        SeatLayerPickerCallbacks(
            onCheckout = onCheckout,
            onClose = onClose,
            onError = onError,
            onReady = onReady,
            onChartLoad = onChartLoad,
            onSnapshot = onSnapshot,
            onSelectionChanged = onSelectionChanged,
            onHoldChanged = onHoldChanged,
        )
    }
    SeatLayerPickerScope(
        configuration = configuration,
        options = options,
        stateHolder = stateHolder,
        themeMode = themeMode,
        theme = theme,
        strings = strings,
        styles = styles,
        moneyFormatter = moneyFormatter,
        callbacks = callbacks,
    ) {
        SeatLayerReadyMadePicker(modifier = modifier, builders = builders)
    }
}

@Composable
internal fun SeatLayerPickerScope.SeatLayerReadyMadePicker(
    modifier: Modifier,
    builders: SeatLayerPickerBuilders,
) {
    var reloadGeneration by remember { mutableIntStateOf(0) }
    var topChromePixels by remember { mutableIntStateOf(0) }
    var rightChromePixels by remember { mutableIntStateOf(0) }
    var bottomChromePixels by remember { mutableIntStateOf(0) }
    var lastInsets by remember(controller) {
        mutableStateOf<SeatLayerPickerViewportInsets?>(null)
    }
    val movableMap = remember(builders) {
        movableContentOf<Modifier, Int> { mapModifier, currentReloadGeneration ->
            SeatLayerPickerPart(
                part = SeatLayerPickerPart.Map,
                builder = builders[SeatLayerPickerPart.Map],
            ) {
                SeatLayerPickerMap(
                    modifier = mapModifier,
                    reloadKey = currentReloadGeneration,
                )
            }
        }
    }
    val density = LocalDensity.current.density
    val blockedRegions = rememberPickerBlockedRegions()
    // False until the first viewport-inset report lands with the picker ready.
    // The first snapshot arrives before the runtime has been told what the
    // native chrome covers, and a map revealed on that snapshot alone is shown
    // at one framing and re-fitted in the next — which reads as the screen
    // loading twice.
    var mapFramed by remember(controller) { mutableStateOf(false) }

    SeatLayerPickerLifecycle()
    SeatLayerPickerBackHandler()
    SeatLayerPickerHapticEffects()

    val immersiveInspection = state.seatView != null ||
        state.snapshot?.map?.isVenue3D == true
    val panorama = state.seatView != null
    val interactionBlocked = state.presentation.cartExpanded ||
        (!immersiveInspection &&
            (state.presentation.candidateSeat != null ||
                state.presentation.activePrompt != null))
    LaunchedEffect(interactionBlocked, state.isReady) {
        if (state.isReady) {
            performAction { controller.setMapInteractionEnabled(!interactionBlocked) }
        }
    }

    LaunchedEffect(
        topChromePixels,
        rightChromePixels,
        bottomChromePixels,
        density,
        state.isReady,
        controller.supportsViewportInsets,
    ) {
        if (!state.isReady || !controller.supportsViewportInsets) return@LaunchedEffect
        withFrameNanos { }
        val next = SeatLayerPickerViewportInsets(
            top = topChromePixels / density.toDouble(),
            right = rightChromePixels / density.toDouble(),
            bottom = bottomChromePixels / density.toDouble(),
        )
        if (next != lastInsets) {
            performAction { controller.setViewportInsets(next) }
            lastInsets = next
        }
        mapFramed = true
    }

    // A runtime that never answers must not be able to hang the picker on a
    // loading screen, so the wait is bounded.
    LaunchedEffect(state.isReady, controller) {
        if (!state.isReady) return@LaunchedEffect
        kotlinx.coroutines.delay(PICKER_MAP_FRAMING_GRACE_MS)
        mapFramed = true
    }

    // One predicate, read once, for the whole screen: while a surface is
    // holding it, everything painted before that surface is inert to assistive
    // technology. The toast band, the prompts and the cart stay audible —
    // they are the answer, the question and what the seat is added to.
    val covered = pickerDecisionSurfaceUp()

    CompositionLocalProvider(LocalPickerBlockedRegions provides blockedRegions) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val wide = when (options.layout) {
            SeatLayerPickerLayoutMode.Compact -> false
            SeatLayerPickerLayoutMode.Wide -> true
            SeatLayerPickerLayoutMode.Adaptive -> maxWidth >= styles.wideBreakpoint
        }
        if (wide) {
            val coroutineScope = rememberCoroutineScope()
            Column(Modifier.fillMaxSize()) {
                if (!panorama) {
                    Box(Modifier.fillMaxWidth().pickerHiddenFromAccessibility(covered)) {
                        PickerHeader(
                            builders = builders,
                            onClose = callbacks.onClose,
                            coroutineScope = coroutineScope,
                            compact = false,
                        )
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .pickerHiddenFromAccessibility(covered),
                        ) {
                            movableMap(Modifier.fillMaxSize(), reloadGeneration)
                            WideMapChrome(builders)
                        }
                        CommonOverlays(
                            builders = builders,
                            wide = true,
                            compact = false,
                            bottomPadding = 0.dp,
                            framing = state.isReady && !mapFramed,
                            onRetry = { reloadGeneration += 1 },
                        )
                    }
                    WideSidePanel(builders)
                }
            }
        } else {
            val coroutineScope = rememberCoroutineScope()
            Column(Modifier.fillMaxSize()) {
                if (!panorama) {
                    Box(Modifier.fillMaxWidth().pickerHiddenFromAccessibility(covered)) {
                        PickerHeader(
                            builders = builders,
                            onClose = callbacks.onClose,
                            coroutineScope = coroutineScope,
                            compact = true,
                        )
                    }
                }
                if (options.chrome.showPriceRail && state.isReady && mapFramed) {
                    // The header, the price rail and the sheet are ROWS OF ONE
                    // COLUMN with the map: the map surface begins and ends at
                    // their edges, so nothing reads through the rail and the
                    // rail is not part of the band the runtime frames inside.
                    Box(Modifier.fillMaxWidth().pickerHiddenFromAccessibility(covered)) {
                        SeatLayerPickerPart(
                            SeatLayerPickerPart.Legend,
                            builders[SeatLayerPickerPart.Legend],
                        ) { SeatLayerPriceLegend(compact = true) }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .pickerHiddenFromAccessibility(covered),
                    ) {
                        movableMap(Modifier.fillMaxSize(), reloadGeneration)
                    }
                    CompactChrome(
                        builders = builders,
                        bottomPadding = (bottomChromePixels / density).dp,
                        onTopMeasured = { topChromePixels = it },
                        onBottomMeasured = { bottomChromePixels = it },
                    )
                    CommonOverlays(
                        builders = builders,
                        wide = false,
                        compact = true,
                        bottomPadding = (bottomChromePixels / density).dp,
                        framing = state.isReady && !mapFramed,
                        onRetry = { reloadGeneration += 1 },
                    )
                }
                if (
                    options.chrome.showTicketPanel &&
                    state.isReady &&
                    !SeatLayerPickerProjections.isProvenEmpty(state.snapshot)
                ) {
                    PickerCart(builders, compact = true)
                }
            }
        }

        LaunchedEffect(wide) {
            if (wide) {
                topChromePixels = 0
                rightChromePixels = 0
                bottomChromePixels = 0
            } else {
                rightChromePixels = 0
            }
        }

    }
    }
}

/**
 * How long the loading surface waits for the runtime to say it has framed the
 * map inside the native chrome.
 */
internal const val PICKER_MAP_FRAMING_GRACE_MS: Long = 700
