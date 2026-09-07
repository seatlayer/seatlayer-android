package io.seatlayer.android.compose

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SeatLayerPickerSelectedSeat

/*
 * The map behind glass, with a hole in it.
 *
 * The card rests over a map that was otherwise fully legible, so the moment of
 * decision competed with several thousand other seats. The veil quiets the map;
 * the hole is what makes it a spotlight rather than a curtain — the buyer is
 * being asked about ONE seat and can still see it. The feather is what stops the
 * hole reading as a drawn circle.
 */

/**
 * The glass over the map while a card is up.
 *
 * The hole is cut at the seat's reported screen point PLUS the pan standing
 * over that snapshot, and that second half is easy to lose: the point is
 * computed when the runtime BUILDS a snapshot, and a camera pan publishes none,
 * so the reported point is where the seat sat BEFORE the lift. Cutting at the
 * reported point alone lands the hole one band below the seat, showing its
 * neighbours while the seat itself stays under the veil.
 *
 * A runtime that reports no screen point gets the same card over flat glass,
 * which is a correct state rather than a degraded one.
 */
@Composable
internal fun SeatLayerSpotlightGlass(
    seat: SeatLayerPickerSelectedSeat,
    anchorDy: Float,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val density = LocalDensity.current
    val flat = seatLayerReducedTransparency()
    // A host that wants a flat wash over the whole map names its own colour;
    // otherwise the veil is the design source's own, deepened where the buyer
    // has asked for legibility over depth.
    val veil = scope.styles.scrimColor ?: Color.Black.copy(
        alpha = if (flat) {
            SeatLayerOpacityTokens.CONFIRM_SCRIM_FLAT
        } else {
            SeatLayerOpacityTokens.CONFIRM_SCRIM
        },
    )
    val point = seat.screenPoint
    Canvas(
        modifier
            .clearAndSetSemantics { }
            // The hole is punched out of the veil rather than painted over it,
            // so what shows through is the map itself at full strength.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(veil)
        if (point == null) return@Canvas
        val clear = with(density) { SeatLayerSizeTokens.CONFIRM_SCRIM_CLEAR_RADIUS.dp.toPx() }
        val feather = with(density) {
            SeatLayerSizeTokens.CONFIRM_SCRIM_FEATHER_RADIUS.dp.toPx()
        }
        // The seat's point arrives in the map's own CSS pixels, which are this
        // platform's density-independent pixels; the pan standing over the
        // snapshot that reported it is added in the same units.
        val centre = with(density) {
            Offset(
                point.x.toFloat().dp.toPx(),
                (point.y.toFloat() + anchorDy).dp.toPx(),
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Black,
                    (clear / feather).coerceIn(0f, 1f) to Color.Black,
                    1f to Color.Transparent,
                ),
                center = centre,
                radius = feather,
            ),
            radius = feather,
            center = centre,
            blendMode = BlendMode.DstOut,
        )
    }
}

/**
 * Whether the buyer has asked for legibility over depth.
 *
 * There is no direct reading of "reduce transparency" on this platform, so the
 * high-contrast text setting stands in for it, exactly as the reference does.
 * Legibility is a setting, not a taste.
 */
@Composable
internal fun seatLayerReducedTransparency(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Secure.getInt(
                context.contentResolver,
                "high_text_contrast_enabled",
                0,
            ) == 1
        }.getOrDefault(false)
    }
}
