package io.seatlayer.android.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerState
import kotlinx.coroutines.launch

/*
 * The states the picker has to be able to show instead of a seat map.
 *
 * Sales that have ended, an event with nothing left, and a buyer session the
 * server will no longer answer for. Each is a designed statement rather than a
 * screen full of disabled controls: a buyer who cannot do something should be
 * told what is true, once, in the picker's own voice.
 */

/**
 * Whether this event has nothing seated left to sell.
 *
 * Every category that is for sale reports zero free, there is at least one
 * such category, and the chart has no standing areas — a venue whose stalls
 * are gone but whose floor is not is not sold out, it is nearly sold out, and
 * saying otherwise sends a buyer away from tickets that exist.
 */
public fun seatLayerPickerIsSoldOut(state: SeatLayerPickerState): Boolean {
    val snapshot = state.snapshot ?: return false
    if (snapshot.generalAdmissionAreas.isNotEmpty()) return false
    val seated = snapshot.categories.filterNot { it.notForSale }
    if (seated.isEmpty()) return false
    return seated.all { it.available <= 0 }
}

/** Nothing seated is left. Informational — the picker offers no waitlist. */
@Composable
public fun SeatLayerPickerSoldOutOverlay(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    if (!scope.state.isReady || !seatLayerPickerIsSoldOut(scope.state)) return
    val snapshot = scope.state.snapshot
    val eyebrow = (
        snapshot?.branding?.brandName
            ?: snapshot?.event?.name
            ?: scope.strings.soldOutEyebrow
        ).uppercase()
    // A buyer-facing state owns the screen and grows with the platform text
    // setting to the state ceiling: past it the layout breaks, and a clipped
    // sentence is worse than a slightly smaller one.
    SeatLayerPickerTypeClamp(SeatLayerTypeTokens.SCALE_CLAMP_STATE) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(scope.theme.background.copy(alpha = STATE_VEIL_OPACITY))
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(STATE_GUTTER.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    eyebrow,
                    color = scope.theme.accent,
                    fontSize = SOLD_OUT_EYEBROW_SIZE.sp,
                    letterSpacing = SOLD_OUT_EYEBROW_TRACKING.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = scope.theme.fontFamily,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(STATE_SMALL_GAP.dp))
                Text(
                    scope.strings.soldOutTitle,
                    modifier = Modifier.semantics { heading() },
                    color = scope.theme.text,
                    fontSize = SOLD_OUT_TITLE_SIZE.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = scope.theme.fontFamily,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(STATE_SMALL_GAP.dp))
                Text(
                    scope.strings.soldOutCopy,
                    modifier = Modifier.widthIn(max = STATE_COPY_WIDTH.dp),
                    color = scope.theme.mutedText,
                    fontSize = STATE_BODY_SIZE.sp,
                    fontFamily = scope.theme.fontFamily,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * "Sales are closed", said once, where the tickets would have been.
 *
 * Neutral by design: an accent-coloured panel reads as something to act on,
 * and there is nothing here to act on.
 */
@Composable
public fun SeatLayerPickerSalesClosedStatement(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val event = scope.state.snapshot?.event ?: return
    if (!event.salesClosed) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scope.theme.text
            .copy(alpha = SALES_CLOSED_WASH)
            .compositeOver(scope.theme.surface),
        contentColor = scope.theme.text,
        shape = RoundedCornerShape(SALES_CLOSED_RADIUS.dp),
        border = BorderStroke(1.dp, scope.theme.divider),
    ) {
        Column(Modifier.padding(SALES_CLOSED_PAD.dp)) {
            Text(
                scope.strings.salesClosed,
                color = scope.theme.text,
                fontSize = SALES_CLOSED_TITLE_SIZE.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = scope.theme.fontFamily,
            )
            Spacer(Modifier.height(STATE_HAIR_GAP.dp))
            Text(
                scope.strings.salesClosedCopy,
                color = scope.theme.mutedText,
                fontSize = SALES_CLOSED_BODY_SIZE.sp,
                fontFamily = scope.theme.fontFamily,
            )
            event.venue?.takeIf(String::isNotBlank)?.let { venue ->
                Text(
                    venue,
                    color = scope.theme.text,
                    fontSize = SALES_CLOSED_BODY_SIZE.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = scope.theme.fontFamily,
                )
            }
        }
    }
}

/** One access reason's title, sentence, and the action that would help. */
public data class SeatLayerPickerAccessTelling(
    val title: String,
    val body: String,
    val action: String,
)

/**
 * The telling for [reason], falling back to the unverified wording.
 *
 * EVERY REASON HAS EXACTLY ONE ACTION. Two of these used to have none, which
 * left the buyer behind a panel with nothing to press and only the header's
 * close to get out of it. A recovery that might not work is still a way
 * forward; a dead end is not.
 */
public fun seatLayerPickerAccessTelling(
    strings: SeatLayerPickerStrings,
    reason: String?,
): SeatLayerPickerAccessTelling = when (reason) {
    "paused" -> SeatLayerPickerAccessTelling(
        title = strings.accessPausedTitle,
        body = strings.accessPausedCopy,
        // "Try again" is this screen's verb; refresh is the other three's, and
        // one string cannot carry both.
        action = strings.retry,
    )
    "revoked" -> SeatLayerPickerAccessTelling(
        title = strings.accessRevokedTitle,
        body = strings.accessRevokedCopy,
        action = strings.accessRefresh,
    )
    "no_token", "provider_failed" -> SeatLayerPickerAccessTelling(
        title = strings.accessExpiredTitle,
        body = strings.accessExpiredCopy,
        action = strings.accessRefresh,
    )
    else -> SeatLayerPickerAccessTelling(
        title = strings.accessUnverifiedTitle,
        body = strings.accessUnverifiedCopy,
        action = strings.accessRefresh,
    )
}

/** Whether the runtime is refusing to answer for this buyer's access. */
internal fun SeatLayerPickerState.pickerAccessUnavailable(): Boolean {
    val snapshot = snapshot ?: return false
    return snapshot.accessConfigured && snapshot.accessStatus == "unavailable"
}

/**
 * What the picker says when the buyer's access to these seats has lapsed.
 *
 * Four different things are true behind one failure and the buyer can act on
 * only some of them, so the copy names which one happened rather than asking
 * them to guess.
 */
@Composable
public fun SeatLayerPickerAccessPanel(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    if (!scope.state.pickerAccessUnavailable()) return
    val reason = scope.state.snapshot?.accessReason
    val telling = seatLayerPickerAccessTelling(scope.strings, reason)
    var retrying by remember(scope.stateHolder) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    // A buyer-facing state owns the screen and grows with the platform text
    // setting to the state ceiling: past it the layout breaks, and a clipped
    // sentence is worse than a slightly smaller one.
    SeatLayerPickerTypeClamp(SeatLayerTypeTokens.SCALE_CLAMP_STATE) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(scope.theme.background.copy(alpha = ACCESS_VEIL_OPACITY))
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .padding(STATE_GUTTER.dp)
                    .widthIn(max = ACCESS_CARD_WIDTH.dp),
                color = scope.theme.surface,
                contentColor = scope.theme.text,
                shape = RoundedCornerShape(scope.theme.radius),
                border = BorderStroke(1.dp, scope.theme.divider),
                shadowElevation = SeatLayerElevationTokens.CONFIRM_CARD.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = STATE_GUTTER.dp,
                        top = ACCESS_CARD_PAD_TOP.dp,
                        end = STATE_GUTTER.dp,
                        bottom = STATE_GUTTER.dp,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AccessBadge(spinning = retrying)
                    Spacer(Modifier.height(STATE_GAP.dp))
                    Text(
                        telling.title,
                        modifier = Modifier.semantics { heading() },
                        color = scope.theme.text,
                        fontSize = ACCESS_TITLE_SIZE.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = scope.theme.fontFamily,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(STATE_SMALL_GAP.dp))
                    Text(
                        telling.body,
                        color = scope.theme.mutedText,
                        fontSize = STATE_BODY_SIZE.sp,
                        fontFamily = scope.theme.fontFamily,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(ACCESS_ACTION_GAP.dp))
                    Button(
                        onClick = {
                            if (retrying) return@Button
                            retrying = true
                            coroutineScope.launch {
                                try {
                                    recoverPickerAccess(scope, paused = reason == "paused")
                                } finally {
                                    retrying = false
                                }
                            }
                        },
                        enabled = !retrying,
                        modifier = Modifier.sizeIn(
                            minHeight = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp,
                        ),
                        shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scope.theme.accent,
                            contentColor = scope.theme.onAccent,
                        ),
                    ) {
                        Text(
                            telling.action,
                            fontSize = STATE_BODY_SIZE.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = scope.theme.fontFamily,
                        )
                    }
                }
            }
        }
    }
}

