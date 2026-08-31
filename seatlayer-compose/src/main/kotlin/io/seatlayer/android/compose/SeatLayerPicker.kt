package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SeatLayerChartLoad
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff
import io.seatlayer.android.SeatLayerPickerHold
import io.seatlayer.android.SeatLayerPickerPhase
import io.seatlayer.android.SeatLayerPickerProjections
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerThemeMode
import io.seatlayer.android.SeatLayerPickerViewportInsets
import kotlinx.coroutines.launch

/** Complete ready-made native picker, composed exclusively from public parts. */
@Composable
public fun SeatLayerPicker(
    configuration: SeatLayerConfiguration,
    modifier: Modifier = Modifier,
    themeMode: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode.Auto,
    theme: SeatLayerPickerTheme? = null,
    strings: SeatLayerPickerStrings = SeatLayerPickerStrings.localized(),
    options: SeatLayerPickerOptions = SeatLayerPickerOptions(),
    styles: SeatLayerPickerStyles = SeatLayerPickerStyles(),
    moneyFormatter: SeatLayerPickerMoneyFormatter = SeatLayerPickerMoneyFormatter.localized(),
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

    SeatLayerPickerLifecycle()
    SeatLayerPickerBackHandler()
    SeatLayerPickerHapticEffects()

    val immersiveInspection = state.seatView != null ||
        state.snapshot?.map?.isVenue3D == true
    val panorama = state.seatView != null
    val interactionBlocked = state.presentation.cartExpanded ||
        (!immersiveInspection &&
            (state.presentation.pendingSeat != null ||
                state.presentation.activePrompt != null))
    LaunchedEffect(interactionBlocked, state.isReady) {
        if (state.isReady) {
            performAction { controller.setInteractionEnabled(!interactionBlocked) }
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
    }

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
                    PickerHeader(
                        builders = builders,
                        onClose = callbacks.onClose,
                        coroutineScope = coroutineScope,
                        compact = false,
                    )
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        movableMap(Modifier.fillMaxSize(), reloadGeneration)
                        WideMapChrome(builders)
                        CommonOverlays(
                            builders = builders,
                            wide = true,
                            compact = false,
                            bottomPadding = 0.dp,
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
                    PickerHeader(
                        builders = builders,
                        onClose = callbacks.onClose,
                        coroutineScope = coroutineScope,
                        compact = true,
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    movableMap(Modifier.fillMaxSize(), reloadGeneration)
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
                        onRetry = { reloadGeneration += 1 },
                    )
                }
                if (
                    options.chrome.cartSheet &&
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

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CompactChrome(
    builders: SeatLayerPickerBuilders,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onTopMeasured: (Int) -> Unit,
    onBottomMeasured: (Int) -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val snapshot = scope.state.snapshot
    val panorama = scope.state.seatView != null
    val venue3D = snapshot?.map?.isVenue3D == true
    val regularMap = !panorama && !venue3D
    val dockVisible = regularMap &&
        scope.options.chrome.dock &&
        snapshot?.map?.rung == "seats" &&
        snapshot.map.focusedSectionId != null

    LaunchedEffect(regularMap, scope.state.isReady) {
        if (!regularMap) onTopMeasured(0)
    }
    LaunchedEffect(dockVisible) {
        if (!dockVisible) onBottomMeasured(0)
    }
    if (regularMap) Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .onGloballyPositioned { onTopMeasured(it.size.height) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            if (scope.options.chrome.priceLegend) {
                Box(Modifier.weight(1f)) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.Legend,
                        builders[SeatLayerPickerPart.Legend],
                    ) { SeatLayerPriceLegend(compact = true) }
                }
            } else {
                Box(Modifier.weight(1f))
            }
            if (scope.options.enableVenue3D && scope.options.chrome.map3D) {
                SeatLayerPickerViewModeControl(
                    modifier = Modifier.padding(end = 10.dp),
                    compact = true,
                )
            }
        }
        if (
            scope.options.chrome.floorStrip &&
            snapshot?.map?.floors.orEmpty().pickerAuthoredFloors().size > 1
        ) {
            SeatLayerPickerPart(
                SeatLayerPickerPart.FloorStrip,
                builders[SeatLayerPickerPart.FloorStrip],
            ) { SeatLayerFloorStrip(compact = true) }
        }
        SeatLayerPickerTestModeIndicator(
            modifier = Modifier.padding(start = 10.dp),
            compact = true,
        )
    }

    if (regularMap && scope.state.isReady && scope.options.chrome.mapControls) {
        if (scope.options.chrome.accessibility) {
            SeatLayerPickerPart(
                SeatLayerPickerPart.AccessibilityFilters,
                builders[SeatLayerPickerPart.AccessibilityFilters],
            ) {
                SeatLayerPickerAccessibilityFilters(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = bottomPadding + 8.dp),
                    compact = true,
                )
            }
        }
        SeatLayerPickerPart(
            SeatLayerPickerPart.MapControls,
            builders[SeatLayerPickerPart.MapControls],
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = bottomPadding + 8.dp),
            ) {
                if (snapshot?.map?.canZoomOut == true && scope.options.chrome.overview) {
                    SeatLayerPickerStepOutControl()
                } else if (scope.options.chrome.fit) {
                    SeatLayerPickerFitControl()
                }
            }
        }
    }

    if (dockVisible) {
        SeatLayerPickerPart(
            SeatLayerPickerPart.DockBar,
            builders[SeatLayerPickerPart.DockBar],
        ) {
            SeatLayerDockBar(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { onBottomMeasured(it.size.height) },
            )
        }
    }

    if (regularMap && scope.options.chrome.confirmCard &&
        scope.state.presentation.pendingSeat != null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scope.theme.background.copy(alpha = 0.64f)),
            contentAlignment = Alignment.Center,
        ) {
            SeatLayerPickerPart(
                SeatLayerPickerPart.ConfirmCard,
                builders[SeatLayerPickerPart.ConfirmCard],
            ) {
                SeatLayerConfirmCard(
                    modifier = Modifier.padding(horizontal = 15.dp),
                    compact = true,
                )
            }
        }
    }
}

