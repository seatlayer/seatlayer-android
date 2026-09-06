package io.seatlayer.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Typed headless API for one protocol-2 picker.
 *
 * Every public entry lives here, so a host reading the class reads the whole
 * offer in one place; the work behind each one lives in `PickerController*.kt`
 * beside this file, grouped by what the buyer is doing — picking, holding,
 * looking, or filtering.
 *
 * Inventory mutations are serialized, optional operations are gated before a
 * bridge message is sent, and overlapping checkout calls share one handoff.
 */
public class SeatLayerPickerController internal constructor(
    internal val stateHolder: SeatLayerPickerStateHolder,
    private val revisionWaitMillis: Long = 2_000,
) {
    private val mutationMutex = Mutex()
    private val checkoutLock = Any()
    private val availabilityLock = Any()
    private val presentationCoordinator =
        SeatLayerPickerPresentationCoordinator(stateHolder, this)

    @Volatile
    private var transport: SeatLayerPickerCommandTransport? = null

    @Volatile
    private var bundleInfo: BundleInfo? = null

    private var checkoutFlight: CompletableDeferred<SeatLayerPickerCheckoutHandoff>? = null
    private var availabilityFlight: CompletableDeferred<SeatLayerPickerLifecycleResult?>? = null

    public val snapshot: SeatLayerPickerSnapshot?
        get() = stateHolder.state.value.snapshot

    /** Capability-gated runtime chart-load attempts, without SDK transmission. */
    public val chartLoads: SharedFlow<SeatLayerChartLoad>
        get() = stateHolder.chartLoads

    public val isReady: Boolean
        get() = stateHolder.state.value.isReady

    public val presentation: SeatLayerPickerPresentationState
        get() = stateHolder.state.value.presentation

    public val canCheckout: Boolean
        get() = presentation.canCheckout(
            snapshot = snapshot,
            ready = isReady,
            readOnly = stateHolder.behavior.readOnly,
        )

    public val nextBackStep: SeatLayerPickerBackStep
        get() = presentation.nextBackStep(snapshot, stateHolder.state.value.seatView)

    public val supportsFloorStack: Boolean
        get() = supportsCapabilityAndCommand("floor-stack-v1", "picker.setFloor")

    public val supportsViewportInsets: Boolean
        get() = supportsCapabilityAndCommand("viewport-insets-v1", "picker.setViewportInsets")

    public val supportsVenue3D: Boolean
        get() = stateHolder.behavior.enableVenue3D &&
            supportsCapabilityAndCommand("venue-3d-v1", "picker.setBuyerView")

    public val supportsSeatView: Boolean
        get() = stateHolder.behavior.enableSeatView &&
            supportsCapabilityAndCommand("seat-view-v1", "picker.openSeatView")

    /** Whether this runtime lets native chrome close the active panorama. */
    public val supportsSeatViewClose: Boolean
        get() = supportsSeatView && supportsCommand("picker.closeSeatView")

    public val supportsNativeSeatViewChrome: Boolean
        get() = stateHolder.behavior.enableSeatView &&
            supportsCapability("native-seat-view-chrome-v1") &&
            supportsEvent("seatView.changed")

    public val supportsAvailabilityRefresh: Boolean
        get() = supportsCapabilityAndCommand(
            "availability-refresh-v1",
            "picker.refreshAvailability",
        )

    public val supportsHoldSelection: Boolean
        get() = supportsCapabilityAndCommand("hold-selection-v1", "picker.holdSelection")

    /** Whether the runtime reports the accommodations this event actually has. */
    public val supportsAccessNeeds: Boolean
        get() = supportsCapabilityAndCommand("access-needs-v1", "picker.setAccessibilityFilter")

    /** Whether a filter can fly the map, and the accessible-section tour exists. */
    public val supportsAccessibilityFocus: Boolean
        get() = isReady && supportsCapability("accessibility-focus-v1")

    /** Whether sections report how many accessible spaces are left. */
    public val supportsSectionAccessCounts: Boolean
        get() = isReady && supportsCapability("section-access-counts-v1")

    /** Whether a selected seat may carry a photograph of the view from it. */
    public val supportsSeatViewThumbnail: Boolean
        get() = stateHolder.behavior.enableSeatView &&
            isReady &&
            supportsCapability("seat-view-thumbnail-v1")

    public fun supportsCapability(capability: String): Boolean =
        bundleInfo?.supportsCapability(capability) == true

    public fun supportsCommand(command: String): Boolean =
        bundleInfo?.supportsCommand(command) == true

    public fun supportsEvent(event: String): Boolean =
        event in bundleInfo?.events.orEmpty()

    public suspend fun synchronize(): SeatLayerPickerSnapshot? =
        mutation("picker.getSnapshot")

    public suspend fun selectObjects(objects: List<String>): SeatLayerPickerSnapshot? =
        performSelectObjects(objects)

    public suspend fun deselectObjects(objects: List<String>): SeatLayerPickerSnapshot? =
        performDeselectObjects(objects)

    public suspend fun clearSelection(): SeatLayerPickerSnapshot? =
        mutation("picker.clearSelection")

    public suspend fun selectCategories(
        categoryKeys: List<String>,
    ): SeatLayerPickerSnapshot? = performSelectCategories(categoryKeys)

    public suspend fun deselectCategories(
        categoryKeys: List<String>,
    ): SeatLayerPickerSnapshot? = performDeselectCategories(categoryKeys)

    public suspend fun setSeatTier(
        seatId: String,
        tierId: String?,
    ): SeatLayerPickerSnapshot? = performSetSeatTier(seatId, tierId)

    public suspend fun removeCartLine(label: String): SeatLayerPickerSnapshot? =
        performRemoveObject(label)

    public suspend fun setTableQuantity(
        label: String,
        quantity: Int,
        ttlMillis: Int? = null,
    ): SeatLayerPickerSnapshot? = performSetTableQuantity(label, quantity, ttlMillis)

    public suspend fun setSelectableObjects(
        objects: List<String>?,
    ): SeatLayerPickerSnapshot? = performSetSelectableObjects(objects)

    public suspend fun setMaxSelection(maximum: Int): SeatLayerPickerSnapshot? =
        performSetMaxSelection(maximum)

    public suspend fun setCategoryFilter(
        categoryKeys: List<String>,
        focus: Boolean = false,
    ): SeatLayerPickerSnapshot? = performSetCategoryFilter(categoryKeys, focus)

    public suspend fun setAccessibilityFilter(
        types: List<String>,
    ): SeatLayerPickerSnapshot? = performSetAccessibilityFilter(types)

    public suspend fun setLimitedViewFilter(enabled: Boolean): SeatLayerPickerSnapshot? =
        performSetLimitedViewHidden(enabled)

    /**
     * Flies the map to the spaces the accommodation filter matches.
     *
     * A runtime that does not offer the tour answers nothing and the map stays
     * where it is.
     */
    public suspend fun focusAccessibilityFilter(
        types: List<String>? = null,
    ): SeatLayerPickerSnapshot? = performFocusAccessibilityFilter(types)

    /** Steps to the next section holding a matching space, or null if none. */
    public suspend fun focusNextAccessibleSection(
        types: List<String>? = null,
    ): SeatLayerPickerAccessibleStep? = performFocusNextAccessibleSection(types)

    /** Marks the seat a card is asking about; it joins no cart and no total. */
    public suspend fun setSelectionFocus(seatId: String?): Unit =
        performSetSelectionFocus(seatId)

    /** Clears the candidate mark left by [setSelectionFocus]. */
    public suspend fun forgetSelectionFocus(): Unit = performSetSelectionFocus(null)

    /**
     * Replaces the rectangles the map must not answer taps through.
     *
     * Every call carries the whole list, and the list has to be standing before
     * the finger lands, so it is re-sent whenever the layout moves.
     */
    public suspend fun setBlockedRegions(
        regions: List<SeatLayerPickerBlockedRegion>?,
    ) {
        stateHolder.setBlockedRegions(regions.orEmpty())
        performSetBlockedRegions(regions)
    }

    /**
     * Lifts the map so [seatId] clears the card standing over it.
     *
     * [fraction] is how far down the visible map the seat should land.
     * [gestures] is the value a previous frame returned; passing it back is
     * what stops the map from undoing the buyer's own drag.
     */
    public suspend fun frameSeat(
        seatId: String,
        fraction: Double? = null,
        animate: Boolean = true,
        gestures: SeatLayerPickerSeatGestures? = null,
    ): SeatLayerPickerSeatFrame? = performFrameSeat(seatId, fraction, animate, gestures)

    public suspend fun focusSection(sectionId: String): SeatLayerPickerSnapshot? =
        performFocusSection(sectionId)

    public suspend fun overview(): SeatLayerPickerSnapshot? = mutation("picker.overview")

    public suspend fun setRung(rung: String): SeatLayerPickerSnapshot? = performSetRung(rung)

    public suspend fun setFloor(floorId: String): SeatLayerPickerSnapshot? =
        performSetFloor(floorId)

    public suspend fun showAllFloors(): SeatLayerPickerSnapshot? =
        if (supportsFloorStack) setFloor(SEATLAYER_ALL_FLOORS) else snapshot

    public suspend fun setColorblindSafe(enabled: Boolean): SeatLayerPickerSnapshot? =
        performSetColorblindSafe(enabled)

    public suspend fun setViewMode(mode: SeatLayerViewMode): SeatLayerPickerSnapshot? =
        performSetViewMode(mode)

    public suspend fun setBuyerView(
        view: String,
        flyToSeatId: String? = null,
        resetView: Boolean = false,
    ): SeatLayerPickerSnapshot? = performSetBuyerView(view, flyToSeatId, resetView)

    /** Plans and dispatches one exact 3D action; unavailable boundaries are no-ops. */
    public suspend fun venue3D(
        action: SeatLayerPickerVenue3DAction,
    ): SeatLayerPickerSnapshot? {
        val plan = SeatLayerPickerImmersive.plan(action, snapshot) ?: return snapshot
        return setBuyerView(
            view = plan.view,
            flyToSeatId = plan.flyToSeatId,
            resetView = plan.resetView,
        )
    }

    public suspend fun openSeatView(seatId: String): SeatLayerPickerSnapshot? =
        performOpenSeatView(seatId)

    /**
     * Closes the active panorama when the loaded runtime advertises the
     * additive `picker.closeSeatView` command.
     *
     * Older runtimes remain supported: this returns `false` and sends no
     * command, leaving their runtime-owned close control authoritative.
     */
    public suspend fun closeSeatView(): Boolean {
        if (!supportsSeatViewClose) return false
        presentation("picker.closeSeatView")
        return true
    }

    public suspend fun setVenue3DNavigationMode(mode: String): SeatLayerPickerSnapshot? =
        performSet3DNavigationMode(mode)

    public suspend fun zoomIn() {
        mutation("picker.zoomIn")
    }

    public suspend fun zoomOut() {
        mutation("picker.zoomOut")
    }

    public suspend fun zoomToFit() {
        mutation("picker.zoomToFit")
    }

    public suspend fun setThemeMode(
        mode: SeatLayerPickerThemeMode?,
        mapTheme: SeatLayerPickerMapTheme? = null,
    ): Unit = performSetThemeMode(mode, mapTheme)

    public suspend fun setInteractionEnabled(enabled: Boolean) {
        presentation(
            "picker.setInteractionEnabled",
            jsonObject("enabled" to kotlinx.serialization.json.JsonPrimitive(enabled)),
        )
    }

    public suspend fun setViewportInsets(insets: SeatLayerPickerViewportInsets?): Unit =
        performSetViewportInsets(insets)

    public suspend fun holdGeneralAdmission(
        areaId: String,
        quantity: Int,
        tierId: String? = null,
        ttlMillis: Int? = null,
    ): SeatLayerPickerSnapshot? =
        performSetGeneralAdmissionQuantity(areaId, quantity, tierId, ttlMillis)

    public suspend fun holdSelection(ttlMillis: Int? = null): SeatLayerPickerSnapshot? =
        performHoldSelection(ttlMillis)

    public suspend fun bestAvailable(
        quantity: Int,
        categoryKey: String? = null,
        zoneId: String? = null,
        preferPremium: Boolean = false,
        ttlMillis: Int? = null,
    ): SeatLayerPickerSnapshot? =
        performBestAvailable(quantity, categoryKey, zoneId, preferPremium, ttlMillis)

    public suspend fun resumeHold(holdId: String): SeatLayerPickerSnapshot? =
        performResumeHold(holdId)

    public suspend fun extendHold(ttlMillis: Int? = null): SeatLayerPickerSnapshot? =
        performExtendHold(ttlMillis)

    public suspend fun abort(): SeatLayerPickerSnapshot? = mutation("picker.abort")

    public suspend fun rejectHandoff(holdId: String): SeatLayerPickerSnapshot? =
        performRejectHandoff(holdId)

    public suspend fun checkout(
        ttlMillis: Int? = null,
    ): SeatLayerPickerCheckoutHandoff {
        ttlMillis?.let { requirePositiveValue(it, "ttlMillis") }
        val (flight, leader) = synchronized(checkoutLock) {
            checkoutFlight?.let { it to false }
                ?: CompletableDeferred<SeatLayerPickerCheckoutHandoff>().also {
                    checkoutFlight = it
                }.let { it to true }
        }
        if (!leader) return flight.await()

        try {
            val handoff = mutationMutex.withLock { performCheckout(ttlMillis) }
            flight.complete(handoff)
            return handoff
        } catch (error: Throwable) {
            flight.completeExceptionally(error)
            throw error
        } finally {
            synchronized(checkoutLock) {
                if (checkoutFlight === flight) checkoutFlight = null
            }
        }
    }

    /** Delivers one checkout handoff; handler failure releases that exact hold. */
    public suspend fun handoffCheckout(
        handler: suspend (SeatLayerPickerCheckoutHandoff) -> Unit,
    ): SeatLayerPickerCheckoutHandoff = presentationCoordinator.handoffCheckout(handler)

    public fun confirmPending(): Unit = presentationCoordinator.confirmPending()

    /**
     * Applies a locally chosen tier before accepting the pending seat.
     * Failure leaves the confirmation open and returns false.
     */
    public suspend fun confirmPending(tierId: String?): Boolean =
        presentationCoordinator.confirmPending(tierId)

    public fun confirmPendingTable(): Unit = presentationCoordinator.confirmPendingTable()

    public fun setCartExpanded(expanded: Boolean): Unit =
        presentationCoordinator.setCartExpanded(expanded)

    /** Opens the sheet to a named detent rather than to a boolean. */
    public fun setCartSheetDetent(detent: SeatLayerPickerSheetDetent): Unit =
        setCartExpanded(detent == SeatLayerPickerSheetDetent.Open)

    /**
     * Advances the arrival of a seat in the cart.
     *
     * The steps are driven by the surfaces that know — the chip that lands, the
     * tray that finishes swelling — rather than by a clock that decides a
     * flight has happened before it has.
     */
    public fun setCartLanding(landing: SeatLayerPickerCartLanding): Unit =
        stateHolder.setCartLanding(landing)

    /** Opens the card over a seat and marks it as a candidate on the map. */
    public suspend fun openSeatCard(
        seat: SeatLayerPickerSelectedSeat,
        intent: SeatLayerPickerCardIntent = SeatLayerPickerCardIntent.Add,
    ): Unit = performOpenSeatCard(seat, intent)

    /** Closes the card, clears the candidate mark and puts the map back. */
    public suspend fun closeSeatCard(): Unit = performCloseSeatCard()

    /** The seat a Remove card is asking about, if one is up. */
    public val seatAwaitingRemoval: SeatLayerPickerSelectedSeat?
        get() = presentation.seatAwaitingRemoval

    /** Closes a Remove card without touching the seat. */
    public suspend fun dismissSeatRemoval() {
        if (presentation.cardIntent != SeatLayerPickerCardIntent.Remove) return
        performCloseSeatCard()
    }

    /** Lifts the map so a seat clears the card standing over it. */
    public suspend fun liftSeat(
        seatId: String,
        fraction: Double? = null,
    ): SeatLayerPickerSeatLift? = performLiftSeat(seatId, fraction)

    /** Gives back the lift the card borrowed. */
    public suspend fun restoreSeatLift(): Unit = performRestoreSeatLift()

    /** The handoff whose hold left without expiring: a sale, not a timeout. */
    public val bookedHandoff: SeatLayerPickerCheckoutHandoff?
        get() = presentation.bookedHandoff

    public fun dismissBookedOverlay(): Unit = stateHolder.clearBookedHandoff()

    /** Whether a checkout the host took is expected to come back to the map. */
    public fun setResumeAfterCheckout(resume: Boolean): Unit =
        stateHolder.setResumeAfterCheckout(resume)

    public fun dismissActionError(): Unit = presentationCoordinator.dismissActionError()

    /** Removes one authoritative cart line and opens a session-scoped undo window. */
    public suspend fun removeWithUndo(line: SeatLayerPickerCartLine): Boolean =
        presentationCoordinator.removeWithUndo(line)

    public suspend fun undoLastRemoval(): Boolean = presentationCoordinator.undoLastRemoval()

    public fun dismissRemovalUndo(): Unit = presentationCoordinator.dismissRemovalUndo()

    public suspend fun cancelPending(): Boolean = presentationCoordinator.cancelPending()

    public suspend fun cancelPendingTable(): Boolean =
        presentationCoordinator.cancelPendingTable()

    /** Releases picker-owned inventory, then invokes the host close callback once. */
    public suspend fun close(handler: suspend () -> Unit = {}): Unit =
        presentationCoordinator.close(handler)

    /** Consumes one layer of the shared native picker back ladder. */
    public suspend fun back(closeHandler: suspend () -> Unit = {}): SeatLayerPickerBackStep =
        presentationCoordinator.back(closeHandler)

    public suspend fun lifecycle(state: String): SeatLayerPickerLifecycleResult? =
        performLifecycle(state)

    public suspend fun setLifecycle(state: String): SeatLayerPickerSnapshot? =
        lifecycle(state)?.snapshot ?: snapshot

    public suspend fun refreshAvailability(): SeatLayerPickerLifecycleResult? {
        if (!supportsAvailabilityRefresh) return null
        val (flight, leader) = synchronized(availabilityLock) {
            availabilityFlight?.let { it to false }
                ?: CompletableDeferred<SeatLayerPickerLifecycleResult?>().also {
                    availabilityFlight = it
                }.let { it to true }
        }
        if (!leader) return flight.await()

        try {
            val result = performRefreshAccess()
            flight.complete(result)
            return result
        } catch (error: Throwable) {
            flight.completeExceptionally(error)
            throw error
        } finally {
            synchronized(availabilityLock) {
                if (availabilityFlight === flight) availabilityFlight = null
            }
        }
    }

    public fun dismissHoldLapse() {
        stateHolder.clearHoldLapse()
    }

    public suspend fun reselectLapsedSeats(
        ttlMillis: Int? = null,
    ): SeatLayerPickerSnapshot? = performReselectLapsedSeats(ttlMillis)

    public suspend fun destroy() {
        if (stateHolder.state.value.phase is SeatLayerPickerPhase.Destroyed) return
        if (isReady && supportsCommand("picker.destroy")) {
            runCatching {
                mutationMutex.withLock { send("picker.destroy") }
            }
        }
        stateHolder.markDestroyed()
    }

    internal fun connect(
        transport: SeatLayerPickerCommandTransport,
        bundleInfo: BundleInfo,
    ) {
        this.transport = transport
        this.bundleInfo = bundleInfo
    }

    internal fun disconnect() {
        transport = null
        bundleInfo = null
    }

    internal fun resetForLoad() {
        synchronized(checkoutLock) {
            checkoutFlight?.cancel()
            checkoutFlight = null
        }
        presentationCoordinator.reset()
        synchronized(availabilityLock) {
            availabilityFlight?.cancel()
            availabilityFlight = null
        }
        disconnect()
    }

    internal val holdLapse: SeatLayerPickerHoldLapse?
        get() = stateHolder.state.value.holdLapse

    internal val refreshesOnResume: Boolean
        get() = stateHolder.behavior.refreshOnResume

    internal fun setActionInFlight(inFlight: Boolean): Unit =
        stateHolder.setActionInFlight(inFlight)

    internal fun recordPickerError(error: SeatLayerException): Unit = stateHolder.record(error)

    internal fun dismissGeneralAdmissionCandidate(): Unit =
        stateHolder.dismissGeneralAdmissionCandidate()

    internal fun supportsCapabilityAndCommand(capability: String, command: String): Boolean =
        isReady && supportsCapability(capability) && supportsCommand(command)

    internal suspend fun mutation(
        command: String,
        payload: JsonElement? = null,
    ): SeatLayerPickerSnapshot? = mutationMutex.withLock {
        applyMutationResult(send(command, payload))
    }

    internal suspend fun presentation(command: String, payload: JsonElement? = null) {
        mutationMutex.withLock { send(command, payload) }
    }

    /** Sends one command with the mutation lock already held by the caller. */
    internal suspend fun command(
        name: String,
        payload: JsonElement? = null,
    ): JsonElement? = send(name, payload)

    internal suspend fun lifecycleMutation(
        command: String,
        payload: JsonElement? = null,
    ): SeatLayerPickerLifecycleResult? = mutationMutex.withLock {
        val raw = send(command, payload)
        val root = raw as? JsonObject
        val carriesSnapshot = root?.containsKey("snapshot") == true ||
            exactPickerInteger(root?.get("revision")) != null ||
            root?.string("schema") == SEATLAYER_PICKER_SNAPSHOT_SCHEMA
        val applied = applyMutationResult(raw)
        val updated = applied.takeIf { carriesSnapshot }
        val outcome = decodeSeatLayerPickerAvailabilityOutcome(
            root?.get("outcome") ?: root?.get("result") ?: raw,
        )
        outcome?.let(stateHolder::applyAvailabilityOutcome)
        if (updated == null && outcome == null) null
        else SeatLayerPickerLifecycleResult(updated, outcome)
    }

    private suspend fun send(
        command: String,
        payload: JsonElement? = null,
    ): JsonElement? {
        val phase = stateHolder.state.value.phase
        if (phase is SeatLayerPickerPhase.Destroyed) throw SeatLayerException.Destroyed()
        if (phase !is SeatLayerPickerPhase.Ready && command != "picker.destroy") {
            throw pickerBridgeFailure("not_ready", "SeatLayer picker is not ready.")
        }
        val activeTransport = transport
            ?: throw pickerBridgeFailure("not_ready", "SeatLayer picker is not connected.")
        if (!supportsCommand(command)) {
            throw pickerBridgeFailure(
                "unsupported_command",
                "The loaded picker does not advertise '$command'.",
            )
        }
        return activeTransport.command(command, payload)
    }

    internal suspend fun applyMutationResult(
        result: JsonElement?,
    ): SeatLayerPickerSnapshot? {
        val root = result as? JsonObject
        stateHolder.acceptSnapshot(root?.get("snapshot") ?: result)
        val targetRevision = exactPickerInteger(root?.get("revision"))
            ?: return snapshot
        if ((snapshot?.revision ?: -1) >= targetRevision) return snapshot

        withTimeoutOrNull(revisionWaitMillis) {
            stateHolder.state.first { state ->
                (state.snapshot?.revision ?: -1) >= targetRevision
            }
        }
        if ((snapshot?.revision ?: -1) >= targetRevision) return snapshot

        val refreshed = send("picker.getSnapshot")
        val refreshedRoot = refreshed as? JsonObject
        stateHolder.acceptSnapshot(refreshedRoot?.get("snapshot") ?: refreshed)
        if ((snapshot?.revision ?: -1) < targetRevision) {
            throw pickerDecodingFailure(
                "picker.getSnapshot did not reach revision $targetRevision",
            )
        }
        return snapshot
    }
}

internal fun requireNonEmptyValue(value: String, name: String) {
    if (value.isBlank()) throw badPickerPayload("$name is required")
}

internal fun requireNonEmptyValues(values: List<String>, name: String) {
    if (values.any(String::isBlank)) {
        throw badPickerPayload("$name must contain non-empty strings")
    }
}

internal fun requirePositiveValue(value: Int, name: String) {
    if (value <= 0) throw badPickerPayload("$name must be a positive integer")
}

internal fun badPickerPayload(message: String): SeatLayerException.Bridge =
    pickerBridgeFailure("bad_payload", "SeatLayer $message.")

internal fun pickerDecodingFailure(message: String): SeatLayerException.Bridge =
    pickerBridgeFailure("decoding", message)

internal fun pickerBridgeFailure(code: String, message: String): SeatLayerException.Bridge =
    SeatLayerException.Bridge(
        BridgeErrorDetails(
            code = code,
            message = message,
            retryable = false,
            conflicts = emptyList(),
            metadata = null,
        ),
    )
