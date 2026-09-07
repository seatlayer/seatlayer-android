package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerCategoryTier
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
        contentColor = resolvedStyle.contentColor ?: scope.theme.text,
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
    val hasVenue3D = scope.options.enable3D && scope.controller.supportsVenue3D
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = 360.dp),
        shape = RoundedCornerShape(style.cornerRadius ?: 18.dp),
        color = style.containerColor ?: scope.theme.surface,
        contentColor = style.contentColor ?: scope.theme.text,
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
                    contentColor = scope.theme.text,
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