internal fun compactMapControlCount(chrome: SeatLayerPickerChromeOptions): Int =
    (if (chrome.zoom) 2 else 0) +
        (if (chrome.fit) 1 else 0) +
        (if (chrome.overview) 1 else 0) +
        (if (chrome.colorblind) 1 else 0)

internal fun compactMapControlsRequiredHeight(controlCount: Int): androidx.compose.ui.unit.Dp =
    if (controlCount <= 0) 0.dp else (controlCount * 48 + (controlCount - 1) * 8).dp

@Composable
private fun androidx.compose.foundation.layout.BoxScope.WideMapChrome(
    builders: SeatLayerPickerBuilders,
) {
    val scope = LocalSeatLayerPickerScope.current
    val panorama = scope.state.seatView != null
    val venue3D = scope.state.snapshot?.map?.isVenue3D == true
    val regularMap = !panorama && !venue3D
    if (!regularMap) return

    SeatLayerPickerTestModeIndicator(
        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        compact = false,
    )
    if (scope.options.chrome.mapControls && scope.state.isReady) {
        SeatLayerPickerPart(
            SeatLayerPickerPart.MapControls,
            builders[SeatLayerPickerPart.MapControls],
        ) {
            SeatLayerPickerMapControls(
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            )
        }
    }
    if (scope.options.chrome.confirmCard &&
        scope.state.presentation.pendingSeat != null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scope.theme.background.copy(alpha = 0.64f)),
            contentAlignment = Alignment.Center,
        ) {
            SeatLayerPickerPart(
                SeatLayerPickerPart.SeatConfirmation,
                builders[SeatLayerPickerPart.SeatConfirmation],
            ) {
                SeatLayerPickerSeatConfirmation(Modifier.width(420.dp))
            }
        }
    }
}

