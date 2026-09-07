package io.seatlayer.android.compose

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit

/*
 * Words on the card, in the chrome's own typeface and under its own ceiling.
 *
 * Two small things every part of the card wants: the host's typeface where it
 * named one, and a limit on how far the platform's font setting may take the
 * card before the answer it exists to offer stops fitting.
 */

/**
 * Runs [content] with the platform font scale capped at [clamp].
 *
 * The card keeps growing with the buyer's setting up to the ceiling and then
 * stops, so a very large setting makes the card taller rather than clipping the
 * word it is asking about. Nothing below this reads the raw scale.
 */
@Composable
internal fun SeatLayerPickerTypeClamp(
    clamp: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    if (density.fontScale <= clamp) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, clamp),
        content = content,
    )
}

/** One run of chrome text, in the theme's typeface. */
@Composable
internal fun SeatLayerPickerText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    style: TextStyle = LocalTextStyle.current,
    softWrap: Boolean = true,
) {
    val scope = LocalSeatLayerPickerScope.current
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = scope.theme.fontFamily,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
        style = style,
    )
}
