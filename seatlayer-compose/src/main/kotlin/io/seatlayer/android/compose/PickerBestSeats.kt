package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SeatLayerPickerCategory
import io.seatlayer.android.SeatLayerPickerZone
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerBestSeatsForm(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    if (!scope.options.enableBestAvailable || scope.options.readOnly) return
    val snapshot = scope.state.snapshot ?: return
    var quantity by remember(snapshot.maxSelection) { mutableIntStateOf(1) }
    var category by remember(snapshot.sessionId) { mutableStateOf<SeatLayerPickerCategory?>(null) }
    var zone by remember(snapshot.sessionId) { mutableStateOf<SeatLayerPickerZone?>(null) }
    val coroutineScope = rememberCoroutineScope()
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(scope.strings.bestAvailable, style = MaterialTheme.typography.titleMedium)
        PickerQuantityRow(
            value = quantity,
            maximum = snapshot.maxSelection.coerceAtLeast(1),
            onValue = { quantity = it },
        )
        PickerChoiceMenu(
            label = category?.label ?: scope.strings.allPrices,
            choices = snapshot.categories,
            choiceLabel = SeatLayerPickerCategory::label,
            onSelected = { category = it },
        )
        if (snapshot.bestAvailableZones.isNotEmpty()) {
            PickerChoiceMenu(
                label = zone?.label ?: scope.strings.venue,
                choices = snapshot.bestAvailableZones,
                choiceLabel = SeatLayerPickerZone::label,
                onSelected = { zone = it },
            )
        }
        Button(
            onClick = {
                coroutineScope.launch {
                    scope.performAction {
                        scope.controller.bestAvailable(
                            quantity = quantity,
                            categoryKey = category?.key,
                            zoneId = zone?.id,
                            ttlMillis = scope.options.holdTtlMillis,
                        )
                    }
                }
            },
            enabled = !scope.state.presentation.actionInFlight,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(scope.styles.buttonRadius),
        ) {
            Text(scope.strings.findBestSeats(quantity))
        }
    }
}

@Composable
private fun PickerQuantityRow(value: Int, maximum: Int, onValue: (Int) -> Unit) {
    val scope = LocalSeatLayerPickerScope.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(scope.strings.quantity, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { onValue((value - 1).coerceAtLeast(1)) },
            enabled = value > 1,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) { Text("−") }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 8.dp))
        TextButton(
            onClick = { onValue((value + 1).coerceAtMost(maximum)) },
            enabled = value < maximum,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) { Text("+") }
    }
}

@Composable
private fun <T> PickerChoiceMenu(
    label: String,
    choices: List<T>,
    choiceLabel: (T) -> String,
    onSelected: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("—") },
                onClick = {
                    expanded = false
                    onSelected(null)
                },
            )
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choiceLabel(choice)) },
                    onClick = {
                        expanded = false
                        onSelected(choice)
                    },
                )
            }
        }
    }
}
