package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerCartLine
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerPickerCartList(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val confirmed = scope.state.presentation.confirmedCart(scope.state.snapshot)
    if (confirmed.items.isEmpty()) {
        Text(
            scope.strings.emptyTrayHint,
            modifier = modifier.padding(20.dp),
            color = scope.theme.mutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    // Every held seat is its own line. The collapsed run this list used to
    // draw hid the seat a buyer was looking for behind a summary they then had
    // to open, and a row they cannot act on directly is not a shorter list.
    LazyColumn(modifier = modifier) {
        items(confirmed.items, key = SeatLayerPickerCartLine::lineKey) { line ->
            CartLineRow(line, compact)
            PickerDivider()
        }
    }
}

@Composable
private fun CartLineRow(
    line: SeatLayerPickerCartLine,
    compact: Boolean,
) {
    val scope = LocalSeatLayerPickerScope.current
    val coroutineScope = rememberCoroutineScope()
    if (compact) {
        val categoryColor = scope.state.snapshot?.categories
            ?.firstOrNull { it.key == line.categoryKey }
            ?.color
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(parseColor(categoryColor.orEmpty()) ?: scope.theme.accent, RoundedCornerShape(99.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                listOfNotNull(line.sectionLabel, line.rowLabel, line.seatNumber)
                    .ifEmpty { listOf(line.displayLabel ?: line.label) }
                    .joinToString(" · "),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                scope.moneyFormatter.format(line.total, line.currency),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Surface(
                onClick = { coroutineScope.launch { scope.controller.removeWithUndo(line) } },
                enabled = !scope.state.presentation.actionInFlight,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = scope.strings.removeWord
                    },
                color = Color.Transparent,
                contentColor = scope.theme.mutedText,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SeatLayerPickerIcon(
                        SeatLayerPickerGlyph.Close,
                        scope.theme.mutedText,
                        Modifier.size(16.dp),
                    )
                }
            }
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                line.displayLabel ?: line.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
            val place = listOfNotNull(line.sectionLabel, line.rowLabel, line.seatNumber)
                .joinToString(" · ")
            if (place.isNotEmpty()) {
                Text(place, style = MaterialTheme.typography.bodySmall, color = scope.theme.mutedText)
            }
        }
        Text(
            scope.moneyFormatter.format(line.total, line.currency),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        TextButton(
            onClick = { coroutineScope.launch { scope.controller.removeWithUndo(line) } },
            enabled = !scope.state.presentation.actionInFlight,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) {
            Text(scope.strings.removeWord)
        }
    }
}

@Composable
internal fun PickerDivider() {
    HorizontalDivider(color = LocalSeatLayerPickerTheme.current.divider)
}
