package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerPickerUndoNotice(
    modifier: Modifier = Modifier,
    durationMillis: Long = SeatLayerPickerTokens.MOTION_UNDO_WINDOW_MILLIS.toLong(),
) {
    val scope = LocalSeatLayerPickerScope.current
    val undo = scope.state.presentation.removalUndo ?: return
    if (!scope.state.presentation.canUndoRemoval(scope.state.snapshot)) return
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(undo, durationMillis) {
        delay(durationMillis.coerceAtLeast(0))
        scope.controller.dismissRemovalUndo()
    }
    Surface(
        modifier = modifier,
        color = scope.theme.onSurface,
        contentColor = scope.theme.surface,
        shape = RoundedCornerShape(scope.styles.cardRadius),
        shadowElevation = 8.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                undo.line.displayLabel ?: undo.line.label,
                modifier = Modifier.padding(start = 14.dp),
            )
            TextButton(
                onClick = { coroutineScope.launch { scope.controller.undoLastRemoval() } },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) { Text(scope.strings.undo) }
        }
    }
}
