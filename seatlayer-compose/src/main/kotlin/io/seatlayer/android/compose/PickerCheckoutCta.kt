package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
