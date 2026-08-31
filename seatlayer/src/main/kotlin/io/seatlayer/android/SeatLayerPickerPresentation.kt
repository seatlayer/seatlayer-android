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

public data class SeatLayerPickerDenseRun(
    /** Source order remains available for stable UI identity and removal. */
    val memberLineKeys: List<String>,
    /** Display order follows the seat numbers used by [seatsLabel]. */
    val orderedMemberLineKeys: List<String>,
    val items: List<SeatLayerPickerCartLine>,
    val seatsLabel: String,
    val quantity: Int,
    val total: Double,
    val currency: String?,
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
    val pendingSeat: SeatLayerPickerSelectedSeat? = null,
    val pendingTable: SeatLayerPickerSelectedSeat? = null,
    val activePrompt: SeatLayerPickerPrompt? = null,
    val actionInFlight: Boolean = false,
    val checkoutHandoff: SeatLayerPickerCheckoutHandoff? = null,
    val lastActionError: SeatLayerException? = null,
    val cartExpanded: Boolean = false,
    val removalUndo: SeatLayerPickerRemovalUndo? = null,
) {
    public fun confirmedCart(snapshot: SeatLayerPickerSnapshot?):
        SeatLayerPickerConfirmedCartProjection = SeatLayerPickerProjections.confirmedCart(
        items = snapshot?.cartLines.orEmpty(),
        pending = pendingSeat,
    )

    public fun canCheckout(
        snapshot: SeatLayerPickerSnapshot?,
        ready: Boolean,
        readOnly: Boolean,
    ): Boolean = ready &&
        !readOnly &&
        !actionInFlight &&
        checkoutHandoff == null &&
        activePrompt == null &&
        pendingSeat == null &&
        confirmedCart(snapshot).items.isNotEmpty() &&
        snapshot?.hold?.owner != "host" &&
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
            pendingSeat != null -> SeatLayerPickerBackStep.Confirmation
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
        val identity = SeatLayerPickerProjections.ticketIdentity(undo.line)
        val stillAbsent = snapshot?.cartLines.orEmpty().none { line ->
            val candidate = SeatLayerPickerProjections.ticketIdentity(line)
            when {
                identity.lineKey != null -> candidate.lineKey == identity.lineKey
                identity.seatId != null -> candidate.seatId == identity.seatId
                else -> candidate.removalLabel == identity.removalLabel
            }
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

    /**
     * Groups only structurally equivalent addressed seats. Tables, GA lines,
     * tier-controlled lines and multi-quantity lines remain individually
     * actionable, so dense display never changes buyer semantics.
     */
    public fun denseRuns(
        items: List<SeatLayerPickerCartLine>,
    ): List<SeatLayerPickerDenseRun> {
        val groups = linkedMapOf<DenseRunKey, MutableList<SeatLayerPickerCartLine>>()
        items.forEach { line ->
            val foldable = line.objectType == "seat" &&
                line.quantity == 1 &&
                line.tierId == null &&
                line.seatNumber?.isNotBlank() == true
            val key = if (foldable) {
                DenseRunKey(
                    section = line.sectionLabel,
                    row = line.rowLabel,
                    category = line.categoryKey,
                    unitPrice = line.unitPrice,
                    currency = line.currency,
                    unique = null,
                )
            } else {
                DenseRunKey(
                    section = line.sectionLabel,
                    row = line.rowLabel,
                    category = line.categoryKey,
                    unitPrice = line.unitPrice,
                    currency = line.currency,
                    unique = line.lineKey,
                )
            }
            groups.getOrPut(key) { mutableListOf() } += line
        }
        return groups.values.map { members ->
            val ordered = members.sortedWith(SEAT_LINE_ORDER)
            val totals = totals(members)
            SeatLayerPickerDenseRun(
                memberLineKeys = members.map(SeatLayerPickerCartLine::lineKey),
                orderedMemberLineKeys = ordered.map(SeatLayerPickerCartLine::lineKey),
                items = ordered,
                seatsLabel = seatRunLabel(ordered.map { it.seatNumber ?: it.label }),
                quantity = totals.quantity,
                total = totals.total,
                currency = totals.currency,
            )
        }
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

    private val SEAT_LINE_ORDER = compareBy<SeatLayerPickerCartLine>(
        { it.seatNumber?.trim()?.toIntOrNull() ?: Int.MAX_VALUE },
        { it.seatNumber.orEmpty() },
        { it.lineKey },
    )
}

private data class DenseRunKey(
    val section: String?,
    val row: String?,
    val category: String,
    val unitPrice: Double,
    val currency: String,
    val unique: String?,
)

private fun String.nonBlank(): String? = trim().takeIf(String::isNotEmpty)

private fun String.escapeJsonIdentity(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
