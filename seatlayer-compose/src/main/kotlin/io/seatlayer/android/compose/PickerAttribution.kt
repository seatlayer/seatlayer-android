package io.seatlayer.android.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
public fun SeatLayerPickerAttribution(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (scope.state.snapshot?.branding?.attributionRequired != true) return
    Surface(
        modifier = modifier,
        color = if (compact) Color.Transparent else scope.theme.surface.copy(alpha = 0.94f),
        contentColor = scope.theme.mutedText,
        shape = RoundedCornerShape(if (compact) 0.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier
                .then(if (compact) Modifier.height(18.dp) else Modifier)
                .padding(
                    horizontal = if (compact) 4.dp else 10.dp,
                    vertical = if (compact) 1.dp else 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeatLayerPickerPoweredMark(
                background = scope.theme.mutedText,
                ink = scope.theme.surface,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = scope.strings.poweredBy,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = if (compact) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SeatLayerPickerPoweredMark(
    background: Color,
    ink: Color,
) {
    Canvas(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(background)
            .padding(2.dp),
    ) {
        val barHeight = 2.dp.toPx()
        val radius = CornerRadius(1.dp.toPx())
        listOf(8.dp, 5.5.dp, 3.dp).forEachIndexed { index, width ->
            drawRoundRect(
                color = ink,
                topLeft = Offset(0f, index * 3.dp.toPx()),
                size = Size(width.toPx(), barHeight),
                cornerRadius = radius,
            )
        }
    }
}