/**
 * What the recovery button actually does.
 *
 * IN PLACE FIRST, ALWAYS: refreshing re-bootstraps the session and re-reads the
 * chart without the map ever going away, so the buyer keeps their camera and
 * their picks. A reload is the last resort, and only when nobody else is
 * listening — a host that took [SeatLayerPickerCallbacks.onAccessUnavailable]
 * has been told already and may be running its own recovery, which remounting
 * under it would destroy. The paused screen keeps its plain retry: nothing is
 * wrong with the session there, the organizer has simply stopped selling.
 */
private suspend fun recoverPickerAccess(
    scope: SeatLayerPickerScope,
    paused: Boolean,
) {
    if (paused) {
        scope.controller.retry()
        return
    }
    val refreshed = scope.performActionResult { scope.controller.refreshAccess() }
    if (!refreshed && !scope.hostOwnsAccessRecovery) scope.controller.retry()
}

/** The panel's one glyph, turning while a retry is in flight. */
@Composable
private fun AccessBadge(spinning: Boolean) {
    val scope = LocalSeatLayerPickerScope.current
    // Progress the buyer asked for is not decoration: a retry that shows
    // nothing is a button that looks broken. It still stops turning for a
    // viewer who asked for less movement, who gets the disabled button.
    val turning = spinning && !pickerReducedMotion()
    val transition = rememberInfiniteTransition(label = "seatlayer-access-retry")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (turning) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(ACCESS_SPIN_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "seatlayer-access-retry-angle",
    )
    Box(
        modifier = Modifier
            .size(ACCESS_BADGE_SIZE.dp)
            .background(
                scope.theme.accent.copy(alpha = ACCESS_BADGE_WASH)
                    .compositeOver(scope.theme.surface),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SeatLayerPickerIcon(
            SeatLayerPickerGlyph.Recentre,
            scope.theme.accent,
            Modifier.size(ACCESS_BADGE_GLYPH.dp).rotate(angle),
        )
    }
}

/**
 * Whether a host has taken the access-unavailable moment over.
 *
 * A host that wired the callback has been told already and may be running its
 * own recovery; remounting the runtime under it would destroy that. The
 * default set's own no-op is a singleton, so identity is what says "unwired".
 */
internal val SeatLayerPickerScope.hostOwnsAccessRecovery: Boolean
    get() = callbacks.onAccessUnavailable !== DEFAULT_PICKER_CALLBACKS.onAccessUnavailable

private val DEFAULT_PICKER_CALLBACKS = SeatLayerPickerCallbacks()

private const val STATE_VEIL_OPACITY: Float = 0.82f
private const val ACCESS_VEIL_OPACITY: Float = 0.9f
private const val STATE_GUTTER: Float = 24f
private const val STATE_GAP: Float = 16f
private const val STATE_SMALL_GAP: Float = 8f
private const val STATE_HAIR_GAP: Float = 4f
private const val STATE_BODY_SIZE: Float = 13f
private const val STATE_COPY_WIDTH: Float = 360f
private const val SOLD_OUT_EYEBROW_SIZE: Float = 10f
private const val SOLD_OUT_EYEBROW_TRACKING: Float = 2f
private const val SOLD_OUT_TITLE_SIZE: Float = 32f
private const val SALES_CLOSED_WASH: Float = 0.05f
private const val SALES_CLOSED_RADIUS: Float = 13f
private const val SALES_CLOSED_PAD: Float = 13f
private const val SALES_CLOSED_TITLE_SIZE: Float = 13f
private const val SALES_CLOSED_BODY_SIZE: Float = 11.5f
private const val ACCESS_CARD_WIDTH: Float = 420f
private const val ACCESS_CARD_PAD_TOP: Float = 28f
private const val ACCESS_TITLE_SIZE: Float = 18f
private const val ACCESS_ACTION_GAP: Float = 18f
private const val ACCESS_BADGE_SIZE: Float = 52f
private const val ACCESS_BADGE_GLYPH: Float = 28f
private const val ACCESS_BADGE_WASH: Float = 0.16f
private const val ACCESS_SPIN_MS: Int = 720
