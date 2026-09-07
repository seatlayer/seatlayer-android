package io.seatlayer.android.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * One switchable line on the accessibility sheet: an icon cell, what it is,
 * how many are free, and the switch that turns it on.
 *
 * The WHOLE ROW is the control — a 50-point line, not a 20-point switch at the
 * end of one — so the buyer aims at the words rather than at the toggle. The
 * ⓘ and a pressable count are separate nodes inside it, because the three do
 * different things.
 */

/**
 * One row on the sheet.
 *
 * [count] is null for "not counted", which is not zero: the row then draws no
 * figure and is never disabled. An affirmative zero draws `0` and dims.
 */
internal data class PickerAccessRowModel(
    val key: String,
    val iconKey: String,
    val label: String,
    val note: String? = null,
    val count: Int? = null,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val reservesCountColumn: Boolean = true,
    /** What a screen reader calls the count when the tour makes it a button. */
    val jumpLabel: String? = null,
)

/**
 * One fixed height for every row on the sheet.
 *
 * A sheet whose rows breathe differently reads as three lists in a trench
 * coat. The reference SDK keeps this and the count column as file-local
 * layout constants rather than design tokens; both are named here for the
 * same reason.
 */
internal const val PICKER_ACCESS_ROW_HEIGHT: Float = 50f

/** The count column, trailing-aligned, wide enough for "999 free". */
internal const val PICKER_ACCESS_COUNT_COLUMN: Float = 68f

/** The ⓘ column inside the label cell. */
private const val NOTE_COLUMN: Float = 36f

/** How far a row that cannot be had steps back. */
private const val DISABLED_ROW_OPACITY: Float = 0.58f

/** The hairline that closes a row, over the theme's own divider. */
private const val HAIRLINE_OPACITY: Float = 0.35f

/** The quiet ground under a pressable count. */
private const val COUNT_CHIP_OPACITY: Float = 0.06f

/** The switch's own track, off. */
private const val SWITCH_OFF_OPACITY: Float = 0.32f

/** What a row with nothing free reads in its count column: a figure. */
internal const val PICKER_ACCESS_ZERO_COUNT: String = "0"

@Composable
internal fun PickerAccessRow(
    model: PickerAccessRowModel,
    hairline: Boolean,
    onToggle: () -> Unit,
    onJump: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    var noteOpen by remember(model.key) { mutableStateOf(false) }
    val countText = model.count?.let { free ->
        if (free > 0) scope.strings.accessFreeCount(free) else PICKER_ACCESS_ZERO_COUNT
    }
    val jump = onJump?.takeIf { model.enabled && (model.count ?: 0) > 0 }
    val spoken = listOfNotNull(model.label, countText).joinToString(", ")
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PICKER_ACCESS_ROW_HEIGHT.dp)
                .clip(RoundedCornerShape(scope.styles.buttonRadius))
                .clickable(enabled = model.enabled, onClick = onToggle)
                .semantics(mergeDescendants = jump == null) {
                    role = Role.Switch
                    contentDescription = spoken
                    toggleableState = if (model.checked) {
                        ToggleableState.On
                    } else {
                        ToggleableState.Off
                    }
                }
                .alpha(if (model.enabled) 1f else DISABLED_ROW_OPACITY)
                .padding(horizontal = SeatLayerSizeTokens.ACCESS_ROW_PADDING_X.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(SeatLayerSizeTokens.ACCESS_ROW_ICON_CELL.dp)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                PickerAccessRowGlyph(
                    iconKey = model.iconKey,
                    color = scope.theme.mutedText,
                    modifier = Modifier.size(SeatLayerSizeTokens.ACCESS_ROW_ICON_SIZE.dp),
                )
            }
            Spacer(Modifier.width(SeatLayerSizeTokens.ACCESS_ROW_GAP.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = model.label,
                    modifier = Modifier.weight(1f, fill = false),
                    color = scope.theme.text,
                    fontSize = SeatLayerSizeTokens.ACCESS_ROW_LABEL_FONT_SIZE.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = scope.theme.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                model.note?.let { note ->
                    Box(
                        modifier = Modifier.width(NOTE_COLUMN.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PickerAccessNoteButton(
                            open = noteOpen,
                            note = note,
                            onPress = { noteOpen = !noteOpen },
                        )
                    }
                }
            }
            if (countText != null || model.reservesCountColumn) {
                Box(
                    modifier = Modifier.width(PICKER_ACCESS_COUNT_COLUMN.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    countText?.let { text ->
                        PickerAccessCount(
                            text = text,
                            jumpLabel = model.jumpLabel,
                            onJump = jump,
                        )
                    }
                }
            }
            Spacer(Modifier.width(SeatLayerSizeTokens.ACCESS_ROW_SWITCH_GAP.dp))
            PickerAccessSwitch(on = model.checked)
        }
        if (hairline) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(scope.theme.divider.copy(alpha = HAIRLINE_OPACITY)),
            )
        }
        // The sentence opens UNDER the row it explains, so the line stays a
        // line whether or not the buyer has asked for it.
        if (noteOpen) {
            model.note?.let { note ->
                Text(
                    text = note,
                    modifier = Modifier.padding(
                        start = (
                            SeatLayerSizeTokens.ACCESS_ROW_PADDING_X +
                                SeatLayerSizeTokens.ACCESS_ROW_ICON_CELL +
                                SeatLayerSizeTokens.ACCESS_ROW_GAP
                            ).dp,
                        end = SeatLayerSizeTokens.ACCESS_ROW_PADDING_X.dp,
                        bottom = SeatLayerSizeTokens.ACCESS_ROW_PADDING_Y.dp,
                    ).clearAndSetSemantics { },
                    color = scope.theme.mutedText,
                    fontSize = SeatLayerSizeTokens.ACCESS_ROW_NOTE_FONT_SIZE.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = scope.theme.fontFamily,
                )
            }
        }
    }
}

