package io.seatlayer.android.compose

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import io.seatlayer.android.SeatLayerPickerRemovalPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How much a toast matters. Only the border changes between tones. */
public enum class SeatLayerPickerToastTone {
    Neutral,
    Error,
    Warning,
    Success,
}

/** One thing to say, and at most one thing to do about it. */
@Immutable
public data class SeatLayerPickerToast(
    val message: String,
    val tone: SeatLayerPickerToastTone = SeatLayerPickerToastTone.Neutral,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * A queue of one.
 *
 * A new message replaces the standing one and restarts its dwell. The same
 * sentence arriving twice in a row is ignored: two signals commonly describe one
 * event, and a re-entering toast says it happened twice.
 */
public class SeatLayerPickerToastQueue {
    public var current: SeatLayerPickerToast? by mutableStateOf(null)
        private set

    /** The number of times the standing message has been (re)raised. */
    public var generation: Int by mutableStateOf(0)
        private set

    public fun show(toast: SeatLayerPickerToast) {
        if (current?.message == toast.message) return
        current = toast
        generation += 1
    }

    public fun dismiss() {
        current = null
    }
}

/** The queue this picker speaks through, held beside the scope rather than in it. */
@Composable
public fun rememberSeatLayerPickerToastQueue(): SeatLayerPickerToastQueue {
    val scope = LocalSeatLayerPickerScope.current
    return remember(scope.controller) { SeatLayerPickerToastQueue() }
}

/**
 * The picker's own toast band — never the host's messenger.
 *
 * One centred card in the bottom-centre region, lifted by the dock when it is
 * up and moved above the seat card when one is open: a reply printed across
 * Cancel / Add seat is a reply the buyer has to move to read.
 *
 * It renders nothing until something is said, and takes no pointer except on
 * its own action, so the map underneath stays the way out.
 */
@Composable
public fun SeatLayerPickerToastLayer(
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    lifted: Boolean = false,
    queue: SeatLayerPickerToastQueue = rememberSeatLayerPickerToastQueue(),
) {
    val scope = LocalSeatLayerPickerScope.current
    val reducedMotion = pickerReducedMotion()
    // The one consequence the picker itself raises: a removal the buyer can
    // still take back. It outlives its own sentence, so it is a state with an
    // action rather than a line of news.
    val undo = scope.state.presentation.removalUndo
    val canUndo = undo != null &&
        undo.phase == SeatLayerPickerRemovalPhase.UndoWindow &&
        scope.state.presentation.canUndoRemoval(scope.state.snapshot)
    val coroutineScope = rememberComposeScope()
    LaunchedEffect(canUndo, undo?.line?.lineKey) {
        if (!canUndo) return@LaunchedEffect
        queue.show(
            SeatLayerPickerToast(
                message = scope.strings.seatRemoved,
                actionLabel = scope.strings.undo,
                onAction = { coroutineScope.launch { scope.controller.undoLastRemoval() } },
            ),
        )
    }

    val toast = queue.current ?: return
    var rise by remember(queue.generation) { mutableFloatStateOf(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(queue.generation) {
        if (!reducedMotion) {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(SeatLayerMotionTokens.TOAST),
            ) { value, _ -> rise = value }
        }
        delay(SeatLayerMotionTokens.TOAST_DWELL.toLong())
        queue.dismiss()
        scope.controller.dismissRemovalUndo()
    }

    val lift = if (lifted) SeatLayerSizeTokens.TOAST_CARD_LIFT.dp else 0.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ToastLiterals.BAND_PAD_X.dp,
                end = ToastLiterals.BAND_PAD_X.dp,
                bottom = bottomInset + lift + ToastLiterals.BAND_PAD_BOTTOM.dp,
            ),
        // A centred child in a loose-height layout takes the whole map: the
        // band is constrained to the height of what it is actually saying.
        contentAlignment = Alignment.BottomCenter,
    ) {
        SeatLayerPickerToastCard(
            toast = toast,
            modifier = Modifier
                .alpha(rise)
                .graphicsLayer {
                    translationY = (1f - rise) * ToastLiterals.RISE_DISTANCE * density
                },
        )
    }
}

