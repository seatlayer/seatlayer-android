package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import io.seatlayer.android.SeatLayerPickerCategory
import io.seatlayer.android.SeatLayerPickerDenseRun
import io.seatlayer.android.SeatLayerPickerProjections
import io.seatlayer.android.SeatLayerPickerZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerPickerHoldCountdown(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val hold = scope.state.snapshot?.hold ?: return
    val expiresAt = hold.expiresAt ?: return
    if (!hold.active || hold.owner == "host") return
    val expiryMillis = normalizeEpochMillis(expiresAt)
    var remainingMillis by remember(expiryMillis) {
        mutableStateOf((expiryMillis - System.currentTimeMillis()).coerceAtLeast(0L))
    }
    LaunchedEffect(expiryMillis) {
        while (remainingMillis > 0) {
            delay(minOf(1_000L, remainingMillis))
            remainingMillis = (expiryMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = scope.theme.accent.copy(alpha = 0.12f),
        contentColor = scope.theme.onSurface,
    ) {
        Text(
            formatCountdown(remainingMillis),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

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
public fun SeatLayerPickerCheckoutBar(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.CheckoutBar]
    val projection = scope.state.presentation.confirmedCart(scope.state.snapshot)
    val totals = projection.totals
    val coroutineScope = rememberCoroutineScope()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = resolvedStyle.containerColor ?: scope.theme.surface,
        contentColor = resolvedStyle.contentColor ?: scope.theme.onSurface,
        tonalElevation = resolvedStyle.elevation ?: 2.dp,
        shape = RoundedCornerShape(resolvedStyle.cornerRadius ?: 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = resolvedStyle.horizontalPadding ?: 12.dp,
                vertical = resolvedStyle.verticalPadding ?: 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    scope.strings.ticketCount(totals.quantity),
                    style = MaterialTheme.typography.labelMedium,
                    color = scope.theme.muted,
                )
                Text(
                    totals.currency?.let { scope.moneyFormatter.format(totals.total, it) }
                        ?: if (totals.hasMixedCurrencies) "—" else totals.total.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Button(
                onClick = {
                    coroutineScope.launch {
                        scope.performAction {
                            scope.controller.handoffCheckout(scope.callbacks.onCheckout)
                        }
                    }
                },
                enabled = scope.controller.canCheckout,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(scope.styles.buttonRadius),
            ) {
                if (scope.state.presentation.actionInFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier.sizeIn(maxWidth = 20.dp, maxHeight = 20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(scope.strings.continueWord)
                }
            }
        }
    }
}

@Composable
public fun SeatLayerBookButton(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val coroutineScope = rememberCoroutineScope()
    Button(
        onClick = {
            coroutineScope.launch {
                scope.performAction {
                    scope.controller.handoffCheckout(scope.callbacks.onCheckout)
                }
            }
        },
        enabled = scope.controller.canCheckout,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(scope.styles.buttonRadius),
    ) {
        Text(scope.strings.holdAndCheckout)
    }
}

@Composable
public fun SeatLayerPickerCartSheet(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
    cartList: @Composable () -> Unit = { SeatLayerPickerCartList() },
    bestAvailable: @Composable () -> Unit = { SeatLayerBestSeatsForm() },
    checkoutBar: @Composable () -> Unit = { SeatLayerPickerCheckoutBar() },
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.CartSheet]
    val presentation = scope.state.presentation
    val confirmed = presentation.confirmedCart(scope.state.snapshot)
    if (compact) {
        CompactCartSheet(
            modifier = modifier,
            style = resolvedStyle,
            cartList = cartList,
            bestAvailable = bestAvailable,
            checkoutBar = checkoutBar,
        )
        return
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = resolvedStyle.cornerRadius ?: scope.styles.sheetRadius,
            topEnd = resolvedStyle.cornerRadius ?: scope.styles.sheetRadius,
        ),
        color = resolvedStyle.containerColor ?: scope.theme.surface,
        contentColor = resolvedStyle.contentColor ?: scope.theme.onSurface,
        shadowElevation = resolvedStyle.elevation ?: 10.dp,
    ) {
        Column {
            TextButton(
                onClick = { scope.controller.setCartExpanded(!presentation.cartExpanded) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    if (presentation.cartExpanded) scope.strings.hideCart
                    else "${scope.strings.showCart} · " +
                        scope.strings.ticketCount(confirmed.totals.quantity),
                )
            }
            if (presentation.cartExpanded) {
                Box(Modifier.heightIn(max = 360.dp)) {
                    if (confirmed.items.isEmpty()) bestAvailable() else cartList()
                }
            }
            checkoutBar()
        }
    }
}

@Composable
private fun CompactCartSheet(
    modifier: Modifier,
    style: SeatLayerPickerPartStyle,
    cartList: @Composable () -> Unit,
    bestAvailable: @Composable () -> Unit,
    checkoutBar: @Composable () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val presentation = scope.state.presentation
    val confirmed = presentation.confirmedCart(scope.state.snapshot)
    val totals = confirmed.totals
    val coroutineScope = rememberCoroutineScope()
    val totalLabel = totals.currency?.let { scope.moneyFormatter.format(totals.total, it) }
        ?: if (totals.hasMixedCurrencies) "—" else totals.total.toString()
    val minimum = scope.state.snapshot?.categories
        .orEmpty()
        .asSequence()
        .filter { !it.notForSale && it.available > 0 }
        .minOfOrNull(SeatLayerPickerCategory::priceMin)
    val emptyLabel = minimum?.let { price ->
        val currency = scope.state.snapshot?.currency.orEmpty()
        scope.strings.fromPrice(scope.moneyFormatter.format(price, currency))
    } ?: scope.strings.noTickets

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            // Flutter and React Native keep the edge-to-edge peek bar square.
            // Rounded defaults expose renderer pixels as dark corner wedges.
            topStart = style.cornerRadius ?: 0.dp,
            topEnd = style.cornerRadius ?: 0.dp,
        ),
        color = style.containerColor ?: scope.theme.surface,
        contentColor = style.contentColor ?: scope.theme.onSurface,
        shadowElevation = style.elevation ?: 10.dp,
    ) {
        Column {
            Surface(
                onClick = { scope.controller.setCartExpanded(!presentation.cartExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (presentation.cartExpanded) {
                            scope.strings.hideCart
                        } else {
                            scope.strings.showCart
                        }
                    },
                color = Color.Transparent,
                contentColor = scope.theme.onSurface,
            ) {
                Box {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                            .width(32.dp)
                            .height(3.dp)
                            .background(scope.theme.muted.copy(alpha = 0.62f), RoundedCornerShape(99.dp)),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(start = 13.dp, end = 8.dp, top = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (totals.quantity > 0) {
                                "${scope.strings.ticketCount(totals.quantity)} · $totalLabel"
                            } else {
                                emptyLabel
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        if (totals.quantity > 0 && !presentation.cartExpanded) {
                            Surface(
                                onClick = {
                                    coroutineScope.launch {
                                        scope.performAction {
                                            scope.controller.handoffCheckout(scope.callbacks.onCheckout)
                                        }
                                    }
                                },
                                enabled = scope.controller.canCheckout,
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = scope.theme.accent,
                                contentColor = scope.theme.onAccent,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${scope.strings.continueWord} · $totalLabel",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    SeatLayerPickerIcon(
                                        SeatLayerPickerGlyph.Forward,
                                        scope.theme.onAccent,
                                        Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                        SeatLayerPickerIcon(
                            glyph = if (presentation.cartExpanded) {
                                SeatLayerPickerGlyph.ChevronUp
                            } else {
                                SeatLayerPickerGlyph.ChevronUp
                            },
                            color = scope.theme.muted,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (presentation.cartExpanded) 180f else 0f),
                        )
                    }
                }
            }
            if (presentation.cartExpanded) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cap = (maxHeight * 0.6f).coerceAtLeast(180.dp).coerceAtMost(420.dp)
                    Column(Modifier.fillMaxWidth().heightIn(max = cap)) {
                        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                            if (confirmed.items.isEmpty()) bestAvailable() else cartList()
                        }
                        checkoutBar()
                    }
                }
            }
            if (scope.state.snapshot?.branding?.attributionRequired == true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    SeatLayerPickerAttribution(compact = true)
                }
            }
        }
    }
}

