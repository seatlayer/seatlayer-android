package io.seatlayer.android

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred

internal class SeatLayerPickerPresentationCoordinator(
    private val stateHolder: SeatLayerPickerStateHolder,
    private val controller: SeatLayerPickerController,
) {
    private val handoffLock = Any()
    private val closeLock = Any()
    private var handoffFlight: CompletableDeferred<SeatLayerPickerCheckoutHandoff>? = null
    private var closeFlight: CompletableDeferred<Unit>? = null

    suspend fun handoffCheckout(
        handler: suspend (SeatLayerPickerCheckoutHandoff) -> Unit,
    ): SeatLayerPickerCheckoutHandoff {
        stateHolder.state.value.presentation.checkoutHandoff?.let { return it }
        val (flight, leader) = synchronized(handoffLock) {
            handoffFlight?.let { it to false }
                ?: CompletableDeferred<SeatLayerPickerCheckoutHandoff>().also {
                    handoffFlight = it
                }.let { it to true }
        }
        if (!leader) return flight.await()

        stateHolder.setActionInFlight(true)
        try {
            val handoff = controller.checkout(stateHolder.behavior.holdTtlMillis)
            try {
                handler(handoff)
            } catch (error: CancellationException) {
                runCatching { controller.rejectHandoff(handoff.holdId) }
                throw error
            } catch (error: Throwable) {
                runCatching { controller.rejectHandoff(handoff.holdId) }
                throw SeatLayerException.Transport(
                    "The host rejected the SeatLayer checkout handoff.",
                    error,
                )
            }
            stateHolder.setCheckoutHandoff(handoff)
            flight.complete(handoff)
            return handoff
        } catch (error: Throwable) {
            if (error is SeatLayerException) stateHolder.recordActionError(error)
            flight.completeExceptionally(error)
            throw error
        } finally {
            stateHolder.setActionInFlight(false)
            synchronized(handoffLock) {
                if (handoffFlight === flight) handoffFlight = null
            }
        }
    }

    fun confirmPending() = stateHolder.confirmPendingSeat()

    suspend fun confirmPending(tierId: String?): Boolean {
        val pending = stateHolder.state.value.presentation.pendingSeat ?: return false
        val selectedTier = if (pending.tiers.isEmpty() && tierId == null) {
            null
        } else {
            pending.tiers.firstOrNull { it.id == tierId }
                ?: throw SeatLayerException.Bridge(
                    BridgeErrorDetails(
                        code = "bad_payload",
                        message = "The chosen tier is not available for this seat.",
                        retryable = false,
                        conflicts = emptyList(),
                        metadata = null,
                    ),
                )
        }
        if (stateHolder.state.value.presentation.actionInFlight) return false
        stateHolder.setActionInFlight(true)
        return try {
            if (selectedTier?.id != pending.tierId) {
                controller.setSeatTier(pending.id, selectedTier?.id)
            }
            stateHolder.confirmPendingSeat()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: SeatLayerException) {
            stateHolder.recordActionError(error)
            false
        } finally {
            stateHolder.setActionInFlight(false)
        }
    }

    fun confirmPendingTable() = stateHolder.confirmPendingTable()

    fun setCartExpanded(expanded: Boolean) = stateHolder.setCartExpanded(expanded)

    fun dismissActionError() = stateHolder.recordActionError(null)

    suspend fun removeWithUndo(line: SeatLayerPickerCartLine): Boolean {
        val presentation = stateHolder.state.value.presentation
        if (presentation.actionInFlight) return false
        val sessionId = stateHolder.state.value.snapshot?.sessionId ?: return false
        stateHolder.setActionInFlight(true)
        stateHolder.setRemovalUndo(
            SeatLayerPickerRemovalUndo(
                line = line,
                sessionId = sessionId,
                phase = SeatLayerPickerRemovalPhase.AwaitingRemove,
            ),
        )
        return try {
            controller.removeCartLine(line.label)
            stateHolder.setRemovalUndo(
                SeatLayerPickerRemovalUndo(
                    line = line,
                    sessionId = sessionId,
                    phase = SeatLayerPickerRemovalPhase.UndoWindow,
                ),
            )
            true
        } catch (error: CancellationException) {
            stateHolder.setRemovalUndo(null)
            throw error
        } catch (error: SeatLayerException) {
            stateHolder.setRemovalUndo(null)
            stateHolder.recordActionError(error)
            false
        } finally {
            stateHolder.setActionInFlight(false)
        }
    }

    suspend fun undoLastRemoval(): Boolean {
        val presentation = stateHolder.state.value.presentation
        val undo = presentation.removalUndo ?: return false
        if (
            !presentation.canUndoRemoval(stateHolder.state.value.snapshot) ||
            presentation.actionInFlight
        ) return false
        stateHolder.setActionInFlight(true)
        return try {
            controller.selectObjects(listOf(undo.line.label))
            stateHolder.setRemovalUndo(null)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: SeatLayerException) {
            stateHolder.recordActionError(error)
            false
        } finally {
            stateHolder.setActionInFlight(false)
        }
    }

    fun dismissRemovalUndo() = stateHolder.setRemovalUndo(null)

    suspend fun cancelPending(): Boolean {
        val pending = stateHolder.state.value.presentation.pendingSeat ?: return false
        return cancelPendingSeat(pending)
    }

    suspend fun cancelPendingTable(): Boolean {
        val pending = stateHolder.state.value.presentation.pendingTable ?: return false
        return cancelPendingSeat(pending)
    }

    suspend fun close(handler: suspend () -> Unit = {}) {
        val (flight, leader) = synchronized(closeLock) {
            closeFlight?.let { it to false }
                ?: CompletableDeferred<Unit>().also { closeFlight = it }.let { it to true }
        }
        if (!leader) return flight.await()
        try {
            try {
                controller.abort()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Runtime expiry is the final safety net for best-effort close.
            }
            handler()
            flight.complete(Unit)
        } catch (error: Throwable) {
            flight.completeExceptionally(error)
            throw error
        } finally {
            synchronized(closeLock) {
                if (closeFlight === flight) closeFlight = null
            }
        }
    }

    suspend fun back(
        closeHandler: suspend () -> Unit = {},
    ): SeatLayerPickerBackStep {
        val step = controller.nextBackStep
        when (step) {
            SeatLayerPickerBackStep.Prompt -> when (
                stateHolder.state.value.presentation.activePrompt
            ) {
                is SeatLayerPickerPrompt.GeneralAdmission ->
                    stateHolder.dismissGeneralAdmissionCandidate()
                is SeatLayerPickerPrompt.Table -> cancelPendingTable()
                null -> Unit
            }
            SeatLayerPickerBackStep.Cart -> setCartExpanded(false)
            // New runtimes can expose a close command. Older ones safely no-op,
            // preserving the target/session for the runtime-owned close control.
            SeatLayerPickerBackStep.Panorama -> runAction {
                controller.closeSeatView()
            }
            SeatLayerPickerBackStep.Venue3D -> runAction {
                controller.venue3D(SeatLayerPickerVenue3DAction.Back)
            }
            SeatLayerPickerBackStep.Confirmation -> cancelPending()
            SeatLayerPickerBackStep.Section -> runAction { controller.zoomOut() }
            SeatLayerPickerBackStep.Venue -> runAction {
                if (
                    controller.snapshot?.map?.buyerView != "map" &&
                    controller.supportsVenue3D
                ) {
                    controller.setBuyerView("map")
                } else {
                    controller.overview()
                }
            }
            SeatLayerPickerBackStep.Close -> close(closeHandler)
        }
        return step
    }

    fun reset() {
        synchronized(handoffLock) {
            handoffFlight?.cancel()
            handoffFlight = null
        }
        synchronized(closeLock) {
            closeFlight?.cancel()
            closeFlight = null
        }
    }

    private suspend fun cancelPendingSeat(seat: SeatLayerPickerSelectedSeat): Boolean {
        if (stateHolder.state.value.presentation.actionInFlight) return false
        stateHolder.setActionInFlight(true)
        return try {
            controller.deselectObjects(listOf(seat.label))
            stateHolder.answer(seat)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: SeatLayerException) {
            stateHolder.recordActionError(error)
            false
        } finally {
            stateHolder.setActionInFlight(false)
        }
    }

    private suspend fun runAction(action: suspend () -> Unit) {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: SeatLayerException) {
            stateHolder.recordActionError(error)
        }
    }
}
