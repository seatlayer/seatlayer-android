package io.seatlayer.android

public data class SeatLayerPickerAccessibilityDraft(
    val keys: Set<String>,
    val limitedView: Boolean,
    val colorblindSafe: Boolean,
)

public data class SeatLayerPickerAccessibilityAvailability(
    val accessibility: Boolean,
    val limitedView: Boolean,
    val colorblindSafe: Boolean,
)
