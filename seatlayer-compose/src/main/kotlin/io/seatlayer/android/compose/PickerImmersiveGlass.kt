package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/*
 * One dark glass for every surface that floats over a rendered venue.
 *
 * The immersive scene is drawn by the runtime and is dark whatever the resolved
 * mode is, so its chrome takes the dark palette's own glass rather than the
 * picker panel's `surface`/`divider` — a translucent panel surface over a dark
 * scene measured 1.14:1, a dark blob on dark. Mixed once here so no surface
 * re-mixes it.
 */

/** The ground, hairline and ink one immersive surface wears. */
@Immutable
internal data class PickerGlass(
    val ground: Color,
    val line: Color,
    val ink: Color,
    val blur: Float,
)

/** The glass a control wears: chips, discs, the back pill, the deck. */
internal fun pickerControlGlass(): PickerGlass = PickerGlass(
    ground = Color(SeatLayerDarkTokens.IMMERSIVE_GLASS),
    line = Color(SeatLayerDarkTokens.IMMERSIVE_GLASS_BORDER),
    ink = Color(SeatLayerDarkTokens.IMMERSIVE_GLASS_INK),
    blur = SeatLayerSizeTokens.IMMERSIVE_GLASS_BLUR,
)

/** The deeper glass a caption wears, so words stay readable over the scene. */
internal fun pickerCaptionGlass(): PickerGlass = PickerGlass(
    ground = Color(SeatLayerDarkTokens.IMMERSIVE_CAPTION),
    line = Color(SeatLayerDarkTokens.IMMERSIVE_CAPTION_BORDER),
    ink = Color(SeatLayerDarkTokens.IMMERSIVE_CAPTION_INK),
    blur = SeatLayerSizeTokens.IMMERSIVE_CAPTION_BLUR,
)

/**
 * A pane of glass with something on it.
 *
 * The blur token is carried on [PickerGlass] and is not applied here: Compose
 * blurs a composable's own content rather than what is behind it, and a
 * content blur would smear the label instead of the scene. The grounds are
 * mixed to stand on their own without one.
 */
@Composable
internal fun PickerGlassSurface(
    glass: PickerGlass,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = glass.ground,
        contentColor = glass.ink,
        shape = shape,
        border = BorderStroke(1.dp, glass.line),
        content = content,
    )
}

/**
 * One labelled chip on the immersive deck.
 *
 * Drawn at the deck's own height and padding, and always inside a full hit
 * target: the chips are small type over a moving scene and a thumb must not
 * have to aim.
 */
@Composable
internal fun PickerImmersiveChip(
    label: String,
    glyph: SeatLayerPickerGlyph?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showsLabel: Boolean = true,
    action: suspend () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val glass = pickerControlGlass()
    val coroutineScope = rememberCoroutineScope()
    val live = enabled && !scope.state.presentation.actionInFlight
    Box(
        modifier = modifier.defaultMinSize(
            minWidth = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp,
            minHeight = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = { coroutineScope.launch { scope.performAction(action) } },
            enabled = live,
            modifier = Modifier
                .height(SeatLayerSizeTokens.IMMERSIVE_NAV_CHIP_HEIGHT.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                },
            color = glass.ground,
            contentColor = glass.ink,
            shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
            border = BorderStroke(1.dp, glass.line),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = SeatLayerSizeTokens.IMMERSIVE_NAV_CHIP_PADDING_X.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = if (live) {
                    glass.ink
                } else {
                    glass.ink.copy(alpha = SeatLayerOpacityTokens.MAP_CONTROL_DISABLED)
                }
                glyph?.let {
                    SeatLayerPickerIcon(
                        it,
                        ink,
                        Modifier
                            .size(SeatLayerSizeTokens.IMMERSIVE_BACK_ICON_SIZE.dp)
                            .clearAndSetSemantics { },
                    )
                }
                if (showsLabel) {
                    if (glyph != null) Spacer(Modifier.width(IMMERSIVE_CHIP_GAP.dp))
                    Text(
                        label,
                        color = ink,
                        fontSize = SeatLayerSizeTokens.IMMERSIVE_NAV_CHIP_FONT_SIZE.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = scope.theme.fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The caption plate naming what the camera is looking at. */
@Composable
internal fun PickerImmersiveCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val glass = pickerCaptionGlass()
    PickerGlassSurface(
        glass = glass,
        shape = RoundedCornerShape(SeatLayerRadiusTokens.CHIP.dp),
        modifier = modifier,
    ) {
        Text(
            text,
            modifier = Modifier.padding(
                horizontal = SeatLayerSizeTokens.IMMERSIVE_NAV_CHIP_PADDING_X.dp,
                vertical = IMMERSIVE_CAPTION_PAD_Y.dp,
            ),
            color = glass.ink,
            fontSize = SeatLayerSizeTokens.IMMERSIVE_CAPTION_FONT_SIZE.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = scope.theme.fontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val IMMERSIVE_CHIP_GAP: Float = 6f
private const val IMMERSIVE_CAPTION_PAD_Y: Float = 7f
