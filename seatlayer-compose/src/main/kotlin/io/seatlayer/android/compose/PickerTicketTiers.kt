package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerCategoryTier
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import kotlinx.coroutines.launch

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
internal fun TierChoices(
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
                contentColor = scope.theme.text,
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
                            if (selected) scope.theme.accent else scope.theme.mutedText,
                        ),
                    ) { }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tier.name, fontWeight = FontWeight.ExtraBold)
                        guidance?.let {
                            Text(
                                it,
                                color = scope.theme.mutedText,
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
