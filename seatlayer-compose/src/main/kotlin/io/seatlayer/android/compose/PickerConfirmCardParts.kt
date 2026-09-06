package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerSelectedSeat

@Composable
internal fun CompactInspectionButton(
    label: String,
    glyph: SeatLayerPickerGlyph,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        modifier = Modifier.height(48.dp),
        shape = RoundedCornerShape(9.dp),
        color = scope.theme.surface.copy(alpha = 0.72f),
        contentColor = scope.theme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeatLayerPickerIcon(
                glyph,
                scope.theme.onSurface,
                Modifier.size(15.dp).clearAndSetSemantics { },
            )
            Spacer(Modifier.size(5.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

internal fun compactSeatLabel(
    seat: SeatLayerPickerSelectedSeat,
    strings: SeatLayerPickerStrings,
): String = listOfNotNull(
    seat.sectionLabel ?: seat.displayLabel ?: seat.label,
    seat.rowLabel?.let { "${strings.row} $it" },
    seat.seatNumber?.let { "${strings.seat} $it" },
).distinct().joinToString(" · ")

@Composable
internal fun SeatIdentity(
    seat: SeatLayerPickerSelectedSeat,
    shownPrice: Double = seat.price,
    shownCurrency: String = seat.currency,
) {
    val scope = LocalSeatLayerPickerScope.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                seat.buyerFacingLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                seat.sectionLabel,
                seat.rowLabel?.let { "${scope.strings.row} $it" },
                seat.seatNumber?.let { "${scope.strings.seat} $it" },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = scope.theme.muted)
            }
        }
        Text(
            scope.moneyFormatter.format(shownPrice, shownCurrency),
            fontWeight = FontWeight.SemiBold,
        )
    }
}
