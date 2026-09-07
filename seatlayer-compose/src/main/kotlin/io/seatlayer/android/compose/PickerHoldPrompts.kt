package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * "Your seats are held for 0:48. Need more time?"
 *
 * Offered only in the last minute, and only while there is a hold to extend:
 * asked earlier it is a question about a problem the buyer does not have yet.
 * OFF on a phone by default — there the header's countdown is the one timer
 * the buyer is given, and a card inside the last minute is a second decision
 * at the worst moment.
 */

/** Below this much time left, the buyer is offered more of it. */
public const val SEATLAYER_HOLD_EXTEND_THRESHOLD_MS: Long = 60_000L

/** One tap asks for this many minutes, and the button prints the number. */
public const val SEATLAYER_HOLD_EXTEND_STEP_MINUTES: Int = 5

/** How the picker answers one extend attempt. */
public enum class SeatLayerHoldExtendOutcome {
    /** The server gave the time; say so, and retire the control. */
    Granted,

    /**
     * The server declined.
     *
     * SILENCE. A hold resumed from a host carries extensions this picker never
     * offered, and there is no sentence the buyer needs about it. The control
     * retires all the same.
     */
    Refused,

    /** Nothing was decided, so it is worth saying and worth offering again. */
    TransportFailure,
}

/** What one attempt decided, and whether the control comes back for it. */
public data class SeatLayerHoldExtendAnswer(
    val outcome: SeatLayerHoldExtendOutcome,
    val retires: Boolean,
    val announcement: String?,
)

/**
 * The policy, apart from the code that fires one attempt.
 *
 * One per hold, three endings: a buyer who can keep asking has been handed a
 * way to sit on inventory by reflex rather than by decision, and a countdown
 * that can always be pushed back is not a deadline.
 */
public fun seatLayerHoldExtendAnswer(
    strings: SeatLayerPickerStrings,
    outcome: SeatLayerHoldExtendOutcome,
): SeatLayerHoldExtendAnswer = when (outcome) {
    SeatLayerHoldExtendOutcome.Granted -> SeatLayerHoldExtendAnswer(
        outcome = outcome,
        retires = true,
        announcement = strings.moreTimeAdded,
    )
    SeatLayerHoldExtendOutcome.Refused -> SeatLayerHoldExtendAnswer(
        outcome = outcome,
        retires = true,
        announcement = null,
    )
    SeatLayerHoldExtendOutcome.TransportFailure -> SeatLayerHoldExtendAnswer(
        outcome = outcome,
        retires = false,
        announcement = strings.couldNotAddMoreTime,
    )
}

/** Epoch seconds and epoch milliseconds both arrive here; both are answered. */
internal fun seatLayerHoldExpiryMillis(expiresAt: Double): Long =
    if (expiresAt < 10_000_000_000.0) (expiresAt * 1_000.0).toLong() else expiresAt.toLong()

/**
 * The card offering more time on a hold.
 *
 * Mounted only where the layout asks for it, which by default is the wide
 * layout alone. [onAnnounce] hands the two spoken outcomes to whatever the
 * composition uses for transient statements; unwired, the card says them
 * itself as a live region under the row.
 */
