package io.seatlayer.android.compose

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SeatLayerPickerCardIntent
import io.seatlayer.android.SeatLayerPickerCartLanding
import io.seatlayer.android.SeatLayerPickerHapticCue
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Where the card lives, and how the map gets out from under it.
 *
 * The card has ONE home — the bottom of the map, at a constant inset, for every
 * seat — and nothing about the tapped seat is read when it is placed. Keeping
 * the seat and its card together is then the map's job, done by a pan.
 *
 * The card used to move to the seat instead. On a tall phone that read badly:
 * the seat is a small ring inside a small hole in the glass, the card is at the
 * foot of the map, and the two halves of one question could be most of a screen
 * apart — so the buyer hunted for the seat they had just tapped, and the buttons
 * moved out from under the thumb that had just used them.
 */

/** How far the seat is asked to sit into the band the sheet leaves clear. */
internal const val SEATLAYER_SHEET_LIFT_FRACTION: Double = 0.48

/** Where the map is put back to when the card leaves. */
internal const val SEATLAYER_SHEET_RESTORE_FRACTION: Double = 0.5

/**
 * The seat card, the glass behind it, and the pan that keeps them together.
 *
 * Mounted inside the map's own box, so the glass fills exactly what the map
 * fills and the hole is cut in the map's own coordinates.
 *
 * [bottomBand] is the band the picker's own chrome stands on at the foot of the
 * map — the dock, where there is one. The card rests above THAT, not above the
 * whole reported bottom inset, which rests it a chrome's height too high.
 */
