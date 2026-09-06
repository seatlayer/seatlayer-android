package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerHoldLapseNotice(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val lapse = scope.state.holdLapse ?: return
    val coroutineScope = rememberCoroutineScope()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scope.theme.surface,
        contentColor = scope.theme.onSurface,
        shape = RoundedCornerShape(scope.styles.cardRadius),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(scope.strings.holdExpired, fontWeight = FontWeight.SemiBold)
            if (lapse.lapsedLabels.isNotEmpty()) {
                Text(
                    lapse.lapsedLabels.joinToString(),
                    color = scope.theme.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lapse.recoverableLabels.isNotEmpty()) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                scope.performAction {
                                    scope.controller.reselectLapsedSeats(
                                        scope.options.holdTtlMillis,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(scope.styles.buttonRadius),
                    ) { Text(scope.strings.reselectSeats(lapse.recoverableLabels.size)) }
                }
                TextButton(
                    onClick = scope.controller::dismissHoldLapse,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(scope.strings.dismiss) }
            }
        }
    }
}

@Composable
public fun SeatLayerPickerActionError(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val error = scope.state.presentation.lastActionError ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scope.theme.error,
        contentColor = scope.theme.onAccent,
        shape = RoundedCornerShape(scope.styles.cardRadius),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                error.message?.takeIf(String::isNotBlank) ?: scope.strings.actionFailed,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = scope.controller::dismissActionError,
                modifier = Modifier.sizeIn(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = scope.theme.onAccent,
                ),
            ) { Text(scope.strings.dismiss) }
        }
    }
}
