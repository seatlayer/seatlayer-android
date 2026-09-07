package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerCategory
import kotlinx.coroutines.launch

/**
 * The prices, as a rail.
 *
 * On the phone it carries the prices ALONE, as the web's does: a "not
 * available" key that ends the row half off the screen reads as a cut word, and
 * the grey disc explains itself the moment a seat is tapped. The wide layout
 * has the room and keeps the key.
 */
@Composable
public fun SeatLayerPriceLegend(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.Legend]
    // A category nobody can buy is not a price.
    val categories = scope.state.snapshot?.categories.orEmpty().filterNot { it.notForSale }
    if (categories.isEmpty()) return
    val active = scope.state.snapshot?.map?.categoryFilter.orEmpty().toSet()
    val currency = scope.state.snapshot?.currency ?: DEFAULT_LEGEND_CURRENCY
    // Free counts are a present-only read: a runtime that does not report them
    // has not said the category is sold out, and a rail that greys a price from
    // an absent count invents a sold-out event.
    val counted = scope.controller.supportsCapability("category-availability-v1")
    val coroutineScope = rememberCoroutineScope()

    val content: @Composable () -> Unit = {
        // The rail is a fixed band of prices, and a clipped price is worse
        // than a slightly smaller one: it grows to the rail ceiling.
        SeatLayerPickerTypeClamp(SeatLayerTypeTokens.SCALE_CLAMP_RAIL) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pickerRailBandHeight(compact))
                    .padding(
                        horizontal = resolvedStyle.horizontalPadding
                            ?: if (compact) LEGEND_PADDING_X_COMPACT else LEGEND_PADDING_X_WIDE,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // THE WAY OUT COMES FIRST AND NEVER SCROLLS AWAY. It is simply not
                // in the scroller: a buyer who has narrowed the map to one price
                // has to be able to get back without hunting for the chip.
                PickerLegendChip(
                    label = scope.strings.allPrices,
                    spoken = scope.strings.allPrices,
                    dot = null,
                    selected = active.isEmpty(),
                    compact = compact,
                    naming = false,
                ) {
                    coroutineScope.launch {
                        scope.performAction {
                            scope.controller.setCategoryFilter(emptyList(), focus = true)
                        }
                    }
                }
                Spacer(Modifier.width(SeatLayerSizeTokens.ACCESS_STEP_GAP.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    // Horizontal only: a vertical pad inside a fixed band steals
                    // the chip's touch target off the bottom of the design source's
                    // 44 dp minimum.
                    contentPadding = PaddingValues(horizontal = LEGEND_SCROLLER_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(
                        SeatLayerSizeTokens.ACCESS_STEP_GAP.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(categories, key = SeatLayerPickerCategory::key) { category ->
                        val selected = category.key in active
                        val amount = pickerCategoryAmount(
                            category = category,
                            currency = currency,
                            formatter = scope.moneyFormatter,
                        )
                        PickerLegendChip(
                            // The phone chip is the PRICE alone; the wide chip is
                            // name · price. A category with no configured price
                            // wears its name on both.
                            label = when {
                                amount == null -> category.label
                                compact -> amount
                                else -> "${category.label} · $amount"
                            },
                            // The NAME is spoken even where only the price is drawn.
                            spoken = pickerCategorySpoken(category, amount, counted),
                            dot = parseColor(category.color) ?: scope.theme.accent,
                            selected = selected,
                            compact = compact,
                            naming = true,
                        ) {
                            coroutineScope.launch {
                                scope.performAction {
                                    scope.controller.setCategoryFilter(
                                        if (selected) emptyList() else listOf(category.key),
                                        // On BOTH directions: clearing without it
                                        // left the buyer inside the drill-in they
                                        // were already in.
                                        focus = true,
                                    )
                                }
                            }
                        }
                    }
                    if (!compact) {
                        item { PickerUnavailableKey() }
                    }
                }
            }
        }
    }
    if (compact) {
        // A band of the map's own chrome palette with a hairline under it, not
        // a floating strip: it is a ROW of the same column as the map, and the
        // line is what says where the map begins.
        val disc = pickerMapChromeDisc(scope.theme, scope.state.snapshot?.map?.isVenue3D == true)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(disc.ground)
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawRect(
                        color = disc.line,
                        topLeft = Offset(0f, size.height - stroke),
                        size = Size(size.width, stroke),
                    )
                },
        ) { content() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = resolvedStyle.containerColor ?: scope.theme.surface,
            contentColor = resolvedStyle.contentColor ?: scope.theme.text,
            tonalElevation = resolvedStyle.elevation ?: LEGEND_WIDE_ELEVATION,
            shape = RoundedCornerShape(resolvedStyle.cornerRadius ?: 0.dp),
        ) { content() }
    }
}

/**
 * The price a chip wears.
 *
 * A spread cannot fit a chip, so it becomes a floor: `{min}+`.
 */
internal fun pickerCategoryAmount(
    category: SeatLayerPickerCategory,
    currency: String,
    formatter: SeatLayerMoneyFormatter,
): String? {
    val min = category.priceMin
    val max = category.priceMax
    if (!min.isFinite() || min <= 0) return null
    return if (min == max) {
        formatter.format(min, currency)
    } else {
        "${formatter.format(min, currency)}+"
    }
}

