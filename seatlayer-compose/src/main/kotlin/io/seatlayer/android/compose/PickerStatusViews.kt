package io.seatlayer.android.compose

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerErrorCodes
import io.seatlayer.android.SeatLayerPickerHoldLapse
import io.seatlayer.android.SeatLayerPickerRecovery
import io.seatlayer.android.SeatLayerPickerPhase
import kotlinx.coroutines.launch

/**
 * Says, once, that a hold lapsed — and offers the seats back where it can.
 *
 * A persistent line where the buyer's tickets were, and nothing blocking. A
 * dialog was considered and rejected: the buyer has usually just come back
 * from somewhere else, and a modal is a second thing to dismiss before they
 * can see whether their seats are still there — over a map that is still
 * entirely usable, and a lapse that is often fully recoverable.
 */
@Composable
public fun SeatLayerHoldLapseNotice(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    // A host that would rather own the moment takes it whole; `onHoldExpired`
    // fires either way.
    if (!scope.options.announceHoldLapse) return
    val lapse = scope.state.holdLapse ?: return
    val coroutineScope = rememberCoroutineScope()
    val recoverable = lapse.recoverableLabels.size
    // A buyer-facing state owns the screen and grows with the platform text
    // setting to the state ceiling: past it the layout breaks, and a clipped
    // sentence is worse than a slightly smaller one.
    SeatLayerPickerTypeClamp(SeatLayerTypeTokens.SCALE_CLAMP_STATE) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    start = LAPSE_PAD_START.dp,
                    top = LAPSE_PAD_TOP.dp,
                    end = LAPSE_PAD_END.dp,
                    bottom = LAPSE_PAD_BOTTOM.dp,
                )
                .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    scope.strings.holdLapsedTitle,
                    color = scope.theme.text,
                    fontSize = LAPSE_TITLE_SIZE.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = scope.theme.fontFamily,
                )
                seatLayerHoldLapseBody(scope.strings, lapse)?.let { body ->
                    Text(
                        body,
                        color = scope.theme.mutedText,
                        fontSize = LAPSE_BODY_SIZE.sp,
                        fontFamily = scope.theme.fontFamily,
                    )
                }
            }
            if (recoverable > 0) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            scope.performAction {
                                scope.controller.reselectLapsedSeats(
                                    scope.options.holdTtl?.inWholeMilliseconds?.toInt(),
                                )
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp),
                ) {
                    Text(
                        scope.strings.reselectSeats(recoverable),
                        color = scope.theme.accent,
                        fontSize = LAPSE_TITLE_SIZE.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = scope.theme.fontFamily,
                    )
                }
            } else {
                TextButton(
                    onClick = scope.controller::dismissHoldLapse,
                    modifier = Modifier.heightIn(min = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp),
                ) {
                    Text(
                        scope.strings.dismiss,
                        color = scope.theme.mutedText,
                        fontSize = LAPSE_BODY_SIZE.sp,
                        fontFamily = scope.theme.fontFamily,
                    )
                }
            }
        }
    }
}

/**
 * The sentence under the lapse title, or null when there is nothing to add.
 *
 * Two facts at most, and only the ones that are true: how long the hold was
 * good for, when the session named a window, and how many of the seats are
 * gone for good, when some are and some are not.
 */
public fun seatLayerHoldLapseBody(
    strings: SeatLayerPickerStrings,
    lapse: SeatLayerPickerHoldLapse,
): String? {
    val parts = buildList {
        lapse.heldForMillis?.let { add(strings.holdLapsedBody(it / MILLIS_PER_MINUTE)) }
        if (lapse.recovery == SeatLayerPickerRecovery.Partial) {
            add(strings.seatsNotRecovered(lapse.unrecoverableCount))
        }
    }
    return parts.takeIf(List<String>::isNotEmpty)?.joinToString(" ")
}

/**
 * Refusals the runtime answers when the picker tries to change a hold it may
 * not change.
 *
 * The buyer came back from checkout and tapped another seat, or asked for a
 * hold they already have. Each is a STATE with a way out, not a failure, and
 * the bar says so in the buyer's words rather than the bridge's.
 */
