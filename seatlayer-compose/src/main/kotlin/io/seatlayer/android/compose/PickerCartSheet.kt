package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import io.seatlayer.android.SeatLayerPickerCategory
import kotlinx.coroutines.launch

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
