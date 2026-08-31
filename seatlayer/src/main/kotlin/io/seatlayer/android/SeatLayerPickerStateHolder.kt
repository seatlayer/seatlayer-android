package io.seatlayer.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/**
 * Lifecycle-neutral owner for one protocol-2 picker session.
 *
 * Custom Compose and View/XML applications observe [state] and invoke semantic
 * actions through [controller]. The holder never persists buyer credentials or
 * raw bridge envelopes.
 */
public class SeatLayerPickerStateHolder(
    public val configuration: SeatLayerConfiguration,
    public val behavior: SeatLayerPickerBehavior = SeatLayerPickerBehavior(),
) {
    private val lock = Any()
    private val closeMutex = Mutex()
    private val mutableState = MutableStateFlow(SeatLayerPickerState())
    private val mutableChartLoads = MutableSharedFlow<SeatLayerChartLoad>(
        extraBufferCapacity = 1,
    )
    private val answeredSeatIdentities = linkedSetOf<String>()
    private var presentationSessionId: String? = null

    internal val bridgeProfile: SeatLayerPickerBridgeProfile =
        SeatLayerPickerBridgeProfile.create(
            enable3D = behavior.enableVenue3D,
            enableSeatView = behavior.enableSeatView,
            config = behavior.bridgeConfig(),
        )

    public val state: StateFlow<SeatLayerPickerState> = mutableState.asStateFlow()
    /** Runtime-authored load records; late collectors do not receive old attempts. */
    public val chartLoads: SharedFlow<SeatLayerChartLoad> = mutableChartLoads.asSharedFlow()
    public val controller: SeatLayerPickerController = SeatLayerPickerController(this)

    public suspend fun close() {
        closeMutex.withLock {
            if (state.value.phase is SeatLayerPickerPhase.Destroyed) return
            if (state.value.phase is SeatLayerPickerPhase.Ready) {
                controller.close()
            }
            controller.destroy()
        }
    }

    internal fun beginLoading() {
        if (state.value.phase is SeatLayerPickerPhase.Destroyed) {
            throw SeatLayerException.Destroyed()
        }
        controller.resetForLoad()
        replace(
            SeatLayerPickerState(
                phase = SeatLayerPickerPhase.Loading,
                presentation = SeatLayerPickerPresentationState(
                    cartExpanded = !behavior.panelInitiallyCollapsed,
                ),
            ),
        )
    }

    internal fun connect(
        transport: SeatLayerPickerCommandTransport,
        bundleInfo: BundleInfo,
    ) {
        bridgeProfile.validate(bundleInfo)
        controller.connect(transport, bundleInfo)
        update { it.copy(bundleInfo = bundleInfo, lastError = null) }
    }

    internal fun markReady(
        info: ReadyInfo,
        timing: SeatLayerPickerReadyTiming = SeatLayerPickerReadyTiming(),
    ) {
        update {
            it.copy(
                phase = SeatLayerPickerPhase.Ready(info, timing),
                lastError = null,
            )
        }
    }

    internal fun emitChartLoad(load: SeatLayerChartLoad) {
        mutableChartLoads.tryEmit(load)
    }

    internal fun acceptSnapshot(value: JsonElement?): SeatLayerPickerSnapshot? =
        decodeSeatLayerPickerSnapshot(value)?.takeIf(::acceptSnapshot)

    internal fun acceptSnapshot(candidate: SeatLayerPickerSnapshot): Boolean =
        synchronized(lock) {
            if (candidate.event.key != configuration.event) return@synchronized false
            val currentState = mutableState.value
            val current = currentState.snapshot
            if (
                current != null &&
                (candidate.sessionId != current.sessionId ||
                    candidate.revision <= current.revision)
            ) return@synchronized false

            mutableState.value = currentState.copy(
                snapshot = candidate,
                holdLapse = if (candidate.hold.active) null else currentState.holdLapse,
                presentation = presentationForSnapshot(
                    currentState.presentation,
                    candidate,
                ),
            )
            true
        }

    internal fun acceptSeatView(value: JsonElement?) {
        if (!controller.supportsNativeSeatViewChrome) return
        update { it.copy(seatView = decodeSeatLayerSeatView(value)) }
    }

    internal fun acceptGeneralAdmissionCandidate(value: JsonElement?) {
        val area = decodeSeatLayerPickerGeneralAdmissionArea(
            (value as? kotlinx.serialization.json.JsonObject)?.get("area") ?: value,
        ) ?: return
        if (state.value.isReady) {
            update { current ->
                if (current.presentation.activePrompt != null) current
                else current.copy(
                    presentation = current.presentation.copy(
                        activePrompt = SeatLayerPickerPrompt.GeneralAdmission(area),
                    ),
                )
            }
        }
    }

    public fun dismissGeneralAdmissionCandidate() {
        update { current ->
            val presentation = current.presentation
            current.copy(
                presentation = presentation.copy(
                    activePrompt = presentation.pendingTable?.let(
                        SeatLayerPickerPrompt::Table,
                    ),
                ),
            )
        }
    }

    internal fun fail(error: SeatLayerException) {
        controller.disconnect()
        update {
            it.copy(
                phase = SeatLayerPickerPhase.Failed(error),
                lastError = error,
            )
        }
    }

    internal fun record(error: SeatLayerException) {
        update { it.copy(lastError = error) }
    }

    internal fun applyAvailabilityOutcome(outcome: SeatLayerPickerAvailabilityOutcome) {
        update { current ->
            val candidate = if (outcome.holdLapsed) {
                SeatLayerPickerHoldLapse(
                    lapsedLabels = outcome.lapsedLabels,
                    recoverableLabels = outcome.recoverableLabels,
                    heldForMillis = outcome.heldForMillis,
                )
            } else {
                null
            }
            val currentLapse = current.holdLapse
            current.copy(
                availabilityOutcome = outcome,
                holdLapse = when {
                    candidate == null -> currentLapse
                    currentLapse == null -> candidate
                    candidate.lapsedLabels.size > currentLapse.lapsedLabels.size -> candidate
                    else -> currentLapse
                },
            )
        }
    }

    internal fun clearHoldLapse() {
        update { it.copy(holdLapse = null) }
    }

    internal fun markDestroyed() {
        controller.disconnect()
        update {
            it.copy(
                phase = SeatLayerPickerPhase.Destroyed,
                seatView = null,
                availabilityOutcome = null,
                holdLapse = null,
                presentation = it.presentation.copy(
                    pendingSeat = null,
                    pendingTable = null,
                    activePrompt = null,
                    actionInFlight = false,
                ),
            )
        }
    }

    internal fun confirmPendingSeat() {
        val pending = state.value.presentation.pendingSeat ?: return
        answer(pending)
    }

    internal fun confirmPendingTable() {
        val pending = state.value.presentation.pendingTable ?: return
        answer(pending)
    }

    internal fun answer(seat: SeatLayerPickerSelectedSeat) {
        synchronized(lock) {
            answeredSeatIdentities += SeatLayerPickerProjections.seatIdentity(seat)
            val current = mutableState.value
            val snapshot = current.snapshot ?: return@synchronized
            mutableState.value = current.copy(
                presentation = presentationForSnapshot(current.presentation, snapshot),
            )
        }
    }

    internal fun setCartExpanded(expanded: Boolean) {
        update { it.copy(presentation = it.presentation.copy(cartExpanded = expanded)) }
    }

    internal fun setActionInFlight(inFlight: Boolean) {
        update {
            it.copy(presentation = it.presentation.copy(actionInFlight = inFlight))
        }
    }

    internal fun setCheckoutHandoff(handoff: SeatLayerPickerCheckoutHandoff?) {
        update {
            it.copy(presentation = it.presentation.copy(checkoutHandoff = handoff))
        }
    }

    internal fun recordActionError(error: SeatLayerException?) {
        update {
            it.copy(
                lastError = error ?: it.lastError,
                presentation = it.presentation.copy(lastActionError = error),
            )
        }
    }

    internal fun setRemovalUndo(removal: SeatLayerPickerRemovalUndo?) {
        update {
            it.copy(presentation = it.presentation.copy(removalUndo = removal))
        }
    }

    private fun recomputePresentation() {
        update { current ->
            val snapshot = current.snapshot ?: return@update current
            current.copy(
                presentation = presentationForSnapshot(current.presentation, snapshot),
            )
        }
    }

    private fun presentationForSnapshot(
        current: SeatLayerPickerPresentationState,
        snapshot: SeatLayerPickerSnapshot,
    ): SeatLayerPickerPresentationState {
        val sameSession = presentationSessionId == snapshot.sessionId
        if (!sameSession) {
            presentationSessionId = snapshot.sessionId
            answeredSeatIdentities.clear()
        }
        val present = snapshot.selection
            .map(SeatLayerPickerProjections::seatIdentity)
            .toSet()
        answeredSeatIdentities.retainAll(present)

        val mayPrompt = !behavior.readOnly && !snapshot.hold.active
        val pendingTable = if (mayPrompt) {
            snapshot.selection.asReversed().firstOrNull { seat ->
                seat.isVariableTable &&
                    SeatLayerPickerProjections.seatIdentity(seat) !in answeredSeatIdentities
            }
        } else {
            null
        }
        val pendingSeat = if (mayPrompt && behavior.confirmSelection) {
            snapshot.selection.asReversed().firstOrNull { seat ->
                !seat.isVariableTable &&
                    SeatLayerPickerProjections.seatIdentity(seat) !in answeredSeatIdentities
            }
        } else {
            null
        }
        val retainedPrompt = current.activePrompt
            .takeIf { it is SeatLayerPickerPrompt.GeneralAdmission }
        return current.copy(
            pendingSeat = pendingSeat,
            pendingTable = pendingTable,
            activePrompt = retainedPrompt
                ?: pendingTable?.let(SeatLayerPickerPrompt::Table),
            checkoutHandoff = current.checkoutHandoff.takeIf { sameSession },
            removalUndo = current.removalUndo.takeIf { sameSession },
            cartExpanded = if (sameSession) {
                current.cartExpanded
            } else {
                !behavior.panelInitiallyCollapsed
            },
        )
    }

    private fun replace(state: SeatLayerPickerState) {
        synchronized(lock) { mutableState.value = state }
    }

    private fun update(transform: (SeatLayerPickerState) -> SeatLayerPickerState) {
        synchronized(lock) { mutableState.value = transform(mutableState.value) }
    }
}

private fun SeatLayerPickerBehavior.bridgeConfig(): kotlinx.serialization.json.JsonObject =
    jsonObject(
        "readOnly" to kotlinx.serialization.json.JsonPrimitive(readOnly),
        "confirmSelection" to kotlinx.serialization.json.JsonPrimitive(confirmSelection),
        "enableBestAvailable" to kotlinx.serialization.json.JsonPrimitive(enableBestAvailable),
        "enable3D" to kotlinx.serialization.json.JsonPrimitive(enableVenue3D),
        "enableSeatView" to kotlinx.serialization.json.JsonPrimitive(enableSeatView),
        "holdTtlMs" to jsonNumber(holdTtlMillis),
        "initialHoldId" to jsonString(initialHoldId),
        "max3DSeats" to jsonNumber(max3DSeats),
        "hideEventDetails" to kotlinx.serialization.json.JsonPrimitive(hideEventDetails),
        "panelCollapsed" to kotlinx.serialization.json.JsonPrimitive(panelInitiallyCollapsed),
        "languages" to languages.takeIf { it.isNotEmpty() }?.let(::jsonStrings),
    )
