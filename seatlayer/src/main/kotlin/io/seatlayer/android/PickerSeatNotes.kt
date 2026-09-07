package io.seatlayer.android

/**
 * What a seat's own attributes say, as one list every surface draws the same.
 *
 * A seat can carry twelve accommodations, a wheelchair provision, three selling
 * marks and the organizer's own sentence. Deciding per surface which of those
 * matter is how a seat behind both a rail and a pillar gets told about the rail
 * and never about the pillar. The decision lives here; surfaces only draw it.
 *
 * The rows carry a [labelKey] rather than a sentence: this module has no
 * strings, and the chrome resolves the key in the buyer's language.
 */
public data class SeatLayerPickerSeatNote(
    /** Stable identity for the row, for keying and for tests. */
    val key: String,
    /** Which drawing the row wears, and which attribute it came from. */
    val iconKey: String,
    /** The string this row's title is written from. */
    val labelKey: String,
    val tone: SeatLayerPickerSeatNoteTone,
    /** The organizer's free text, attached to the row it belongs to. */
    val note: String? = null,
)

/** How a note row reads; the colour is the surface's. */
public enum class SeatLayerPickerSeatNoteTone {
    /** An accommodation or a wheelchair provision: neutral and factual. */
    Access,

    /** A view restriction, which the buyer should know before paying. */
    Warn,

    /** A premium seat. */
    Premium,

    /** The organizer's own sentence, with no attribute of its own. */
    Note,
}

/** A seat's selling marks and the organizer's sentence about them. */
public data class SeatLayerPickerSeatCommercial(
    val restrictedView: Boolean = false,
    val obstructedView: Boolean = false,
    val premium: Boolean = false,
    val organizerNote: String? = null,
) {
    internal val isEmpty: Boolean
        get() = !restrictedView && !obstructedView && !premium &&
            organizerNote.isNullOrBlank()
}

/**
 * Every row a seat's attributes earn, in reading order.
 *
 * The order is fixed and is not discovery order: what the seat provides first
 * (accommodations, then the physical wheelchair fact), then what a buyer should
 * know before paying (restricted, obstructed, premium), then the organizer's
 * own words. Two seats with the same attributes always produce the same list.
 *
 * Restricted and obstructed are separate rows on purpose: collapsing them lets
 * one hide the other. A wheelchair accommodation with a provision yields the
 * provision row instead, because "empty wheelchair space" says everything
 * "wheelchair space" would, more precisely.
 */
public fun seatLayerPickerSeatNotes(
    accessibility: List<String> = emptyList(),
    wheelchairSpaceType: String? = null,
    commercial: SeatLayerPickerSeatCommercial? = null,
): List<SeatLayerPickerSeatNote> {
    val rows = mutableListOf<SeatLayerPickerSeatNote>()
    accessibility.forEach { type ->
        if (type == WHEELCHAIR && wheelchairSpaceType != null) return@forEach
        if (type !in SEATLAYER_PICKER_ACCESS_NEED_KEYS) return@forEach
        rows += SeatLayerPickerSeatNote(
            key = "access:$type",
            iconKey = type,
            labelKey = type,
            tone = SeatLayerPickerSeatNoteTone.Access,
        )
    }
    when (wheelchairSpaceType) {
        "no-seat" -> rows += SeatLayerPickerSeatNote(
            key = "wheelchair:no-seat",
            iconKey = WHEELCHAIR,
            labelKey = "emptyWheelchairSpace",
            tone = SeatLayerPickerSeatNoteTone.Access,
        )
        "seat-present" -> rows += SeatLayerPickerSeatNote(
            key = "wheelchair:seat-present",
            iconKey = WHEELCHAIR,
            labelKey = "accessiblePhysicalSeat",
            tone = SeatLayerPickerSeatNoteTone.Access,
        )
    }
    if (commercial?.restrictedView == true) {
        rows += SeatLayerPickerSeatNote(
            key = "mark:restrictedView",
            iconKey = "restrictedView",
            labelKey = "restrictedView",
            tone = SeatLayerPickerSeatNoteTone.Warn,
        )
    }
    if (commercial?.obstructedView == true) {
        rows += SeatLayerPickerSeatNote(
            key = "mark:obstructedView",
            iconKey = "obstructedView",
            labelKey = "obstructedView",
            tone = SeatLayerPickerSeatNoteTone.Warn,
        )
    }
    if (commercial?.premium == true) {
        rows += SeatLayerPickerSeatNote(
            key = "mark:premium",
            iconKey = "premium",
            labelKey = "premiumSeat",
            tone = SeatLayerPickerSeatNoteTone.Premium,
        )
    }
    val note = commercial?.organizerNote?.trim()?.takeIf(String::isNotEmpty) ?: return rows
    // The sentence belongs to the first selling mark on the seat: an organizer
    // writing "pillar at the aisle end" is explaining the restriction, not
    // adding a second unrelated fact. With no mark to explain, it is its own row.
    val owner = rows.indexOfFirst {
        it.tone == SeatLayerPickerSeatNoteTone.Warn ||
            it.tone == SeatLayerPickerSeatNoteTone.Premium
    }
    if (owner >= 0) {
        rows[owner] = rows[owner].copy(note = note)
        return rows
    }
    rows += SeatLayerPickerSeatNote(
        key = "note",
        iconKey = "note",
        labelKey = "organizerNote",
        tone = SeatLayerPickerSeatNoteTone.Note,
        note = note,
    )
    return rows
}

/** The rows one selected seat earns, read from the runtime's own fields. */
public fun SeatLayerPickerSelectedSeat.seatNotes(): List<SeatLayerPickerSeatNote> =
    seatLayerPickerSeatNotes(accessibility, wheelchairSpaceType, commercial)

/** The twelve accommodations the runtime reports, in the taxonomy's order. */
public val SEATLAYER_PICKER_ACCESS_NEED_KEYS: List<String> = listOf(
    "wheelchair",
    "companion",
    "semi-ambulatory",
    "designated-aisle",
    "step-free",
    "hearing",
    "cart",
    "sign-language",
    "low-vision",
    "sensory-friendly",
    "plus-size",
    "lift-armrest",
)

private const val WHEELCHAIR = "wheelchair"
