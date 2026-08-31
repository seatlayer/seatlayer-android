package io.seatlayer.android

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Typed headless API for one protocol-2 picker.
 *
 * Inventory mutations are serialized, optional operations are gated before a
 * bridge message is sent, and overlapping checkout calls share one handoff.
 */
public class SeatLayerPickerController internal constructor(
    private val stateHolder: SeatLayerPickerStateHolder,
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
        get() = supports("floor-stack-v1", "picker.setFloor")

    public val supportsViewportInsets: Boolean
        get() = supports("viewport-insets-v1", "picker.setViewportInsets")

    public val supportsVenue3D: Boolean
        get() = stateHolder.behavior.enableVenue3D &&
            supports("venue-3d-v1", "picker.setBuyerView")

    public val supportsSeatView: Boolean
        get() = stateHolder.behavior.enableSeatView &&
            supports("seat-view-v1", "picker.openSeatView")

    /** Whether this runtime lets native chrome close the active panorama. */
    public val supportsSeatViewClose: Boolean
        get() = supportsSeatView && supportsCommand("picker.closeSeatView")

    public val supportsNativeSeatViewChrome: Boolean
        get() = stateHolder.behavior.enableSeatView &&
            supportsCapability("native-seat-view-chrome-v1") &&
            supportsEvent("seatView.changed")

    public val supportsAvailabilityRefresh: Boolean
        get() = supports("availability-refresh-v1", "picker.refreshAvailability")

    public val supportsHoldSelection: Boolean
        get() = supports("hold-selection-v1", "picker.holdSelection")

    public fun supportsCapability(capability: String): Boolean =
        bundleInfo?.supportsCapability(capability) == true

    public fun supportsCommand(command: String): Boolean =
        bundleInfo?.supportsCommand(command) == true

    public fun supportsEvent(event: String): Boolean =
        event in bundleInfo?.events.orEmpty()

    public suspend fun synchronize(): SeatLayerPickerSnapshot? =
        mutation("picker.getSnapshot")

    public suspend fun selectObjects(objects: List<String>): SeatLayerPickerSnapshot? {
        requireNonEmpty(objects, "objects")
        return mutation(
            "picker.selectObjects",
            jsonObject("objects" to jsonStrings(objects)),
        )
    }

    public suspend fun deselectObjects(objects: List<String>): SeatLayerPickerSnapshot? {
        requireNonEmpty(objects, "objects")
        return mutation(
            "picker.deselectObjects",
            jsonObject("objects" to jsonStrings(objects)),
        )
    }

    public suspend fun clearSelection(): SeatLayerPickerSnapshot? =
        mutation("picker.clearSelection")

    public suspend fun selectCategories(
        categoryKeys: List<String>,
    ): SeatLayerPickerSnapshot? {
        requireNonEmpty(categoryKeys, "categoryKeys")
        return mutation(
            "picker.selectCategories",
            jsonObject("categoryKeys" to jsonStrings(categoryKeys)),
        )
    }

    public suspend fun deselectCategories(
        categoryKeys: List<String>,
    ): SeatLayerPickerSnapshot? {
        requireNonEmpty(categoryKeys, "categoryKeys")
        return mutation(
            "picker.deselectCategories",
            jsonObject("categoryKeys" to jsonStrings(categoryKeys)),
        )
    }

    public suspend fun setSeatTier(
        seatId: String,
        tierId: String?,
    ): SeatLayerPickerSnapshot? {
        requireNonEmpty(seatId, "seatId")
        tierId?.let { requireNonEmpty(it, "tierId") }
        return mutation(
            "picker.setSeatTier",
            jsonObject(
                "seatId" to JsonPrimitive(seatId),
                "tierId" to (tierId?.let(::JsonPrimitive) ?: JsonNull),
            ),
        )
    }

    public suspend fun removeCartLine(label: String): SeatLayerPickerSnapshot? {
        requireNonEmpty(label, "label")
        return mutation(
            "picker.removeCartLine",
            jsonObject("label" to JsonPrimitive(label)),
        )
    }

    public suspend fun setTableQuantity(
        label: String,
        quantity: Int,
        ttlMillis: Int? = null,
    ): SeatLayerPickerSnapshot? {
        requireNonEmpty(label, "label")
        requirePositive(quantity, "quantity")
        ttlMillis?.let { requirePositive(it, "ttlMillis") }
        return mutation(
            "picker.setTableQuantity",
            jsonObject(
                "label" to JsonPrimitive(label),
                "quantity" to JsonPrimitive(quantity),
                "ttlMs" to jsonNumber(ttlMillis),
            ),
        )
    }

    public suspend fun setSelectableObjects(
        objects: List<String>?,
    ): SeatLayerPickerSnapshot? {
        objects?.let { requireNonEmpty(it, "objects") }
        return mutation(
            "picker.setSelectableObjects",
            jsonObject(
                "objects" to (objects?.let(::jsonStrings) ?: JsonNull),
            ),
        )
    }

    public suspend fun setMaxSelection(maximum: Int): SeatLayerPickerSnapshot? {
        requirePositive(maximum, "maxSelection")
        return mutation(
            "picker.setMaxSelection",
            jsonObject("maxSelection" to JsonPrimitive(maximum)),
        )
    }

    public suspend fun setCategoryFilter(
        categoryKeys: List<String>,
        focus: Boolean = false,
    ): SeatLayerPickerSnapshot? {
        if (categoryKeys.isNotEmpty()) requireNonEmpty(categoryKeys, "categoryKeys")
        return mutation(
            "picker.setCategoryFilter",
            jsonObject(
                "categoryKeys" to if (categoryKeys.isEmpty()) {
                    JsonNull
                } else {
                    jsonStrings(categoryKeys)
                },
                "focus" to JsonPrimitive(focus),
            ),
        )
    }

    public suspend fun setAccessibilityFilter(
        types: List<String>,
    ): SeatLayerPickerSnapshot? {
        if (types.isNotEmpty()) requireNonEmpty(types, "types")
        return mutation(
            "picker.setAccessibilityFilter",
            jsonObject(
                "types" to if (types.isEmpty()) JsonNull else jsonStrings(types),
            ),
        )
    }

    public suspend fun setLimitedViewFilter(enabled: Boolean): SeatLayerPickerSnapshot? =
        mutation(
            "picker.setLimitedViewFilter",
            jsonObject("on" to JsonPrimitive(enabled)),
        )

    public suspend fun focusSection(sectionId: String): SeatLayerPickerSnapshot? {
        requireNonEmpty(sectionId, "sectionId")
        return mutation(
            "picker.focusSection",
            jsonObject("sectionId" to JsonPrimitive(sectionId)),
        )
    }

    public suspend fun overview(): SeatLayerPickerSnapshot? =
        mutation("picker.overview")

    public suspend fun setRung(rung: String): SeatLayerPickerSnapshot? {
        if (rung !in setOf("zones", "sections", "seats")) {
            throw badPayload("rung must be zones, sections, or seats")
        }
        return mutation(
            "picker.setRung",
            jsonObject("rung" to JsonPrimitive(rung)),
        )
    }

    public suspend fun setFloor(floorId: String): SeatLayerPickerSnapshot? {
        requireNonEmpty(floorId, "floorId")
        return mutation(
            "picker.setFloor",
            jsonObject("floorId" to JsonPrimitive(floorId)),
        )
    }

    public suspend fun showAllFloors(): SeatLayerPickerSnapshot? =
        if (supportsFloorStack) setFloor(SEATLAYER_ALL_FLOORS) else snapshot

    public suspend fun setColorblindSafe(enabled: Boolean): SeatLayerPickerSnapshot? =
        mutation(
            "picker.setColorblindSafe",
            jsonObject("on" to JsonPrimitive(enabled)),
        )

    public suspend fun setViewMode(
        mode: SeatLayerViewMode,
    ): SeatLayerPickerSnapshot? = mutation(
        "picker.setViewMode",
        jsonObject("mode" to JsonPrimitive(mode.raw)),
    )

    public suspend fun setBuyerView(
        view: String,
        flyToSeatId: String? = null,
        resetView: Boolean = false,
    ): SeatLayerPickerSnapshot? {
        requireNonEmpty(view, "view")
        flyToSeatId?.let { requireNonEmpty(it, "flyToSeatId") }
        if (!supportsVenue3D) return snapshot
        return mutation(
            "picker.setBuyerView",
            jsonObject(
                "view" to JsonPrimitive(view),
                "flyToSeatId" to jsonString(flyToSeatId),
                "resetView" to JsonPrimitive(resetView),
            ),
        )
    }

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

    public suspend fun openSeatView(seatId: String): SeatLayerPickerSnapshot? {
        requireNonEmpty(seatId, "seatId")
        if (!supportsSeatView) return snapshot
        return mutation(
            "picker.openSeatView",
            jsonObject("seatId" to JsonPrimitive(seatId)),
        )
    }

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

    public suspend fun setVenue3DNavigationMode(
        mode: String,
    ): SeatLayerPickerSnapshot? {
        if (mode !in setOf("orbit", "pan")) {
            throw badPayload("3D navigation mode must be orbit or pan")
        }
        if (!supports("venue-3d-controls-v1", "picker.setVenue3DNavigationMode")) {
            return snapshot
        }
        return mutation(
            "picker.setVenue3DNavigationMode",
            jsonObject("mode" to JsonPrimitive(mode)),
        )
    }

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
    ) {
        presentation(
            "picker.setThemeMode",
            jsonObject(
                "mode" to (mode?.raw?.let(::JsonPrimitive) ?: JsonNull),
                "mapTheme" to mapTheme?.let {
                    jsonObject(
                        "background" to JsonPrimitive(it.background),
                        "rowLabelColor" to JsonPrimitive(it.rowLabel),
                        "textColor" to JsonPrimitive(it.text),
                        "selectionColor" to JsonPrimitive(it.selection),
                    )
                },
            ),
        )
    }

    public suspend fun setInteractionEnabled(enabled: Boolean) {
        presentation(
            "picker.setInteractionEnabled",
            jsonObject("enabled" to JsonPrimitive(enabled)),
        )
    }

    public suspend fun setViewportInsets(insets: SeatLayerPickerViewportInsets?) {
        if (!supportsViewportInsets) return
        val payload = if (insets == null) {
            jsonObject("insets" to JsonNull)
        } else {
            jsonObject(
                "top" to JsonPrimitive(insets.top),
                "right" to JsonPrimitive(insets.right),
                "bottom" to JsonPrimitive(insets.bottom),
                "left" to JsonPrimitive(insets.left),
            )
        }
        presentation("picker.setViewportInsets", payload)
    }

    public suspend fun holdGeneralAdmission(
        areaId: String,
        quantity: Int,
        tierId: String? = null,
        ttlMillis: Int? = null,
    ): SeatLayerPickerSnapshot? {
        requireNonEmpty(areaId, "areaId")
        requirePositive(quantity, "quantity")
        tierId?.let { requireNonEmpty(it, "tierId") }
        ttlMillis?.let { requirePositive(it, "ttlMillis") }
        val updated = mutation(
            "picker.holdGA",
            jsonObject(
                "areaId" to JsonPrimitive(areaId),
                "qty" to JsonPrimitive(quantity),
                "tierId" to jsonString(tierId),
                "ttlMs" to jsonNumber(ttlMillis),
            ),
        )
        stateHolder.dismissGeneralAdmissionCandidate()
        return updated
    }

    public suspend fun holdSelection(
        ttlMillis: Int? = null,
    ): SeatLayerPickerSnapshot? {
        ttlMillis?.let { requirePositive(it, "ttlMillis") }
        if (!supportsHoldSelection) return snapshot
        return mutation(
            "picker.holdSelection",
            jsonObject("ttlMs" to jsonNumber(ttlMillis)),
        )
    }

    public suspend fun bestAvailable(
        quantity: Int,
        categoryKey: String? = null,
        zoneId: String? = null,
        preferPremium: Boolean = false,
        ttlMillis: Int? = null,
    ): SeatLayerPickerSnapshot? {
        requirePositive(quantity, "quantity")
        categoryKey?.let { requireNonEmpty(it, "categoryKey") }
        zoneId?.let { requireNonEmpty(it, "zoneId") }
        ttlMillis?.let { requirePositive(it, "ttlMillis") }
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

    public suspend fun resumeHold(holdId: String): SeatLayerPickerSnapshot? {
        requireNonEmpty(holdId, "holdId")
        return mutation(
            "picker.resumeHold",
            jsonObject("holdId" to JsonPrimitive(holdId)),
        )
    }

    public suspend fun extendHold(ttlMillis: Int? = null): SeatLayerPickerSnapshot? {
        ttlMillis?.let { requirePositive(it, "ttlMillis") }
        return mutation(
            "picker.extendHold",
            jsonObject("ttlMs" to jsonNumber(ttlMillis)),
        )
    }

    public suspend fun abort(): SeatLayerPickerSnapshot? = mutation("picker.abort")

    public suspend fun rejectHandoff(holdId: String): SeatLayerPickerSnapshot? {
        requireNonEmpty(holdId, "holdId")
        return mutation(
            "picker.rejectHandoff",
            jsonObject("holdId" to JsonPrimitive(holdId)),
        )
    }

    public suspend fun checkout(
        ttlMillis: Int? = null,
    ): SeatLayerPickerCheckoutHandoff {
        ttlMillis?.let { requirePositive(it, "ttlMillis") }
        val (flight, leader) = synchronized(checkoutLock) {
            checkoutFlight?.let { it to false }
                ?: CompletableDeferred<SeatLayerPickerCheckoutHandoff>().also {
                    checkoutFlight = it
                }.let { it to true }
        }
        if (!leader) return flight.await()

        try {
            val handoff = mutationMutex.withLock {
                val result = send(
                    "picker.continue",
                    jsonObject("ttlMs" to jsonNumber(ttlMillis)),
                )
                applyMutationResult(result)
                val root = result as? JsonObject
                decodeSeatLayerPickerCheckoutHandoff(root?.get("handoff"))
                    ?: throw decodingFailure(
                        "picker.continue returned no checkout handoff",
                    )
            }
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

    public suspend fun lifecycle(state: String): SeatLayerPickerLifecycleResult? {
        requireNonEmpty(state, "state")
        val runtimeState = if (state == "resumed" || state == "foreground") {
            "foreground"
        } else {
            "background"
        }
        if (runtimeState == "background" || !stateHolder.behavior.refreshOnResume) {
            return lifecycleMutation(
                "picker.lifecycle",
                jsonObject("state" to JsonPrimitive(runtimeState)),
            )
        }

        stateHolder.setActionInFlight(true)
        return try {
            val initial = lifecycleMutation(
                "picker.lifecycle",
                jsonObject("state" to JsonPrimitive(runtimeState)),
            )
            val refreshed = if (
                initial?.outcome == null && supportsAvailabilityRefresh
            ) {
                refreshAvailability()
            } else {
                null
            }
            val updated = refreshed?.snapshot ?: initial?.snapshot ?: synchronize()
            SeatLayerPickerLifecycleResult(
                snapshot = updated,
                outcome = refreshed?.outcome ?: initial?.outcome,
            )
        } finally {
            stateHolder.setActionInFlight(false)
        }
    }

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
            val result = lifecycleMutation("picker.refreshAvailability")
            flight.complete(result)
            return result
        } catch (error: CancellationException) {
            flight.completeExceptionally(error)
            throw error
        } catch (error: SeatLayerException) {
            stateHolder.record(error)
            flight.complete(null)
            return null
        } catch (error: Throwable) {
            stateHolder.record(
                SeatLayerException.Transport(
                    "SeatLayer availability refresh failed.",
                    error,
                ),
            )
            flight.complete(null)
            return null
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
    ): SeatLayerPickerSnapshot? {
        val lapse = stateHolder.state.value.holdLapse ?: return snapshot
        if (lapse.recoverableLabels.isEmpty()) return snapshot
        selectObjects(lapse.recoverableLabels)
        stateHolder.clearHoldLapse()
        if (supportsHoldSelection) holdSelection(ttlMillis)
        return snapshot
    }

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

    private fun supports(capability: String, command: String): Boolean =
        isReady && supportsCapability(capability) && supportsCommand(command)

    private suspend fun mutation(
        command: String,
        payload: JsonElement? = null,
    ): SeatLayerPickerSnapshot? = mutationMutex.withLock {
        applyMutationResult(send(command, payload))
    }

    private suspend fun presentation(command: String, payload: JsonElement? = null) {
        mutationMutex.withLock { send(command, payload) }
    }

    private suspend fun lifecycleMutation(
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
            throw bridgeFailure("not_ready", "SeatLayer picker is not ready.")
        }
        val activeTransport = transport
            ?: throw bridgeFailure("not_ready", "SeatLayer picker is not connected.")
        if (!supportsCommand(command)) {
            throw bridgeFailure(
                "unsupported_command",
                "The loaded picker does not advertise '$command'.",
            )
        }
        return activeTransport.command(command, payload)
    }

    private suspend fun applyMutationResult(result: JsonElement?): SeatLayerPickerSnapshot? {
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
            throw decodingFailure(
                "picker.getSnapshot did not reach revision $targetRevision",
            )
        }
        return snapshot
    }

    private fun requireNonEmpty(value: String, name: String) {
        if (value.isBlank()) throw badPayload("$name is required")
    }
    private fun requireNonEmpty(values: List<String>, name: String) {
        if (values.any(String::isBlank)) {
            throw badPayload("$name must contain non-empty strings")
        }
    }
    private fun requirePositive(value: Int, name: String) {
        if (value <= 0) throw badPayload("$name must be a positive integer")
    }
    private fun badPayload(message: String): SeatLayerException.Bridge =
        bridgeFailure("bad_payload", "SeatLayer $message.")
    private fun decodingFailure(message: String): SeatLayerException.Bridge =
        bridgeFailure("decoding", message)
    private fun bridgeFailure(code: String, message: String): SeatLayerException.Bridge =
        SeatLayerException.Bridge(
            BridgeErrorDetails(
                code = code,
                message = message,
                retryable = false,
                conflicts = emptyList(),
                metadata = null,
            ),
        )
}
