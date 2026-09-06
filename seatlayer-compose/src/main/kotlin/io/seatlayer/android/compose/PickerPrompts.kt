package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.seatlayer.android.SeatLayerPickerGeneralAdmissionArea
import io.seatlayer.android.SeatLayerPickerPrompt
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerPickerGeneralAdmissionPrompt(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val prompt = scope.state.presentation.activePrompt as?
        SeatLayerPickerPrompt.GeneralAdmission ?: return
    Dialog(onDismissRequest = scope.stateHolder::dismissGeneralAdmissionCandidate) {
        GeneralAdmissionDialog(prompt.area, modifier)
    }
}

@Composable
private fun GeneralAdmissionDialog(
    area: SeatLayerPickerGeneralAdmissionArea,
    modifier: Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val maximum = minOf(
        area.available ?: scope.state.snapshot?.maxSelection ?: 10,
        scope.state.snapshot?.maxSelection ?: 10,
    ).coerceAtLeast(1)
    var quantity by remember(area.id, maximum) { mutableIntStateOf(1) }
    var tier by remember(area.id) { mutableStateOf(area.tiers.firstOrNull()) }
    val coroutineScope = rememberCoroutineScope()
    PromptSurface(modifier) {
        Text(
            area.label ?: scope.strings.generalAdmission,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        QuantityStepper(
            value = quantity,
            minimum = 1,
            maximum = maximum,
            label = scope.strings.quantity,
            onValue = { quantity = it },
        )
        if (area.tiers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TierChoices(
                tiers = area.tiers,
                selectedTierId = tier?.id,
                currency = area.currency ?: scope.state.snapshot?.currency.orEmpty(),
                enabled = !scope.state.presentation.actionInFlight,
                onTierSelected = { id -> tier = area.tiers.firstOrNull { it.id == id } },
            )
        }
        Spacer(Modifier.height(16.dp))
        PromptActions(
            onCancel = scope.stateHolder::dismissGeneralAdmissionCandidate,
            onConfirm = {
                coroutineScope.launch {
                    scope.performAction {
                        scope.controller.holdGeneralAdmission(
                            areaId = area.id,
                            quantity = quantity,
                            tierId = tier?.id,
                            ttlMillis = scope.options.holdTtlMillis,
                        )
                    }
                }
            },
        )
    }
}

@Composable
public fun SeatLayerPickerTablePrompt(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val prompt = scope.state.presentation.activePrompt as?
        SeatLayerPickerPrompt.Table ?: return
    val coroutineScope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = {
            coroutineScope.launch { scope.controller.cancelPendingTable() }
        },
    ) {
        TableDialog(prompt.seat, modifier)
    }
}

@Composable
private fun TableDialog(
    seat: SeatLayerPickerSelectedSeat,
    modifier: Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val minimum = (seat.minOccupancy ?: 1).coerceAtLeast(1)
    val maximum = (
        seat.maxOccupancy ?: seat.capacity ?: scope.state.snapshot?.maxSelection ?: minimum
    ).coerceAtLeast(minimum)
    var quantity by remember(seat.id, minimum, maximum) {
        mutableIntStateOf((seat.quantity ?: minimum).coerceIn(minimum, maximum))
    }
    var tier by remember(seat.id) {
        mutableStateOf(seat.tiers.firstOrNull { it.id == seat.tierId } ?: seat.tiers.firstOrNull())
    }
    val coroutineScope = rememberCoroutineScope()
    PromptSurface(modifier) {
        Text(
            seat.buyerFacingLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        QuantityStepper(
            value = quantity,
            minimum = minimum,
            maximum = maximum,
            label = scope.strings.tableGuests,
            onValue = { quantity = it },
        )
        if (seat.tiers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TierChoices(
                tiers = seat.tiers,
                selectedTierId = tier?.id,
                currency = seat.currency,
                enabled = !scope.state.presentation.actionInFlight,
                onTierSelected = { id -> tier = seat.tiers.firstOrNull { it.id == id } },
            )
        }
        Spacer(Modifier.height(16.dp))
        PromptActions(
            onCancel = {
                coroutineScope.launch { scope.controller.cancelPendingTable() }
            },
            onConfirm = {
                coroutineScope.launch {
                    scope.performAction {
                        if (tier?.id != seat.tierId) {
                            scope.controller.setSeatTier(seat.id, tier?.id)
                        }
                        scope.controller.setTableQuantity(
                            label = seat.label,
                            quantity = quantity,
                            ttlMillis = scope.options.holdTtlMillis,
                        )
                        scope.controller.confirmPendingTable()
                    }
                }
            },
        )
    }
}

@Composable
private fun PromptSurface(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(scope.styles.cardRadius),
        color = scope.theme.surface,
        contentColor = scope.theme.onSurface,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun QuantityStepper(
    value: Int,
    minimum: Int,
    maximum: Int,
    label: String,
    onValue: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { onValue((value - 1).coerceAtLeast(minimum)) },
            enabled = value > minimum,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) { Text("−") }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 10.dp))
        TextButton(
            onClick = { onValue((value + 1).coerceAtMost(maximum)) },
            enabled = value < maximum,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) { Text("+") }
    }
}

@Composable
private fun PromptActions(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(scope.styles.buttonRadius),
        ) { Text(scope.strings.cancel) }
        Button(
            onClick = onConfirm,
            enabled = !scope.state.presentation.actionInFlight,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(scope.styles.buttonRadius),
        ) { Text(scope.strings.confirm) }
    }
}