@Composable
public fun SeatLayerPickerExtendHoldPrompt(
    modifier: Modifier = Modifier,
    onAnnounce: ((SeatLayerHoldExtendAnswer) -> Unit)? = null,
) {
    val scope = LocalSeatLayerPickerScope.current
    val hold = scope.state.snapshot?.hold
    val expiresAt = hold?.expiresAt
    // No hold means the next one is a NEW hold, and a new hold gets its own
    // extension. Snapshots deliberately never carry the hold id, so the gap
    // between one hold ending and the next beginning is the only honest
    // boundary available.
    var spent by remember(scope.stateHolder) { mutableStateOf(false) }
    var adding by remember(scope.stateHolder) { mutableStateOf(false) }
    var spoken by remember(scope.stateHolder) { mutableStateOf<String?>(null) }
    LaunchedEffect(hold?.active) { if (hold?.active != true) spent = false }

    if (hold == null || !hold.active || expiresAt == null) return
    // A host-owned hold is not this picker's to extend (§4.8).
    if (hold.owner == "host") return
    // A hold the host has taken to checkout is checkout's business now.
    if (scope.state.presentation.checkoutHoldsTheCart) return
    if (spent) return

    val expiryMillis = seatLayerHoldExpiryMillis(expiresAt)
    var remaining by remember(expiryMillis) {
        mutableStateOf((expiryMillis - System.currentTimeMillis()).coerceAtLeast(0L))
    }
    // The clock only runs while there is something to count. Ticking through
    // the whole hold to reach its last minute wakes the frame every second for
    // a control that is not on screen, and on a deterministic test clock it
    // drags every other surface's timing along with it.
    LaunchedEffect(expiryMillis) {
        val untilDue = remaining - SEATLAYER_HOLD_EXTEND_THRESHOLD_MS
        if (untilDue > 0) {
            delay(untilDue)
            remaining = (expiryMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        while (remaining > 0) {
            delay(minOf(HOLD_TICK_MS, remaining))
            remaining = (expiryMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        }
    }
    if (remaining <= 0L || remaining > SEATLAYER_HOLD_EXTEND_THRESHOLD_MS) return

    val coroutineScope = rememberCoroutineScope()
    val clock = "%d:%02d".format(remaining / 60_000L, remaining / 1_000L % 60L)
    Surface(
        modifier = modifier.semantics(mergeDescendants = false) {
            liveRegion = LiveRegionMode.Polite
        },
        color = scope.theme.surface,
        contentColor = scope.theme.text,
        shape = RoundedCornerShape(SeatLayerRadiusTokens.BASE.dp),
        border = BorderStroke(1.dp, scope.theme.divider),
        shadowElevation = SeatLayerElevationTokens.SHEET.dp,
    ) {
        Column(
            Modifier.padding(
                start = EXTEND_PAD_START.dp,
                top = EXTEND_PAD_Y.dp,
                end = EXTEND_PAD_END.dp,
                bottom = EXTEND_PAD_Y.dp,
            ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(EXTEND_GAP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    scope.strings.seatsHeldForNeedMoreTime(clock),
                    modifier = Modifier.weight(1f),
                    color = scope.theme.text,
                    fontSize = EXTEND_FONT_SIZE.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = scope.theme.fontFamily,
                )
                Button(
                    onClick = {
                        if (adding) return@Button
                        adding = true
                        coroutineScope.launch {
                            val answer = seatLayerHoldExtendAnswer(
                                scope.strings,
                                extendPickerHold(scope),
                            )
                            adding = false
                            if (answer.retires) spent = true
                            if (onAnnounce != null) {
                                onAnnounce(answer)
                            } else {
                                spoken = answer.announcement
                            }
                        }
                    },
                    enabled = !adding,
                    modifier = Modifier.sizeIn(
                        minHeight = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp,
                    ),
                    shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scope.theme.accent,
                        contentColor = scope.theme.onAccent,
                    ),
                ) {
                    // The button says exactly what one tap does: "Add time"
                    // beside a countdown reads like an invitation to choose an
                    // amount, and there is nothing to choose.
                    Text(
                        if (adding) {
                            scope.strings.addingEllipsis
                        } else {
                            scope.strings.addMinutes(SEATLAYER_HOLD_EXTEND_STEP_MINUTES)
                        },
                        fontSize = EXTEND_FONT_SIZE.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = scope.theme.fontFamily,
                    )
                }
                // A way to say no: an offer with no refusal is a thing in the
                // way. Dismissing spends nothing and retires the control the
                // same way.
                Surface(
                    onClick = { if (!adding) spent = true },
                    enabled = !adding,
                    modifier = Modifier
                        .size(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = scope.strings.close
                        },
                    color = scope.theme.surface,
                    contentColor = scope.theme.mutedText,
                    shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
                ) {
                    SeatLayerPickerIcon(
                        SeatLayerPickerGlyph.Close,
                        scope.theme.mutedText,
                        Modifier
                            .padding(EXTEND_CLOSE_INSET.dp)
                            .clearAndSetSemantics { },
                    )
                }
            }
            spoken?.let { sentence ->
                Spacer(Modifier.size(EXTEND_SPOKEN_GAP.dp))
                Text(
                    sentence,
                    color = scope.theme.mutedText,
                    fontSize = EXTEND_SPOKEN_SIZE.sp,
                    fontFamily = scope.theme.fontFamily,
                )
            }
        }
    }
}

/**
 * One attempt, reported as an outcome rather than as an exception.
 *
 * A runtime that answers "no" is not a fault; a transport that never answered
 * decided nothing at all, and only the second is worth offering again.
 */
private suspend fun extendPickerHold(scope: SeatLayerPickerScope): SeatLayerHoldExtendOutcome =
    try {
        val ttl = scope.options.holdTtl?.inWholeMilliseconds?.toInt()
        val snapshot = scope.controller.extendHold(ttl)
        if (snapshot == null) {
            SeatLayerHoldExtendOutcome.Refused
        } else {
            SeatLayerHoldExtendOutcome.Granted
        }
    } catch (error: SeatLayerException.Bridge) {
        scope.callbacks.onError(error)
        SeatLayerHoldExtendOutcome.Refused
    } catch (error: SeatLayerException) {
        scope.callbacks.onError(error)
        SeatLayerHoldExtendOutcome.TransportFailure
    }

private const val HOLD_TICK_MS: Long = 1_000L
private const val EXTEND_PAD_START: Float = 16f
private const val EXTEND_PAD_END: Float = 12f
private const val EXTEND_PAD_Y: Float = 10f
private const val EXTEND_GAP: Float = 12f
private const val EXTEND_FONT_SIZE: Float = 12.5f
private const val EXTEND_CLOSE_INSET: Float = 13f
private const val EXTEND_SPOKEN_GAP: Float = 6f
private const val EXTEND_SPOKEN_SIZE: Float = 11.5f

/**
 * Whether this composition mounts the extend prompt.
 *
 * Null is auto, which is OFF on the phone and on in the wide layout: the phone
 * picker gives the buyer ONE timer, the header's hold countdown, and a card
 * arriving over the map inside the last minute is a second decision at the
 * worst moment.
 */
internal fun SeatLayerPickerOptions.resolvedExtendHoldPrompt(wide: Boolean): Boolean =
    showExtendHoldPrompt ?: wide
