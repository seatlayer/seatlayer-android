package io.seatlayer.android.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerCategory
import io.seatlayer.android.SeatLayerPickerZone
import kotlinx.coroutines.launch

/**
 * The finder, on one plate inside the OPEN sheet.
 *
 * The collapsed sheet is the foot alone, so the empty cart's way in is the
 * footer's own call to action; pressing it opens the sheet on this form. Two
 * entry points for one action is the duplication the web round removed.
 *
 * There is deliberately no premium quick-pick chip: the bridge carries no
 * chart-level premium fact, and a chip that cannot know whether the chart has
 * premium seats would be guessing.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
public fun SeatLayerBestSeatsForm(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    if (!scope.options.enableBestAvailable || scope.options.readOnly) return
    val snapshot = scope.state.snapshot ?: return
    // A runtime that does not advertise the command does not offer the
    // feature: the form is withheld silently, never drawn to fail.
    if (!scope.controller.supportsCommand("picker.bestAvailable")) return
    val theme = scope.theme
    val forSale = snapshot.categories.filterNot(SeatLayerPickerCategory::notForSale)
    val zones = snapshot.bestAvailableZones
    val maximum = snapshot.maxSelection.coerceAtLeast(1)

    var quantity by remember(snapshot.sessionId) {
        mutableIntStateOf(BestSeatsLiterals.INITIAL_QUANTITY.coerceIn(1, maximum))
    }
    // Session adoption: the finder starts from what the buyer is already
    // looking at, and only where that reading is unambiguous.
    var category by remember(snapshot.sessionId) {
        mutableStateOf(
            snapshot.map.categoryFilter
                .singleOrNull()
                ?.let { key -> forSale.firstOrNull { it.key == key } },
        )
    }
    var zone by remember(snapshot.sessionId) {
        mutableStateOf(
            snapshot.sections
                .firstOrNull { it.id == snapshot.map.focusedSectionId }
                ?.zoneId
                ?.let { id -> zones.firstOrNull { it.id == id } },
        )
    }
    var explaining by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val enabled = scope.state.isReady && !submitting && !scope.state.presentation.actionInFlight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BestSeatsLiterals.PLATE_RADIUS.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        lerp(theme.surface, theme.accent, BestSeatsLiterals.WASH_FROM),
                        lerp(theme.surface, theme.accent, BestSeatsLiterals.WASH_TO),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = lerp(theme.divider, theme.accent, BestSeatsLiterals.BORDER_ACCENT),
                shape = RoundedCornerShape(BestSeatsLiterals.PLATE_RADIUS.dp),
            )
            .padding(BestSeatsLiterals.PLATE_PAD.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(BestSeatsLiterals.ROW_GAP.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PickerSparkle(
                    color = theme.accent,
                    modifier = Modifier.size(BestSeatsLiterals.TITLE_SPARKLE.dp),
                )
                Spacer(Modifier.width(BestSeatsLiterals.SPARKLE_GAP.dp))
                Text(
                    text = scope.strings.findSeatsTogether,
                    modifier = Modifier.weight(1f, fill = false),
                    color = theme.text,
                    fontSize = BestSeatsLiterals.TITLE_SIZE.sp,
                    fontWeight = FontWeight(BestSeatsLiterals.TITLE_WEIGHT),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The ⓘ sits beside the words it explains, not at the far end
                // of the row where it would explain the row.
                Box(
                    modifier = Modifier
                        .size(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
                        .clickable { explaining = !explaining }
                        .semantics {
                            role = Role.Button
                            contentDescription = scope.strings.aboutBestSeats
                            stateDescription = scope.strings.closestGroupChosenInstantly
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PickerInfoGlyph(
                        color = if (explaining) theme.accent else theme.mutedText,
                        modifier = Modifier.size(BestSeatsLiterals.INFO_SIZE.dp),
                    )
                }
            }
            if (explaining) {
                Text(
                    text = scope.strings.closestGroupChosenInstantly,
                    modifier = Modifier.padding(top = BestSeatsLiterals.EXPLAIN_PAD_TOP.dp),
                    color = theme.mutedText,
                    fontSize = BestSeatsLiterals.EXPLAIN_SIZE.sp,
                )
            }
        }
        // Exactly one category is not a choice, so it is not a row.
        if (forSale.size > 1) {
            PickerBestSeatsSelect(
                label = category?.label ?: scope.strings.anyTicketType,
                choices = forSale,
                anything = scope.strings.anyTicketType,
                choiceLabel = SeatLayerPickerCategory::label,
                onSelected = { category = it },
            )
        }
        // A venue with no zones is not asked which zone.
        if (zones.isNotEmpty()) {
            PickerBestSeatsSelect(
                label = zone?.label ?: scope.strings.anyVenueZone,
                choices = zones,
                anything = scope.strings.anyVenueZone,
                choiceLabel = SeatLayerPickerZone::label,
                onSelected = { zone = it },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BestSeatsLiterals.ROW_GAP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickerQuantityStepper(
                value = quantity,
                maximum = maximum,
                enabled = enabled,
                onValue = { quantity = it },
            )
            PickerBestSeatsAction(
                quantity = quantity,
                busy = submitting,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onPress = {
                    coroutineScope.launch {
                        submitting = true
                        try {
                            scope.performAction {
                                scope.controller.bestAvailable(
                                    quantity = quantity,
                                    categoryKey = category?.key,
                                    zoneId = zone?.id,
                                    ttlMillis = scope.options.holdTtl
                                        ?.inWholeMilliseconds
                                        ?.toInt(),
                                )
                            }
                        } finally {
                            submitting = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun <T> PickerBestSeatsSelect(
    label: String,
    choices: List<T>,
    anything: String,
    choiceLabel: (T) -> String,
    onSelected: (T?) -> Unit,
) {
    val theme = LocalSeatLayerPickerTheme.current
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(SeatLayerSizeTokens.BEST_SEATS_SELECT_HEIGHT.dp)
                .clickable { expanded = true }
                .semantics { role = Role.Button },
            shape = RoundedCornerShape(SeatLayerRadiusTokens.CONTROL.dp),
            color = theme.surface,
            contentColor = theme.text,
            border = BorderStroke(1.dp, theme.divider),
        ) {
            Row(
                modifier = Modifier.padding(
                    start = BestSeatsLiterals.SELECT_PAD_START.dp,
                    end = BestSeatsLiterals.SELECT_PAD_END.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    fontSize = SeatLayerTypeTokens.BEST_SEATS_SELECT_SIZE.sp,
                    fontWeight = FontWeight(SeatLayerTypeTokens.BEST_SEATS_SELECT_WEIGHT),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SeatLayerPickerIcon(
                    glyph = SeatLayerPickerGlyph.ChevronUp,
                    color = theme.mutedText,
                    modifier = Modifier
                        .size(BestSeatsLiterals.SELECT_CHEVRON.dp)
                        .rotate(180f),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(anything) },
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

/** A tabular value between two filled keys, at the width the design names. */
@Composable
private fun PickerQuantityStepper(
    value: Int,
    maximum: Int,
    enabled: Boolean,
    onValue: (Int) -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val theme = scope.theme
    Surface(
        modifier = Modifier
            .width(SeatLayerSizeTokens.BEST_SEATS_STEPPER_WIDTH.dp)
            .height(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp),
        shape = RoundedCornerShape(SeatLayerRadiusTokens.CONTROL.dp),
        color = theme.surface,
        contentColor = theme.text,
        border = BorderStroke(1.dp, theme.divider),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PickerStepperKey(
                glyph = SeatLayerPickerGlyph.Minus,
                label = scope.strings.fewerTickets,
                enabled = enabled && value > 1,
                onClick = { onValue((value - 1).coerceAtLeast(1)) },
            )
            Text(
                text = value.toString(),
                // The number the two keys move, said when it moves: the buyer
                // presses a key, not the figure between them.
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = theme.text,
                fontSize = SeatLayerTypeTokens.BEST_SEATS_SELECT_SIZE.sp,
                fontWeight = FontWeight(BestSeatsLiterals.KEY_GLYPH_WEIGHT),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
            )
            PickerStepperKey(
                glyph = SeatLayerPickerGlyph.Plus,
                label = scope.strings.moreTickets,
                enabled = enabled && value < maximum,
                onClick = { onValue((value + 1).coerceAtMost(maximum)) },
            )
        }
    }
}

