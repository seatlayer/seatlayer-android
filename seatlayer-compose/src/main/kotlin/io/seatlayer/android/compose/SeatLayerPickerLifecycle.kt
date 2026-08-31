package io.seatlayer.android.compose

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.seatlayer.android.SeatLayerPickerSnapshot
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Connects the scoped picker to the host lifecycle without owning navigation. */
@Composable
public fun SeatLayerPickerLifecycle() {
    val scope = LocalSeatLayerPickerScope.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val currentScope by rememberUpdatedState(scope)
    var lastSent by remember(scope.stateHolder) { mutableStateOf<String?>(null) }

    fun dispatch(next: String) {
        if (!currentScope.state.isReady || lastSent == next) return
        lastSent = next
        coroutineScope.launch {
            currentScope.performAction { currentScope.controller.lifecycle(next) }
        }
    }

    DisposableEffect(lifecycleOwner, scope.stateHolder) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> dispatch("foreground")
                Lifecycle.Event.ON_STOP -> dispatch("background")
                Lifecycle.Event.ON_DESTROY -> coroutineScope.launch {
                    currentScope.stateHolder.close()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(scope.state.isReady, lifecycleOwner) {
        if (scope.state.isReady) {
            dispatch(
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    "foreground"
                } else {
                    "background"
                },
            )
        }
    }
}

/** Hardware and predictive back share the controller's one-layer reducer. */
@Composable
public fun SeatLayerPickerBackHandler(enabled: Boolean = true) {
    val scope = LocalSeatLayerPickerScope.current
    PredictiveBackHandler(enabled = enabled && scope.state.isReady) { progress ->
        // Consuming progress is preview-only. No state or renderer command is
        // committed unless the flow completes.
        progress.collect { }
        scope.controller.back { scope.callbacks.onClose() }
    }
}

/** Deduplicated native cues; a resumed session's first snapshot stays quiet. */
@Composable
public fun SeatLayerPickerHapticEffects() {
    val scope = LocalSeatLayerPickerScope.current
    val haptics = LocalHapticFeedback.current
    var previous by remember(scope.stateHolder) {
        mutableStateOf<SeatLayerPickerSnapshot?>(null)
    }
    val current = scope.state.snapshot
    LaunchedEffect(current?.sessionId, current?.revision, scope.options.haptics) {
        if (!scope.options.haptics || current == null) {
            previous = current
            return@LaunchedEffect
        }
        val before = previous
        if (before != null && before.sessionId == current.sessionId) {
            when {
                current.selection.size > before.selection.size ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                current.hold.active && !before.hold.active ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                !current.hold.active && before.hold.active ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                current.map.focusedSectionId != before.map.focusedSectionId ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        previous = current
    }
}
