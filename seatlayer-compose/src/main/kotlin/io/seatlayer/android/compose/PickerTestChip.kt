package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.EventMode

/**
 * The badge that says these are not real tickets.
 *
 * Drawn on the map itself, so it takes the map's own measurements rather than
 * a size of its own: a badge that sat a little higher than the rail beside it
 * read as a notification rather than as a statement about the event.
 *
 * The badge is one size at both widths; [compact] is kept for hosts that place
 * it themselves and is deliberately not read.
 */
@Composable
public fun SeatLayerPickerTestModeIndicator(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val mode = scope.state.snapshot?.event?.mode ?: return
    if (mode == EventMode.Live) return
    val dark = scope.theme.background.luminance() < TEST_CHIP_DARK_LUMINANCE
    Surface(
        modifier = modifier.height(SeatLayerSizeTokens.TEST_CHIP_HEIGHT.dp),
        color = if (dark) scope.theme.background else scope.theme.warning,
        contentColor = if (dark) scope.theme.warning else scope.theme.onWarning,
        border = BorderStroke(1.dp, scope.theme.warning),
        shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TEST_CHIP_PADDING_X),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TEST_CHIP_GAP),
        ) {
            Box(
                Modifier
                    .size(SeatLayerSizeTokens.TEST_CHIP_DOT_SIZE.dp)
                    .clip(CircleShape)
                    .background(if (dark) scope.theme.warning else scope.theme.onWarning),
            )
            Text(
                text = if (mode == EventMode.Test) scope.strings.testMode else mode.raw,
                fontSize = SeatLayerSizeTokens.TEST_CHIP_FONT_SIZE.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = TEST_CHIP_TRACKING,
                maxLines = 1,
            )
        }
    }
}

private const val TEST_CHIP_DARK_LUMINANCE = 0.35f
private val TEST_CHIP_PADDING_X: Dp = 9.dp
private val TEST_CHIP_GAP: Dp = 5.dp
private val TEST_CHIP_TRACKING = 1.1.sp
