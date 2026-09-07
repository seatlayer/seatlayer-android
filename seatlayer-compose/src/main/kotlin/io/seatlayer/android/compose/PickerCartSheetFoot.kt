package io.seatlayer.android.compose

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import io.seatlayer.android.SeatLayerPickerCartLanding
import io.seatlayer.android.SeatLayerPickerCartLine

/**
 * The whole foot, in both sheet states.
 *
 * A buyer with seats already held could not see the button that takes their
 * money, because the foot that carries it was hidden while the sheet was shut.
 * It is drawn in both states, and the hairline above it appears only when there
 * are tickets above it to separate.
 */
@Composable
internal fun SeatLayerPickerSheetFoot(
    showHairline: Boolean,
    modifier: Modifier = Modifier,
    collapsed: Boolean = true,
    onOpenCart: () -> Unit = {},
    onOpenFinder: () -> Unit = {},
    checkoutBar: @Composable (onFindBestSeats: () -> Unit) -> Unit = { find ->
        SeatLayerBookButton(onFindBestSeats = find)
    },
) {
    val scope = LocalSeatLayerPickerScope.current
    val hairline = scope.theme.divider
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Where a ticket in the air is going. This line is what the ticket
            // has just changed, so it is what the chip flies to.
            .pickerFlightTarget(LocalSeatLayerPickerFlight.current)
            .then(
                if (showHairline) {
                    Modifier.drawBehind {
                        drawLine(hairline, Offset(0f, 0f), Offset(size.width, 0f), 1f)
                    }
                } else {
                    Modifier
                },
            )
            .padding(
                start = SeatLayerSizeTokens.FOOT_PAD_X.dp,
                end = SeatLayerSizeTokens.FOOT_PAD_X.dp,
                top = SeatLayerSizeTokens.FOOT_PAD_TOP.dp,
                bottom = SeatLayerSizeTokens.FOOT_PAD_BOTTOM.dp,
            ),
    ) {
        SeatLayerHoldLapseNotice()
        SeatLayerPickerActionError()
        PickerTotalLine(collapsed = collapsed, onOpenCart = onOpenCart)
        Spacer(Modifier.height(SeatLayerSizeTokens.FOOT_TOTAL_GAP.dp))
        checkoutBar(onOpenFinder)
        SeatLayerPickerAttribution(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            compact = true,
        )
    }
}

/**
 * The whole summary while the sheet is shut, and the total when it is open.
 *
 * Collapsed and non-empty, a second muted line names the seats: "2 tickets"
 * alone told a buyer they had bought something and not what, and the handle
 * above was the only way to find out. Tapping that line opens the sheet — it is
 * an affordance on the live region, not a second named control.
 */
@Composable
@Suppress("LongMethod")
internal fun PickerTotalLine(
    collapsed: Boolean,
    onOpenCart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val snapshot = scope.state.snapshot
    val confirmed = scope.state.presentation.confirmedCart(snapshot)
    val totals = confirmed.totals
    val count = totals.quantity
    val summary = if (count == 0) {
        scope.strings.noSeatsSelected
    } else {
        scope.strings.ticketCount(count)
    }
    val total = when {
        count == 0 -> ""
        totals.hasMixedCurrencies -> ""
        else -> scope.moneyFormatter.format(
            totals.total,
            totals.currency ?: snapshot?.currency.orEmpty(),
        )
    }
    val swell = pickerLandingSwell(summary + total)
    val ink = lerp(scope.theme.text, scope.theme.accent, swell)
    val scale = 1f + CartSheetLiterals.SWELL_SCALE * swell
    val seats = if (collapsed && count > 0) pickerCartSeatLine(confirmed.items) else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (seats != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { onOpenCart() }
                    }
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = if (total.isEmpty()) summary else "$summary, $total"
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
                color = ink,
                fontSize = SeatLayerTypeTokens.FOOT_TOTAL_LABEL_SIZE.sp,
                fontWeight = FontWeight(SeatLayerTypeTokens.FOOT_TOTAL_LABEL_WEIGHT),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (total.isNotEmpty()) {
                Text(
                    text = total,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    },
                    color = ink,
                    fontSize = SeatLayerTypeTokens.FOOT_TOTAL_AMOUNT_SIZE.sp,
                    fontWeight = FontWeight(SeatLayerTypeTokens.FOOT_TOTAL_AMOUNT_WEIGHT),
                    maxLines = 1,
                )
            }
        }
        if (seats != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CartSheetLiterals.SEAT_LINE_PAD_TOP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = seats,
                    modifier = Modifier.weight(1f),
                    color = scope.theme.mutedText,
                    fontSize = CartSheetLiterals.SEAT_LINE_FONT_SIZE.sp,
                    fontWeight = FontWeight(CartSheetLiterals.SEAT_LINE_WEIGHT),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(CartSheetLiterals.SEAT_LINE_GLYPH_GAP.dp))
                PickerUnfoldGlyph(
                    modifier = Modifier.size(CartSheetLiterals.SEAT_LINE_GLYPH_SIZE.dp),
                )
            }
        }
    }
}

