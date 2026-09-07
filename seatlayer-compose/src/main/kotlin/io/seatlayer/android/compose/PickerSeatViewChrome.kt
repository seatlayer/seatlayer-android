package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The native caption strip over the 2D view-from-seat panorama.
 *
 * Every word here is the runtime's, carried on `seatView.changed`: the seat's
 * identity, the caption, the disclosure badge and the drag hint. The strip
 * PRINTS them and never re-derives the disclosure — whether a view is a
 * photograph or a model is the runtime's finding, and a native re-derivation
 * from two booleans is a second opinion the buyer did not ask for.
 *
 * A runtime advertising `native-seat-view-chrome-v1` is asked at init to
 * suppress its own words, so a host that hides this strip OWNS the disclosure
 * itself: turning it off does not give the words back to the runtime. The
 * panorama's own close control stays the runtime's either way.
 */
@Composable
public fun SeatLayerSeatViewChrome(
    modifier: Modifier = Modifier,
    showDragHint: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (!scope.controller.supportsNativeSeatViewChrome) return
    val view = scope.state.seatView ?: return
    val title = view.title?.trim()?.takeIf(String::isNotEmpty)
    val caption = view.caption?.trim()?.takeIf(String::isNotEmpty)
    val badge = view.badge?.trim()?.takeIf(String::isNotEmpty)
    val dragHint = view.dragHint?.trim()?.takeIf(String::isNotEmpty)
    if (title == null && caption == null && badge == null && dragHint == null) return
    val glass = pickerCaptionGlass()
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp,
                    top = SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp,
                    end = SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp,
                    // The runtime keeps its own close control and drag hint in
                    // the lowest panorama band. Leave that band free unless a
                    // host has explicitly taken the hint over.
                    bottom = if (showDragHint) {
                        SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp
                    } else {
                        SEAT_VIEW_RUNTIME_BAND.dp
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SeatLayerSizeTokens.MAP_ANCHOR_GAP.dp),
        ) {
            if (title != null || caption != null || badge != null) {
                PickerGlassSurface(
                    glass = glass,
                    shape = RoundedCornerShape(SeatLayerRadiusTokens.CHIP.dp),
                    modifier = Modifier.fillMaxWidth().widthIn(max = SEAT_VIEW_MAX_WIDTH.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = SeatLayerSizeTokens.IMMERSIVE_NAV_CHIP_PADDING_X.dp,
                            vertical = SEAT_VIEW_PAD_Y.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            title?.let {
                                Text(
                                    it,
                                    color = glass.ink,
                                    fontSize = SeatLayerSizeTokens
                                        .IMMERSIVE_NAV_CHIP_FONT_SIZE.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = scope.theme.fontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            caption?.let {
                                Text(
                                    it,
                                    color = glass.ink.copy(alpha = SEAT_VIEW_MUTED),
                                    fontSize = SeatLayerSizeTokens
                                        .IMMERSIVE_CAPTION_FONT_SIZE.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = scope.theme.fontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        badge?.let {
                            Surface(
                                color = glass.ink.copy(alpha = SEAT_VIEW_BADGE_WASH),
                                contentColor = glass.ink,
                                shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
                            ) {
                                Text(
                                    it,
                                    modifier = Modifier.padding(
                                        horizontal = SEAT_VIEW_BADGE_PAD_X.dp,
                                        vertical = SEAT_VIEW_BADGE_PAD_Y.dp,
                                    ),
                                    color = glass.ink,
                                    fontSize = SEAT_VIEW_BADGE_FONT.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = scope.theme.fontFamily,
                                )
                            }
                        }
                    }
                }
            }
            dragHint?.takeIf { showDragHint }?.let { hint ->
                PickerImmersiveCaption(hint)
            }
        }
    }
}

private const val SEAT_VIEW_MAX_WIDTH: Float = 420f
private const val SEAT_VIEW_PAD_Y: Float = 9f
private const val SEAT_VIEW_RUNTIME_BAND: Float = 48f
private const val SEAT_VIEW_MUTED: Float = 0.72f
private const val SEAT_VIEW_BADGE_WASH: Float = 0.12f
private const val SEAT_VIEW_BADGE_PAD_X: Float = 9f
private const val SEAT_VIEW_BADGE_PAD_Y: Float = 5f
private const val SEAT_VIEW_BADGE_FONT: Float = 10f
