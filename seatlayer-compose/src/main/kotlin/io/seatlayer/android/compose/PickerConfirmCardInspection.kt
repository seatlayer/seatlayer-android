package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerSeatConfidence

/*
 * The two ways a card offers to look before deciding, and the honest line about
 * a view that was modelled rather than photographed.
 */

/**
 * How a modelled view was produced, where nothing can be opened about it.
 *
 * A chip saying only one word beside a target that opens nothing would say
 * nothing and do nothing, so where the host took no passport callback the
 * disclosure stays what it has always been: the headline and the detail, which
 * ARE the information. It carries no button semantics and no focus stop.
 */
@Composable
internal fun SeatLayerConfidenceTeaser(
    confidence: SeatLayerPickerSeatConfidence,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val ground = pickerWash(scope.theme.accent, GROUND_WASH, scope.theme.surface)
    val hairline = pickerWash(scope.theme.accent, HAIRLINE_WASH, scope.theme.divider)
    val badge = pickerReadableAccent(
        accent = scope.theme.accent,
        text = scope.theme.text,
        surface = scope.theme.surface,
        ground = ground,
    )
    val detail = confidence.modeledTarget?.takeIf(String::isNotBlank) ?: confidence.reality
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = SeatLayerSizeTokens.CONFIDENCE_TEASER_TOP.dp)
            .heightIn(min = SeatLayerSizeTokens.CONFIDENCE_TEASER_MIN_HEIGHT.dp)
            .background(
                ground,
                RoundedCornerShape(SeatLayerSizeTokens.CONFIDENCE_TEASER_RADIUS.dp),
            )
            .border(
                Dp.Hairline,
                hairline,
                RoundedCornerShape(SeatLayerSizeTokens.CONFIDENCE_TEASER_RADIUS.dp),
            )
            .padding(
                horizontal = SeatLayerSizeTokens.CONFIDENCE_TEASER_PAD_X.dp,
                vertical = SeatLayerSizeTokens.CONFIDENCE_TEASER_PAD_Y.dp,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${confidence.headline}. $detail"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SeatLayerPickerText(
                confidence.headline,
                color = scope.theme.text,
                fontSize = SeatLayerSizeTokens.CONFIDENCE_TEASER_HEAD_FONT.sp,
                fontWeight = FontWeight.W700,
                maxLines = 1,
            )
            SeatLayerPickerText(
                detail,
                color = scope.theme.mutedText,
                fontSize = SeatLayerSizeTokens.CONFIDENCE_TEASER_DETAIL_FONT.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        SeatLayerPickerText(
            scope.strings.passport,
            color = badge,
            fontSize = SeatLayerSizeTokens.CONFIDENCE_TEASER_BADGE_FONT.sp,
            fontWeight = FontWeight.W700,
            maxLines = 1,
        )
    }
}

/**
 * ONE inspection row inside the scene.
 *
 * The passport and the view from the seat used to be two full-width bars above
 * the two answers, over the very section the buyer had just flown into. They
 * share one compact line now, each chip taking the same width.
 */
@Composable
internal fun SeatLayerInspectionRow(
    onPassport: (() -> Unit)?,
    onViewFromSeat: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (onPassport == null && onViewFromSeat == null) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        if (onPassport != null) {
            SeatLayerInspectChip(
                label = scope.strings.passport,
                spoken = scope.strings.passport,
                dot = true,
                onClick = onPassport,
                modifier = Modifier.weight(1f),
            )
        }
        if (onViewFromSeat != null) {
            SeatLayerInspectChip(
                label = scope.strings.viewFromHere,
                // The seat is named twice directly above this, so the chip
                // says which seat it means rather than repeating the word.
                spoken = scope.strings.viewFromThisSeat,
                dot = false,
                onClick = onViewFromSeat,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SeatLayerInspectChip(
    label: String,
    spoken: String,
    dot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(SeatLayerSizeTokens.CONFIRM_INSPECT_CHIP_HEIGHT.dp)
            .semantics { contentDescription = spoken },
        shape = RoundedCornerShape(SeatLayerRadiusTokens.BUTTON.dp),
        color = pickerWash(scope.theme.accent, CHIP_WASH, scope.theme.surface),
        contentColor = scope.theme.text,
        border = BorderStroke(Dp.Hairline, scope.theme.divider),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot) {
                Box(
                    Modifier
                        .size(PASSPORT_DOT)
                        .background(scope.theme.accent, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
            }
            SeatLayerPickerText(
                label,
                color = scope.theme.text,
                fontSize = SeatLayerSizeTokens.CONFIRM_INSPECT_CHIP_FONT_SIZE.sp,
                fontWeight = FontWeight.W800,
                maxLines = 1,
            )
        }
    }
}

/**
 * Focus lands on the answer the card exists to collect.
 *
 * Cancel is drawn first because leaving reads left; the first focusable is the
 * primary all the same, so a buyer arriving by keyboard or switch control is on
 * the answer rather than on the way out.
 */
@Composable
internal fun SeatLayerFocusPrimaryOnArrival(requester: FocusRequester, key: Any?) {
    LaunchedEffect(key) {
        runCatching { requester.requestFocus() }
    }
}

private val CHIP_GAP: Dp = 6.dp
private val PASSPORT_DOT: Dp = 7.dp
private const val CHIP_WASH = 0.12f
private const val GROUND_WASH = 0.07f
private const val HAIRLINE_WASH = 0.35f
