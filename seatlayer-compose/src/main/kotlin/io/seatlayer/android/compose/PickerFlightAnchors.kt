package io.seatlayer.android.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/*
 * Where the ticket leaves from, and where it is going.
 *
 * Both are MEASURED rather than guessed: the card has one home but many
 * heights, and the summary the ticket is joining sits in a sheet on a phone
 * and in a side panel on a tablet. A chip that flies to a computed point lands
 * beside the total on one device and in the middle of the map on another.
 *
 * The anchors are session state, not card state — a card that has just been
 * answered is on its way out, and the chip it launched has to keep flying
 * after it has gone.
 */

/** One ticket in the air, as the chip that carries it needs to draw itself. */
internal data class SeatLayerPickerFlight(
    val id: Int,
    val label: String,
    val color: Color,
    val from: Offset?,
    val to: Offset?,
)

/** The two anchors, and the one flight that may be between them. */
internal class SeatLayerPickerFlightState {
    /** The centre of the card, in window coordinates. */
    var cardCentre: Offset? = null

    /** The summary line the ticket is joining, in window coordinates. */
    var cartAnchor: Offset? = null

    /** The flight in the air, or null. */
    var flight: SeatLayerPickerFlight? by mutableStateOf(null)
        private set

    private var sequence = 0

    /** Send [label] on its way, from wherever the card was when it was pressed. */
    fun launch(label: String, color: Color) {
        sequence += 1
        flight = SeatLayerPickerFlight(
            id = sequence,
            label = label,
            color = color,
            from = cardCentre,
            to = cartAnchor,
        )
    }

    /** Take [flight] out of the air. A later flight is never cleared by an earlier one. */
    fun landed(flight: SeatLayerPickerFlight) {
        if (this.flight?.id == flight.id) this.flight = null
    }
}

internal val LocalSeatLayerPickerFlight =
    staticCompositionLocalOf { SeatLayerPickerFlightState() }

/** Report this element's centre as the point a ticket leaves from. */
internal fun Modifier.pickerFlightOrigin(state: SeatLayerPickerFlightState): Modifier =
    onGloballyPositioned { coordinates ->
        state.cardCentre = coordinates.boundsInWindow().center
    }

/**
 * Report this element's centre as the point a ticket is going to.
 *
 * Measured continuously rather than once at the press, because the count it
 * sits beside reflows as the cart grows and the sheet it rides can be dragged
 * while a card is up.
 */
internal fun Modifier.pickerFlightTarget(state: SeatLayerPickerFlightState): Modifier =
    onGloballyPositioned { coordinates ->
        state.cartAnchor = coordinates.boundsInWindow().center
    }
