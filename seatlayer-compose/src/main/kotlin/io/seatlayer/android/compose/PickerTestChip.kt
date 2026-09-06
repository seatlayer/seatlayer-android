package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.EventMode

@Composable
public fun SeatLayerPickerTestModeIndicator(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val mode = scope.state.snapshot?.event?.mode ?: return
    if (mode == EventMode.Live) return
    val dark = scope.theme.background.luminance() < 0.35f
    Surface(
        modifier = modifier,
        color = if (dark) scope.theme.background.copy(alpha = 0.94f) else scope.theme.warning,
        contentColor = if (dark) scope.theme.warning else scope.theme.onWarning,
        border = androidx.compose.foundation.BorderStroke(1.dp, scope.theme.warning),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = if (mode == EventMode.Test) scope.strings.testMode else mode.raw,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.1.sp,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = if (compact) 9.dp else 11.dp,
                vertical = if (compact) 3.dp else 5.dp,
            ),
        )
    }
}
