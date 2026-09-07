package io.seatlayer.android.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SelectionValidity
import kotlinx.coroutines.launch

/** Which piece of work the checkout action is waiting on, if any. */
public enum class SeatLayerPickerCheckoutBusy {
    None,

    /** The hold is being created. */
    SecuringSeats,

    /** The hold exists and the host is opening its own checkout. */
    OpeningCheckout,
}

/**
 * Everything the checkout action draws, decided in one place.
 *
 * One rule for every checkout surface. Gating per call site is how a button
 * ends up saying "Continue" on a sold-out event, or offering the finder from
 * behind a seat card, because each site learned a different subset of the
 * facts.
 */
@Immutable
public data class SeatLayerPickerCheckoutCtaState(
    val label: String,
    val enabled: Boolean,
    val busy: Boolean,
    /** True when the label is explaining a refusal rather than naming an action. */
    val statesReason: Boolean,
    /** True when pressing opens the best-seats form rather than checkout. */
    val findsBestSeats: Boolean,
)

/**
 * The checkout ladder, in the buyer picker's own precedence.
 *
 * [pendingCount] is the number of picked seats a live hold does not yet cover.
 * The bridge does not report the held/pending split, so callers pass zero and
 * the ladder answers `continueToCheckout`; the parameter is here so the day the
 * snapshot carries the split, the wording changes and nothing else does.
 */
@Suppress("LongParameterList", "ReturnCount", "CyclomaticComplexMethod")
public fun seatLayerCheckoutCtaState(
    strings: SeatLayerPickerStrings,
    label: String,
    canCheckout: Boolean,
    salesClosed: Boolean,
    promptUnanswered: Boolean,
    seatCardOpen: Boolean,
    busy: SeatLayerPickerCheckoutBusy,
    selectionValidity: SelectionValidity?,
    ticketCount: Int,
    pendingCount: Int,
    holdActive: Boolean,
    canOfferFind: Boolean,
): SeatLayerPickerCheckoutCtaState {
    // 1. Nothing is for sale.
    if (salesClosed) {
        return SeatLayerPickerCheckoutCtaState(
            label = strings.salesClosedCta,
            enabled = false,
            busy = false,
            statesReason = true,
            findsBestSeats = false,
        )
    }
    // 2a. A general-admission question is still open.
    if (promptUnanswered) {
        return SeatLayerPickerCheckoutCtaState(
            label = strings.confirmYourTickets,
            enabled = false,
            busy = false,
            statesReason = true,
            findsBestSeats = false,
        )
    }
    // 2b. A seat card is asking. The button keeps the label it would have had
    // with no card up and is simply held down: the card is the question, and a
    // footer that changes its words as well makes the cart read as the card's.
    if (seatCardOpen) {
        val underneath = seatLayerCheckoutCtaState(
            strings = strings,
            label = label,
            canCheckout = canCheckout,
            salesClosed = false,
            promptUnanswered = false,
            seatCardOpen = false,
            busy = busy,
            selectionValidity = selectionValidity,
            ticketCount = ticketCount,
            pendingCount = pendingCount,
            holdActive = holdActive,
            canOfferFind = canOfferFind,
        )
        return underneath.copy(enabled = false, findsBestSeats = false)
    }
    // 3 and 4. Work in flight, each ending where the work ends.
    if (busy == SeatLayerPickerCheckoutBusy.SecuringSeats) {
        return SeatLayerPickerCheckoutCtaState(
            label = strings.securingSeats,
            enabled = false,
            busy = true,
            statesReason = false,
            findsBestSeats = false,
        )
    }
    if (busy == SeatLayerPickerCheckoutBusy.OpeningCheckout) {
        return SeatLayerPickerCheckoutCtaState(
            label = strings.openingCheckout,
            enabled = false,
            busy = true,
            statesReason = false,
            findsBestSeats = false,
        )
    }
    // 5. The selection breaks the event's own rules.
    if (selectionValidity != null && !selectionValidity.isValid) {
        val wording = when {
            selectionValidity.remaining > 0 -> strings.chooseMore(selectionValidity.remaining)
            selectionValidity.count > selectionValidity.required ->
                strings.removeTickets(selectionValidity.count - selectionValidity.required)
            else -> strings.adjustSelection
        }
        return SeatLayerPickerCheckoutCtaState(
            label = wording,
            enabled = false,
            busy = false,
            statesReason = true,
            findsBestSeats = false,
        )
    }
    // 6. An empty cart on a surface that can offer the finder gets a door, not
    // a full-width refusal.
    if (ticketCount == 0 && canOfferFind && !holdActive) {
        return SeatLayerPickerCheckoutCtaState(
            label = strings.findBestSeatsCta,
            enabled = true,
            busy = false,
            statesReason = false,
            findsBestSeats = true,
        )
    }
    // 7. A hold exists.
    if (holdActive) {
        return SeatLayerPickerCheckoutCtaState(
            label = if (pendingCount > 0) {
                strings.secureMoreAndCheckout(pendingCount)
            } else {
                strings.continueToCheckout
            },
            enabled = canCheckout,
            busy = false,
            statesReason = false,
            findsBestSeats = false,
        )
    }
    // 8. Nothing picked, and no finder to offer.
    if (ticketCount == 0) {
        return SeatLayerPickerCheckoutCtaState(
            label = strings.selectSeats,
            enabled = false,
            busy = false,
            statesReason = true,
            findsBestSeats = false,
        )
    }
    // 9. Tickets, no hold.
    return SeatLayerPickerCheckoutCtaState(
        label = label,
        enabled = canCheckout,
        busy = false,
        statesReason = false,
        findsBestSeats = false,
    )
}

