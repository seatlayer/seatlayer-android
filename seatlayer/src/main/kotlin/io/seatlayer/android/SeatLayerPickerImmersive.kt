package io.seatlayer.android

/** Position reported by the runtime for the current 3D row target. */
public data class SeatLayerPickerVenue3DNeighbours(
    val previousSeatId: String?,
    val target: SeatLayerPickerSelectedSeat?,
    val targetSeatId: String?,
    val nextSeatId: String?,
)

@JvmInline
public value class SeatLayerPickerVenue3DAction(public val raw: String) {
    public companion object {
        public val Back: SeatLayerPickerVenue3DAction = SeatLayerPickerVenue3DAction("back")
        public val Map: SeatLayerPickerVenue3DAction = SeatLayerPickerVenue3DAction("map")
        public val Previous: SeatLayerPickerVenue3DAction =
            SeatLayerPickerVenue3DAction("previous")
        public val Next: SeatLayerPickerVenue3DAction = SeatLayerPickerVenue3DAction("next")
        public val Recenter: SeatLayerPickerVenue3DAction =
            SeatLayerPickerVenue3DAction("recenter")
    }
}

/** Exact picker.setBuyerView payload, before bridge dispatch. */
public data class SeatLayerPickerVenue3DActionPlan(
    val view: String,
    val flyToSeatId: String? = null,
    val resetView: Boolean = false,
)

/** Pure 3D projections shared by the ready widget and custom native chrome. */
public object SeatLayerPickerImmersive {
    public fun neighbours(
        snapshot: SeatLayerPickerSnapshot?,
    ): SeatLayerPickerVenue3DNeighbours {
        val targetId = snapshot?.map?.view3DTargetSeatId
        val index = if (targetId == null) {
            -1
        } else {
            snapshot.selection.indexOfFirst { it.id == targetId }
        }
        val map = snapshot?.map
        val authoredPosition = map?.reportsView3DPosition == true
        return SeatLayerPickerVenue3DNeighbours(
            previousSeatId = if (authoredPosition) {
                map.view3DPreviousSeatId
            } else {
                snapshot?.selection?.getOrNull(index - 1)?.id
            },
            target = map?.view3DTargetSeat
                ?: snapshot?.selection?.getOrNull(index),
            targetSeatId = targetId,
            nextSeatId = if (authoredPosition) {
                map.view3DNextSeatId
            } else {
                snapshot?.selection?.getOrNull(index + 1)?.id
            },
        )
    }

    /** True below the whole-venue 3D camera, even without a selected seat. */
    public fun hasFocusedView(snapshot: SeatLayerPickerSnapshot?): Boolean {
        val map = snapshot?.map ?: return false
        if (map.reportsView3DPosition) {
            return map.view3DTargetSeatId != null ||
                map.view3DTargetSeat != null ||
                map.view3DFocusedSectionId != null
        }
        return map.view3DTargetSeatId != null ||
            map.view3DTargetSeat != null ||
            map.focusedSectionId != null ||
            map.focusedSection != null ||
            map.rung == "seats"
    }

    /** Returns null for unavailable row boundaries and non-3D snapshots. */
    public fun plan(
        action: SeatLayerPickerVenue3DAction,
        snapshot: SeatLayerPickerSnapshot?,
    ): SeatLayerPickerVenue3DActionPlan? {
        if (snapshot?.map?.buyerView != "venue3d") return null
        val neighbours = neighbours(snapshot)
        return when (action) {
            SeatLayerPickerVenue3DAction.Back -> if (hasFocusedView(snapshot)) {
                SeatLayerPickerVenue3DActionPlan(view = "venue3d", resetView = true)
            } else {
                SeatLayerPickerVenue3DActionPlan(view = "map")
            }
            SeatLayerPickerVenue3DAction.Map ->
                SeatLayerPickerVenue3DActionPlan(view = "map")
            SeatLayerPickerVenue3DAction.Previous -> neighbours.previousSeatId?.let {
                SeatLayerPickerVenue3DActionPlan(view = "venue3d", flyToSeatId = it)
            }
            SeatLayerPickerVenue3DAction.Next -> neighbours.nextSeatId?.let {
                SeatLayerPickerVenue3DActionPlan(view = "venue3d", flyToSeatId = it)
            }
            SeatLayerPickerVenue3DAction.Recenter -> neighbours.targetSeatId?.let {
                SeatLayerPickerVenue3DActionPlan(
                    view = "venue3d",
                    flyToSeatId = it,
                    resetView = true,
                )
            }
            else -> null
        }
    }
}
