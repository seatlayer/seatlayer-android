package io.seatlayer.android

public data class SeatLayerPickerTicketIdentity(
    val lineKey: String?,
    val removalLabel: String?,
    val objectId: String?,
    val seatId: String?,
)

public data class SeatLayerPickerCartTotals(
    val quantity: Int,
    val total: Double,
    val currency: String?,
    val hasMixedCurrencies: Boolean,
)

public data class SeatLayerPickerConfirmedCartProjection(
    val items: List<SeatLayerPickerCartLine>,
    val totals: SeatLayerPickerCartTotals,
)

public data class SeatLayerPickerRemovalUndo(
    val line: SeatLayerPickerCartLine,
    val sessionId: String,
    val phase: SeatLayerPickerRemovalPhase,
)

@JvmInline
public value class SeatLayerPickerRemovalPhase(public val raw: String) {
    public companion object {
        public val AwaitingRemove: SeatLayerPickerRemovalPhase =
            SeatLayerPickerRemovalPhase("awaitingRemove")
        public val UndoWindow: SeatLayerPickerRemovalPhase =
            SeatLayerPickerRemovalPhase("undoWindow")
    }
}

public sealed interface SeatLayerPickerPrompt {
    public data class GeneralAdmission(
        val area: SeatLayerPickerGeneralAdmissionArea,
    ) : SeatLayerPickerPrompt

    public data class Table(
        val seat: SeatLayerPickerSelectedSeat,
    ) : SeatLayerPickerPrompt
}

@JvmInline
public value class SeatLayerPickerBackStep(public val raw: String) {
    public companion object {
        public val Prompt: SeatLayerPickerBackStep = SeatLayerPickerBackStep("prompt")
        public val Cart: SeatLayerPickerBackStep = SeatLayerPickerBackStep("cart")
        /** Panorama close never changes selection; native Back uses it when advertised. */
        public val Panorama: SeatLayerPickerBackStep = SeatLayerPickerBackStep("panorama")
        public val Venue3D: SeatLayerPickerBackStep = SeatLayerPickerBackStep("venue3d")
        public val Confirmation: SeatLayerPickerBackStep = SeatLayerPickerBackStep("confirmation")
        public val Section: SeatLayerPickerBackStep = SeatLayerPickerBackStep("section")
        /** Retained for source compatibility with the older full-overview ladder. */
        public val Venue: SeatLayerPickerBackStep = SeatLayerPickerBackStep("venue")
        public val Close: SeatLayerPickerBackStep = SeatLayerPickerBackStep("close")
    }
}