/** Every confirmed line's own label, said the way a buyer reads a seat. */
internal fun pickerCartSeatLine(items: List<SeatLayerPickerCartLine>): String? = items
    .map { line -> (line.displayLabel ?: line.label).replace("-", " · ") }
    .takeIf(List<String>::isNotEmpty)
    ?.joinToString(separator = ",  ")

/**
 * The count and the total swelling once, when the ticket lands.
 *
 * Not on the press: the chip is still in the air then, and a total that has
 * already moved makes the flight look like a replay. The swell waits for the
 * landing the flight itself reports, and is skipped outright where the device
 * has asked for no animation.
 */
@Composable
private fun pickerLandingSwell(token: String): Float {
    val scope = LocalSeatLayerPickerScope.current
    val reducedMotion = pickerReducedMotion()
    val landing = scope.state.presentation.cartLanding
    var value by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf(token) }
    LaunchedEffect(token) {
        if (token != last) {
            last = token
            if (!reducedMotion) armed = true
        }
    }
    LaunchedEffect(armed, landing) {
        if (!armed || landing == SeatLayerPickerCartLanding.Flying) return@LaunchedEffect
        armed = false
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = SeatLayerMotionTokens.BUMP,
            ),
        ) { fraction, _ ->
            value = pickerSwellCurve(fraction)
        }
        value = 0f
    }
    return value
}

/** Out to full at the peak, then back: a swell, not a step. */
internal fun pickerSwellCurve(fraction: Float): Float {
    val peak = CartSheetLiterals.SWELL_PEAK
    return if (fraction <= peak) fraction / peak else (1f - fraction) / (1f - peak)
}

/** The hint that the summary above has cards behind it. */
@Composable
private fun PickerUnfoldGlyph(modifier: Modifier = Modifier) {
    val color = LocalSeatLayerPickerTheme.current.mutedText
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.105f
        val inset = size.width * 0.22f
        val mid = size.height / 2f
        val reach = size.height * 0.2f
        val cap = androidx.compose.ui.graphics.StrokeCap.Round
        drawLine(
            color,
            Offset(inset, mid - reach * 0.4f),
            Offset(size.width / 2f, mid - reach * 1.2f),
            stroke,
            cap,
        )
        drawLine(
            color,
            Offset(size.width / 2f, mid - reach * 1.2f),
            Offset(size.width - inset, mid - reach * 0.4f),
            stroke,
            cap,
        )
        drawLine(
            color,
            Offset(inset, mid + reach * 0.4f),
            Offset(size.width / 2f, mid + reach * 1.2f),
            stroke,
            cap,
        )
        drawLine(
            color,
            Offset(size.width / 2f, mid + reach * 1.2f),
            Offset(size.width - inset, mid + reach * 0.4f),
            stroke,
            cap,
        )
    }
}