/** The group heading that stops the map switches reading as two more needs. */
@Composable
internal fun PickerAccessGroupHeading(text: String, modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    Text(
        text = text,
        modifier = modifier.padding(
            start = HEADING_INSET.dp,
            end = HEADING_INSET.dp,
            top = HEADING_TOP.dp,
            bottom = HEADING_BOTTOM.dp,
        ),
        color = scope.theme.mutedText,
        fontSize = HEADING_FONT_SIZE.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = scope.theme.fontFamily,
        letterSpacing = HEADING_TRACKING.sp,
    )
}

private const val HEADING_INSET: Float = 8f
private const val HEADING_TOP: Float = 18f
private const val HEADING_BOTTOM: Float = 4f
private const val HEADING_FONT_SIZE: Float = 11f
private const val HEADING_TRACKING: Float = 0.6f

/**
 * The ⓘ a row wears when it has a sentence to add.
 *
 * It carries the sentence as its own accessible name, so the note is spoken
 * whether or not it is open, and it is a separate node from the row's switch
 * because the two do different things.
 */
@Composable
private fun PickerAccessNoteButton(
    open: Boolean,
    note: String,
    onPress: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Box(
        modifier = Modifier
            .size(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
            .clip(RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp))
            .clickable(onClick = onPress)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = note
            },
        contentAlignment = Alignment.Center,
    ) {
        PickerInfoGlyph(
            filled = open,
            color = if (open) scope.theme.accent else scope.theme.mutedText,
            modifier = Modifier
                .size(SeatLayerSizeTokens.ACCESS_NOTE_ICON_SIZE.dp)
                .clearAndSetSemantics { },
        )
    }
}

/**
 * The `12 free` at the end of a row — a fact, or the button that starts the
 * accessible-section tour.
 *
 * One drawing for both, so the figure does not move or change size when it
 * becomes pressable.
 */
@Composable
private fun PickerAccessCount(
    text: String,
    jumpLabel: String?,
    onJump: (() -> Unit)?,
) {
    val scope = LocalSeatLayerPickerScope.current
    val figure: @Composable () -> Unit = {
        Text(
            text = text,
            color = if (onJump == null) scope.theme.mutedText else scope.theme.text,
            fontSize = SeatLayerSizeTokens.ACCESS_ROW_NOTE_FONT_SIZE.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = scope.theme.fontFamily,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
    // A count that is only a fact merges into the row's own line rather than
    // standing as a node of its own: the figure is part of what the row is
    // called, not a second thing to walk past.
    if (onJump == null) {
        figure()
        return
    }
    Box(
        modifier = Modifier
            .defaultMinSize(
                minWidth = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp,
                minHeight = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp,
            )
            .clip(RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp))
            .clickable(onClick = onJump)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = jumpLabel ?: text
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .height(SeatLayerSizeTokens.ACCESS_STEP_HEIGHT.dp)
                .clip(RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp))
                .background(scope.theme.text.copy(alpha = COUNT_CHIP_OPACITY))
                .padding(horizontal = SeatLayerSizeTokens.ACCESS_STEP_PADDING_X.dp),
            contentAlignment = Alignment.Center,
        ) { figure() }
    }
}

/**
 * The switch drawn at the end of a row.
 *
 * Drawn rather than the platform's: a Material switch is half again as tall as
 * this line and would set the row's height instead of sitting in it. The row
 * carries the semantics, so this is decoration.
 */
@Composable
private fun PickerAccessSwitch(on: Boolean) {
    val scope = LocalSeatLayerPickerScope.current
    val width = SeatLayerSizeTokens.ACCESS_SWITCH_WIDTH
    val height = SeatLayerSizeTokens.ACCESS_SWITCH_HEIGHT
    val knob = SeatLayerSizeTokens.ACCESS_SWITCH_KNOB
    val inset = (height - knob) / 2f
    val travel = width - knob - inset * 2f
    val offset by animateDpAsState(
        targetValue = if (on) travel.dp else 0.dp,
        animationSpec = tween(
            durationMillis = if (seatLayerPickerMotionEnabled()) {
                SeatLayerMotionTokens.CROSSFADE
            } else {
                0
            },
        ),
        label = "seatlayer-access-switch",
    )
    Box(
        modifier = Modifier
            .size(width = width.dp, height = height.dp)
            .clip(RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp))
            .background(
                if (on) {
                    scope.theme.accent
                } else {
                    scope.theme.mutedText.copy(alpha = SWITCH_OFF_OPACITY)
                },
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = inset.dp + offset)
                .size(knob.dp)
                .clip(RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp))
                .background(scope.theme.surface),
        )
    }
}

