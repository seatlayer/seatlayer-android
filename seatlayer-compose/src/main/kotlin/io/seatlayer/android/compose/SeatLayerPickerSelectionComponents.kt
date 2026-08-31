package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.seatlayer.android.SeatLayerPickerCategoryTier
import io.seatlayer.android.SeatLayerPickerGeneralAdmissionArea
import io.seatlayer.android.SeatLayerPickerPrompt
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import kotlinx.coroutines.launch

/** Compact confirmation card for the newest unconfirmed addressed seat. */
@Composable
public fun SeatLayerConfirmCard(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.ConfirmCard]
    val seat = scope.state.presentation.pendingSeat ?: return
    val coroutineScope = rememberCoroutineScope()
    var selectedTierId by remember(
        seat.id,
        seat.label,
        seat.tierId,
        seat.tiers.map(SeatLayerPickerCategoryTier::id),
    ) {
        mutableStateOf(
            seat.tierId?.takeIf { id -> seat.tiers.any { it.id == id } }
                ?: seat.tiers.firstOrNull()?.id,
        )
    }
    val selectedTier = seat.tiers.firstOrNull { it.id == selectedTierId }
    val shownPrice = selectedTier?.price ?: seat.price
    val shownCurrency = selectedTier?.currency ?: seat.currency
    val confirm = {
        coroutineScope.launch { scope.controller.confirmPending(selectedTierId) }
        Unit
    }
    if (compact) {
        CompactSeatConfirmation(
            seat = seat,
            selectedTierId = selectedTierId,
            shownPrice = shownPrice,
            shownCurrency = shownCurrency,
            onTierSelected = { selectedTierId = it },
            onConfirm = confirm,
            modifier = modifier,
            style = resolvedStyle,
        )
        return
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(resolvedStyle.cornerRadius ?: scope.styles.cardRadius),
        color = resolvedStyle.containerColor ?: scope.theme.surface,
        contentColor = resolvedStyle.contentColor ?: scope.theme.onSurface,
        shadowElevation = resolvedStyle.elevation ?: 8.dp,
    ) {
        Column(
            Modifier.padding(
                horizontal = resolvedStyle.horizontalPadding ?: 16.dp,
                vertical = resolvedStyle.verticalPadding ?: 16.dp,
            ),
        ) {
            SeatIdentity(seat, shownPrice, shownCurrency)
            if (seat.tiers.size > 1) {
                Spacer(Modifier.height(10.dp))
                Text(
                    scope.strings.ticketType,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                SeatLayerPickerTierChoices(
                    seat = seat,
                    selectedTierId = selectedTierId,
                    onTierSelected = { selectedTierId = it },
                )
            }
            if (scope.options.enableSeatView && scope.controller.supportsSeatView) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            scope.performAction { scope.controller.openSeatView(seat.id) }
                        }
                    },
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                    shape = RoundedCornerShape(scope.styles.buttonRadius),
                ) {
                    Text(scope.strings.viewFromHere)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = {
                        coroutineScope.launch { scope.controller.cancelPending() }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(scope.styles.buttonRadius),
                ) {
                    Text(scope.strings.cancel)
                }
                Button(
                    onClick = confirm,
                    enabled = !scope.state.presentation.actionInFlight,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(scope.styles.buttonRadius),
                ) {
                    Text(scope.strings.select)
                }
            }
        }
    }
}

