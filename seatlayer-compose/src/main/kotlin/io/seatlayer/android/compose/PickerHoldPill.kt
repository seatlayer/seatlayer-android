package io.seatlayer.android.compose

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The picker's ONE clock.
 *
 * It shows the full server hold. The SDK never names a hold length of its own
 * and never subtracts a "display buffer": a countdown the SDK invents is a
 * promise the server did not make. The pill is drawn for as long as a live hold
 * exists, whoever owns it — a host that draws its own clock turns this one off
 * with `showHoldPill`.
 */
@Composable
public fun SeatLayerPickerHoldCountdown(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val hold = scope.state.snapshot?.hold ?: return
    val expiresAt = hold.expiresAt ?: return
    if (!hold.active) return
    val reducedMotion = pickerReducedMotion()
    val expiryMillis = seatLayerNormalizeEpochMillis(expiresAt)
    var remaining by remember(expiryMillis) {
        mutableLongStateOf(seatLayerHoldSecondsLeft(expiryMillis, System.currentTimeMillis()))
    }
    LaunchedEffect(expiryMillis) {
        while (remaining > 0) {
            delay(HoldPillLiterals.TICK_MILLIS)
            remaining = seatLayerHoldSecondsLeft(expiryMillis, System.currentTimeMillis())
        }
    }
    val expiring = seatLayerHoldIsExpiring(remaining)
    val theme = scope.theme
    val ground = if (expiring) {
        theme.accent
    } else {
        lerp(theme.surface, theme.accent, HoldPillLiterals.RESTING_ACCENT_MIX)
    }
    val ink = if (expiring) theme.onAccent else theme.accent

    // The countdown is said on the minute, then every second of the last
    // minute. A screen reader interrupted once a second cannot be used to buy
    // anything.
    var announcement by remember(expiryMillis) { mutableStateOf<String?>(null) }
    LaunchedEffect(remaining) {
        seatLayerHoldAnnouncement(remaining, scope.strings)?.let { announcement = it }
    }

    val breathe = if (expiring && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "holdPillDot")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = HoldPillLiterals.DOT_BREATH_FLOOR,
            animationSpec = infiniteRepeatable(
                animation = tween(SeatLayerMotionTokens.INVITE_BREATHE),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "holdPillDotAlpha",
        ).value
    } else {
        1f
    }

    Surface(
        modifier = modifier
            .sizeIn(minHeight = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement ?: scope.strings.timeRemaining
            },
        shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
        color = ground,
        contentColor = ink,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = HoldPillLiterals.PAD_X.dp,
                vertical = HoldPillLiterals.PAD_Y.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                Modifier
                    .size(HoldPillLiterals.DOT_SIZE.dp)
                    .alpha(breathe)
                    .background(ink, CircleShape),
            )
            Spacer(Modifier.width(HoldPillLiterals.DOT_GAP.dp))
            Text(
                text = scope.strings.heldFor(seatLayerHoldClock(remaining)),
                fontSize = SeatLayerTypeTokens.PILL_SIZE.sp,
                fontWeight = FontWeight(SeatLayerTypeTokens.PILL_WEIGHT),
                maxLines = 1,
            )
        }
    }
}

/** The runtime reports seconds or milliseconds; both mean the same instant. */
internal fun seatLayerNormalizeEpochMillis(value: Double): Long =
    if (value < 10_000_000_000.0) (value * 1_000.0).toLong() else value.toLong()

internal fun seatLayerHoldSecondsLeft(expiryMillis: Long, nowMillis: Long): Long =
    ((expiryMillis - nowMillis) / 1_000L).coerceAtLeast(0L)

internal fun seatLayerHoldIsExpiring(remainingSeconds: Long): Boolean =
    remainingSeconds in 1 until SECONDS_PER_MINUTE

internal fun seatLayerHoldClock(remainingSeconds: Long): String =
    "%d:%02d".format(remainingSeconds / SECONDS_PER_MINUTE, remainingSeconds % SECONDS_PER_MINUTE)

/**
 * What the countdown says out loud, and when it says nothing.
 *
 * Pure, so the throttle is the same rule on every platform: each minute mark,
 * then every second of the last minute, and silence in between.
 */
internal fun seatLayerHoldAnnouncement(
    remainingSeconds: Long,
    strings: SeatLayerPickerStrings,
): String? = when {
    remainingSeconds <= 0L -> null
    remainingSeconds < SECONDS_PER_MINUTE -> strings.holdSecondsLeft(remainingSeconds.toInt())
    remainingSeconds % SECONDS_PER_MINUTE == 0L ->
        strings.holdMinutesLeft((remainingSeconds / SECONDS_PER_MINUTE).toInt())
    else -> null
}

private const val SECONDS_PER_MINUTE = 60L

/** Pill-local numbers the design source keeps as literals. Reported as a gap. */
private object HoldPillLiterals {
    const val TICK_MILLIS = 500L
    const val RESTING_ACCENT_MIX = 0.12f
    const val DOT_SIZE = 6f
    const val DOT_GAP = 6f
    const val DOT_BREATH_FLOOR = 0.45f
    const val PAD_X = 10f
    const val PAD_Y = 5f
}