/**
 * The drawing a sheet row wears.
 *
 * Provision rows take the attribute's own mark from the shared per-attribute
 * set, by the runtime's own key: one wheelchair standing for twelve different
 * provisions tells the buyer nothing about which row is which. The colour row
 * wears a contrast disc, which is authored beside that set rather than in it —
 * it recolours the map, it does not choose seats.
 */
@Composable
private fun PickerAccessRowGlyph(
    iconKey: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // The shared per-attribute set is asked first, so a provision wears its
    // OWN mark. It also carries the colour row's contrast disc, authored
    // beside the seat attributes rather than in them.
    if (seatLayerSeatIconPath(iconKey) != null) {
        SeatLayerSeatIcon(iconKey, color, modifier)
        return
    }
    SeatLayerPickerIcon(seatLayerAccessRowGlyph(iconKey), color, modifier)
}

/** The ⓘ, outlined when the sentence is closed and solid when it is open. */
@Composable
private fun PickerInfoGlyph(
    filled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        val stroke = size.minDimension * STROKE_RATIO
        if (filled) {
            drawCircle(color = color, radius = radius, center = centre)
        } else {
            drawCircle(
                color = color,
                radius = radius - stroke / 2f,
                center = centre,
                style = Stroke(width = stroke),
            )
        }
        val ink = if (filled) Color.Transparent else color
        if (!filled) {
            drawCircle(
                color = ink,
                radius = stroke * 0.6f,
                center = Offset(centre.x, centre.y - radius * 0.42f),
            )
            drawLine(
                color = ink,
                start = Offset(centre.x, centre.y - radius * 0.05f),
                end = Offset(centre.x, centre.y + radius * 0.5f),
                strokeWidth = stroke,
            )
        }
    }
}

private const val STROKE_RATIO: Float = 0.105f

/** The wire key the colour row is drawn under; never a runtime provision. */
internal const val PICKER_ACCESS_CONTRAST_KEY: String = "contrast"

/**
 * The mark a key with no drawing of its own falls back to.
 *
 * The per-attribute set is asked first and answers for every provision the
 * runtime names. A key that set has never heard of still gets a mark rather
 * than a gap, and a view restriction reads as an eye rather than as a
 * wheelchair.
 */
internal fun seatLayerAccessRowGlyph(iconKey: String): SeatLayerPickerGlyph = when (iconKey) {
    "restrictedView", "obstructedView" -> SeatLayerPickerGlyph.Eye
    else -> SeatLayerPickerGlyph.Accessibility
}

/**
 * How tall the sheet may grow, given the room the screen has.
 *
 * A fraction of the room that exists, floored so a short phone still gets a
 * window worth scrolling rather than a sliver, and never taller than the
 * screen it is bounded by.
 */
internal fun seatLayerAccessSheetMaxHeight(screenHeight: Float): Float {
    val bound = screenHeight * SeatLayerSizeTokens.ACCESS_SHEET_MAX_HEIGHT_FRACTION
    val floored = maxOf(bound, SeatLayerSizeTokens.ACCESS_SHEET_MIN_HEIGHT)
    return minOf(floored, screenHeight)
}

/** Everything below the sheet's own scrolling list, kept out of the scroll. */
@Composable
internal fun PickerAccessMapGroup(
    rows: List<PickerAccessRowModel>,
    modifier: Modifier = Modifier,
    onToggle: (PickerAccessRowModel) -> Unit,
) {
    if (rows.isEmpty()) return
    val scope = LocalSeatLayerPickerScope.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Top) {
        PickerAccessGroupHeading(scope.strings.viewGroupTitle)
        rows.forEachIndexed { index, row ->
            PickerAccessRow(
                model = row,
                hairline = index < rows.lastIndex,
                onToggle = { onToggle(row) },
                onJump = null,
            )
        }
    }
}

/** A narrow wrapper so the sheet's own width never collapses on a tablet. */
@Composable
internal fun PickerAccessSheetWidth(
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().widthIn(max = SHEET_MAX_WIDTH.dp)) { content() }
}

private const val SHEET_MAX_WIDTH: Float = 560f

/**
 * What the accessible-section stepper prints for one step off the wire.
 *
 * The runtime counts its steps from zero and the buyer counts from one, so the
 * display index is the wire index plus one: `2 of 6` is the third stop only if
 * the port forgets which of the two it is holding.
 */
internal fun seatLayerAccessibleStepLabel(
    strings: SeatLayerPickerStrings,
    step: io.seatlayer.android.SeatLayerPickerAccessibleStep,
): String = strings.accessibleStep(step.index + 1, step.total)
