package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The picker's banner: who is selling, what is being sold, and the way out.
 *
 * The ground and its ink are resolved as a PAIR through the header's own style
 * slot. Two independent tokens is how a host that darkens the map's background
 * ends up with an unreadable event name.
 *
 * The venue-and-date meta line is not drawn on a phone: the event name alone is
 * the identity, and the clock beside it is the picker's one clock.
 */
@Composable
@Suppress("LongMethod")
public fun SeatLayerPickerHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    holdContent: @Composable () -> Unit = { SeatLayerPickerHoldCountdown() },
    style: SeatLayerPickerPartStyle? = null,
    /** Retained for source compatibility: there is one header. */
    @Suppress("UNUSED_PARAMETER") compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.Header]
    val theme = scope.theme
    val ground = resolvedStyle.containerColor ?: theme.surface
    val ink = resolvedStyle.contentColor ?: theme.text
    val event = scope.state.snapshot?.event
    val branding = scope.state.snapshot?.branding
    val brandLabel = branding?.brandName?.trim()?.takeIf(String::isNotEmpty)
    val title = if (scope.options.hideEventDetails) {
        brandLabel ?: scope.strings.chooseSeats
    } else {
        scope.options.eventName ?: event?.name ?: brandLabel ?: scope.strings.chooseSeats
    }
    val hairline = theme.divider

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ground,
        contentColor = ink,
        tonalElevation = resolvedStyle.elevation ?: SeatLayerElevationTokens.HEADER.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SeatLayerSizeTokens.HEADER_HEIGHT.dp)
                .drawBehind {
                    drawLine(hairline, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                }
                .padding(
                    horizontal = resolvedStyle.horizontalPadding
                        ?: HeaderLiterals.PAD_X.dp,
                    vertical = resolvedStyle.verticalPadding ?: HeaderLiterals.PAD_Y.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickerBrandMark(label = brandLabel ?: title)
            Spacer(Modifier.width(HeaderLiterals.MARK_GAP.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f).semantics { heading() },
                color = ink,
                fontSize = SeatLayerSizeTokens.HEADER_NAME_FONT_SIZE.sp,
                fontWeight = FontWeight(HeaderLiterals.NAME_WEIGHT),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            holdContent()
            if (event?.salesClosed == true) {
                Spacer(Modifier.width(HeaderLiterals.PILL_GAP.dp))
                PickerSalesClosedPill()
            }
            Spacer(Modifier.width(HeaderLiterals.PILL_GAP.dp))
            PickerCloseRing(onClose = onClose)
        }
    }
}

/**
 * The organizer's mark, or the first letter of their name.
 *
 * A slot rather than an image: a mark is a wordmark, a painter or a whole row
 * depending on the brand, and the picker is not the one to decide which.
 */
@Composable
private fun PickerBrandMark(label: String) {
    val theme = LocalSeatLayerPickerTheme.current
    val logo = theme.logo
    Box(
        modifier = Modifier
            .size(SeatLayerSizeTokens.HEADER_LOGO_SIZE.dp)
            .background(
                theme.accent,
                RoundedCornerShape(SeatLayerRadiusTokens.HEADER_LOGO.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            logo()
        } else {
            Text(
                text = label.trim().take(1).uppercase(),
                color = theme.onAccent,
                fontSize = SeatLayerSizeTokens.HEADER_NAME_FONT_SIZE.sp,
                fontWeight = FontWeight(SeatLayerTypeTokens.HEADER_TITLE_WEIGHT),
                maxLines = 1,
            )
        }
    }
}

/** Same pill geometry as the clock, deliberately neutral: never the accent. */
@Composable
private fun PickerSalesClosedPill() {
    val scope = LocalSeatLayerPickerScope.current
    val theme = scope.theme
    Surface(
        shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
        color = theme.text.copy(alpha = HeaderLiterals.CLOSED_PILL_WASH),
        contentColor = theme.text,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = HeaderLiterals.PILL_PAD_X.dp,
                vertical = HeaderLiterals.PILL_PAD_Y.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = scope.strings.salesClosedPill,
                fontSize = SeatLayerTypeTokens.PILL_SIZE.sp,
                fontWeight = FontWeight(SeatLayerTypeTokens.PILL_WEIGHT),
                maxLines = 1,
            )
        }
    }
}

/**
 * A ring at the token size, with the press target expanded around it.
 *
 * The ring is drawn at `size.headerCloseSize`; the box that answers the press
 * takes the design's own `size.minimumHitTarget` and then the platform's
 * minimum interactive size on top, because Android's floor is the higher of the
 * two and a control the buyer misses is not a control.
 */
@Composable
private fun PickerCloseRing(onClose: () -> Unit) {
    val scope = LocalSeatLayerPickerScope.current
    val theme = scope.theme
    Box(
        modifier = Modifier
            // The design's own target, raised to the platform's floor where
            // that is higher — read from the platform rather than written down
            // here, so a control the buyer misses is never the smaller of two
            // right answers.
            .size(
                SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp
                    .coerceAtLeast(LocalMinimumInteractiveComponentSize.current),
            )
            .clip(CircleShape)
            .clickable(onClick = onClose)
            .semantics {
                role = Role.Button
                contentDescription = scope.strings.close
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(SeatLayerSizeTokens.HEADER_CLOSE_SIZE.dp)
                .border(1.dp, theme.divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SeatLayerPickerIcon(
                glyph = SeatLayerPickerGlyph.Close,
                color = theme.mutedText,
                modifier = Modifier.size(HeaderLiterals.CLOSE_GLYPH_SIZE.dp),
            )
        }
    }
}

/** Header-local numbers the design source keeps as literals. Reported as a gap. */
private object HeaderLiterals {
    const val PAD_X = 12f
    const val PAD_Y = 4f
    const val MARK_GAP = 10f
    const val PILL_GAP = 6f
    const val NAME_WEIGHT = 700
    const val CLOSED_PILL_WASH = 0.08f
    const val PILL_PAD_X = 10f
    const val PILL_PAD_Y = 5f
    const val CLOSE_GLYPH_SIZE = 14f
}