public data class SeatLayerPickerPresentationState(
    /**
     * The seat a card is asking about.
     *
     * It is a candidate, not a purchase: it is in no cart line the buyer sees,
     * in no ticket count and in no total until the card is answered.
     */
    val candidateSeat: SeatLayerPickerSelectedSeat? = null,
    val pendingTable: SeatLayerPickerSelectedSeat? = null,
    val activePrompt: SeatLayerPickerPrompt? = null,
    val actionInFlight: Boolean = false,
    val checkoutHandoff: SeatLayerPickerCheckoutHandoff? = null,
    val lastActionError: SeatLayerException? = null,
    val cartExpanded: Boolean = false,
    val removalUndo: SeatLayerPickerRemovalUndo? = null,
    /** Which question the card over [candidateSeat] is asking. */
    val cardIntent: SeatLayerPickerCardIntent = SeatLayerPickerCardIntent.Add,
    val cartLanding: SeatLayerPickerCartLanding = SeatLayerPickerCartLanding.Idle,
    val seatLift: SeatLayerPickerSeatLift? = null,
    /** The chrome rectangles the map must not answer taps through. */
    val blockedRegions: List<SeatLayerPickerBlockedRegion> = emptyList(),
    /** True while a checkout the host took is expected to come back. */
    val resumeAfterCheckout: Boolean = false,
    /**
     * The handoff whose hold left without expiring.
     *
     * A hold the host took to checkout and that then vanishes without an expiry
     * is a sale, not a timeout. Reading it as a timeout tells a buyer who has
     * just paid that their seats were released.
     */
    val bookedHandoff: SeatLayerPickerCheckoutHandoff? = null,
) {
    /** The seat a card is asking about. */
    @Deprecated("Renamed to candidateSeat.", ReplaceWith("candidateSeat"))
    public val pendingSeat: SeatLayerPickerSelectedSeat?
        get() = candidateSeat

    /** The seat a Remove card is asking about, if that is the question. */
    public val seatAwaitingRemoval: SeatLayerPickerSelectedSeat?
        get() = candidateSeat.takeIf { cardIntent == SeatLayerPickerCardIntent.Remove }

    public val sheetDetent: SeatLayerPickerSheetDetent
        get() = if (cartExpanded) {
            SeatLayerPickerSheetDetent.Open
        } else {
            SeatLayerPickerSheetDetent.Collapsed
        }

    public fun confirmedCart(snapshot: SeatLayerPickerSnapshot?):
        SeatLayerPickerConfirmedCartProjection = SeatLayerPickerProjections.confirmedCart(
        items = snapshot?.cartLines.orEmpty(),
        pending = candidateSeat.takeIf { cardIntent == SeatLayerPickerCardIntent.Add },
    )

    /**
     * Whether a checkout the host took still stands between the buyer and
     * their cart.
     *
     * Nothing inside the picker ever answers a handoff the HOST carried away,
     * so the handoff and the hold it took would otherwise gate the cart for
     * the rest of the session. `resumeAfterCheckout()` is the host saying the
     * buyer is back on the map, and it is the only thing that lifts them:
     * without it Continue is dead on return, and a seat tapped afterwards
     * cannot be acted on.
     */
    public val checkoutHoldsTheCart: Boolean
        get() = checkoutHandoff != null && !resumeAfterCheckout

    /**
     * Whether the picker may still add to, or take from, the cart.
     *
     * A hold the host owns is not the picker's to change — until the host has
     * said the checkout came back.
     */
    public fun canMutateCart(snapshot: SeatLayerPickerSnapshot?): Boolean =
        !checkoutHoldsTheCart &&
            (snapshot?.hold?.owner != "host" || resumeAfterCheckout)

    public fun canCheckout(
        snapshot: SeatLayerPickerSnapshot?,
        ready: Boolean,
        readOnly: Boolean,
    ): Boolean = ready &&
        !readOnly &&
        // Deliberately not gated on actionInFlight: adding a seat to a live
        // hold puts a command in flight, and greying Continue through it tells
        // the buyer their tickets stopped being buyable.
        canMutateCart(snapshot) &&
        activePrompt == null &&
        candidateSeat == null &&
        confirmedCart(snapshot).items.isNotEmpty() &&
        snapshot?.selectionValidity?.isValid != false

    public fun nextBackStep(
        snapshot: SeatLayerPickerSnapshot?,
        seatView: SeatLayerSeatView? = null,
    ): SeatLayerPickerBackStep =
        when {
            activePrompt != null -> SeatLayerPickerBackStep.Prompt
            cartExpanded -> SeatLayerPickerBackStep.Cart
            seatView != null -> SeatLayerPickerBackStep.Panorama
            snapshot?.map?.buyerView == "venue3d" -> SeatLayerPickerBackStep.Venue3D
            candidateSeat != null -> SeatLayerPickerBackStep.Confirmation
            snapshot?.map?.buyerView != null && snapshot.map.buyerView != "map" ->
                SeatLayerPickerBackStep.Venue
            snapshot?.map?.canZoomOut == true &&
                (
                    snapshot.map.focusedSectionId != null ||
                        snapshot.map.focusedSection != null ||
                        snapshot.hasNavigableVenueEvidence()
                    ) ->
                SeatLayerPickerBackStep.Section
            else -> SeatLayerPickerBackStep.Close
        }

    public fun canUndoRemoval(snapshot: SeatLayerPickerSnapshot?): Boolean {
        val undo = removalUndo ?: return false
        val sameSession = snapshot?.sessionId == undo.sessionId
        // Per TICKET, not per line key: `lineKey` is row-scoped, so the row a
        // removed seat came from is still in the cart while its neighbour is,
        // and matching on it says the seat came back when it did not.
        val stillAbsent = snapshot?.cartLines.orEmpty().none { line ->
            SeatLayerPickerProjections.sameTicket(line, undo.line)
        }
        return SeatLayerPickerProjections.canUndoRemoval(
            phase = undo.phase,
            sameSession = sameSession,
            stillAbsent = stillAbsent,
        )
    }
}

private fun SeatLayerPickerSnapshot.hasNavigableVenueEvidence(): Boolean =
    categories.isNotEmpty() ||
        zones.isNotEmpty() ||
        sections.isNotEmpty() ||
        generalAdmissionAreas.isNotEmpty() ||
        map.floors.isNotEmpty()

/** Pure cart/selection projections shared by ready-made and custom UIs. */
public object SeatLayerPickerProjections {
    public fun ticketIdentity(
        line: SeatLayerPickerCartLine,
    ): SeatLayerPickerTicketIdentity = SeatLayerPickerTicketIdentity(
        lineKey = line.lineKey.nonBlank(),
        removalLabel = line.label.nonBlank(),
        objectId = line.objectId.nonBlank(),
        seatId = line.seatId?.nonBlank(),
    )

