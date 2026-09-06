package io.seatlayer.android

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/*
 * What the buyer picks, and what that costs: selection, tiers, cart lines and
 * the filters that decide which objects are pickable at all.
 *
 * Split out of the controller so the class stays a readable table of what a
 * host may ask for; every entry here is reached through the public member of
 * the same name.
 */

internal suspend fun SeatLayerPickerController.performSelectObjects(
    objects: List<String>,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValues(objects, "objects")
    return mutation("picker.selectObjects", jsonObject("objects" to jsonStrings(objects)))
}

internal suspend fun SeatLayerPickerController.performDeselectObjects(
    objects: List<String>,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValues(objects, "objects")
    return mutation("picker.deselectObjects", jsonObject("objects" to jsonStrings(objects)))
}

internal suspend fun SeatLayerPickerController.performSelectCategories(
    categoryKeys: List<String>,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValues(categoryKeys, "categoryKeys")
    return mutation(
        "picker.selectCategories",
        jsonObject("categoryKeys" to jsonStrings(categoryKeys)),
    )
}

internal suspend fun SeatLayerPickerController.performDeselectCategories(
    categoryKeys: List<String>,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValues(categoryKeys, "categoryKeys")
    return mutation(
        "picker.deselectCategories",
        jsonObject("categoryKeys" to jsonStrings(categoryKeys)),
    )
}

internal suspend fun SeatLayerPickerController.performSetSeatTier(
    seatId: String,
    tierId: String?,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValue(seatId, "seatId")
    tierId?.let { requireNonEmptyValue(it, "tierId") }
    return mutation(
        "picker.setSeatTier",
        jsonObject(
            "seatId" to JsonPrimitive(seatId),
            "tierId" to (tierId?.let(::JsonPrimitive) ?: JsonNull),
        ),
    )
}

internal suspend fun SeatLayerPickerController.performRemoveObject(
    label: String,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValue(label, "label")
    return mutation("picker.removeCartLine", jsonObject("label" to JsonPrimitive(label)))
}

internal suspend fun SeatLayerPickerController.performSetTableQuantity(
    label: String,
    quantity: Int,
    ttlMillis: Int?,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValue(label, "label")
    requirePositiveValue(quantity, "quantity")
    ttlMillis?.let { requirePositiveValue(it, "ttlMillis") }
    return mutation(
        "picker.setTableQuantity",
        jsonObject(
            "label" to JsonPrimitive(label),
            "quantity" to JsonPrimitive(quantity),
            "ttlMs" to jsonNumber(ttlMillis),
        ),
    )
}

internal suspend fun SeatLayerPickerController.performSetSelectableObjects(
    objects: List<String>?,
): SeatLayerPickerSnapshot? {
    objects?.let { requireNonEmptyValues(it, "objects") }
    return mutation(
        "picker.setSelectableObjects",
        jsonObject("objects" to (objects?.let(::jsonStrings) ?: JsonNull)),
    )
}

internal suspend fun SeatLayerPickerController.performSetMaxSelection(
    maximum: Int,
): SeatLayerPickerSnapshot? {
    requirePositiveValue(maximum, "maxSelection")
    return mutation(
        "picker.setMaxSelection",
        jsonObject("maxSelection" to JsonPrimitive(maximum)),
    )
}

internal suspend fun SeatLayerPickerController.performSetCategoryFilter(
    categoryKeys: List<String>,
    focus: Boolean,
): SeatLayerPickerSnapshot? {
    if (categoryKeys.isNotEmpty()) requireNonEmptyValues(categoryKeys, "categoryKeys")
    return mutation(
        "picker.setCategoryFilter",
        jsonObject(
            "categoryKeys" to if (categoryKeys.isEmpty()) JsonNull else jsonStrings(categoryKeys),
            "focus" to JsonPrimitive(focus),
        ),
    )
}
