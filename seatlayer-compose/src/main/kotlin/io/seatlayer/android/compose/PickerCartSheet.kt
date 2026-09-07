package io.seatlayer.android.compose

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SeatLayerPickerSheetDetent
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The picker's ticket panel: one surface, in two states.
 *
 * **Collapsed, the sheet IS the footer block** — the total line, the call to
 * action and the by-line. The cards wait behind the handle. A list that
 * unrolled itself every time a seat was added read as a panel the buyer had not
 * opened, so the cart region's height at rest is zero and an add leaves the
 * sheet exactly as it was.
 *
 * There is no peek bar, no second summary and no `From <price>` line. One cart
 * said twice is two things to keep in step, and they drift.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
public fun SeatLayerPickerCartSheet(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
    cartList: @Composable () -> Unit = { SeatLayerPickerCartList() },
    bestAvailable: @Composable () -> Unit = { SeatLayerBestSeatsForm() },
    checkoutBar: @Composable (onFindBestSeats: () -> Unit) -> Unit = { find ->
        SeatLayerBookButton(onFindBestSeats = find)
    },
    /** Retained for source compatibility: there is one sheet on every width. */
    @Suppress("UNUSED_PARAMETER") compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.CartSheet]
    val presentation = scope.state.presentation
    val snapshot = scope.state.snapshot
    val confirmed = presentation.confirmedCart(snapshot)
    val hasTickets = confirmed.items.isNotEmpty()
    val confirming = presentation.candidateSeat != null
    val reducedMotion = pickerReducedMotion()
    val density = LocalDensity.current.density
    val screenHeight = LocalConfiguration.current.screenHeightDp.toFloat()
    val coroutineScope = rememberCoroutineScope()

    var footHeight by remember { mutableFloatStateOf(0f) }
    var cartNatural by remember { mutableFloatStateOf(0f) }
    var extrasNatural by remember { mutableFloatStateOf(0f) }
    var bodyValue by remember { mutableFloatStateOf(0f) }
    var rest by remember { mutableStateOf(PickerSheetRest.Peek) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    val detents = pickerSheetDetents(
        screenHeight = screenHeight,
        footHeight = footHeight,
        cartNatural = cartNatural,
        extrasNatural = extrasNatural,
        hasTickets = hasTickets,
    )
    // The finger, not the last published answer, decides whether the sheet reads
    // as open: a surface halfway up under a thumb is not shut.
    val open = rest != PickerSheetRest.Peek || bodyValue > PickerSheetDetents.EPSILON

    fun settleTo(target: PickerSheetRest, velocity: Float) {
        rest = target
        scope.controller.setCartSheetDetent(
            if (target == PickerSheetRest.Peek) {
                SeatLayerPickerSheetDetent.Collapsed
            } else {
                SeatLayerPickerSheetDetent.Open
            },
        )
        settleJob?.cancel()
        settleJob = coroutineScope.launch {
            animate(
                initialValue = bodyValue,
                targetValue = detents.heightOf(target),
                initialVelocity = velocity,
                animationSpec = pickerSheetSettleSpec(reducedMotion),
            ) { value, _ -> bodyValue = value }
            bodyValue = detents.heightOf(target)
        }
    }

    // The host — or the back gesture — asking for a state the sheet is not in.
    // An echo of the sheet's own answer is ignored, or every opening tap would
    // stutter as the answer came back.
    LaunchedEffect(presentation.cartExpanded, detents) {
        val wantsOpen = presentation.cartExpanded
        if (wantsOpen && rest == PickerSheetRest.Peek) {
            settleTo(PickerSheetRest.Content, 0f)
        } else if (!wantsOpen && rest != PickerSheetRest.Peek) {
            settleTo(PickerSheetRest.Peek, 0f)
        } else if (settleJob?.isActive != true) {
            bodyValue = detents.heightOf(rest)
        }
    }
    // A card is the only question on the screen while it is up.
    LaunchedEffect(confirming) {
        if (confirming && presentation.cartExpanded) scope.controller.setCartSheetExpanded(false)
    }

    val belowPeek = min(bodyValue, 0f)
    val shape = RoundedCornerShape(
        topStart = resolvedStyle.cornerRadius ?: scope.styles.sheetRadius,
        topEnd = resolvedStyle.cornerRadius ?: scope.styles.sheetRadius,
    )
    val ground = resolvedStyle.containerColor ?: scope.theme.background
    val hairline = scope.theme.divider

    var dragStart by remember { mutableStateOf(PickerSheetRest.Peek) }
    var raw by remember { mutableFloatStateOf(0f) }
    var travel by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { deltaPixels ->
        val delta = -deltaPixels / density
        raw += delta
        travel += delta
        bodyValue = pickerRubberBand(raw, 0f, detents.top)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, (-belowPeek * density).roundToInt()) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ground, shape)
                .drawBehind {
                    drawLine(
                        color = hairline,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1f,
                    )
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        settleJob?.cancel()
                        dragStart = rest
                        raw = bodyValue
                        travel = 0f
                    },
                    onDragStopped = { velocityPixels ->
                        val velocity = -velocityPixels / density
                        settleTo(
                            detents.settleWithFloor(bodyValue, velocity, dragStart, travel),
                            velocity,
                        )
                    },
                ),
        ) {
            Spacer(Modifier.height(SeatLayerSizeTokens.SHEET_HEAD_HEIGHT.dp))
            PickerCartRegion(
                height = bodyValue.coerceAtLeast(0f),
                open = open,
                hasTickets = hasTickets,
                cartList = cartList,
                bestAvailable = bestAvailable,
                onCartMeasured = { cartNatural = it },
                onExtrasMeasured = { extrasNatural = it },
            )
            SeatLayerPickerSheetFoot(
                showHairline = open && hasTickets,
                checkoutBar = checkoutBar,
                // The sheet answers its own controls itself and then publishes
                // the answer, rather than waiting to be told what it just did.
                onOpenFinder = {
                    settleTo(PickerSheetRest.Content, 0f)
                    scope.controller.setCartSheetExpanded(true)
                },
                onOpenCart = {
                    settleTo(PickerSheetRest.Content, 0f)
                    scope.controller.setCartSheetExpanded(true)
                },
                collapsed = !open,
                modifier = Modifier.onGloballyPositioned { footHeight = it.size.height / density },
            )
        }
        // The handle straddles the sheet's own top edge. It is not drawn at all
        // while a confirm card is up: the disc sat half under the card's scrim,
        // and the card is the only question until it is answered.
        if (!confirming) {
            PickerSheetHandle(
                open = open,
                onToggle = {
                    settleTo(
                        if (open) PickerSheetRest.Peek else PickerSheetRest.Content,
                        0f,
                    )
                    scope.controller.setCartSheetExpanded(!open)
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/**
 * The two ceilings and the open height, from one measurement.
 *
 * Every height here is the CART REGION's height above the collapsed block, so
 * the collapsed detent is zero rather than a number to keep in step with the
 * foot it would otherwise have to predict.
 */
internal fun pickerSheetDetents(
    screenHeight: Float,
    footHeight: Float,
    cartNatural: Float,
    extrasNatural: Float,
    hasTickets: Boolean,
): PickerSheetDetents {
    val fraction = if (hasTickets) {
        SeatLayerSizeTokens.SHEET_MAX_HEIGHT_FRACTION
    } else {
        SeatLayerSizeTokens.EMPTY_TRAY_MAX_HEIGHT_FRACTION
    }
    val ceiling = if (hasTickets) {
        SeatLayerSizeTokens.SHEET_MAX_HEIGHT
    } else {
        SeatLayerSizeTokens.EMPTY_TRAY_MAX_HEIGHT
    }
    val maxSheet = min(screenHeight * fraction, ceiling)
    val chrome = SeatLayerSizeTokens.SHEET_HEAD_HEIGHT + footHeight
    val maxBody = (maxSheet - chrome).coerceIn(0f, screenHeight)
    // Three whole cards and a sliver of the fourth, then the region scrolls.
    val tray = if (cartNatural > 0f) {
        SeatLayerSizeTokens.CART_TRAY_PAD_TOP + SeatLayerSizeTokens.CART_TRAY_PAD_BOTTOM
    } else {
        0f
    }
    val openNatural =
        min(cartNatural, SeatLayerSizeTokens.CART_PEEK_MAX_HEIGHT) + tray + extrasNatural
    val fullCeiling =
        (screenHeight * SeatLayerSizeTokens.SHEET_FULL_HEIGHT_FRACTION - chrome)
            .coerceIn(0f, screenHeight)
    return PickerSheetDetents(
        content = min(openNatural, maxBody).coerceIn(0f, maxBody),
        full = min(openNatural, fullCeiling).coerceAtLeast(0f),
    )
}

/**
 * The scrolling window the cards live in.
 *
 * The extras stay laid out while the sheet is shut — offstage, not unmounted —
 * so the sheet always knows how tall it would open to and opens straight to it
 * rather than springing to a guess and correcting.
 */
@Composable
private fun PickerCartRegion(
    height: Float,
    open: Boolean,
    hasTickets: Boolean,
    cartList: @Composable () -> Unit,
    bestAvailable: @Composable () -> Unit,
    onCartMeasured: (Float) -> Unit,
    onExtrasMeasured: (Float) -> Unit,
) {
    val density = LocalDensity.current.density
    val scrollState = rememberScrollState()
    LaunchedEffect(hasTickets) { if (!hasTickets) onCartMeasured(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clipToBounds()
            .verticalScroll(scrollState),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (hasTickets) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = SeatLayerSizeTokens.CART_TRAY_PAD_X.dp,
                            end = SeatLayerSizeTokens.CART_TRAY_PAD_X.dp,
                            top = SeatLayerSizeTokens.CART_TRAY_PAD_TOP.dp,
                            bottom = SeatLayerSizeTokens.CART_TRAY_PAD_BOTTOM.dp,
                        ),
                ) {
                    // The cap is on the CARDS, not on the tray around them: the
                    // gutters are chrome, and counting them into the cap is what
                    // turns "three cards and a sliver" into two and a half.
                    Box(
                        modifier = Modifier.onGloballyPositioned {
                            onCartMeasured(it.size.height / density)
                        },
                    ) { cartList() }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = SeatLayerSizeTokens.CART_TRAY_PAD_X.dp,
                        end = SeatLayerSizeTokens.CART_TRAY_PAD_X.dp,
                        bottom = SeatLayerSizeTokens.CART_TRAY_PAD_BOTTOM.dp,
                    )
                    .alpha(if (open) 1f else 0f)
                    .onGloballyPositioned { onExtrasMeasured(it.size.height / density) },
            ) {
                if (!hasTickets) bestAvailable()
            }
        }
    }
}