private val HOLD_OWNERSHIP_CODES = setOf(
    SeatLayerPickerErrorCodes.HOLD_OWNED_BY_HOST,
    SeatLayerPickerErrorCodes.HOLD_SELECTION_MISMATCH,
    SeatLayerPickerErrorCodes.HOLD_ALREADY_ACTIVE,
)

/**
 * The inline failure bar, and the one place the picker states a hold it may
 * not change.
 *
 * The runtime's own sentence for those refusals is never shown to the buyer:
 * the bar says the STATE instead, with the one action that gets them out of it.
 */
@Composable
public fun SeatLayerPickerActionError(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val error = scope.state.presentation.lastActionError ?: return
    val handoff = scope.state.presentation.checkoutHandoff
    val hostOwnedHold = scope.state.snapshot?.hold
        ?.let { it.active && it.owner == "host" } == true
    val code = (error as? SeatLayerException.Bridge)?.code
    val coroutineScope = rememberCoroutineScope()
    var title: String = error.message?.takeIf(String::isNotBlank) ?: scope.strings.actionFailed
    var detail: String? = null
    var release: (() -> Unit)? = null
    if (code != null && code in HOLD_OWNERSHIP_CODES) {
        if (handoff != null || hostOwnedHold) {
            title = scope.strings.holdInCheckoutTitle
            detail = scope.strings.holdInCheckoutBody
            // Rejecting the handoff puts the seats back on sale so the buyer
            // can pick again; with no handoff to give back there is nothing to
            // offer but dismiss.
            if (handoff != null) {
                release = {
                    coroutineScope.launch {
                        scope.performAction {
                            scope.controller.rejectCheckoutHandoff(handoff)
                            scope.controller.dismissError()
                        }
                    }
                }
            }
        } else {
            title = scope.strings.holdAlreadyHeldTitle
            detail = scope.strings.holdAlreadyHeldBody
        }
    }
    // A buyer-facing state owns the screen and grows with the platform text
    // setting to the state ceiling: past it the layout breaks, and a clipped
    // sentence is worse than a slightly smaller one.
    SeatLayerPickerTypeClamp(SeatLayerTypeTokens.SCALE_CLAMP_STATE) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Assertive },
            color = scope.theme.error,
            contentColor = scope.theme.onAccent,
            shape = RoundedCornerShape(scope.styles.cardRadius),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = ERROR_PAD_X.dp,
                    vertical = ERROR_PAD_Y.dp,
                ),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontFamily = scope.theme.fontFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    detail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = scope.theme.fontFamily,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    release?.let { onRelease ->
                        TextButton(
                            onClick = onRelease,
                            modifier = Modifier.heightIn(
                                min = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp,
                            ),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = scope.theme.onAccent,
                            ),
                        ) { Text(scope.strings.releaseAndChangeSeats) }
                    }
                }
                TextButton(
                    onClick = scope.controller::dismissError,
                    modifier = Modifier.sizeIn(minHeight = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = scope.theme.onAccent,
                    ),
                ) { Text(scope.strings.dismiss) }
            }
        }
    }
}

private const val MILLIS_PER_MINUTE: Int = 60_000
private const val LAPSE_PAD_START: Float = 14f
private const val LAPSE_PAD_TOP: Float = 8f
private const val LAPSE_PAD_END: Float = 6f
private const val LAPSE_PAD_BOTTOM: Float = 2f
private const val LAPSE_TITLE_SIZE: Float = 13f
private const val LAPSE_BODY_SIZE: Float = 12f
private const val ERROR_PAD_X: Float = 14f
private const val ERROR_PAD_Y: Float = 10f

@Composable
public fun SeatLayerPickerLoadingView(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    // A buyer-facing state owns the screen and grows with the platform text
    // setting to the state ceiling: past it the layout breaks, and a clipped
    // sentence is worse than a slightly smaller one.
    SeatLayerPickerTypeClamp(SeatLayerTypeTokens.SCALE_CLAMP_STATE) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(scope.theme.background)
                .clearAndSetSemantics {
                    contentDescription = scope.strings.loading
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                },
            contentAlignment = Alignment.Center,
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = scope.theme.accent,
                trackColor = scope.theme.accent.copy(alpha = 0.12f),
            )
            VenueLoadingSilhouette(
                color = scope.theme.accent,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .widthIn(max = 420.dp)
                    .aspectRatio(10f / 7f),
            )
        }
    }
}

