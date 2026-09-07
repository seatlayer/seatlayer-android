package io.seatlayer.android.compose

import android.animation.ValueAnimator
import android.os.Build
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
import io.seatlayer.android.SeatLayerPickerPhase
import kotlinx.coroutines.launch

/**
 * Whether motion may play at all on this device.
 *
 * One accessor decides for every surface, so none of them can forget: a
 * platform that has turned animations off gets the settled state directly,
 * and anything sequenced behind a skipped animation runs immediately.
 */
@Composable
internal fun seatLayerPickerMotionEnabled(): Boolean = remember {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
}

@Composable
public fun SeatLayerHoldLapseNotice(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val lapse = scope.state.holdLapse ?: return
    val coroutineScope = rememberCoroutineScope()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scope.theme.surface,
        contentColor = scope.theme.text,
        shape = RoundedCornerShape(scope.styles.cardRadius),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(scope.strings.holdExpired, fontWeight = FontWeight.SemiBold)
            if (lapse.lapsedLabels.isNotEmpty()) {
                Text(
                    lapse.lapsedLabels.joinToString(),
                    color = scope.theme.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lapse.recoverableLabels.isNotEmpty()) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                scope.performAction {
                                    scope.controller.reselectLapsedSeats(
                                        scope.options.holdTtl?.inWholeMilliseconds?.toInt(),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(scope.styles.buttonRadius),
                    ) { Text(scope.strings.reselectSeats(lapse.recoverableLabels.size)) }
                }
                TextButton(
                    onClick = scope.controller::dismissHoldLapse,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(scope.strings.dismiss) }
            }
        }
    }
}

@Composable
public fun SeatLayerPickerActionError(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val error = scope.state.presentation.lastActionError ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scope.theme.error,
        contentColor = scope.theme.onAccent,
        shape = RoundedCornerShape(scope.styles.cardRadius),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                error.message?.takeIf(String::isNotBlank) ?: scope.strings.actionFailed,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = scope.controller::dismissError,
                modifier = Modifier.sizeIn(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = scope.theme.onAccent,
                ),
            ) { Text(scope.strings.dismiss) }
        }
    }
}

@Composable
public fun SeatLayerPickerLoadingView(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
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

/** The same generic venue silhouette the web picker uses before chart readiness. */
@Composable
private fun VenueLoadingSilhouette(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = seatLayerPickerMotionEnabled()
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

@Composable
public fun SeatLayerPickerErrorView(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val failure = (scope.state.phase as? SeatLayerPickerPhase.Failed)?.error
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scope.theme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = scope.strings.errorMessage,
                style = MaterialTheme.typography.titleMedium,
                color = scope.theme.text,
            )
            failure?.message?.takeIf(String::isNotBlank)?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = scope.theme.mutedText,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.sizeIn(minHeight = 48.dp),
            ) {
                Text(scope.strings.retry)
            }
        }
    }
}

@Composable
public fun SeatLayerPickerEmptyView(
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
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