private fun pickerCategorySpoken(
    category: SeatLayerPickerCategory,
    amount: String?,
    counted: Boolean,
): String {
    val head = if (amount == null) category.label else "${category.label} — $amount"
    // Absent is not zero. Only a runtime that counts, and that has actually
    // counted this category, may say how many places are left.
    val free = category.free.takeIf { counted } ?: return head
    return "$head · $free"
}

@Composable
private fun PickerLegendChip(
    label: String,
    spoken: String,
    dot: Color?,
    selected: Boolean,
    compact: Boolean,
    naming: Boolean,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    // The whole band answers, not just the pill: the strip above and below a
    // 24 dp chip is dead space that a thumb lands in constantly.
    Box(
        modifier = Modifier
            .height(pickerRailBandHeight(compact))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.selected = selected
                contentDescription = spoken
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.heightIn(
                min = if (compact) {
                    pickerScaledExtent(
                        SeatLayerSizeTokens.LEGEND_CHIP_HEIGHT,
                        SeatLayerTypeTokens.SCALE_CLAMP_RAIL,
                    )
                } else {
                    pickerRailBandHeight(compact = false)
                },
            ),
            shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
            color = when {
                selected -> scope.theme.accent
                naming -> scope.theme.background
                else -> scope.theme.surface
            },
            contentColor = if (selected) scope.theme.onAccent else scope.theme.text,
            border = BorderStroke(
                1.dp,
                if (selected) Color.Transparent else scope.theme.divider,
            ),
        ) {
            Row(
                modifier = Modifier.padding(
                    start = if (compact) LEGEND_CHIP_PADDING_START else LEGEND_PADDING_X_WIDE,
                    end = if (compact) LEGEND_CHIP_PADDING_END else LEGEND_PADDING_X_WIDE,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dot != null) {
                    Box(
                        Modifier
                            .size(pickerLegendDotSize(compact))
                            .clip(CircleShape)
                            .background(dot),
                    )
                    Spacer(Modifier.width(SeatLayerSizeTokens.ACCESS_STEP_GAP.dp))
                }
                Text(
                    text = label,
                    fontSize = pickerLegendFontSize(compact),
                    fontWeight = FontWeight(
                        if (naming) {
                            SeatLayerTypeTokens.LEGEND_CHIP_WEIGHT
                        } else {
                            LEGEND_WAY_OUT_WEIGHT
                        },
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The key for a seat nobody can have.
 *
 * Deliberately NOT a chip — no pill, no hairline, no press target. One entry
 * covers both held and sold, because the map paints one inert grey disc for
 * both. Wide only.
 */
@Composable
private fun PickerUnavailableKey() {
    val scope = LocalSeatLayerPickerScope.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(pickerLegendDotSize(compact = false))
                .clip(CircleShape)
                .background(
                    scope.theme.mutedText
                        .copy(alpha = LEGEND_UNAVAILABLE_WASH)
                        .compositeOver(scope.theme.background),
                ),
        )
        Spacer(Modifier.width(SeatLayerSizeTokens.ACCESS_STEP_GAP.dp))
        Text(
            scope.strings.notAvailable,
            color = scope.theme.mutedText,
            fontSize = pickerLegendFontSize(compact = false),
            fontWeight = FontWeight(LEGEND_UNAVAILABLE_WEIGHT),
            maxLines = 1,
        )
    }
}

private fun pickerLegendDotSize(compact: Boolean): Dp =
    if (compact) {
        SeatLayerSizeTokens.LEGEND_CHIP_DOT_SIZE.dp
    } else {
        (SeatLayerSizeTokens.LEGEND_CHIP_DOT_SIZE + LEGEND_WIDE_DOT_GROWTH).dp
    }

private fun pickerLegendFontSize(compact: Boolean) =
    if (compact) {
        SeatLayerSizeTokens.LEGEND_CHIP_FONT_SIZE.sp
    } else {
        LEGEND_WIDE_FONT_SIZE
    }

private const val DEFAULT_LEGEND_CURRENCY = "USD"
private const val LEGEND_UNAVAILABLE_WASH = 0.45f
private const val LEGEND_UNAVAILABLE_WEIGHT = 650
private const val LEGEND_WAY_OUT_WEIGHT = 750
private const val LEGEND_WIDE_DOT_GROWTH = 3f
private val LEGEND_WIDE_HEIGHT: Dp = 40.dp

/**
 * The band a price chip stands in, grown by the platform's text setting.
 *
 * A fixed band becomes a ceiling the moment a buyer scales their text up, and
 * a price taller than its band clips. At the platform default it is unchanged.
 */
@Composable
private fun pickerRailBandHeight(compact: Boolean): Dp = pickerScaledExtent(
    if (compact) {
        SeatLayerSizeTokens.MINIMUM_HIT_TARGET
    } else {
        LEGEND_WIDE_HEIGHT.value
    },
    SeatLayerTypeTokens.SCALE_CLAMP_RAIL,
)
private val LEGEND_WIDE_ELEVATION: Dp = 1.dp
private val LEGEND_WIDE_FONT_SIZE = 12.sp
private val LEGEND_PADDING_X_COMPACT: Dp = 10.dp
private val LEGEND_PADDING_X_WIDE: Dp = 12.dp
private val LEGEND_SCROLLER_PADDING: Dp = 1.dp
private val LEGEND_CHIP_PADDING_START: Dp = 7.dp
private val LEGEND_CHIP_PADDING_END: Dp = 9.dp
