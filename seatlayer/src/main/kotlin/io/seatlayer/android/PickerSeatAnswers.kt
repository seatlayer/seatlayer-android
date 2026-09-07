package io.seatlayer.android

/*
 * Which seats the picker is allowed to ask a question about.
 *
 * A seat that is sold, blocked, or in another buyer's hold is inert. The engine
 * swallows the tap, and this is the same rule kept on the native side for the
 * case an older runtime still reports one.
 */

/**
 * Whether a card may stand over [status].
 *
 * `booked` and `blocked` never raise one. `held` raises one only while the
 * picker's OWN hold is active, because that is when the buyer's own seats
 * report as held and the card is what offers them back. A status this build
 * does not recognise is left alone: an unknown word is not a reason to swallow
 * a seat the runtime selected, and every runtime shipped before the field omits
 * it entirely.
 *
 * There is deliberately no "held by another buyer" and no "already booked"
 * card, and no string for one. A reason the buyer can do nothing about is worse
 * than being left on the map.
 */
public fun seatLayerPickerMayAskAboutSeat(
    status: String?,
    ownHoldActive: Boolean,
): Boolean = when (status) {
    null -> true
    SEAT_STATUS_BOOKED, SEAT_STATUS_BLOCKED -> false
    SEAT_STATUS_HELD -> ownHoldActive
    else -> true
}

/** The seat may be asked about, given the picker's own hold. */
internal fun SeatLayerPickerSelectedSeat.mayBeAskedAbout(ownHoldActive: Boolean): Boolean =
    seatLayerPickerMayAskAboutSeat(status, ownHoldActive)

private const val SEAT_STATUS_BOOKED = "booked"
private const val SEAT_STATUS_BLOCKED = "blocked"
private const val SEAT_STATUS_HELD = "held"
