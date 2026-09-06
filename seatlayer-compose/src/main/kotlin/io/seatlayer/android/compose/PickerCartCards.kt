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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import io.seatlayer.android.SeatLayerPickerDenseRun
import io.seatlayer.android.SeatLayerPickerProjections
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerPickerCartList(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val confirmed = scope.state.presentation.confirmedCart(scope.state.snapshot)
    val runs = remember(confirmed.items) { SeatLayerPickerProjections.denseRuns(confirmed.items) }
    var expandedRun by remember { mutableStateOf<String?>(null) }
    if (runs.isEmpty()) {
        Text(
            scope.strings.noTickets,
            modifier = modifier.padding(20.dp),
            color = scope.theme.muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    LazyColumn(modifier = modifier) {
        items(runs, key = { it.memberLineKeys.joinToString("|") }) { run ->
            if (run.items.size == 1 || expandedRun == run.memberLineKeys.first()) {
                run.items.forEach { line -> CartLineRow(line, compact) }
                if (run.items.size > 1) {
                    TextButton(
                        onClick = { expandedRun = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(scope.strings.showLess) }
                }
            } else {
                DenseRunRow(run) { expandedRun = run.memberLineKeys.first() }
            }
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
                        contentDescription = scope.strings.remove
                    },
                color = Color.Transparent,
                contentColor = scope.theme.muted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SeatLayerPickerIcon(
                        SeatLayerPickerGlyph.Close,
                        scope.theme.muted,
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
                Text(place, style = MaterialTheme.typography.bodySmall, color = scope.theme.muted)
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
            Text(scope.strings.remove)
        }
    }
}

@Composable
private fun DenseRunRow(run: SeatLayerPickerDenseRun, onOpen: () -> Unit) {
    val scope = LocalSeatLayerPickerScope.current
    val first = run.items.first()
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        color = scope.theme.surface,
        contentColor = scope.theme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(first.sectionLabel, first.rowLabel, run.seatsLabel)
                        .joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${run.quantity} × ${scope.moneyFormatter.format(first.unitPrice, first.currency)}",
                    color = scope.theme.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                run.currency?.let { scope.moneyFormatter.format(run.total, it) }
                    ?: run.total.toString(),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun PickerDivider() {
    HorizontalDivider(color = LocalSeatLayerPickerTheme.current.divider)
}
