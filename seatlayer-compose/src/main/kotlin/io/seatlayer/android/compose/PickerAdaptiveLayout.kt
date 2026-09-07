package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SeatLayerPickerPhase
import io.seatlayer.android.SeatLayerPickerProjections
import kotlinx.coroutines.launch

@Composable
internal fun androidx.compose.foundation.layout.BoxScope.CompactChrome(
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
    // The dock bar is off unless a host asks for it: the section it names is
    // already on the map, under the buyer's thumb.
    val dockVisible = regularMap &&
        scope.options.showDockBar == true &&
        snapshot?.map?.rung == "seats" &&
        snapshot.map.focusedSectionId != null

    LaunchedEffect(regularMap, scope.state.isReady) {
        if (!regularMap) onTopMeasured(0)
    }
    LaunchedEffect(dockVisible) {
        if (!dockVisible) onBottomMeasured(0)
    }
    // A tap on the venue is the way an open sheet is put away. The map itself
    // is already inert while the sheet is open, so the tap has to be caught
    // here rather than answered by the renderer.
    if (regularMap && scope.state.presentation.cartExpanded) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { scope.controller.setCartSheetExpanded(false) }
                },
        )
    }
    // Only chrome drawn OVER the map is counted here. The header, the price
    // rail and the cart sheet are rows of the same column as the map surface,
    // so the map already begins and ends where they do — measuring them again
    // would frame every section into a band that does not exist.
    if (regularMap) Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .onGloballyPositioned { onTopMeasured(it.size.height) },
        horizontalAlignment = Alignment.End,
    ) {
        if (scope.options.enable3D && scope.options.chrome.showViewModeControl) {
            PickerMapChromeRegion(
                modifier = Modifier.padding(
                    end = SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp,
                    top = SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp,
                ),
            ) {
                SeatLayerPickerViewModeControl(compact = true)
            }
        }
        if (
            scope.options.chrome.showFloorStrip &&
            snapshot?.map?.floors.orEmpty().pickerAuthoredFloors().size > 1
        ) {
            PickerMapChromeRegion(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = SeatLayerSizeTokens.MAP_ANCHOR_GAP.dp),
            ) {
                SeatLayerPickerPart(
                    SeatLayerPickerPart.FloorStrip,
                    builders[SeatLayerPickerPart.FloorStrip],
                ) { SeatLayerFloorStrip(compact = true) }
            }
        }
        PickerMapChromeRegion(
            modifier = Modifier
                .align(Alignment.Start)
                .padding(
                    start = SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp,
                    top = SeatLayerSizeTokens.MAP_ANCHOR_GAP.dp,
                ),
        ) {
            SeatLayerPickerTestModeIndicator(compact = true)
        }
    }

    // The discs step out of the way of a seat card: a column of controls poking
    // out beside the card asking about a seat read as clutter, and none of them
    // may be pressed while it asks. They FADE rather than unmount, so nothing
    // moves under a thumb already reaching for one.
    val cardUp = scope.state.presentation.candidateSeat != null &&
        scope.options.chrome.showConfirmCard
    if (scope.state.isReady && scope.options.chrome.showMapControls) {
        SeatLayerPickerPart(
            SeatLayerPickerPart.MapControls,
            builders[SeatLayerPickerPart.MapControls],
        ) {
            SeatLayerPickerMapControls(
                modifier = Modifier
                    .matchParentSize()
                    .pickerFadeUnderCard(cardUp),
                compact = true,
                bottomInset = bottomPadding,
                // The turnkey phone layout draws the Map/3D control on the top
                // band itself, so the two can never land on top of each other
                // whatever the translated labels measure.
                includeViewModeControl = false,
                accessibilityControl = {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.AccessibilityFilters,
                        builders[SeatLayerPickerPart.AccessibilityFilters],
                    ) { SeatLayerPickerAccessibilityFilters(compact = true) }
                },
            )
        }
    }

    if (dockVisible) {
        SeatLayerPickerPart(
            SeatLayerPickerPart.DockBar,
            builders[SeatLayerPickerPart.DockBar],
        ) {
            PickerMapChromeRegion(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { onBottomMeasured(it.size.height) },
            ) { SeatLayerDockBar() }
        }
    }

    // The card is a fixed sheet at the foot of the map, not a centred modal:
    // the MAP moves to keep the seat clear of it. It is mounted for a retap
    // even with no pending add, or the runtime reports the second tap and
    // nothing appears.
    if ((regularMap || venue3D) && scope.options.chrome.showConfirmCard) {
        SeatLayerConfirmCardLayer(
            bottomBand = if (dockVisible) bottomPadding else 0.dp,
            immersive = venue3D,
        ) { seat, intent, answered, onAnswered ->
            SeatLayerPickerPart(
                SeatLayerPickerPart.ConfirmCard,
                builders[SeatLayerPickerPart.ConfirmCard],
            ) {
                SeatLayerConfirmCardBody(
                    seat = seat,
                    intent = intent,
                    answered = answered,
                    onAnswered = onAnswered,
                    immersive = venue3D,
                )
            }
        }
    }
}

