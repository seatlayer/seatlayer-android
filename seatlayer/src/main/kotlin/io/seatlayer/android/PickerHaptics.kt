package io.seatlayer.android

/**
 * The nine moments the picker answers with a feeling.
 *
 * The weight each one is played at belongs to the design source, not here: a
 * cue names what happened, and the chrome decides how that feels on this
 * platform.
 *
 * The first snapshot of a session never fires a cue — a resumed focus or a
 * resumed hold is not something the buyer just did.
 */
public enum class SeatLayerPickerHapticCue {
    SelectionAdded,
    SectionFocused,
    TicketRemoved,
    HoldEnding,
    HoldCreated,
    HoldExpired,
    CardArrived,
    SeatConfirmed,
    CardCancelled,
}
