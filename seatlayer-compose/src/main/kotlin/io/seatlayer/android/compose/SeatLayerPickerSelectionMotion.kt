package io.seatlayer.android.compose

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SeatLayerPickerMotion
import io.seatlayer.android.SeatLayerPickerProjections
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import kotlin.math.PI
import kotlin.math.sin

/** Short, non-interactive confirmation cue from the card toward the cart. */
@Composable
internal fun SeatLayerSelectionFlight(
    wide: Boolean,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val pending = scope.state.presentation.pendingSeat
    val snapshot = scope.state.snapshot
    var previous by remember(scope.stateHolder) {
        mutableStateOf<SeatLayerPickerSelectedSeat?>(pending)
    }
    var flight by remember(scope.stateHolder) {
        mutableStateOf<SeatLayerPickerSelectedSeat?>(null)
    }
    var flightId by remember(scope.stateHolder) { mutableIntStateOf(0) }
    val progress = remember(scope.stateHolder) { Animatable(1f) }
    val animationsEnabled = remember {
        Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
    }

    LaunchedEffect(
        snapshot?.sessionId,
        snapshot?.revision,
        pending?.let(SeatLayerPickerProjections::seatIdentity),
    ) {
        val accepted = SeatLayerPickerMotion.newlyConfirmedSeat(previous, pending, snapshot)
        previous = pending
        if (accepted != null && animationsEnabled) {
            flight = accepted
            flightId += 1
        }
    }
    LaunchedEffect(flightId) {
        if (flightId == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        )
        flight = null
    }

    val active = flight ?: return
    val category = snapshot?.categories?.firstOrNull { it.key == active.categoryKey }
    val color = parseColor(category?.color.orEmpty()) ?: scope.theme.accent
    BoxWithConstraints(modifier.fillMaxSize().clearAndSetSemantics { }) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val bottom = with(density) { bottomPadding.toPx() }
        val t = progress.value
        val startX = width * 0.5f
        val startY = height * 0.5f
        val endX = if (wide) width - 22.dp.value * density.density else width * 0.5f
        val endY = (height - bottom - 20.dp.value * density.density).coerceAtLeast(0f)
        val x = startX + (endX - startX) * t
        val y = startY + (endY - startY) * t -
            sin(t * PI).toFloat() * 56.dp.value * density.density
        Surface(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    translationX = x - 18.dp.toPx()
                    translationY = y - 18.dp.toPx()
                    scaleX = 1f - t * 0.28f
                    scaleY = scaleX
                }
                .alpha((1f - (t - 0.72f).coerceAtLeast(0f) / 0.28f).coerceIn(0f, 1f)),
            shape = CircleShape,
            color = color,
            contentColor = Color.White,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                SeatLayerPickerIcon(
                    SeatLayerPickerGlyph.Check,
                    Color.White,
                    Modifier.size(17.dp),
                )
            }
        }
    }
}