/**
 * How many discs the phone column carries.
 *
 * Three: the accessibility disc at its head, `+`, and the whole venue. `+`
 * keeps its slot when it retires, so this figure does not move with the camera.
 */
internal fun compactMapControlCount(chrome: SeatLayerPickerChromeOptions): Int =
    (if (chrome.showAccessibilityControl) 1 else 0) +
        (if (chrome.zoomControlsFor(phone = true)) 1 else 0) +
        (if (chrome.zoomToFitControlFor(phone = true)) 1 else 0)

internal fun compactMapControlsRequiredHeight(controlCount: Int): androidx.compose.ui.unit.Dp =
    if (controlCount <= 0) {
        0.dp
    } else {
        (
            controlCount * SeatLayerSizeTokens.MAP_CONTROL_SIZE +
                (controlCount - 1) * SeatLayerSizeTokens.ZOOM_COLUMN_GAP
            ).dp
    }

/** Takes every touch inside, without moving the nodes underneath it. */
internal fun Modifier.pickerSwallowTouches(swallow: Boolean): Modifier =
    if (!swallow) {
        this
    } else {
        this.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                        .changes
                        .forEach(PointerInputChange::consume)
                }
            }
        }
    }

@Composable
internal fun androidx.compose.foundation.layout.BoxScope.WideMapChrome(
    builders: SeatLayerPickerBuilders,
) {
    val scope = LocalSeatLayerPickerScope.current
    val panorama = scope.state.seatView != null
    val venue3D = scope.state.snapshot?.map?.isVenue3D == true
    val regularMap = !panorama && !venue3D
    if (!regularMap) return

    SeatLayerPickerTestModeIndicator(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp),
        compact = false,
    )
    if (scope.options.chrome.showMapControls && scope.state.isReady) {
        SeatLayerPickerPart(
            SeatLayerPickerPart.MapControls,
            builders[SeatLayerPickerPart.MapControls],
        ) {
            SeatLayerPickerMapControls(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp),
                compact = false,
            )
        }
    }
    if (scope.options.chrome.showConfirmCard &&
        scope.state.presentation.candidateSeat != null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scope.confirmScrim()),
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
internal fun WideSidePanel(builders: SeatLayerPickerBuilders) {
    val scope = LocalSeatLayerPickerScope.current
    val snapshot = scope.state.snapshot
    val confirmed = scope.state.presentation.confirmedCart(snapshot)
    val immersive = scope.state.seatView != null || snapshot?.map?.isVenue3D == true
    Surface(
        modifier = Modifier.fillMaxHeight().width(scope.styles.panelWidth),
        color = scope.theme.surface,
        contentColor = scope.theme.text,
        shadowElevation = 8.dp,
    ) {
        BoxWithConstraints {
            val prioritiseCart = confirmed.items.isNotEmpty() && maxHeight < 520.dp
            Column {
                if (!immersive && scope.options.enable3D && scope.options.chrome.showViewModeControl) {
                    SeatLayerPickerViewModeControl(
                        Modifier.align(Alignment.CenterHorizontally).padding(10.dp),
                    )
                }
                if (!immersive && !prioritiseCart && scope.options.chrome.showPriceRail) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.Legend,
                        builders[SeatLayerPickerPart.Legend],
                    ) { SeatLayerPriceLegend(compact = false) }
                }
                if (!immersive && !prioritiseCart && scope.options.chrome.showFloorStrip &&
                    snapshot?.map?.floors.orEmpty().pickerAuthoredFloors().size > 1
                ) {
                    SeatLayerPickerPart(
                        SeatLayerPickerPart.FloorStrip,
                        builders[SeatLayerPickerPart.FloorStrip],
                    ) { SeatLayerFloorStrip(compact = false) }
                } else if (!immersive && !prioritiseCart && scope.options.chrome.showFloorSelector) {
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
                if (!immersive && !prioritiseCart && scope.options.chrome.showAccessibilityControl) {
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
                SeatLayerPickerSalesClosedStatement(Modifier.padding(horizontal = 12.dp))
                SeatLayerPickerPart(
                    SeatLayerPickerPart.CheckoutBar,
                    builders[SeatLayerPickerPart.CheckoutBar],
                ) { SeatLayerPickerCheckoutBar() }
            }
        }
    }
}

