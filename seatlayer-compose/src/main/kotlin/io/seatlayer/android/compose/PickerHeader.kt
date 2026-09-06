package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
public fun SeatLayerPickerHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    holdContent: @Composable () -> Unit = { SeatLayerPickerHoldCountdown() },
    style: SeatLayerPickerPartStyle? = null,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.Header]
    val event = scope.state.snapshot?.event
    val branding = scope.state.snapshot?.branding
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = resolvedStyle.containerColor ?: scope.theme.surface,
        contentColor = resolvedStyle.contentColor ?: scope.theme.onSurface,
        tonalElevation = resolvedStyle.elevation ?: 0.dp,
        shape = RoundedCornerShape(resolvedStyle.cornerRadius ?: 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (compact) {
                        Modifier.height(SeatLayerPickerTokens.SIZE_HEADER_HEIGHT.dp)
                    } else {
                        Modifier.heightIn(min = SeatLayerPickerTokens.SIZE_HEADER_HEIGHT.dp)
                    },
                )
                .padding(
                    start = resolvedStyle.horizontalPadding ?: 12.dp,
                    end = resolvedStyle.horizontalPadding ?: 4.dp,
                    top = resolvedStyle.verticalPadding ?: if (compact) 0.dp else 8.dp,
                    bottom = resolvedStyle.verticalPadding ?: if (compact) 0.dp else 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val brandLabel = branding?.brandName?.trim()?.takeIf(String::isNotEmpty)
            val brandSize = if (compact) {
                SeatLayerPickerTokens.SIZE_HEADER_LOGO_SIZE.dp
            } else {
                36.dp
            }
            Surface(
                modifier = Modifier.size(brandSize),
                shape = RoundedCornerShape(if (compact) 6.dp else 10.dp),
                color = scope.theme.accent,
                contentColor = scope.theme.onAccent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SeatLayerPickerIcon(
                        glyph = SeatLayerPickerGlyph.Seat,
                        color = scope.theme.onAccent,
                        modifier = Modifier.size(brandSize * 0.56f),
                    )
                }
            }
            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (scope.options.hideEventDetails) {
                        brandLabel ?: scope.strings.chooseSeats
                    } else {
                        event?.name ?: brandLabel ?: scope.strings.chooseSeats
                    },
                    style = if (compact) {
                        MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                event?.venue
                    ?.takeUnless { compact || scope.options.hideEventDetails }
                    ?.let { venue ->
                    Text(
                        text = venue,
                        style = MaterialTheme.typography.bodySmall,
                        color = scope.theme.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            holdContent()
            Surface(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = scope.strings.close
                    },
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = scope.theme.onSurface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SeatLayerPickerIcon(
                        glyph = SeatLayerPickerGlyph.Close,
                        color = scope.theme.onSurface,
                        modifier = Modifier.size(if (compact) 20.dp else 22.dp),
                    )
                }
            }
        }
    }
}
