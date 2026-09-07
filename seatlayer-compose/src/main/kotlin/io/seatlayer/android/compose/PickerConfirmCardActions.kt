package io.seatlayer.android.compose

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The two answers, and the choreography of pressing one.
 *
 * The row is two boxes inside the card's gutter rather than a bar fused to its
 * bottom edge: a corner-to-corner fill reads as the card's frame instead of as
 * a thing to press. They are not equally likely answers, so they are not
 * equally wide.
 */

/** Just over a third of the row to leave; the rest to accept. */
internal const val SEATLAYER_CONFIRM_CANCEL_SHARE: Float = 0.34f

/** The daylight between the boxes of the decision row. */
private val ACTION_GAP: Dp = 8.dp

/** The leading square that carries the way into the scene. */
private val SQUARE_SIZE: Dp = 44.dp

/** How far the invitation halo reaches beyond the button's own edge. */
private val INVITE_HALO: Dp = 6.dp

/** Whether this device is playing animations at all. */
@Composable
internal fun seatLayerAnimationsEnabled(): Boolean = remember {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
}

private val EASE_ENTER = CubicBezierEasing(
    SeatLayerCurveTokens.EASE_ENTER_X1,
    SeatLayerCurveTokens.EASE_ENTER_Y1,
    SeatLayerCurveTokens.EASE_ENTER_X2,
    SeatLayerCurveTokens.EASE_ENTER_Y2,
)

/**
 * Cancel, the primary answer, and the optional way into the scene.
 *
 * [sweep] runs 0 → 1 while the press is being acknowledged; the primary fills
 * with its own ink from the leading edge under a mark that draws itself, and
 * the label turns to the past tense. Nothing here waits for the inventory
 * command: the cart has already been told.
 */
@Composable
internal fun SeatLayerConfirmActions(
    primaryLabel: String,
    cancelLabel: String,
    destructive: Boolean,
    answered: Boolean,
    sweep: Float,
    invite: Boolean,
    enabled: Boolean,
    onCancel: () -> Unit,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    show3DSquare: Boolean = false,
    on3D: (() -> Unit)? = null,
    primaryFocus: FocusRequester? = null,
) {
    val scope = LocalSeatLayerPickerScope.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val rowWidth = maxWidth
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ACTION_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (show3DSquare && on3D != null) {
                SeatLayerSee3DSquare(enabled = enabled, onClick = on3D)
            }
            // The square and its gap come out of what the primary had, never
            // out of Cancel: leaving is the same size of decision either way.
            SeatLayerCancelButton(
                label = cancelLabel,
                enabled = enabled,
                onClick = onCancel,
                modifier = Modifier.width(rowWidth * SEATLAYER_CONFIRM_CANCEL_SHARE),
            )
            SeatLayerPrimaryAnswerButton(
                label = primaryLabel,
                destructive = destructive,
                answered = answered,
                sweep = sweep,
                invite = invite,
                enabled = enabled,
                onClick = onPrimary,
                focusRequester = primaryFocus,
                modifier = Modifier.weight(1f),
            )
        }
        // The row's own height comes from the tallest box; both boxes fill it.
        Spacer(Modifier.fillMaxHeight())
    }
}