@Composable
private fun CompactSeatConfirmation(
    seat: SeatLayerPickerSelectedSeat,
    selectedTierId: String?,
    shownPrice: Double,
    shownCurrency: String,
    onTierSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier,
    style: SeatLayerPickerPartStyle,
) {
    val scope = LocalSeatLayerPickerScope.current
    val coroutineScope = rememberCoroutineScope()
    val category = scope.state.snapshot?.categories
        ?.firstOrNull { it.key == seat.categoryKey }
    val dotColor = parseColor(category?.color.orEmpty()) ?: scope.theme.accent
    val hasSeatView = scope.options.enableSeatView && scope.controller.supportsSeatView
    val hasVenue3D = scope.options.enableVenue3D && scope.controller.supportsVenue3D
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = 360.dp),
        shape = RoundedCornerShape(style.cornerRadius ?: 18.dp),
        color = style.containerColor ?: scope.theme.surface,
        contentColor = style.contentColor ?: scope.theme.onSurface,
        border = BorderStroke(1.dp, scope.theme.divider),
        shadowElevation = style.elevation ?: 10.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(43.dp)
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = dotColor,
                ) { }
                Spacer(Modifier.size(7.dp))
                Text(
                    compactSeatLabel(seat, scope.strings),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    scope.moneyFormatter.format(shownPrice, shownCurrency),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
            if (seat.tiers.size > 1) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        scope.strings.ticketType,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    SeatLayerPickerTierChoices(
                        seat = seat,
                        selectedTierId = selectedTierId,
                        onTierSelected = onTierSelected,
                    )
                }
            }
            if (hasSeatView || hasVenue3D) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(scope.theme.accent.copy(alpha = 0.13f))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (hasSeatView) {
                        CompactInspectionButton(
                            label = scope.strings.viewFromHere,
                            glyph = SeatLayerPickerGlyph.Eye,
                        ) {
                            coroutineScope.launch {
                                scope.performAction { scope.controller.openSeatView(seat.id) }
                            }
                        }
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }
                    if (hasVenue3D) {
                        CompactInspectionButton(
                            label = scope.strings.venue3D,
                            glyph = SeatLayerPickerGlyph.Cube,
                        ) {
                            coroutineScope.launch {
                                scope.performAction {
                                    scope.controller.setBuyerView(
                                        view = "venue3d",
                                        flyToSeatId = seat.id,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Surface(
                    onClick = {
                        coroutineScope.launch { scope.controller.cancelPending() }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = Color.Transparent,
                    contentColor = scope.theme.onSurface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            scope.strings.cancel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                Surface(
                    onClick = onConfirm,
                    enabled = !scope.state.presentation.actionInFlight,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = scope.theme.accent,
                    contentColor = scope.theme.onAccent,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SeatLayerPickerIcon(
                            SeatLayerPickerGlyph.Check,
                            scope.theme.onAccent,
                            Modifier.size(15.dp).clearAndSetSemantics { },
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            scope.strings.select,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactInspectionButton(
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

private fun compactSeatLabel(
    seat: SeatLayerPickerSelectedSeat,
    strings: SeatLayerPickerStrings,
): String = listOfNotNull(
    seat.sectionLabel ?: seat.displayLabel ?: seat.label,
    seat.rowLabel?.let { "${strings.row} $it" },
    seat.seatNumber?.let { "${strings.seat} $it" },
).distinct().joinToString(" · ")

/** Wide-layout form of the same confirmation state and commands. */
@Composable
public fun SeatLayerPickerSeatConfirmation(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
) {
    val scope = LocalSeatLayerPickerScope.current
    SeatLayerConfirmCard(
        modifier = modifier,
        style = style ?: scope.styles[SeatLayerPickerPart.SeatConfirmation],
        compact = false,
    )
}

@Composable
public fun SeatLayerPickerTierSelector(
    seat: SeatLayerPickerSelectedSeat,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (seat.tiers.size < 2) return
    val coroutineScope = rememberCoroutineScope()
    var selectedTierId by remember(seat.id, seat.tierId) {
        mutableStateOf(seat.tierId ?: seat.tiers.first().id)
    }
    SeatLayerPickerTierChoices(
        seat = seat,
        selectedTierId = selectedTierId,
        modifier = modifier,
        onTierSelected = { tierId ->
            selectedTierId = tierId
            coroutineScope.launch {
                scope.performAction { scope.controller.setSeatTier(seat.id, tierId) }
            }
        },
    )
}

/** Visible native radio choices matching the ready picker tier decision. */
@Composable
public fun SeatLayerPickerTierChoices(
    seat: SeatLayerPickerSelectedSeat,
    selectedTierId: String?,
    onTierSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TierChoices(
        tiers = seat.tiers,
        selectedTierId = selectedTierId,
        currency = seat.currency,
        enabled = enabled,
        modifier = modifier,
        onTierSelected = onTierSelected,
    )
}

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
private fun TierChoices(
    tiers: List<SeatLayerPickerCategoryTier>,
    selectedTierId: String?,
    currency: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onTierSelected: (String) -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tiers.forEach { tier ->
            val selected = tier.id == selectedTierId
            val guidance = tier.buyerMessage?.takeIf(String::isNotBlank)
                ?: scope.strings.tierCompanionGuidance.takeIf {
                    tier.restriction == "companion"
                }
            val tierCurrency = tier.currency ?: currency
            val price = scope.moneyFormatter.format(tier.price, tierCurrency)
            Surface(
                onClick = { onTierSelected(tier.id) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = selected
                    },
                shape = RoundedCornerShape(scope.styles.buttonRadius),
                color = if (selected) {
                    scope.theme.accent.copy(alpha = 0.1f)
                } else {
                    Color.Transparent
                },
                contentColor = scope.theme.onSurface,
                border = BorderStroke(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) scope.theme.accent else scope.theme.divider,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(20.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(
                            if (selected) 6.dp else 1.5.dp,
                            if (selected) scope.theme.accent else scope.theme.muted,
                        ),
                    ) { }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tier.name, fontWeight = FontWeight.ExtraBold)
                        guidance?.let {
                            Text(
                                it,
                                color = scope.theme.muted,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(price, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
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

@Composable
private fun SeatIdentity(
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
