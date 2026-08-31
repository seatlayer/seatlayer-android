package io.seatlayer.android

/** Pure selection transition used by native cart-motion implementations. */
public object SeatLayerPickerMotion {
    public fun newlyConfirmedSeat(
        previousPending: SeatLayerPickerSelectedSeat?,
        currentPending: SeatLayerPickerSelectedSeat?,
        snapshot: SeatLayerPickerSnapshot?,
    ): SeatLayerPickerSelectedSeat? {
        val previous = previousPending ?: return null
        if (
            currentPending != null &&
            SeatLayerPickerProjections.seatIdentity(currentPending) ==
            SeatLayerPickerProjections.seatIdentity(previous)
        ) return null
        val cartContainsSeat = snapshot?.cartLines.orEmpty().any { line ->
            if (line.seatId != null) {
                line.seatId == previous.id
            } else {
                line.label == previous.label
            }
        }
        return previous.takeIf { cartContainsSeat }
    }
}