@Composable
public fun SeatLayerHoldLapseNotice(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val lapse = scope.state.holdLapse ?: return
    val coroutineScope = rememberCoroutineScope()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scope.theme.surface,
        contentColor = scope.theme.onSurface,
        shape = RoundedCornerShape(scope.styles.cardRadius),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(scope.strings.holdExpired, fontWeight = FontWeight.SemiBold)
            if (lapse.lapsedLabels.isNotEmpty()) {
                Text(
                    lapse.lapsedLabels.joinToString(),
                    color = scope.theme.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lapse.recoverableLabels.isNotEmpty()) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                scope.performAction {
                                    scope.controller.reselectLapsedSeats(
                                        scope.options.holdTtlMillis,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(scope.styles.buttonRadius),
                    ) { Text(scope.strings.reselectSeats(lapse.recoverableLabels.size)) }
                }
                TextButton(
                    onClick = scope.controller::dismissHoldLapse,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(scope.strings.dismiss) }
            }
        }
    }
}

@Composable
public fun SeatLayerPickerActionError(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val error = scope.state.presentation.lastActionError ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scope.theme.error,
        contentColor = scope.theme.onAccent,
        shape = RoundedCornerShape(scope.styles.cardRadius),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                error.message?.takeIf(String::isNotBlank) ?: scope.strings.actionFailed,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = scope.controller::dismissActionError,
                modifier = Modifier.sizeIn(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = scope.theme.onAccent,
                ),
            ) { Text(scope.strings.dismiss) }
        }
    }
}

@Composable
public fun SeatLayerPickerUndoNotice(
    modifier: Modifier = Modifier,
    durationMillis: Long = SeatLayerPickerTokens.MOTION_UNDO_WINDOW_MILLIS.toLong(),
) {
    val scope = LocalSeatLayerPickerScope.current
    val undo = scope.state.presentation.removalUndo ?: return
    if (!scope.state.presentation.canUndoRemoval(scope.state.snapshot)) return
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(undo, durationMillis) {
        delay(durationMillis.coerceAtLeast(0))
        scope.controller.dismissRemovalUndo()
    }
    Surface(
        modifier = modifier,
        color = scope.theme.onSurface,
        contentColor = scope.theme.surface,
        shape = RoundedCornerShape(scope.styles.cardRadius),
        shadowElevation = 8.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                undo.line.displayLabel ?: undo.line.label,
                modifier = Modifier.padding(start = 14.dp),
            )
            TextButton(
                onClick = { coroutineScope.launch { scope.controller.undoLastRemoval() } },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) { Text(scope.strings.undo) }
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

private fun normalizeEpochMillis(value: Double): Long =
    if (value < 10_000_000_000.0) (value * 1_000.0).toLong() else value.toLong()

private fun formatCountdown(remainingMillis: Long): String {
    val seconds = (remainingMillis / 1_000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