@Composable
internal fun BoxScope.SeatLayerConfirmCardLayer(
    bottomBand: Dp,
    modifier: Modifier = Modifier,
    immersive: Boolean = false,
    content: @Composable (
        seat: SeatLayerPickerSelectedSeat,
        intent: SeatLayerPickerCardIntent,
        answered: Boolean,
        onAnswered: (SeatLayerPickerSelectedSeat) -> Unit,
    ) -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val presentation = scope.state.presentation
    val candidate = presentation.candidateSeat
    val snapshot = scope.state.snapshot
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val animate = seatLayerAnimationsEnabled()

    // The card keeps drawing itself for the length of its own sweep after the
    // seat has stopped being a candidate: the cart is told on the press, and
    // only the DEPARTURE is sequenced behind the acknowledgement.
    var departing by remember(scope.stateHolder) {
        mutableStateOf<Pair<SeatLayerPickerSelectedSeat, SeatLayerPickerCardIntent>?>(null)
    }
    LaunchedEffect(departing, animate) {
        val leaving = departing ?: return@LaunchedEffect
        if (animate) delay(SeatLayerMotionTokens.PRESS_SWEEP.toLong())
        if (departing === leaving) departing = null
    }

    val shownSeat = candidate ?: departing?.first
    val shownIntent = if (candidate != null) presentation.cardIntent else departing?.second
    val answered = candidate == null && departing != null

    // The lift is given back only once nothing is standing on it: a map moving
    // under a chip still in flight reads as two motions fighting.
    LaunchedEffect(
        shownSeat == null,
        presentation.cartLanding,
        presentation.seatLift != null,
    ) {
        if (shownSeat != null) return@LaunchedEffect
        if (presentation.cartLanding == SeatLayerPickerCartLanding.Flying) return@LaunchedEffect
        if (presentation.cartLanding == SeatLayerPickerCartLanding.Landed) return@LaunchedEffect
        scope.controller.restoreSeatLift()
    }

    // Naming the seat to the runtime is what paints the candidate: a tapped
    // seat is already in the selection, and every selected seat is drawn alike,
    // so the seat under the question was a plain ring among the settled ones.
    // A focus is never CLEARED unless this card asked for one.
    var focusRequested by remember(scope.stateHolder) { mutableStateOf(false) }
    LaunchedEffect(shownSeat?.id) {
        val id = shownSeat?.id
        if (id != null) {
            focusRequested = true
            runCatching { scope.controller.setSelectionFocus(id) }
            scope.controller.emitHaptic(SeatLayerPickerHapticCue.CardArrived)
        } else if (focusRequested) {
            focusRequested = false
            runCatching { scope.controller.forgetSelectionFocus() }
        }
    }

    if (shownSeat == null || shownIntent == null) return

    var cardHeightPx by remember(shownSeat.id) { mutableFloatStateOf(0f) }
    var mapHeightPx by remember { mutableFloatStateOf(0f) }
    var anchorDy by remember(snapshot?.revision) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    // Re-asked the frame after the card is measured, again shortly after (the
    // map surface resizes a frame or two after layout), and after every snapshot
    // the runtime publishes, so a glide that lands with the card up is followed.
    LaunchedEffect(shownSeat.id, cardHeightPx, mapHeightPx, snapshot?.revision, immersive) {
        if (immersive) return@LaunchedEffect
        if (cardHeightPx <= 0f || mapHeightPx <= 0f) return@LaunchedEffect
        val bottomPx = with(density) { bottomBand.toPx() }
        val restPx = with(density) { SeatLayerSizeTokens.CONFIRM_CARD_REST_INSET.dp.toPx() }
        val gapPx = with(density) { SeatLayerSizeTokens.CONFIRM_CARD_SEAT_GAP.dp.toPx() }
        val fraction = seatLayerSheetLiftFraction(
            mapHeight = mapHeightPx.toDouble(),
            cardHeight = cardHeightPx.toDouble(),
            bottomBand = bottomPx.toDouble(),
            restInset = restPx.toDouble(),
            seatGap = gapPx.toDouble(),
        ) ?: return@LaunchedEffect
        listOf(0L, RE_ASK_EARLY, RE_ASK_LATE).forEach { wait ->
            if (wait > 0) delay(wait)
            val before = scope.state.presentation.seatLift?.dy ?: 0.0
            val after = runCatching {
                scope.controller.liftSeat(shownSeat.id, fraction)
            }.getOrNull() ?: return@forEach
            // `dy: 0` is the runtime saying the seat is already in place. It is
            // a normal answer, never an error.
            anchorDy += (after.dy - before).toFloat()
        }
    }

    val cancel: () -> Unit = {
        coroutineScope.launch {
            scope.controller.emitHaptic(SeatLayerPickerHapticCue.CardCancelled)
            if (shownIntent == SeatLayerPickerCardIntent.Remove) {
                // A way out of the question, not an answer to it.
                scope.controller.dismissSeatRemoval()
            } else {
                scope.controller.cancelPending()
            }
        }
        Unit
    }

    // The glass takes no pointer event of its own: a press over it reaches the
    // map underneath and cancels the card, exactly as a press on bare map does.
    SeatLayerSpotlightGlass(
        seat = shownSeat,
        anchorDy = anchorDy,
        modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned { mapHeightPx = it.size.height.toFloat() }
            .pointerInput(shownSeat.id, shownIntent) {
                detectTapGestures { cancel() }
            },
    )

    Box(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .onGloballyPositioned { cardHeightPx = it.size.height.toFloat() }
            .padding(
                start = SeatLayerSizeTokens.CONFIRM_CARD_GUTTER.dp,
                end = SeatLayerSizeTokens.CONFIRM_CARD_GUTTER.dp,
                top = SeatLayerSizeTokens.CONFIRM_CARD_TOP_INSET.dp,
                bottom = bottomBand + if (immersive) {
                    SeatLayerSizeTokens.CONFIRM_CARD_IMMERSIVE_REST_INSET.dp
                } else {
                    SeatLayerSizeTokens.CONFIRM_CARD_REST_INSET.dp
                },
            ),
    ) {
        content(
            shownSeat,
            shownIntent,
            answered,
        ) { seat ->
            departing = seat to shownIntent
            coroutineScope.launch {
                if (shownIntent == SeatLayerPickerCardIntent.Remove) {
                    scope.controller.emitHaptic(SeatLayerPickerHapticCue.TicketRemoved)
                    scope.performAction { scope.controller.removeObject(seat.label) }
                    scope.callbacks.onSeatRemoved(seat)
                    scope.controller.dismissSeatRemoval()
                    return@launch
                }
                scope.controller.emitHaptic(SeatLayerPickerHapticCue.SeatConfirmed)
                // Under reduced motion no chip is created, so no landing is
                // begun and nothing is left waiting on a beat that never plays.
                if (animate) {
                    scope.controller.setCartLanding(SeatLayerPickerCartLanding.Flying)
                }
                scope.controller.confirmPending(seat.tierId)
                scope.callbacks.onSeatSelected(seat)
            }
        }
    }
}

/**
 * How far into the clear band the seat is asked to sit, or null with no room.
 *
 * The sheet is folded into the FRACTION rather than reported as an inset,
 * because an inset re-frames the whole section and changes the zoom, and the
 * buyer did not ask for a different zoom by tapping a seat.
 */
internal fun seatLayerSheetLiftFraction(
    mapHeight: Double,
    cardHeight: Double,
    bottomBand: Double,
    restInset: Double,
    seatGap: Double,
): Double? {
    if (mapHeight <= 0 || cardHeight <= 0) return null
    val cardTop = mapHeight - bottomBand - restInset - cardHeight
    val band = mapHeight - cardTop + seatGap
    val clear = mapHeight - band
    val usable = mapHeight - bottomBand
    if (clear <= 0 || usable <= 0) return null
    return (SEATLAYER_SHEET_LIFT_FRACTION * clear / usable).coerceIn(0.0, 1.0)
}

private const val RE_ASK_EARLY = 350L
private const val RE_ASK_LATE = 450L

/**
 * Faded to nothing and inert while a card is up.
 *
 * Faded rather than unmounted: a control that leaves the tree takes its space
 * with it, and the chrome around the map should not reflow because a question
 * was asked about one seat.
 */
internal fun Modifier.pickerFadeUnderCard(cardUp: Boolean): Modifier =
    if (!cardUp) this else this.alpha(0f).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
