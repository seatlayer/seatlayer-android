package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SEATLAYER_ALL_FLOORS
import io.seatlayer.android.SeatLayerPickerFloorInfo
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerPickerFloorSelector(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val map = scope.state.snapshot?.map
    val floors = map?.floors.orEmpty().pickerAuthoredFloors()
    if (floors.size < 2) return
    var expanded by remember { mutableStateOf(false) }
    val label = if (map?.showsAllFloors == true) {
        scope.strings.allFloors
    } else {
        floors.firstOrNull { it.id == map?.activeFloorId }?.name ?: scope.strings.floors
    }
    val coroutineScope = rememberCoroutineScope()
    val offersAll = scope.controller.supportsFloorStack && map?.floorMode != null

    Column(modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.sizeIn(
                minHeight = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp,
            ),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (offersAll) {
                DropdownMenuItem(
                    text = { Text(scope.strings.allFloors) },
                    onClick = {
                        expanded = false
                        coroutineScope.launch {
                            scope.performAction {
                                scope.controller.setFloor(SEATLAYER_ALL_FLOORS)
                            }
                        }
                    },
                )
            }
            floors.forEach { floor ->
                DropdownMenuItem(
                    text = { Text(pickerFloorLabel(floor, map?.floorLabelStyle)) },
                    onClick = {
                        expanded = false
                        coroutineScope.launch {
                            scope.performAction { scope.controller.setFloor(floor.id) }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Which floor of the building the buyer is looking at.
 *
 * A theatre stacked three levels deep drawn all at once is a picture of a
 * building, not a plan of one. It renders nothing unless there is a choice to
 * make: no floors, one floor, or a runtime that does not report floors, and the
 * strip is nothing at all.
 *
 * The "All floors" chip needs BOTH halves of the runtime's word — the
 * `floor-stack-v1` capability, which says the runtime has modes, and a reported
 * `floorMode`, which says which one it is in. A chip drawn on one half alone
 * would send the `'all'` sentinel to a runtime with no such floor.
 *
 * The floors are drawn in the order the snapshot gave them, which is the
 * venue's own order from the stage upward. Nothing is re-sorted: the runtime
 * reports no level, and a sort key that is always null only ever sorts by
 * accident. Below the phone breakpoint the runtime's own default is ONE floor,
 * not the stack; this control shows which that is and lets the buyer change it.
 */
@Composable
public fun SeatLayerFloorStrip(
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    val scope = LocalSeatLayerPickerScope.current
    val map = scope.state.snapshot?.map ?: return
    val floors = map.floors.pickerAuthoredFloors()
    // One floor is not a choice, and neither is none.
    if (floors.size < 2) return
    val coroutineScope = rememberCoroutineScope()
    val showsAll = map.showsAllFloors
    val offersAll = map.floorMode != null && scope.controller.supportsFloorStack
    val busy = scope.state.presentation.actionInFlight
    val disc = pickerMapChromeDisc(scope.theme, map.isVenue3D)

    fun choose(floorId: String) {
        coroutineScope.launch {
            scope.performAction { scope.controller.setFloor(floorId) }
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        // One track, not a row of loose chips: the floors are one control with
        // a choice in it, and the pill around them is what says so. It scrolls
        // inside itself, so a venue with six levels never pushes the map about.
        Surface(
            shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
            color = scope.theme.surface.copy(alpha = FLOOR_TRACK_ALPHA),
            contentColor = scope.theme.text,
            border = BorderStroke(1.dp, disc.line),
            shadowElevation = FLOOR_TRACK_ELEVATION,
        ) {
            LazyRow(
                modifier = Modifier.height(pickerFloorStripHeight(compact)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    SeatLayerSizeTokens.FLOOR_RAIL_PADDING.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(
                    SeatLayerSizeTokens.FLOOR_RAIL_GAP.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (offersAll) {
                    item(key = SEATLAYER_ALL_FLOORS) {
                        PickerFloorChip(
                            label = scope.strings.allFloors,
                            selected = showsAll,
                            compact = compact,
                            enabled = !busy && !showsAll,
                        ) { choose(SEATLAYER_ALL_FLOORS) }
                    }
                }
                items(floors.size, key = { floors[it].id }) { index ->
                    val floor = floors[index]
                    val selected = !showsAll && map.activeFloorId == floor.id
                    PickerFloorChip(
                        label = pickerFloorLabel(floor, map.floorLabelStyle),
                        selected = selected,
                        compact = compact,
                        enabled = !busy && !selected,
                    ) { choose(floor.id) }
                }
            }
        }
    }
}

/** How tall the strip is, so a layout can report the band it covers. */
internal fun pickerFloorStripHeight(compact: Boolean): Dp =
    pickerFloorChipHeight(compact) + (SeatLayerSizeTokens.FLOOR_RAIL_PADDING * 2).dp

private fun pickerFloorChipHeight(compact: Boolean): Dp =
    if (compact) {
        SeatLayerSizeTokens.FLOOR_CHIP_HEIGHT.dp
    } else {
        (SeatLayerSizeTokens.FLOOR_CHIP_HEIGHT + FLOOR_WIDE_GROWTH).dp
    }

/**
 * The words on a floor chip.
 *
 * `floorLabelStyle` is the runtime's word about how this venue names its
 * levels: a `number` venue wants "3", a naming one wants "Balcony". Absent, the
 * authored name stands — inventing a shorter one for a floor called "Royal
 * Circle" loses the only name the buyer has ever seen on a ticket.
 */
internal fun pickerFloorLabel(
    floor: SeatLayerPickerFloorInfo,
    labelStyle: String?,
): String = when (labelStyle) {
    "number" -> floor.name.pickerFloorNumber() ?: floor.name
    else -> floor.name
}

private fun String.pickerFloorNumber(): String? =
    filter(Char::isDigit).takeIf(String::isNotEmpty)

@Composable
private fun PickerFloorChip(
    label: String,
    selected: Boolean,
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .defaultMinSize(minHeight = pickerFloorChipHeight(compact))
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            },
        shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
        // Inside the track a chip is a segment, not a card: unselected it is
        // transparent on the track's own ground and reads as muted text, and
        // only the floor being drawn wears the accent.
        color = if (selected) scope.theme.accent else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) scope.theme.onAccent else scope.theme.mutedText,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = if (compact) {
                    SeatLayerSizeTokens.FLOOR_CHIP_PADDING_X.dp
                } else {
                    (SeatLayerSizeTokens.FLOOR_CHIP_PADDING_X + FLOOR_WIDE_PADDING_GROWTH).dp
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = if (compact) {
                    SeatLayerSizeTokens.FLOOR_CHIP_FONT_SIZE.sp
                } else {
                    (SeatLayerSizeTokens.FLOOR_CHIP_FONT_SIZE + FLOOR_WIDE_FONT_GROWTH).sp
                },
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (SeatLayerSizeTokens.FLOOR_CHIP_FONT_SIZE * FLOOR_TRACKING).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val FLOOR_TRACK_ALPHA = 0.92f
private const val FLOOR_TRACKING = 0.05f
private const val FLOOR_WIDE_GROWTH = 6f
private const val FLOOR_WIDE_PADDING_GROWTH = 3f
private const val FLOOR_WIDE_FONT_GROWTH = 1f
private val FLOOR_TRACK_ELEVATION: Dp = 2.dp

internal fun List<SeatLayerPickerFloorInfo>.pickerAuthoredFloors():
    List<SeatLayerPickerFloorInfo> = distinctBy(SeatLayerPickerFloorInfo::id)
    .filterNot { it.id == SEATLAYER_ALL_FLOORS }
