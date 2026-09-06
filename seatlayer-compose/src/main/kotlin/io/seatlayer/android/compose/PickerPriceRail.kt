package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerCategory
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerPriceLegend(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.Legend]
    val categories = scope.state.snapshot?.categories.orEmpty()
    val active = scope.state.snapshot?.map?.categoryFilter.orEmpty().toSet()
    val coroutineScope = rememberCoroutineScope()
    if (categories.isEmpty()) return

    val content: @Composable () -> Unit = {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.height(48.dp) else Modifier),
            contentPadding = PaddingValues(
                horizontal = resolvedStyle.horizontalPadding ?: if (compact) 10.dp else 12.dp,
                vertical = resolvedStyle.verticalPadding ?: if (compact) 0.dp else 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            items(categories, key = SeatLayerPickerCategory::key) { category ->
                val selected = category.key in active
                val price = categoryPrice(
                    category,
                    scope.state.snapshot?.currency ?: "USD",
                    scope.moneyFormatter,
                )
                PickerLegendChip(
                    category = category,
                    price = price,
                    selected = selected,
                    compact = compact,
                    onClick = {
                        coroutineScope.launch {
                            scope.performAction {
                                scope.controller.setCategoryFilter(
                                    if (selected) emptyList() else listOf(category.key),
                                    focus = !selected,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
    if (compact) {
        Box(modifier = modifier.fillMaxWidth()) { content() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = resolvedStyle.containerColor ?: scope.theme.surface,
            contentColor = resolvedStyle.contentColor ?: scope.theme.onSurface,
            tonalElevation = resolvedStyle.elevation ?: 1.dp,
            shape = RoundedCornerShape(resolvedStyle.cornerRadius ?: 0.dp),
        ) { content() }
    }
}

@Composable
private fun PickerLegendChip(
    category: SeatLayerPickerCategory,
    price: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (compact) {
        Box(
            modifier = Modifier
                .height(48.dp)
                .widthIn(min = 48.dp)
                .clickable(onClick = onClick)
                .semantics {
                    role = Role.Checkbox
                    toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                    contentDescription = "${category.label}, $price"
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) scope.theme.accent
                        else scope.theme.surface.copy(alpha = 0.94f),
                    )
                    .border(
                        1.dp,
                        if (selected) scope.theme.accent else scope.theme.divider,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(parseColor(category.color) ?: scope.theme.accent),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = price,
                    color = if (selected) scope.theme.onAccent else scope.theme.onSurface,
                    fontSize = SeatLayerPickerTokens.SIZE_LEGEND_CHIP_FONT_SIZE.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
        }
        return
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Checkbox
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            },
        shape = RoundedCornerShape(scope.theme.cornerRadius),
        color = if (selected) scope.theme.accent.copy(alpha = 0.14f)
        else scope.theme.background,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) scope.theme.accent else scope.theme.divider,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(parseColor(category.color) ?: scope.theme.accent),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(category.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(price, style = MaterialTheme.typography.labelSmall, color = scope.theme.muted)
            }
        }
    }
}

private fun categoryPrice(
    category: SeatLayerPickerCategory,
    currency: String,
    formatter: SeatLayerPickerMoneyFormatter,
): String {
    if (category.notForSale || category.available <= 0) return "—"
    return if (category.priceMin == category.priceMax) {
        formatter.format(category.priceMin, currency)
    } else {
        "${formatter.format(category.priceMin, currency)}–" +
            formatter.format(category.priceMax, currency)
    }
}
