package io.seatlayer.android.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The by-line, centred at the foot of the sheet.
 *
 * Centred because at the foot of a phone the trailing edge is the rounded
 * display corner. It reads the MAP CHROME's palette rather than the picker's,
 * so its words survive on the dark scene sheet.
 *
 * It is drawn only where the account's own branding asks for it, and is absent
 * entirely on a white-labelled account — an organizer entitlement reported in
 * the snapshot, never a host switch.
 */
@Composable
public fun SeatLayerPickerAttribution(
    modifier: Modifier = Modifier,
    /** Retained for source compatibility: there is one by-line. */
    @Suppress("UNUSED_PARAMETER") compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (scope.state.snapshot?.branding?.attributionRequired != true) return
    val ink = parseColor(scope.theme.mapTheme.text) ?: scope.theme.mutedText
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SeatLayerSizeTokens.ATTRIBUTION_HEIGHT.dp)
            .alpha(AttributionLiterals.OPACITY),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeatLayerPickerPoweredMark()
            Spacer(Modifier.width(AttributionLiterals.MARK_GAP.dp))
            Text(
                text = scope.strings.poweredBy,
                color = ink,
                fontSize = SeatLayerTypeTokens.ATTRIBUTION_SIZE.sp,
                fontWeight = FontWeight(SeatLayerTypeTokens.ATTRIBUTION_WEIGHT),
                letterSpacing = AttributionLiterals.LETTER_SPACING.sp,
                maxLines = 1,
            )
        }
    }
}

/** A fixed mark on every surface: the plate carries its own contrast. */
@Composable
private fun SeatLayerPickerPoweredMark() {
    Canvas(
        modifier = Modifier
            .size(AttributionLiterals.MARK_SIZE.dp)
            .clip(RoundedCornerShape(AttributionLiterals.MARK_RADIUS.dp))
            .background(AttributionLiterals.plate()),
    ) {
        val ink = AttributionLiterals.ink()
        val bar = size.height * AttributionLiterals.BAR_HEIGHT_RATIO
        val step = (size.height - bar) / 2f
        val radius = CornerRadius(bar / 2f)
        val inset = size.width * AttributionLiterals.MARK_INSET_RATIO
        listOf(
            AttributionLiterals.BAR_ONE,
            AttributionLiterals.BAR_TWO,
            AttributionLiterals.BAR_THREE,
        ).forEachIndexed { index, ratio ->
            drawRoundRect(
                color = ink,
                topLeft = Offset(inset, index * step),
                size = Size((size.width - inset * 2f) * ratio, bar),
                cornerRadius = radius,
            )
        }
    }
}

/** Mark-local numbers the design source keeps as literals. Reported as a gap. */
private object AttributionLiterals {
    const val OPACITY = 0.72f
    const val MARK_GAP = 5f
    const val MARK_SIZE = 16f
    const val MARK_RADIUS = 4f
    fun plate(): Color = Color(red = 0x0C, green = 0x12, blue = 0x20)
    fun ink(): Color = Color(red = 0xFC, green = 0xF7, blue = 0xEE)
    const val MARK_INSET_RATIO = 0.19f
    const val BAR_HEIGHT_RATIO = 0.18f
    const val BAR_ONE = 1f
    const val BAR_TWO = 0.7f
    const val BAR_THREE = 0.4f
    const val LETTER_SPACING = 0.22f
}