/**
 * The one named control for the sheet, in both states.
 *
 * A disc on the line rather than a bar above it, with the chevron inside it, so
 * the state and the control that changes it are one thing. There is deliberately
 * no hairline and no band under it: an arrow between two lines read as a band.
 */
@Composable
private fun PickerSheetHandle(
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val overhang = SeatLayerSizeTokens.SHEET_HANDLE_OVERHANG
    val band = overhang + SeatLayerSizeTokens.SHEET_HEAD_HEIGHT
    Box(
        modifier = modifier
            .offset { IntOffset(0, -(overhang * density).roundToInt()) }
            .fillMaxWidth()
            .height(band.dp)
            .pointerInput(open) {
                detectTapGestures { onToggle() }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (open) {
                    scope.strings.collapseCart
                } else {
                    scope.strings.expandCart
                }
                onClick(
                    label = if (open) scope.strings.collapseCart else scope.strings.expandCart,
                ) {
                    onToggle()
                    true
                }
                // The platform says "expanded"/"collapsed" in the buyer's own
                // language, so the state needs no string of its own.
                if (open) {
                    collapse { onToggle(); true }
                } else {
                    expand { onToggle(); true }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = SeatLayerSizeTokens.SHEET_HANDLE_WIDTH.dp,
                    height = SeatLayerSizeTokens.SHEET_HANDLE_HEIGHT.dp,
                )
                .shadow(
                    elevation = CartSheetLiterals.HANDLE_SHADOW_BLUR.dp,
                    shape = CircleShape,
                    clip = false,
                )
                .background(scope.theme.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SeatLayerPickerIcon(
                glyph = SeatLayerPickerGlyph.ChevronUp,
                color = scope.theme.mutedText,
                modifier = Modifier
                    .size(CartSheetLiterals.HANDLE_CHEVRON_SIZE.dp)
                    .rotate(if (open) 180f else 0f),
            )
        }
    }
}

/**
 * Numbers the design source keeps as file-local constants.
 *
 * Reported as a tokens.json gap. A constant invented here would be a design
 * decision the design source never took.
 */
internal object CartSheetLiterals {
    const val HANDLE_SHADOW_BLUR = 10f
    const val HANDLE_CHEVRON_SIZE = 16f
    const val SEAT_LINE_FONT_SIZE = 12f
    const val SEAT_LINE_WEIGHT = 600
    const val SEAT_LINE_PAD_TOP = 3f
    const val SEAT_LINE_GLYPH_SIZE = 16f
    const val SEAT_LINE_GLYPH_GAP = 6f
    const val SWELL_SCALE = 0.3f
    const val SWELL_PEAK = 0.45f
}
