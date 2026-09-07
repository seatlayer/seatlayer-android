package io.seatlayer.android

/*
 * The state the chrome draws from and the few actions that move it: the card's
 * candidate seat, the map lift under it, the arrival of a seat in the cart, and
 * the rectangles the map must not answer taps through.
 */

/**
 * Opens the card over [seat], marking it as a candidate on the map.
 *
 * A candidate is not a purchase: it is in no cart line, no ticket count and no
 * total until the card is answered.
 */
internal suspend fun SeatLayerPickerController.performOpenSeatCard(
    seat: SeatLayerPickerSelectedSeat,
    intent: SeatLayerPickerCardIntent,
) {
    stateHolder.setCardIntent(seat, intent)
    runCatching { setSelectionFocus(seat.id) }
}

/** Closes the card, clears the candidate mark and puts the map back. */
internal suspend fun SeatLayerPickerController.performCloseSeatCard() {
    stateHolder.setCardIntent(null, SeatLayerPickerCardIntent.Add)
    runCatching { forgetSelectionFocus() }
    performRestoreSeatLift()
}

/**
 * Lifts the map so the seat clears the card standing over it.
 *
 * The gesture record from the previous frame is handed back, so a buyer who has
 * dragged the map since keeps that drag.
 */
internal suspend fun SeatLayerPickerController.performLiftSeat(
    seatId: String,
    fraction: Double?,
): SeatLayerPickerSeatLift? {
    val previous = presentation.seatLift?.takeIf { it.seatId == seatId }
    val frame = frameSeat(
        seatId = seatId,
        fraction = fraction,
        gestures = previous?.gestures,
    ) ?: return null
    val lift = SeatLayerPickerSeatLift(
        seatId = seatId,
        dy = (previous?.dy ?: 0.0) + frame.dy,
        gestures = frame.gestures ?: previous?.gestures,
    )
    stateHolder.setSeatLift(lift)
    return lift
}

/** Gives back the lift the card borrowed, leaving the buyer's own drag alone. */
internal suspend fun SeatLayerPickerController.performRestoreSeatLift() {
    val lift = presentation.seatLift ?: return
    stateHolder.setSeatLift(null)
    if (lift.dy == 0.0) return
    runCatching {
        frameSeat(seatId = lift.seatId, fraction = null, gestures = lift.gestures)
    }
}