@Composable
private fun WideSidePanel(builders: SeatLayerPickerBuilders) {
    val scope = LocalSeatLayerPickerScope.current
    val snapshot = scope.state.snapshot
    val confirmed = scope.state.presentation.confirmedCart(snapshot)
    val immersive = scope.state.seatView != null || snapshot?.map?.isVenue3D == true
    Surface(
        modifier = Modifier.fillMaxHeight().width(scope.styles.panelWidth),
        color = scope.theme.surface,
        contentColor = scope.theme.onSurface,
        shadowElevation = 8.dp,
    ) {
        BoxWithConstraints {
            val prioritiseCart = confirmed.items.isNotEmpty() && maxHeight < 520.dp
            Column {
                if (!immersive && scope.options.enableVenue3D && scope.options.chrome.map3D) {
                    SeatLayerPickerViewModeControl(
                        Modifier.align(Alignment.CenterHorizontally).padding(10.dp),
                    )
                }
                if (!immersive && !prioritiseCart && scope.options.chrome.priceLegend) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.Legend,
                        builders[SeatLayerPickerPart.Legend],
                    ) { SeatLayerPriceLegend(compact = false) }
                }
                if (!immersive && !prioritiseCart && scope.options.chrome.floorStrip &&
                    snapshot?.map?.floors.orEmpty().pickerAuthoredFloors().size > 1
                ) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.FloorStrip,
                        builders[SeatLayerPickerPart.FloorStrip],
                    ) { SeatLayerFloorStrip(compact = false) }
                } else if (!immersive && !prioritiseCart && scope.options.chrome.floorSelector) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.FloorSelector,
                        builders[SeatLayerPickerPart.FloorSelector],
                    ) { SeatLayerPickerFloorSelector(Modifier.padding(12.dp)) }
                }
                if (!immersive && !prioritiseCart) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.SectionNavigator,
                        builders[SeatLayerPickerPart.SectionNavigator],
                    ) { SeatLayerPickerSectionNavigator(Modifier.weight(1f)) }
                } else if (immersive) {
                    Spacer(Modifier.weight(1f))
                }
                if (!immersive && !prioritiseCart && scope.options.chrome.accessibility) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.AccessibilityFilters,
                        builders[SeatLayerPickerPart.AccessibilityFilters],
                    ) { SeatLayerPickerAccessibilityFilters() }
                }
                if (!immersive && scope.options.enableBestAvailable && confirmed.items.isEmpty()) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.BestAvailable,
                        builders[SeatLayerPickerPart.BestAvailable],
                    ) { SeatLayerBestSeatsForm() }
                } else if (confirmed.items.isNotEmpty()) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.CartList,
                        builders[SeatLayerPickerPart.CartList],
                    ) {
                        SeatLayerPickerCartList(
                            if (prioritiseCart) {
                                Modifier.weight(1f)
                            } else {
                                Modifier.heightIn(max = 280.dp)
                            },
                        )
                    }
                }
                if (scope.options.chrome.attribution) {
                    SeatLayerPickerAttribution(Modifier.align(Alignment.CenterHorizontally))
                }
                if (confirmed.items.isNotEmpty()) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.CheckoutBar,
                        builders[SeatLayerPickerPart.CheckoutBar],
                    ) { SeatLayerPickerCheckoutBar() }
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(
    builders: SeatLayerPickerBuilders,
    onClose: () -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (!scope.options.chrome.header) return
    SeatLayerPickerPart(
        SeatLayerPickerPart.Header,
        builders[SeatLayerPickerPart.Header],
    ) {
        SeatLayerPickerHeader(
            onClose = {
                coroutineScope.launch { scope.controller.close { onClose() } }
            },
            holdContent = {
                if (scope.options.chrome.holdCountdown) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.HoldCountdown,
                        builders[SeatLayerPickerPart.HoldCountdown],
                    ) { SeatLayerPickerHoldCountdown() }
                }
            },
            compact = compact,
        )
    }
}