@Composable
private fun SeatLayerCancelButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val style = scope.styles[SeatLayerPickerPart.ConfirmCard]
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(SeatLayerRadiusTokens.BUTTON.dp),
        color = Color.Transparent,
        contentColor = scope.theme.mutedText,
        border = BorderStroke(Dp.Hairline, scope.theme.divider),
    ) {
        Box(contentAlignment = Alignment.Center) {
            SeatLayerPickerText(
                label,
                color = scope.theme.mutedText,
                fontSize = SeatLayerTypeTokens.CONFIRM_ACTION_SIZE.sp,
                fontWeight = FontWeight(SeatLayerTypeTokens.CONFIRM_ACTION_WEIGHT),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun SeatLayerPrimaryAnswerButton(
    label: String,
    destructive: Boolean,
    answered: Boolean,
    sweep: Float,
    invite: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val ground = if (destructive) scope.theme.error else scope.theme.accent
    val ink = if (destructive) Color.White else scope.theme.onAccent
    val animate = seatLayerAnimationsEnabled()
    // The halo is drawn OUTSIDE the button's own clip: inside it, the growth
    // that is supposed to read as breathing is cut off at the corner radius
    // and never appears at all.
    val breathe = if (invite && animate) rememberInviteBreathe() else 0f
    Box(
        modifier = modifier
            .fillMaxHeight()
            .drawBehind {
                if (breathe <= 0f) return@drawBehind
                val reach = INVITE_HALO.toPx() * breathe
                drawRoundRect(
                    color = ground.copy(alpha = 0.28f * (1f - breathe)),
                    topLeft = Offset(-reach, -reach),
                    size = Size(size.width + reach * 2, size.height + reach * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        (SeatLayerRadiusTokens.BUTTON.dp.toPx() + reach),
                    ),
                )
            },
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    focusRequester?.let {
                        Modifier.focusRequester(it)
                    } ?: Modifier,
                ),
            shape = RoundedCornerShape(SeatLayerRadiusTokens.BUTTON.dp),
            color = ground,
            contentColor = ink,
        ) {
            Box(Modifier.clipToBounds(), contentAlignment = Alignment.Center) {
                if (sweep > 0f) {
                    // The button filling with its OWN ink: the acknowledgement
                    // is a change of weight, not a change of colour.
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(sweep)
                            .background(ink.copy(alpha = 0.22f))
                            .align(Alignment.CenterStart),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (sweep > 0f) {
                        SeatLayerAnswerMark(
                            cross = destructive,
                            progress = sweep,
                            color = ink,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    SeatLayerPickerText(
                        label,
                        color = ink,
                        fontSize = SeatLayerTypeTokens.CONFIRM_ACTION_SIZE.sp,
                        fontWeight = FontWeight(SeatLayerTypeTokens.CONFIRM_ACTION_WEIGHT),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** A tick, or a cross for a remove — drawn as far as [progress] has reached. */
@Composable
internal fun SeatLayerAnswerMark(
    cross: Boolean,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier.clearAndSetSemantics { }) {
        drawAnswerMark(cross, progress.coerceIn(0f, 1f), color)
    }
}

private fun DrawScope.drawAnswerMark(cross: Boolean, progress: Float, color: Color) {
    val stroke = Stroke(
        width = size.minDimension * 0.12f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
    fun between(from: Offset, to: Offset, t: Float) =
        Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
    // Each mark is two legs; the first is drawn over the first 40 % of the
    // sweep and the second over the rest, so the shape appears to be written.
    val first = (progress / 0.4f).coerceIn(0f, 1f)
    val second = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)
    if (cross) {
        val a = point(0.24f, 0.24f)
        val b = point(0.76f, 0.76f)
        val c = point(0.76f, 0.24f)
        val d = point(0.24f, 0.76f)
        drawLine(color, a, between(a, b, first), stroke.width, stroke.cap)
        if (second > 0f) drawLine(color, c, between(c, d, second), stroke.width, stroke.cap)
        return
    }
    val a = point(0.18f, 0.52f)
    val b = point(0.4f, 0.74f)
    val c = point(0.82f, 0.26f)
    drawLine(color, a, between(a, b, first), stroke.width, stroke.cap)
    if (second > 0f) drawLine(color, b, between(b, c, second), stroke.width, stroke.cap)
}

/** The 44 × 44 way into the scene, drawn only where no photograph took it. */
@Composable
internal fun SeatLayerSee3DSquare(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(SQUARE_SIZE)
            .semantics { contentDescription = scope.strings.seeItIn3D },
        shape = RoundedCornerShape(SeatLayerRadiusTokens.BUTTON.dp),
        color = pickerWash(scope.theme.accent, 0.12f, scope.theme.surface),
        contentColor = scope.theme.text,
        border = BorderStroke(Dp.Hairline, scope.theme.divider),
    ) {
        Box(contentAlignment = Alignment.Center) {
            SeatLayerPickerText(
                scope.strings.venue3D,
                color = scope.theme.text,
                fontSize = SeatLayerSizeTokens.CONFIRM3D_SQUARE_FONT_SIZE.sp,
                fontWeight = FontWeight.W800,
                maxLines = 1,
            )
        }
    }
}

/** The halo's ~2 % breath, restarting for as long as the invitation stands. */
@Composable
private fun rememberInviteBreathe(): Float {
    val transition = rememberInfiniteTransition(label = "seatlayer.invite")
    val value by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = SeatLayerMotionTokens.INVITE_BREATHE,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "seatlayer.invite.breathe",
    )
    return value
}

/**
 * The press sweep, run once and then held.
 *
 * The card's departure waits on this; the cart does not. Under reduced motion
 * it completes at once, so nothing is left waiting on a beat that never plays.
 */
@Composable
internal fun rememberPressSweep(
    answered: Boolean,
    animate: Boolean,
): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(answered, animate) {
        if (!answered) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        if (!animate) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.animateTo(
            1f,
            animationSpec = tween(
                durationMillis = SeatLayerMotionTokens.PRESS_SWEEP,
                easing = EASE_ENTER,
            ),
        )
    }
    return progress.value
}
