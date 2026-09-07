package io.seatlayer.android

import java.util.concurrent.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Inventory: general admission, best available, the hold behind the buyer's
 * seats, and the one command that turns that hold into a checkout handoff.
 */

internal suspend fun SeatLayerPickerController.performSetGeneralAdmissionQuantity(
    areaId: String,
    quantity: Int,
    tierId: String?,
    ttlMillis: Int?,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValue(areaId, "areaId")
    requirePositiveValue(quantity, "quantity")
    tierId?.let { requireNonEmptyValue(it, "tierId") }
    ttlMillis?.let { requirePositiveValue(it, "ttlMillis") }
    val updated = mutation(
        "picker.holdGA",
        jsonObject(
            "areaId" to JsonPrimitive(areaId),
            "qty" to JsonPrimitive(quantity),
            "tierId" to jsonString(tierId),
            "ttlMs" to jsonNumber(ttlMillis),
        ),
    )
    dismissGeneralAdmissionCandidate()
    return updated
}

internal suspend fun SeatLayerPickerController.performHoldSelection(
    ttlMillis: Int?,
): SeatLayerPickerSnapshot? {
    ttlMillis?.let { requirePositiveValue(it, "ttlMillis") }
    if (!supportsHoldSelection) return snapshot
    return mutation("picker.holdSelection", jsonObject("ttlMs" to jsonNumber(ttlMillis)))
}

internal suspend fun SeatLayerPickerController.performBestAvailable(
    quantity: Int,
    categoryKey: String?,
    zoneId: String?,
    preferPremium: Boolean,
    ttlMillis: Int?,
): SeatLayerPickerSnapshot? {
    requirePositiveValue(quantity, "quantity")
    categoryKey?.let { requireNonEmptyValue(it, "categoryKey") }
    zoneId?.let { requireNonEmptyValue(it, "zoneId") }
    ttlMillis?.let { requirePositiveValue(it, "ttlMillis") }
    return mutation(
        "picker.bestAvailable",
        jsonObject(
            "qty" to JsonPrimitive(quantity),
            "categoryKey" to jsonString(categoryKey),
            "zoneId" to jsonString(zoneId),
            "preferPremium" to JsonPrimitive(preferPremium),
            "ttlMs" to jsonNumber(ttlMillis),
        ),
    )
}

internal suspend fun SeatLayerPickerController.performResumeHold(
    holdId: String,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValue(holdId, "holdId")
    return mutation("picker.resumeHold", jsonObject("holdId" to JsonPrimitive(holdId)))
}

internal suspend fun SeatLayerPickerController.performExtendHold(
    ttlMillis: Int?,
): SeatLayerPickerSnapshot? {
    ttlMillis?.let { requirePositiveValue(it, "ttlMillis") }
    return mutation("picker.extendHold", jsonObject("ttlMs" to jsonNumber(ttlMillis)))
}

internal suspend fun SeatLayerPickerController.performRejectHandoff(
    holdId: String,
): SeatLayerPickerSnapshot? {
    requireNonEmptyValue(holdId, "holdId")
    return mutation("picker.rejectHandoff", jsonObject("holdId" to JsonPrimitive(holdId)))
}

/**
 * Turns the buyer's seats into a checkout handoff.
 *
 * A runtime that already treats the live hold as checkout's refuses a selection
 * the hold does not cover — which is every seat added after the hold was made.
 * When it does, the chart's own `hold` command is sent once: it carries every
 * seat already held along with the new ones, so no seat list is invented here.
 * The checkout is then asked for again. The observable order is
 * `picker.continue → hold → picker.continue`, and the buyer sees no error.
 */
internal suspend fun SeatLayerPickerController.performCheckout(
    ttlMillis: Int?,
): SeatLayerPickerCheckoutHandoff {
    // `ttlMs` travels only when a host set a hold length; the server's own
    // window applies otherwise, and the SDK never names a number.
    val ttl = jsonObject("ttlMs" to jsonNumber(ttlMillis))
    val result = try {
        command("picker.continue", ttl)
    } catch (error: SeatLayerException.Bridge) {
        if (error.code != SeatLayerPickerErrorCodes.HOLD_SELECTION_MISMATCH) throw error
        // Without the chart's own hold there is nothing to replace it with, and
        // the buyer is better served by the runtime's own refusal than by a
        // second, vaguer one.
        if (!supportsCommand("hold")) throw error
        command("hold", ttl)
        command("picker.continue", ttl)
    }
    applyMutationResult(result)
    return decodeSeatLayerPickerCheckoutHandoff((result as? JsonObject)?.get("handoff"))
        ?: throw pickerDecodingFailure("picker.continue returned no checkout handoff")
}

internal suspend fun SeatLayerPickerController.performRefreshAccess():
    SeatLayerPickerLifecycleResult? {
    if (!supportsAvailabilityRefresh) return null
    return try {
        lifecycleMutation("picker.refreshAvailability")
    } catch (error: CancellationException) {
        throw error
    } catch (error: SeatLayerException) {
        recordPickerError(error)
        null
    } catch (error: Throwable) {
        recordPickerError(
            SeatLayerException.Transport("SeatLayer availability refresh failed.", error),
        )
        null
    }
}

internal suspend fun SeatLayerPickerController.performLifecycle(
    state: String,
): SeatLayerPickerLifecycleResult? {
    requireNonEmptyValue(state, "state")
    val runtimeState = if (state == "resumed" || state == "foreground") {
        "foreground"
    } else {
        "background"
    }
    val payload = jsonObject("state" to JsonPrimitive(runtimeState))
    if (runtimeState == "background" || !refreshesOnResume) {
        return lifecycleMutation("picker.lifecycle", payload)
    }

    setActionInFlight(true)
    return try {
        val initial = lifecycleMutation("picker.lifecycle", payload)
        val refreshed = if (initial?.outcome == null && supportsAvailabilityRefresh) {
            refreshAccess()
        } else {
            null
        }
        SeatLayerPickerLifecycleResult(
            snapshot = refreshed?.snapshot ?: initial?.snapshot ?: synchronize(),
            outcome = refreshed?.outcome ?: initial?.outcome,
        )
    } finally {
        setActionInFlight(false)
    }
}

internal suspend fun SeatLayerPickerController.performReselectLapsedSeats(
    ttlMillis: Int?,
): SeatLayerPickerSnapshot? {
    val lapse = holdLapse ?: return snapshot
    if (lapse.recoverableLabels.isEmpty()) return snapshot
    selectObjects(lapse.recoverableLabels)
    dismissHoldLapse()
    if (supportsHoldSelection) holdSelection(ttlMillis)
    return snapshot
}
