package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerAccessNeed
import io.seatlayer.android.SeatLayerPickerAccessibilityAvailability
import kotlinx.coroutines.launch

/**
 * The filters a buyer with an access need reaches for.
 *
 * Every switch applies as it is flipped and the sheet stays open: a switch IS
 * the action, so staging the flips behind an apply step asked twice for one
 * decision — and left a buyer who dragged the sheet away, the gesture that
 * closes every other sheet, with a map that had ignored everything they did.
 */
@Composable
public fun SeatLayerPickerAccessibilityFilters(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    SeatLayerPickerAccessibilityFiltersContent(
        modifier = modifier,
        compact = compact,
        availabilityOverride = null,
        initiallyExpanded = false,
    )
}

/** Deterministic component harness; production callers always use runtime gates. */
@Composable
internal fun SeatLayerPickerAccessibilityFiltersForEvidence(
    availability: SeatLayerPickerAccessibilityAvailability,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    initiallyExpanded: Boolean = false,
) {
    SeatLayerPickerAccessibilityFiltersContent(
        modifier = modifier,
        compact = compact,
        availabilityOverride = availability,
        initiallyExpanded = initiallyExpanded,
    )
}

private const val WHEELCHAIR_NEED_KEY: String = "wheelchair"
private const val COMPANION_NEED_KEY: String = "companion"
private const val LIMITED_VIEW_ROW_KEY: String = "limitedView"
private const val COLORBLIND_ROW_KEY: String = "colorblindSafe"
private const val RESTRICTED_VIEW_ICON_KEY: String = "restrictedView"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeatLayerPickerAccessibilityFiltersContent(
    modifier: Modifier,
    compact: Boolean,
    availabilityOverride: SeatLayerPickerAccessibilityAvailability?,
    initiallyExpanded: Boolean,
) {
    val scope = LocalSeatLayerPickerScope.current
    val snapshot = scope.state.snapshot ?: return
    val controller = scope.controller
    val nativeChrome = controller.supportsCapability("native-chrome-contract-v1")
    // An unadvertised capability is a feature that is not offered: the row is
    // absent, silently, and the sheet keeps its shape around what is left.
    val availability = availabilityOverride ?: SeatLayerPickerAccessibilityAvailability(
        accessibility = snapshot.capabilities.contains("accessibilityFilter") &&
            controller.supportsCapability("access-needs-v1") &&
            nativeChrome &&
            controller.supportsCommand("picker.setAccessibilityFilter"),
        limitedView = snapshot.capabilities.contains("limitedViewFilter") &&
            nativeChrome &&
            controller.supportsCommand("picker.setLimitedViewFilter"),
        colorblindSafe = nativeChrome &&
            controller.supportsCapability("colorblind-safe") &&
            controller.supportsCommand("picker.setColorblindSafe"),
    )
    val needs = snapshot.map.accessNeeds
    val accessAvailable = availability.accessibility && needs.isNotEmpty()
    if (!accessAvailable && !availability.limitedView && !availability.colorblindSafe) return

    val active = snapshot.map.accessibilityFilter.toSet()
    val activeCount = (if (accessAvailable) active.size else 0) +
        (if (snapshot.map.hideLimitedView && availability.limitedView) 1 else 0) +
        (if (snapshot.map.colorblindSafe && availability.colorblindSafe) 1 else 0)

    var expanded by remember(scope.stateHolder) { mutableStateOf(initiallyExpanded) }
    val coroutineScope = rememberCoroutineScope()
    val busy = scope.state.presentation.actionInFlight

    AccessibilityLauncher(
        modifier = modifier,
        compact = compact,
        activeCount = activeCount,
        enabled = !busy,
        onClick = {
            if (busy) return@AccessibilityLauncher
            // Only one decision surface may hold the screen: an unanswered
            // card is cancelled first, so the sheet never comes up over a
            // question the buyer can neither read nor answer.
            coroutineScope.launch {
                scope.performAction { controller.cancelPending() }
                expanded = true
            }
        },
    )
    if (!expanded) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.toFloat()
    val bound = seatLayerAccessSheetMaxHeight(screenHeight)
    // The count only becomes a jump where the runtime answers the tour.
    val canJump = controller.supportsAccessibilityFocus
    val hasCompanionPlaces = needs.any { it.key == COMPANION_NEED_KEY }

    fun apply(mutation: suspend () -> Unit) {
        coroutineScope.launch { scope.performAction(mutation) }
    }

    val needRows = if (accessAvailable) {
        needs.map { need -> need.toRow(scope, active, hasCompanionPlaces, canJump) }
    } else {
        emptyList()
    }
    val mapRows = buildList {
        if (availability.limitedView) {
            add(
                PickerAccessRowModel(
                    key = LIMITED_VIEW_ROW_KEY,
                    // The switch that hides limited-view seats wears the mark
                    // those seats carry, so the row and what it acts on are
                    // one idea.
                    iconKey = RESTRICTED_VIEW_ICON_KEY,
                    label = scope.strings.hideLimitedView,
                    checked = snapshot.map.hideLimitedView,
                    reservesCountColumn = false,
                ),
            )
        }
        if (availability.colorblindSafe) {
            add(
                PickerAccessRowModel(
                    key = COLORBLIND_ROW_KEY,
                    iconKey = PICKER_ACCESS_CONTRAST_KEY,
                    label = scope.strings.colorblindSafe,
                    checked = snapshot.map.colorblindSafe,
                    reservesCountColumn = false,
                ),
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = { expanded = false },
        sheetState = sheetState,
        containerColor = scope.theme.surface,
        contentColor = scope.theme.text,
        shape = RoundedCornerShape(
            topStart = scope.styles.sheetRadius,
            topEnd = scope.styles.sheetRadius,
        ),
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        PickerAccessSheetWidth {
            Column(
                Modifier
                    .fillMaxWidth()
                    // BOUNDED, AND IT SCROLLS INSIDE THE BOUND: twelve rows in
                    // an unbounded sheet take the whole screen, and the map the
                    // buyer is filtering disappears behind the filter.
                    .heightIn(max = bound.dp)
                    .padding(
                        start = SHEET_GUTTER.dp,
                        end = SHEET_GUTTER.dp,
                        bottom = SHEET_GUTTER.dp,
                    ),
            ) {
                Text(
                    scope.strings.accessibilityTitle,
                    modifier = Modifier.semantics { heading() },
                    color = scope.theme.text,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = SHEET_TITLE_FONT_SIZE.sp,
                    fontFamily = scope.theme.fontFamily,
                )
                Spacer(Modifier.height(SHEET_TITLE_GAP.dp))
                if (needRows.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        needRows.forEachIndexed { index, row ->
                            PickerAccessRow(
                                model = row,
                                hairline = index < needRows.lastIndex,
                                onToggle = {
                                    val next = active.toMutableSet()
                                    if (!next.add(row.key)) next.remove(row.key)
                                    apply { controller.setAccessibilityFilter(next.toList()) }
                                },
                                onJump = if (canJump) {
                                    {
                                        // The one control on the sheet that
                                        // closes it: the walk it starts happens
                                        // on the map this sheet covers.
                                        expanded = false
                                        val next = active.toMutableSet().apply { add(row.key) }
                                        apply {
                                            controller.setAccessibilityFilter(next.toList())
                                            controller.focusNextAccessibleSection(listOf(row.key))
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
                PickerAccessMapGroup(rows = mapRows) { row ->
                    when (row.key) {
                        LIMITED_VIEW_ROW_KEY ->
                            apply { controller.setLimitedViewHidden(!row.checked) }
                        COLORBLIND_ROW_KEY ->
                            apply { controller.setColorblindSafe(!row.checked) }
                    }
                }
            }
        }
    }
}

@Suppress("ReturnCount")
private fun SeatLayerPickerAccessNeed.toRow(
    scope: SeatLayerPickerScope,
    active: Set<String>,
    hasCompanionPlaces: Boolean,
    canJump: Boolean,
): PickerAccessRowModel {
    val on = key in active
    val label = scope.strings.accessNeed(key)
    val free = count
    return PickerAccessRowModel(
        key = key,
        iconKey = key,
        label = label,
        // The companion sentence is only true of a chart that authors
        // companion places, so it is drawn from the same inventory the rows
        // are — and it rides an ⓘ rather than a second line.
        note = scope.strings.companionSeatsNote
            .takeIf { hasCompanionPlaces && key == WHEELCHAIR_NEED_KEY },
        count = free,
        checked = on,
        // A provision the venue has but has sold out of stays on the sheet and
        // goes dark; one it never had is absent. A switch that is ON stays live
        // at zero: the buyer holding the last space is the one who emptied it.
        // A count that is not COUNTED is not zero, and never disables the row.
        enabled = on || free == null || free > 0,
        jumpLabel = if (canJump && (free ?: 0) > 0) {
            listOf(
                label,
                scope.strings.accessFreeCount(free ?: 0),
                scope.strings.accessJumpFirstSection,
            ).joinToString(", ")
        } else {
            null
        },
    )
}

private const val SHEET_GUTTER: Float = 16f
private const val SHEET_TITLE_FONT_SIZE: Float = 20f
private const val SHEET_TITLE_GAP: Float = 12f
