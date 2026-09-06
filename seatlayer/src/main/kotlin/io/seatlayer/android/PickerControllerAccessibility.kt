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
