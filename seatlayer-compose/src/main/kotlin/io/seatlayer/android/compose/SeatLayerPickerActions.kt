package io.seatlayer.android.compose

import kotlinx.coroutines.CancellationException

internal suspend fun SeatLayerPickerScope.performAction(
    action: suspend () -> Unit,
) {
    try {
        action()
    } catch (error: CancellationException) {
        throw error
    } catch (error: io.seatlayer.android.SeatLayerException) {
        callbacks.onError(error)
    }
}

internal suspend fun SeatLayerPickerScope.performActionResult(
    action: suspend () -> Unit,
): Boolean = try {
    action()
    true
} catch (error: CancellationException) {
    throw error
} catch (error: io.seatlayer.android.SeatLayerException) {
    callbacks.onError(error)
    false
}
