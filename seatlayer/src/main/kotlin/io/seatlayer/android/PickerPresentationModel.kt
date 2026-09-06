package io.seatlayer.android

/** Which question the card standing over a seat is asking. */
public enum class SeatLayerPickerCardIntent {
    /** A seat the buyer has just tapped: "add this one?" */
    Add,

    /**
     * A seat already in the cart, tapped again: "remove this one?"
     *
     * A second tap used to drop the seat in silence, which on a held seat also
     * released inventory the buyer had paid attention to. It asks now.
     */
    Remove,
}

/** How far the cart sheet is open. */
public enum class SeatLayerPickerSheetDetent {
    /** Footer only: the count, the total and one action. */
    Collapsed,

    /** The tickets themselves, up to the sheet's own ceiling. */
    Open,
}

/**
 * The arrival of a seat in the cart, as a state machine rather than a clock.
 *
 * A timer decides how long a flight takes before it has happened; these steps
 * are advanced by the surfaces that actually know — the chip that lands and the
 * tray that swells — so a slow frame delays the swell instead of losing it.
 */
public enum class SeatLayerPickerCartLanding {
    Idle,

    /** A chip is travelling from the seat to the tray. */
    Flying,

    /** The chip has landed and the tray is acknowledging it. */
    Landed,

    /** The acknowledgement is finished and the tray is at rest again. */
    Released,
}

/**
 * The map lift that keeps a seat clear of the card standing over it.
 *
 * [gestures] is the renderer's own record of where the buyer has dragged since;
 * carrying it back on every re-frame is what stops the lift from fighting a
 * finger. [restore] is the offset to give back when the card closes.
 */
public data class SeatLayerPickerSeatLift(
    val seatId: String,
    val dy: Double,
    val gestures: SeatLayerPickerSeatGestures? = null,
) {
    /** How far to put the map back when the card closes. */
    public val restore: Double get() = -dy
}
