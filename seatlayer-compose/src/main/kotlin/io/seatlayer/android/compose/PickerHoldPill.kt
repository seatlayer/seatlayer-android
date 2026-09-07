package io.seatlayer.android.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
public fun SeatLayerPickerHoldCountdown(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val hold = scope.state.snapshot?.hold ?: return
    val expiresAt = hold.expiresAt ?: return
    if (!hold.active || hold.owner == "host") return
    val expiryMillis = normalizeEpochMillis(expiresAt)
    var remainingMillis by remember(expiryMillis) {
        mutableStateOf((expiryMillis - System.currentTimeMillis()).coerceAtLeast(0L))
    }
    LaunchedEffect(expiryMillis) {
        while (remainingMillis > 0) {
            delay(minOf(1_000L, remainingMillis))
            remainingMillis = (expiryMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = scope.theme.accent.copy(alpha = 0.12f),
        contentColor = scope.theme.text,
    ) {
        Text(
            formatCountdown(remainingMillis),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun normalizeEpochMillis(value: Double): Long =
    if (value < 10_000_000_000.0) (value * 1_000.0).toLong() else value.toLong()

private fun formatCountdown(remainingMillis: Long): String {
    val seconds = (remainingMillis / 1_000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