/** The card itself. A toast is a sentence, so it wraps. */
@Composable
public fun SeatLayerPickerToastCard(
    toast: SeatLayerPickerToast,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val theme = scope.theme
    val border = when (toast.tone) {
        SeatLayerPickerToastTone.Neutral -> theme.divider
        SeatLayerPickerToastTone.Error -> theme.error
        SeatLayerPickerToastTone.Warning -> theme.accent
        SeatLayerPickerToastTone.Success -> theme.success
    }
    val spoken = listOfNotNull(toast.message, toast.actionLabel).joinToString(separator = ", ")
    Surface(
        modifier = modifier
            .widthIn(max = ToastLiterals.MAX_WIDTH.dp)
            .semantics(mergeDescendants = false) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
        shape = RoundedCornerShape(ToastLiterals.RADIUS.dp),
        color = theme.surface,
        contentColor = theme.text,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(
                start = ToastLiterals.PAD_X.dp,
                end = if (toast.actionLabel == null) ToastLiterals.PAD_X.dp else 0.dp,
                top = ToastLiterals.PAD_Y.dp,
                bottom = ToastLiterals.PAD_Y.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToastLiterals.PAD_X.dp),
        ) {
            Text(
                text = toast.message,
                modifier = Modifier.weight(1f, fill = false),
                color = theme.text,
                fontSize = SeatLayerTypeTokens.PEEK_SUMMARY_SIZE.sp,
                fontWeight = FontWeight(SeatLayerTypeTokens.PEEK_SUMMARY_WEIGHT),
            )
            val action = toast.actionLabel
            val onAction = toast.onAction
            if (action != null && onAction != null) {
                PickerToastAction(label = action, onPress = onAction)
            }
        }
    }
}

/** A 30 pt pill inside a box that clears the touch floor on its own. */
@Composable
private fun PickerToastAction(label: String, onPress: () -> Unit) {
    val theme = LocalSeatLayerPickerTheme.current
    // A fixed 44 pt hit box around a 30 pt drawn pill.
    Box(
        modifier = Modifier
            .height(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
            .padding(end = ToastLiterals.ACTION_INSET.dp)
            .clickable(onClick = onPress)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.height(ToastLiterals.ACTION_HEIGHT.dp),
            shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
            color = theme.accent,
            contentColor = theme.onAccent,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = ToastLiterals.ACTION_PAD_X.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = SeatLayerTypeTokens.PILL_SIZE.sp,
                    fontWeight = FontWeight(SeatLayerTypeTokens.PILL_WEIGHT),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The removal notice, retained as a standalone part for hosts that place it.
 *
 * It draws through the same card the toast band uses, so a picker that shows it
 * twice cannot show it two different ways.
 */
@Composable
public fun SeatLayerPickerUndoNotice(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val undo = scope.state.presentation.removalUndo ?: return
    if (undo.phase != SeatLayerPickerRemovalPhase.UndoWindow) return
    if (!scope.state.presentation.canUndoRemoval(scope.state.snapshot)) return
    val coroutineScope = rememberComposeScope()
    LaunchedEffect(undo) {
        delay(SeatLayerMotionTokens.UNDO_WINDOW.toLong())
        scope.controller.dismissRemovalUndo()
    }
    SeatLayerPickerToastCard(
        toast = SeatLayerPickerToast(
            message = scope.strings.seatRemoved,
            actionLabel = scope.strings.undo,
            onAction = { coroutineScope.launch { scope.controller.undoLastRemoval() } },
        ),
        modifier = modifier,
    )
}

@Composable
private fun rememberComposeScope(): kotlinx.coroutines.CoroutineScope =
    androidx.compose.runtime.rememberCoroutineScope()

/** Band-local numbers the design source keeps as literals. Reported as a gap. */
private object ToastLiterals {
    const val RADIUS = 16f
    const val MAX_WIDTH = 420f
    const val PAD_X = 14f
    const val PAD_Y = 10f
    const val BAND_PAD_X = 16f
    const val BAND_PAD_BOTTOM = 12f
    const val ACTION_HEIGHT = 30f
    const val ACTION_PAD_X = 12f
    const val ACTION_INSET = 7f
    const val RISE_DISTANCE = 12f
}