@Composable
internal fun PickerHeader(
    builders: SeatLayerPickerBuilders,
    onClose: () -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (!scope.options.chrome.showHeader) return
    SeatLayerPickerPart(
        SeatLayerPickerPart.Header,
        builders[SeatLayerPickerPart.Header],
    ) {
        SeatLayerPickerHeader(
            onClose = {
                coroutineScope.launch { scope.controller.close { onClose() } }
            },
            holdContent = {
                if (scope.options.chrome.showHoldPill) {
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
internal fun PickerCart(
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
            checkoutBar = { onFindBestSeats ->
                SeatLayerPickerPart(
                    SeatLayerPickerPart.CheckoutBar,
                    builders[SeatLayerPickerPart.CheckoutBar],
                ) { SeatLayerBookButton(onFindBestSeats = onFindBestSeats) }
            },
            compact = compact,
        )
    }
}

@Composable
internal fun androidx.compose.foundation.layout.BoxScope.CommonOverlays(
    builders: SeatLayerPickerBuilders,
    wide: Boolean,
    compact: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    framing: Boolean = false,
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

    // The sheet's own foot carries the lapse notice and the inline action
    // error. They are only raised over the map where there is no foot to hold
    // them, or a buyer with no ticket panel would never be told.
    if (!immersive && !scope.options.chrome.showTicketPanel) Column(
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
    }
    if (!immersive) {
        SeatLayerPickerToastLayer(
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomInset = bottomPadding,
            lifted = scope.state.presentation.candidateSeat != null,
        )
    }

    if (!wide && !immersive && (!compact || !scope.options.chrome.showTicketPanel)) {
        SeatLayerPickerAttribution(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = bottomPadding + 4.dp),
            compact = compact,
        )
    }

    // Every map-level state, in the order one may cover another. Each renders
    // nothing when its own condition is false.
    if (!immersive) {
        SeatLayerPickerSoldOutOverlay()
        if (scope.options.resolvedExtendHoldPrompt(wide)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = bottomPadding + 70.dp,
                    ),
            ) { SeatLayerPickerExtendHoldPrompt() }
        }
    }
    SeatLayerPickerAccessPanel()
    SeatLayerPickerBookedOverlay()

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
        // Ready, but the runtime has not been told what the chrome covers yet:
        // revealing the map now shows it at one framing and then re-fits it in
        // front of the buyer.
        is SeatLayerPickerPhase.Ready -> if (framing) {
            SeatLayerPickerPart(
                SeatLayerPickerPart.Loading,
                builders[SeatLayerPickerPart.Loading],
            ) { SeatLayerPickerLoadingView() }
        } else if (
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
