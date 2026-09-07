package io.seatlayer.android

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/*
 * Where the buyer is looking: rungs, floors, zoom, the 3D scene, the panorama
 * and the rectangles of chrome the map must not answer taps through.
 */

internal suspend fun SeatLayerPickerController.performFocusSection(
    sectionId: String,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValue(sectionId, "sectionId")
    return mutation("picker.focusSection", jsonObject("sectionId" to JsonPrimitive(sectionId)))
}

internal suspend fun SeatLayerPickerController.performSetRung(
    rung: String,
): SeatLayerPickerSnapshot? {
    if (rung !in setOf("zones", "sections", "seats")) {
        throw badPickerPayload("rung must be zones, sections, or seats")
    }
    return mutation("picker.setRung", jsonObject("rung" to JsonPrimitive(rung)))
}

internal suspend fun SeatLayerPickerController.performSetFloor(
    floorId: String,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValue(floorId, "floorId")
    return mutation("picker.setFloor", jsonObject("floorId" to JsonPrimitive(floorId)))
}

internal suspend fun SeatLayerPickerController.performSetViewMode(
    mode: SeatLayerViewMode,
): SeatLayerPickerSnapshot? =
    mutation("picker.setViewMode", jsonObject("mode" to JsonPrimitive(mode.raw)))

internal suspend fun SeatLayerPickerController.performSetBuyerView(
    view: String,
    flyToSeatId: String?,
    resetView: Boolean,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValue(view, "view")
    flyToSeatId?.let { requireNonEmptyValue(it, "flyToSeatId") }
    if (!supportsVenue3D) return snapshot
    return mutation(
        "picker.setBuyerView",
        jsonObject(
            "view" to JsonPrimitive(view),
            "flyToSeatId" to jsonString(flyToSeatId),
            "resetView" to JsonPrimitive(resetView),
        ),
    )
}

internal suspend fun SeatLayerPickerController.performOpenSeatView(
    seatId: String,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValue(seatId, "seatId")
    if (!supportsSeatView) return snapshot
    return mutation("picker.openSeatView", jsonObject("seatId" to JsonPrimitive(seatId)))
}

internal suspend fun SeatLayerPickerController.performSet3DNavigationMode(
    mode: String,
): SeatLayerPickerSnapshot? {
    if (mode !in setOf("orbit", "pan")) {
        throw badPickerPayload("3D navigation mode must be orbit or pan")
    }
    if (!supportsCapabilityAndCommand("venue-3d-controls-v1", "picker.setVenue3DNavigationMode")) {
        return snapshot
    }
    return mutation(
        "picker.setVenue3DNavigationMode",
        jsonObject("mode" to JsonPrimitive(mode)),
    )
}

internal suspend fun SeatLayerPickerController.performSetThemeMode(
    mode: SeatLayerPickerThemeMode?,
    mapTheme: SeatLayerPickerMapTheme?,
) {
    presentation(
        "picker.setThemeMode",
        jsonObject(
            "mode" to (mode?.raw?.let(::JsonPrimitive) ?: JsonNull),
            "mapTheme" to mapTheme?.let {
                jsonObject(
                    "background" to JsonPrimitive(it.background),
                    "rowLabelColor" to JsonPrimitive(it.rowLabel),
                    "textColor" to JsonPrimitive(it.text),
                    "selectionColor" to JsonPrimitive(it.selection),
                )
            },
        ),
    )
}

internal suspend fun SeatLayerPickerController.performSetViewportInsets(
    insets: SeatLayerPickerViewportInsets?,
) {
    if (!supportsViewportInsets) return
    val payload = if (insets == null) {
        jsonObject("insets" to JsonNull)
    } else {
        jsonObject(
            "top" to JsonPrimitive(insets.top),
            "right" to JsonPrimitive(insets.right),
            "bottom" to JsonPrimitive(insets.bottom),
            "left" to JsonPrimitive(insets.left),
        )
    }
    presentation("picker.setViewportInsets", payload)
}
/**
 * Replaces the whole list of rectangles the map must not answer taps through.
 *
 * Chrome drawn over the map takes the whole touch on its own side, but the
 * renderer never learns that unless the rectangles are standing before the
 * finger lands — so this is re-sent at the same cadence as the viewport insets,
 * and every call carries the complete list, never a delta.
 */
internal suspend fun SeatLayerPickerController.performSetBlockedRegions(
    regions: List<SeatLayerPickerBlockedRegion>?,
) {
    if (!supportsCommand("picker.setBlockedRegions")) return
    presentation(
        "picker.setBlockedRegions",
        jsonObject(
            "rects" to (
                regions?.let { list ->
                    kotlinx.serialization.json.JsonArray(list.map(SeatLayerPickerBlockedRegion::toJson))
                } ?: JsonNull
                ),
        ),
    )
}

/**
 * Lifts the map so a seat clears the card standing over it.
 *
 * The reply's `gestures` is the renderer's own record of where the buyer has
 * since dragged the map. Reading it when the card opens and handing it back on
 * every re-frame is the whole guard against arguing with a finger.
 */
internal suspend fun SeatLayerPickerController.performFrameSeat(
    seatId: String,
    fraction: Double?,
    animate: Boolean,
    gestures: SeatLayerPickerSeatGestures?,
): SeatLayerPickerSeatFrame? {
    requireNonEmptyValue(seatId, "seatId")
    if (fraction != null && (!fraction.isFinite() || fraction <= 0.0 || fraction > 1.0)) {
        throw badPickerPayload("fraction must be greater than zero and at most one")
    }
    if (!supportsCommand("picker.frameSeat")) return null
    val reply = command(
        "picker.frameSeat",
        jsonObject(
            "seatId" to JsonPrimitive(seatId),
            "fraction" to jsonNumber(fraction),
            "animate" to JsonPrimitive(animate),
            "gestures" to gestures?.raw,
        ),
    )
    return decodeSeatLayerPickerSeatFrame(reply)
}
