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

public sealed interface SeatLayerPickerAccessibilityMutation {
    public data class Accessibility(val keys: List<String>) :
        SeatLayerPickerAccessibilityMutation
    public data class LimitedView(val enabled: Boolean) :
        SeatLayerPickerAccessibilityMutation
    public data class ColorblindSafe(val enabled: Boolean) :
        SeatLayerPickerAccessibilityMutation
}

/** Pure filter planning for ready-made and custom native accessibility sheets. */
public object SeatLayerPickerAccessibility {
    public fun plan(
        draft: SeatLayerPickerAccessibilityDraft,
        initial: SeatLayerPickerAccessibilityDraft,
        available: SeatLayerPickerAccessibilityAvailability,
    ): List<SeatLayerPickerAccessibilityMutation> = buildList {
        if (available.accessibility && draft.keys != initial.keys) {
            add(SeatLayerPickerAccessibilityMutation.Accessibility(draft.keys.toList()))
        }
        if (available.limitedView && draft.limitedView != initial.limitedView) {
            add(SeatLayerPickerAccessibilityMutation.LimitedView(draft.limitedView))
        }
        if (available.colorblindSafe && draft.colorblindSafe != initial.colorblindSafe) {
            add(SeatLayerPickerAccessibilityMutation.ColorblindSafe(draft.colorblindSafe))
        }
    }

    /** Positive seat filtering should reveal the seats it just narrowed to. */
    public fun shouldFocusSeats(
        mutations: List<SeatLayerPickerAccessibilityMutation>,
    ): Boolean = mutations.any { mutation ->
        when (mutation) {
            is SeatLayerPickerAccessibilityMutation.Accessibility ->
                mutation.keys.isNotEmpty()
            is SeatLayerPickerAccessibilityMutation.LimitedView -> mutation.enabled
            is SeatLayerPickerAccessibilityMutation.ColorblindSafe -> false
        }
    }
}
