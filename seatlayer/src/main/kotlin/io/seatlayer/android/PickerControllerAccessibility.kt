package io.seatlayer.android

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/*
 * The accessibility and view filters, and the two commands that move the map
 * to what a filter just narrowed to.
 *
 * Each filter applies as it is set: there is no staged plan and no batch, so a
 * buyer sees what a switch did before deciding on the next one.
 */

internal suspend fun SeatLayerPickerController.performSetAccessibilityFilter(
    types: List<String>,
): SeatLayerPickerSnapshot? {
    if (types.isNotEmpty()) requireNonEmptyValues(types, "types")
    return mutation(
        "picker.setAccessibilityFilter",
        jsonObject("types" to if (types.isEmpty()) JsonNull else jsonStrings(types)),
    )
}

internal suspend fun SeatLayerPickerController.performSetLimitedViewHidden(
    hidden: Boolean,
): SeatLayerPickerSnapshot? =
    mutation("picker.setLimitedViewFilter", jsonObject("on" to JsonPrimitive(hidden)))

internal suspend fun SeatLayerPickerController.performSetColorblindSafe(
    enabled: Boolean,
): SeatLayerPickerSnapshot? =
    mutation("picker.setColorblindSafe", jsonObject("on" to JsonPrimitive(enabled)))
/** Flies the map to the spaces the current accommodation filter matches. */
internal suspend fun SeatLayerPickerController.performFocusAccessibilityFilter(
    types: List<String>?,
): SeatLayerPickerSnapshot? {
    types?.takeIf(List<String>::isNotEmpty)?.let { requireNonEmptyValues(it, "types") }
    if (!supportsAccessibilityFocus || !supportsCommand("picker.focusAccessibilityFilter")) {
        return snapshot
    }
    return mutation(
        "picker.focusAccessibilityFilter",
        jsonObject(
            "types" to (
                types?.takeIf(List<String>::isNotEmpty)?.let(::jsonStrings) ?: JsonNull
                ),
        ),
    )
}

/**
 * Steps to the next section holding a matching space.
 *
 * A runtime that lists neither the capability nor the command answers nothing:
 * a tour that is not offered is not a failure, and the stepper simply never
 * appears.
 */
internal suspend fun SeatLayerPickerController.performFocusNextAccessibleSection(
    types: List<String>?,
): SeatLayerPickerAccessibleStep? {
    types?.takeIf(List<String>::isNotEmpty)?.let { requireNonEmptyValues(it, "types") }
    if (!supportsAccessibilityFocus || !supportsCommand("picker.focusNextAccessibleSection")) {
        return null
    }
    val reply = command(
        "picker.focusNextAccessibleSection",
        jsonObject(
            "types" to (
                types?.takeIf(List<String>::isNotEmpty)?.let(::jsonStrings) ?: JsonNull
                ),
        ),
    )
    return decodeSeatLayerPickerAccessibleStep(reply)
}

/**
 * Marks the seat a card is asking about, or clears the mark.
 *
 * The seat is painted as a candidate, not as a selection: it is in no cart and
 * in no total until the card is answered.
 */
internal suspend fun SeatLayerPickerController.performSetSelectionFocus(seatId: String?) {
    seatId?.let { requireNonEmptyValue(it, "seatId") }
    if (!supportsCommand("picker.setSelectionFocus")) return
    presentation(
        "picker.setSelectionFocus",
        jsonObject("seatId" to (seatId?.let(::JsonPrimitive) ?: JsonNull)),
    )
}