/** The same generic venue silhouette the web picker uses before chart readiness. */
@Composable
private fun VenueLoadingSilhouette(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = !pickerReducedMotion()
    val transition = rememberInfiniteTransition(label = "seatlayer-venue-loading")
    val sweep by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "seatlayer-venue-loading-sweep",
    )
    val emphasis = if (animationsEnabled) sweep else 0.72f
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val scale = minOf(size.width / 200f, size.height / 140f)
        val left = (size.width - 200f * scale) / 2f
        val top = (size.height - 140f * scale) / 2f
        val shellBrush = Brush.linearGradient(
            0f to color.copy(alpha = 0.08f),
            0.5f to color.copy(alpha = 0.12f + emphasis * 0.08f),
            1f to color.copy(alpha = 0.08f),
            start = Offset(left, top),
            end = Offset(left + 200f * scale, top + 140f * scale),
        )
        fun shell(x: Float, y: Float, width: Float, height: Float, stroke: Float) {
            drawArc(
                brush = shellBrush,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(left + x * scale, top + y * scale),
                size = Size(width * scale, height * scale),
                style = Stroke(width = stroke * scale),
            )
        }
        shell(x = 20f, y = 40f, width = 160f, height = 104f, stroke = 14f)
        shell(x = 32f, y = 32f, width = 136f, height = 84f, stroke = 12f)
        shell(x = 46f, y = 26f, width = 108f, height = 64f, stroke = 10f)
        drawRoundRect(
            color = color.copy(alpha = 0.14f + emphasis * 0.1f),
            topLeft = Offset(left + 62f * scale, top + 16f * scale),
            size = Size(76f * scale, 16f * scale),
            cornerRadius = CornerRadius(4f * scale),
        )
    }
}

/**
 * The map did not load.
 *
 * The runtime's own error card is suppressed under the native chrome contract,
 * so this is the one telling and the one retry — and the retry remounts the
 * picker rather than patching a half-built one.
 */
@Composable
public fun SeatLayerPickerErrorView(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    // A buyer-facing state owns the screen and grows with the platform text
    // setting to the state ceiling: past it the layout breaks, and a clipped
    // sentence is worse than a slightly smaller one.
    SeatLayerPickerTypeClamp(SeatLayerTypeTokens.SCALE_CLAMP_STATE) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(scope.theme.background)
                .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(ERROR_VIEW_GUTTER.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = scope.strings.mapDidNotLoad,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = scope.theme.text,
                    fontFamily = scope.theme.fontFamily,
                )
                Spacer(Modifier.height(ERROR_VIEW_GAP.dp))
                Text(
                    text = scope.strings.checkConnection,
                    style = MaterialTheme.typography.bodySmall,
                    color = scope.theme.mutedText,
                    fontFamily = scope.theme.fontFamily,
                )
                Spacer(Modifier.height(ERROR_VIEW_ACTION_GAP.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.sizeIn(
                        minHeight = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp,
                    ),
                    shape = RoundedCornerShape(scope.styles.buttonRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scope.theme.accent,
                        contentColor = scope.theme.onAccent,
                    ),
                ) {
                    Text(scope.strings.retry, fontFamily = scope.theme.fontFamily)
                }
            }
        }
    }
}

private const val ERROR_VIEW_GUTTER: Float = 28f
private const val ERROR_VIEW_GAP: Float = 8f
private const val ERROR_VIEW_ACTION_GAP: Float = 20f

@Composable
public fun SeatLayerPickerEmptyView(
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    // A buyer-facing state owns the screen and grows with the platform text
    // setting to the state ceiling: past it the layout breaks, and a clipped
    // sentence is worse than a slightly smaller one.
    SeatLayerPickerTypeClamp(SeatLayerTypeTokens.SCALE_CLAMP_STATE) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(scope.theme.background.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = scope.strings.soldOutTitle,
                style = MaterialTheme.typography.titleMedium,
                color = scope.theme.text,
                modifier = Modifier.padding(28.dp),
            )
        }
    }
}