@Composable
private fun PickerCart(
    builders: SeatLayerPickerBuilders,
    compact: Boolean = false,
) {
    SeatLayerPickerPart(
        SeatLayerPickerPart.CartSheet,
        builders[SeatLayerPickerPart.CartSheet],
    ) {
        SeatLayerPickerCartSheet(
            cartList = {
                SeatLayerPickerPart(
                    SeatLayerPickerPart.CartList,
                    builders[SeatLayerPickerPart.CartList],
                ) { SeatLayerPickerCartList(compact = compact) }
            },
            bestAvailable = {
                SeatLayerPickerPart(
                    SeatLayerPickerPart.BestAvailable,
                    builders[SeatLayerPickerPart.BestAvailable],
                ) { SeatLayerBestSeatsForm() }
            },
            checkoutBar = {
                SeatLayerPickerPart(
                    SeatLayerPickerPart.CheckoutBar,
                    builders[SeatLayerPickerPart.CheckoutBar],
                ) {
                    if (compact) SeatLayerBookButton(Modifier.padding(12.dp))
                    else SeatLayerPickerCheckoutBar()
                }
            },
            compact = compact,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CommonOverlays(
    builders: SeatLayerPickerBuilders,
    wide: Boolean,
    compact: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onRetry: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val panorama = scope.state.seatView != null
    val venue3D = scope.state.snapshot?.map?.isVenue3D == true
    val immersive = panorama || venue3D
    SeatLayerPickerPart(
        SeatLayerPickerPart.Venue3D,
        builders[SeatLayerPickerPart.Venue3D],
    ) { SeatLayerVenue3D() }
    SeatLayerPickerPart(
        SeatLayerPickerPart.SeatViewChrome,
        builders[SeatLayerPickerPart.SeatViewChrome],
    ) { SeatLayerSeatViewChrome() }
    SeatLayerSelectionFlight(wide = wide, bottomPadding = bottomPadding)
    SeatLayerPickerPart(
        SeatLayerPickerPart.GeneralAdmissionPrompt,
        builders[SeatLayerPickerPart.GeneralAdmissionPrompt],
    ) { SeatLayerPickerGeneralAdmissionPrompt() }
    SeatLayerPickerPart(
        SeatLayerPickerPart.TablePrompt,
        builders[SeatLayerPickerPart.TablePrompt],
    ) { SeatLayerPickerTablePrompt() }

    if (!immersive) Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(end = if (wide) scope.styles.panelWidth else 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SeatLayerPickerPart(
            SeatLayerPickerPart.HoldLapse,
            builders[SeatLayerPickerPart.HoldLapse],
        ) { SeatLayerHoldLapseNotice(Modifier.width(360.dp)) }
        SeatLayerPickerPart(
            SeatLayerPickerPart.ActionError,
            builders[SeatLayerPickerPart.ActionError],
        ) { SeatLayerPickerActionError(Modifier.width(360.dp)) }
        SeatLayerPickerUndoNotice(Modifier.align(Alignment.CenterHorizontally))
    }

    if (!wide && !immersive && scope.options.chrome.attribution &&
        (!compact || !scope.options.chrome.cartSheet)
    ) {
        SeatLayerPickerAttribution(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = bottomPadding + 4.dp),
            compact = compact,
        )
    }

    when (scope.state.phase) {
        SeatLayerPickerPhase.Idle,
        SeatLayerPickerPhase.Loading,
        -> SeatLayerPickerPart(
            SeatLayerPickerPart.Loading,
            builders[SeatLayerPickerPart.Loading],
        ) { SeatLayerPickerLoadingView() }
        is SeatLayerPickerPhase.Failed -> SeatLayerPickerPart(
            SeatLayerPickerPart.Error,
            builders[SeatLayerPickerPart.Error],
        ) { SeatLayerPickerErrorView(onRetry) }
        is SeatLayerPickerPhase.Ready -> if (
            SeatLayerPickerProjections.isProvenEmpty(scope.state.snapshot)
        ) {
            SeatLayerPickerPart(
                SeatLayerPickerPart.Empty,
                builders[SeatLayerPickerPart.Empty],
            ) { SeatLayerPickerEmptyView() }
        }
        SeatLayerPickerPhase.Destroyed -> Unit
    }
}