@Composable
private fun PickerStepperKey(
    glyph: SeatLayerPickerGlyph,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalSeatLayerPickerTheme.current
    Surface(
        modifier = Modifier
            .size(BestSeatsLiterals.KEY_SIZE.dp)
            .clip(RoundedCornerShape(BestSeatsLiterals.KEY_RADIUS.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        shape = RoundedCornerShape(BestSeatsLiterals.KEY_RADIUS.dp),
        color = lerp(theme.surface, theme.accent, BestSeatsLiterals.KEY_WASH),
        contentColor = theme.text,
    ) {
        Box(contentAlignment = Alignment.Center) {
            SeatLayerPickerIcon(
                glyph = glyph,
                color = if (enabled) {
                    theme.text
                } else {
                    theme.text.copy(alpha = SeatLayerOpacityTokens.MAP_CONTROL_DISABLED)
                },
                modifier = Modifier.size(BestSeatsLiterals.KEY_GLYPH_SIZE.dp),
            )
        }
    }
}

@Composable
private fun PickerBestSeatsAction(
    quantity: Int,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onPress: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val theme = scope.theme
    Surface(
        modifier = modifier
            .height(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
            .clickable(enabled = enabled, onClick = onPress)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(SeatLayerRadiusTokens.CONTROL.dp),
        // Busy keeps the accent at slight transparency rather than going grey:
        // the work is still the work the buyer asked for.
        color = if (busy) {
            theme.accent.copy(alpha = BestSeatsLiterals.BUSY_ALPHA)
        } else {
            theme.accent
        },
        contentColor = theme.onAccent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = BestSeatsLiterals.ACTION_PAD_X.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(BestSeatsLiterals.SPINNER_SIZE.dp),
                    strokeWidth = BestSeatsLiterals.SPINNER_STROKE.dp,
                    color = theme.onAccent,
                )
            } else {
                PickerSparkle(
                    color = theme.onAccent,
                    modifier = Modifier.size(BestSeatsLiterals.ACTION_SPARKLE.dp),
                )
            }
            Spacer(Modifier.width(BestSeatsLiterals.SPARKLE_GAP.dp))
            Text(
                text = if (busy) {
                    scope.strings.findingBestSeats
                } else {
                    scope.strings.findBestSeats(quantity)
                },
                fontSize = SeatLayerTypeTokens.BEST_SEATS_GO_SIZE.sp,
                fontWeight = FontWeight(SeatLayerTypeTokens.BEST_SEATS_GO_WEIGHT),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The flourish that marks the finder wherever it appears. */
@Composable
private fun PickerSparkle(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val stroke = size.minDimension * 0.14f
        val reach = size.minDimension / 2f
        listOf(0f, 90f, 45f, 135f).forEachIndexed { index, degrees ->
            val length = if (index < 2) reach else reach * 0.55f
            val radians = Math.toRadians(degrees.toDouble())
            val dx = (kotlin.math.cos(radians) * length).toFloat()
            val dy = (kotlin.math.sin(radians) * length).toFloat()
            drawLine(
                color = color,
                start = Offset(centre.x - dx, centre.y - dy),
                end = Offset(centre.x + dx, centre.y + dy),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/** The disclosure's own glyph. */
@Composable
private fun PickerInfoGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.09f
        drawCircle(
            color = color,
            radius = size.minDimension / 2f - stroke,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(size.width / 2f, size.height * 0.44f),
            end = Offset(size.width / 2f, size.height * 0.72f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawCircle(
            color = color,
            radius = stroke * 0.6f,
            center = Offset(size.width / 2f, size.height * 0.3f),
        )
    }
}

/** Card-local numbers the design source keeps as literals. Reported as a gap. */
private object BestSeatsLiterals {
    const val INITIAL_QUANTITY = 2
    const val PLATE_PAD = 8f
    const val PLATE_RADIUS = 11f
    const val BORDER_ACCENT = 0.34f
    const val WASH_FROM = 0.05f
    const val WASH_TO = 0.11f
    const val ROW_GAP = 6f
    const val TITLE_SPARKLE = 14f
    const val SPARKLE_GAP = 6f
    const val TITLE_SIZE = 12.5f
    const val TITLE_WEIGHT = 800
    const val INFO_SIZE = 15f
    const val EXPLAIN_SIZE = 12f
    const val EXPLAIN_PAD_TOP = 4f
    const val SELECT_PAD_START = 9f
    const val SELECT_PAD_END = 10f
    const val SELECT_CHEVRON = 16f
    const val KEY_SIZE = 34f
    const val KEY_RADIUS = 7f
    const val KEY_WASH = 0.1f
    const val KEY_GLYPH_SIZE = 14f
    const val KEY_GLYPH_WEIGHT = 800
    const val ACTION_SPARKLE = 13f
    const val ACTION_PAD_X = 10f
    const val BUSY_ALPHA = 0.8f
    const val SPINNER_SIZE = 14f
    const val SPINNER_STROKE = 2f
}
