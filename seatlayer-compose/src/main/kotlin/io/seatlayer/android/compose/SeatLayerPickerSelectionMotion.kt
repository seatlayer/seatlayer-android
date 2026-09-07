package io.seatlayer.android.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerCartLanding
import kotlinx.coroutines.delay

/**
 * The ticket itself, travelling from the card to the summary that counted it.
 *
 * A LABELLED chip in the category's own colour, not an anonymous dot: what left
 * the card is a specific seat, and the buyer should be able to read which one on
 * its way to a summary that has already counted it.
 *
 * The chip is also the one surface that knows when the flight is over, so it is
 * the chip that advances the landing: the footer's swell waits on `Landed`, and
 * the map gives its lift back only on `Released`. Under reduced motion no chip
 * is created at all, so no landing is ever begun and nothing waits on a beat
 * that never plays.
 */
@Composable
internal fun SeatLayerSelectionFlight(
    wide: Boolean,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val state = LocalSeatLayerPickerFlight.current
    val flight = state.flight
    var origin by remember { mutableStateOf(Offset.Zero) }
    val progress = remember(flight?.id) { Animatable(0f) }

    // The landing is the chip's to report, and it is reported EXACTLY once per
    // flight — including when the chip is taken off screen mid-flight, which
    // would otherwise leave the footer waiting for a swell and the map holding
    // a lift it can never give back.
    DisposableEffect(flight?.id) {
        onDispose {
            // Only a flight still in the air is stranded. One that landed, and
            // one already replaced by a later ticket, are both somebody else's.
            if (flight != null && state.flight?.id == flight.id) {
                scope.controller.setCartLanding(SeatLayerPickerCartLanding.Released)
            }
        }
    }
    LaunchedEffect(flight?.id) {
        val leaving = flight ?: return@LaunchedEffect
        progress.animateTo(
            1f,
            animationSpec = tween(
                durationMillis = SeatLayerMotionTokens.CONFIRM_FLIGHT,
                easing = FLIGHT_SPRING,
            ),
        )
        scope.controller.setCartLanding(SeatLayerPickerCartLanding.Landed)
        // The tray acknowledges the arrival before the map moves: a map pulling
        // back under a swell that has not started reads as two motions fighting.
        delay(SeatLayerMotionTokens.BUMP.toLong())
        scope.controller.setCartLanding(SeatLayerPickerCartLanding.Released)
        state.landed(leaving)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            .clearAndSetSemantics { },
    ) {
        if (flight == null) return@BoxWithConstraints
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val aim = with(density) { (bottomPadding + PEEK_AIM).toPx() }
        val from = flight.from?.minus(origin) ?: Offset(width / 2f, height / 2f)
        // With nothing measured yet the phone's own aim stands in: the summary
        // line of the collapsed sheet, just above the foot of the screen.
        val to = flight.to?.minus(origin)
            ?: Offset(if (wide) width - aim else width / 2f, height - aim)
        val raw = progress.value
        val point = Offset(
            from.x + (to.x - from.x) * raw,
            from.y + (to.y - from.y) * raw,
        )
        val ink = seatLayerPickerConfirmBandInk(flight.color)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer {
                    // It appears at just over half size, reaches full size a
                    // fifth of the way along, and shrinks as it lands.
                    scaleX = seatLayerFlightScale(raw)
                    scaleY = scaleX
                    alpha = seatLayerFlightOpacity(raw)
                    translationX = point.x - size.width / 2f
                    translationY = point.y - size.height / 2f
                }
                .defaultMinSize(minWidth = CHIP_MIN, minHeight = CHIP_MIN),
            shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
            color = flight.color,
            contentColor = ink,
        ) {
            Box(contentAlignment = Alignment.Center) {
                SeatLayerPickerText(
                    flight.label,
                    modifier = Modifier.padding(horizontal = CHIP_PAD_X),
                    color = ink,
                    fontSize = CHIP_FONT.sp,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** When the chip reaches full size, as a fraction of its flight. */
private const val FLY_RISE = 0.22f

internal fun seatLayerFlightScale(raw: Float): Float = if (raw < FLY_RISE) {
    0.6f + 0.4f * (raw / FLY_RISE)
} else {
    1f - 0.45f * ((raw - FLY_RISE) / (1f - FLY_RISE))
}

internal fun seatLayerFlightOpacity(raw: Float): Float = (
    if (raw < FLY_RISE) {
        raw / FLY_RISE
    } else {
        1f - 0.85f * ((raw - FLY_RISE) / (1f - FLY_RISE))
    }
    ).coerceIn(0f, 1f)

private val FLIGHT_SPRING = CubicBezierEasing(
    SeatLayerCurveTokens.SPRING_X1,
    SeatLayerCurveTokens.SPRING_Y1,
    SeatLayerCurveTokens.SPRING_X2,
    SeatLayerCurveTokens.SPRING_Y2,
)

/**
 * How far above the foot the chip aims when nothing has been measured.
 *
 * The collapsed sheet's summary line is what the ticket has just changed, so
 * that is where it lands.
 */
private val PEEK_AIM: Dp = 24.dp

private val CHIP_MIN: Dp = 24.dp
private val CHIP_PAD_X: Dp = 7.dp
private const val CHIP_FONT = 11f