/** Resolves the ladder from the live scope; every checkout surface shares it. */
@Composable
internal fun rememberCheckoutCtaState(
    label: String,
    securing: Boolean,
    canOfferFind: Boolean,
): SeatLayerPickerCheckoutCtaState {
    val scope = LocalSeatLayerPickerScope.current
    val snapshot = scope.state.snapshot
    val presentation = scope.state.presentation
    val confirmed = presentation.confirmedCart(snapshot)
    val busy = when {
        presentation.checkoutHandoff != null -> SeatLayerPickerCheckoutBusy.OpeningCheckout
        securing -> SeatLayerPickerCheckoutBusy.SecuringSeats
        else -> SeatLayerPickerCheckoutBusy.None
    }
    return seatLayerCheckoutCtaState(
        strings = scope.strings,
        label = scope.strings.holdAndCheckout,
        canCheckout = scope.controller.canCheckout,
        salesClosed = snapshot?.event?.salesClosed == true,
        promptUnanswered = presentation.activePrompt != null,
        seatCardOpen = presentation.candidateSeat != null,
        busy = busy,
        selectionValidity = snapshot?.selectionValidity,
        ticketCount = confirmed.totals.quantity,
        // The bridge does not distinguish held lines from freshly picked ones.
        pendingCount = 0,
        holdActive = snapshot?.hold?.active == true,
        canOfferFind = canOfferFind,
    ).let { if (label == scope.strings.holdAndCheckout) it else it.copy(label = label) }
}

/**
 * The one action at the foot of the sheet.
 *
 * It carries its own label only: the total is on the line above it, and a
 * button that restates the money is a second summary to keep in step.
 */
@Composable
public fun SeatLayerBookButton(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
    onFindBestSeats: (() -> Unit)? = null,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.CheckoutBar]
    val coroutineScope = rememberCoroutineScope()
    var securing by remember { mutableStateOf(false) }
    // A door into an empty room is worse than none: the finder is only offered
    // where the form behind it would actually be drawn.
    val canOfferFind = onFindBestSeats != null &&
        scope.options.enableBestAvailable &&
        !scope.options.readOnly &&
        scope.controller.supportsCommand("picker.bestAvailable")
    val cta = rememberCheckoutCtaState(
        label = scope.strings.holdAndCheckout,
        securing = securing,
        canOfferFind = canOfferFind,
    )
    SeatLayerPickerCtaButton(
        modifier = modifier,
        state = cta,
        style = resolvedStyle,
        onPress = {
            if (cta.findsBestSeats) {
                onFindBestSeats?.invoke()
                return@SeatLayerPickerCtaButton
            }
            coroutineScope.launch {
                securing = true
                try {
                    scope.performAction {
                        scope.controller.handoffCheckout(scope.callbacks.onCheckout)
                    }
                    scope.callbacks.onContinue()
                } finally {
                    securing = false
                }
            }
        },
    )
}

/** The drawn button. Disabled is a designed state, never a platform grey. */
@Composable
internal fun SeatLayerPickerCtaButton(
    modifier: Modifier,
    state: SeatLayerPickerCheckoutCtaState,
    style: SeatLayerPickerPartStyle,
    onPress: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val theme = scope.theme
    val ground = if (state.enabled) {
        style.containerColor ?: theme.accent
    } else {
        theme.text.copy(alpha = CheckoutCtaLiterals.DISABLED_GROUND_ALPHA)
    }
    val ink = if (state.enabled) {
        style.contentColor ?: theme.onAccent
    } else {
        theme.text.copy(alpha = CheckoutCtaLiterals.DISABLED_INK_ALPHA)
    }
    val shape = RoundedCornerShape(style.cornerRadius ?: scope.styles.buttonRadius)
    // A plain clickable rather than a clickable Surface: Material's own button
    // pads itself to 48 whatever the style says, and the foot's button is
    // exactly `size.checkoutButtonHeight`.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(SeatLayerSizeTokens.CHECKOUT_BUTTON_HEIGHT.dp)
            .clickable(enabled = state.enabled, onClick = onPress)
            .semantics { role = Role.Button },
        shape = shape,
        color = ground,
        contentColor = ink,
        border = if (state.enabled) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, theme.divider)
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CheckoutCtaLiterals.SPINNER_SIZE.dp),
                        strokeWidth = CheckoutCtaLiterals.SPINNER_STROKE.dp,
                        color = ink,
                    )
                    Spacer(Modifier.width(CheckoutCtaLiterals.SPINNER_GAP.dp))
                }
                Text(
                    text = state.label,
                    fontSize = SeatLayerTypeTokens.BOOK_BUTTON_SIZE.sp,
                    fontWeight = FontWeight(SeatLayerTypeTokens.BOOK_BUTTON_WEIGHT),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The wide panel's checkout bar.
 *
 * Same ladder, same button; the wide composition simply has room to draw the
 * total beside it rather than above it.
 */
@Composable
public fun SeatLayerPickerCheckoutBar(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.CheckoutBar]
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = resolvedStyle.containerColor ?: scope.theme.background,
        contentColor = resolvedStyle.contentColor ?: scope.theme.text,
    ) {
        SeatLayerPickerSheetFoot(showHairline = false)
    }
}

/**
 * Card-local numbers the design source keeps as literals.
 *
 * Reported as a tokens.json gap rather than invented as tokens here: a constant
 * this file makes up is a design decision the design source never took.
 */
private object CheckoutCtaLiterals {
    const val DISABLED_GROUND_ALPHA = 0.08f
    const val DISABLED_INK_ALPHA = 0.55f
    const val SPINNER_SIZE = 16f
    const val SPINNER_STROKE = 2f
    const val SPINNER_GAP = 8f
}