    /**
     * A key that names ONE cart line.
     *
     * The runtime's own `lineKey` is ROW-scoped: both seats of row `102-A`
     * arrive under a single `row_…` key. A list keyed on it draws the first
     * line twice and never draws the second, so the seat is the identity
     * wherever the line names one, and the line's own address together with
     * its position in the list wherever it does not.
     */
    public fun cartLineKey(line: SeatLayerPickerCartLine, index: Int): String {
        line.seatId?.nonBlank()?.let { return it }
        val address = listOfNotNull(
            line.sectionLabel?.nonBlank(),
            line.rowLabel?.nonBlank(),
            line.seatNumber?.nonBlank(),
            line.label.nonBlank(),
        ).joinToString(separator = "/")
        val base = address.ifEmpty { line.lineKey }
        return "$base#$index"
    }

    /**
     * Whether two cart lines stand for the same ticket.
     *
     * The seat decides it wherever either line names one. Only where neither
     * does — a best-available result, a resumed hold — does the address, and
     * finally the row-scoped key, get a say.
     */
    public fun sameTicket(
        first: SeatLayerPickerCartLine,
        second: SeatLayerPickerCartLine,
    ): Boolean {
        val left = ticketIdentity(first)
        val right = ticketIdentity(second)
        if (left.seatId != null || right.seatId != null) return left.seatId == right.seatId
        if (left.removalLabel != null || right.removalLabel != null) {
            return left.removalLabel == right.removalLabel &&
                first.seatNumber?.nonBlank() == second.seatNumber?.nonBlank()
        }
        return left.lineKey == right.lineKey
    }

    public fun confirmedCart(
        items: List<SeatLayerPickerCartLine>,
        pending: SeatLayerPickerSelectedSeat?,
    ): SeatLayerPickerConfirmedCartProjection {
        val kept = if (pending == null) {
            items
        } else {
            val pendingId = pending.id.nonBlank()
            val pendingLabel = pending.label.nonBlank()
            items.filter { line ->
                val identity = ticketIdentity(line)
                if (identity.seatId == null) {
                    identity.removalLabel != pendingLabel
                } else {
                    identity.seatId != pendingId
                }
            }
        }
        return SeatLayerPickerConfirmedCartProjection(kept, totals(kept))
    }

    public fun totals(items: List<SeatLayerPickerCartLine>): SeatLayerPickerCartTotals {
        val currencies = items.mapNotNull { it.currency.nonBlank() }.toSet()
        return SeatLayerPickerCartTotals(
            quantity = items.sumOf { it.quantity.coerceAtLeast(1) },
            total = items.sumOf { it.unitPrice * it.quantity.coerceAtLeast(1) },
            currency = currencies.singleOrNull(),
            hasMixedCurrencies = currencies.size > 1,
        )
    }

    public fun canUndoRemoval(
        phase: SeatLayerPickerRemovalPhase,
        sameSession: Boolean,
        stillAbsent: Boolean,
    ): Boolean = phase == SeatLayerPickerRemovalPhase.UndoWindow &&
        sameSession &&
        stillAbsent

    /** Empty UI requires affirmative runtime evidence; missing data stays unknown. */
    public fun isProvenEmpty(snapshot: SeatLayerPickerSnapshot?): Boolean {
        snapshot ?: return false
        if (snapshot.event.salesClosed) return true
        val categoryEvidence = snapshot.categories.isNotEmpty()
        val gaEvidence = snapshot.generalAdmissionAreas.isNotEmpty()
        if (!categoryEvidence && !gaEvidence) return false
        return snapshot.categories.all { it.notForSale || it.available <= 0 } &&
            snapshot.generalAdmissionAreas.all { (it.available ?: 1) <= 0 }
    }

    public fun seatIdentity(seat: SeatLayerPickerSelectedSeat): String = listOf(
        seat.id.nonBlank(),
        seat.label.nonBlank(),
        seat.objectId?.nonBlank(),
    ).joinToString(separator = ",", prefix = "[", postfix = "]") { value ->
        value?.let { "\"${it.escapeJsonIdentity()}\"" } ?: "null"
    }

    public fun seatRunLabel(labels: List<String>): String {
        if (labels.isEmpty()) return ""
        if (labels.size == 1) return labels.single()
        val numbers = labels.map { it.trim().takeIf { raw -> raw.matches(SEAT_NUMBER) }?.toInt() }
        if (numbers.all { it != null }) {
            val sorted = numbers.filterNotNull().sorted()
            val consecutive = sorted.withIndex().all { (index, number) ->
                index == 0 || number == sorted[index - 1] + 1
            }
            if (consecutive) return "${sorted.first()}–${sorted.last()}"
            return compact(sorted.map(Int::toString))
        }
        return compact(labels)
    }

    private fun compact(labels: List<String>): String {
        val shown = labels.take(3).joinToString()
        return if (labels.size > 3) "$shown +${labels.size - 3}" else shown
    }

    private val SEAT_NUMBER = Regex("^[0-9]{1,4}$")

}

private fun String.nonBlank(): String? = trim().takeIf(String::isNotEmpty)

private fun String.escapeJsonIdentity(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
