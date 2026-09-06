package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.seatlayer.android.SeatLayerPickerAccessNeed
import io.seatlayer.android.SeatLayerPickerAccessibility
import io.seatlayer.android.SeatLayerPickerAccessibilityAvailability
import io.seatlayer.android.SeatLayerPickerAccessibilityDraft
import io.seatlayer.android.SeatLayerPickerAccessibilityMutation
import kotlinx.coroutines.launch

/** Runtime-authored accessibility and colour filter sheet. */
@Composable
public fun SeatLayerPickerAccessibilityFilters(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    SeatLayerPickerAccessibilityFiltersContent(
        modifier = modifier,
        compact = compact,
        availabilityOverride = null,
    )
}

/** Deterministic component harness; production callers always use runtime gates. */
@Composable
internal fun SeatLayerPickerAccessibilityFiltersForEvidence(
    availability: SeatLayerPickerAccessibilityAvailability,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    SeatLayerPickerAccessibilityFiltersContent(
        modifier = modifier,
        compact = compact,
        availabilityOverride = availability,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeatLayerPickerAccessibilityFiltersContent(
    modifier: Modifier,
    compact: Boolean,
    availabilityOverride: SeatLayerPickerAccessibilityAvailability?,
) {
    val scope = LocalSeatLayerPickerScope.current
    val snapshot = scope.state.snapshot ?: return
    val controller = scope.controller
    val nativeChrome = controller.supportsCapability("native-chrome-contract-v1")
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
    val accessAvailable = availability.accessibility && snapshot.map.accessNeeds.isNotEmpty()
    val limitedAvailable = availability.limitedView
    val colorblindAvailable = availability.colorblindSafe
    if (!accessAvailable && !limitedAvailable && !colorblindAvailable) return

    val currentKeys = snapshot.map.accessibilityFilter.toCollection(linkedSetOf())
    fun currentDraft() = SeatLayerPickerAccessibilityDraft(
        keys = currentKeys,
        limitedView = snapshot.map.hideLimitedView,
        colorblindSafe = snapshot.map.colorblindSafe,
    )
    var expanded by remember(scope.stateHolder) { mutableStateOf(false) }
    var initial by remember(scope.stateHolder) { mutableStateOf(currentDraft()) }
    var draft by remember(scope.stateHolder) { mutableStateOf(currentDraft()) }
    var applying by remember(scope.stateHolder) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val activeCount = currentKeys.size +
        (if (snapshot.map.hideLimitedView && limitedAvailable) 1 else 0) +
        (if (snapshot.map.colorblindSafe && colorblindAvailable) 1 else 0)

    fun open() {
        if (applying || scope.state.presentation.actionInFlight) return
        val next = currentDraft()
        initial = next
        draft = next
        expanded = true
    }

    AccessibilityLauncher(
        modifier = modifier,
        compact = compact,
        activeCount = activeCount,
        enabled = !applying && !scope.state.presentation.actionInFlight,
        onClick = ::open,
    )
    if (!expanded) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!applying) expanded = false },
        sheetState = sheetState,
        containerColor = scope.theme.surface,
        contentColor = scope.theme.onSurface,
        shape = RoundedCornerShape(
            topStart = scope.styles.sheetRadius,
            topEnd = scope.styles.sheetRadius,
        ),
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(
                scope.strings.accessibilityTitle,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (accessAvailable) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        snapshot.map.accessNeeds.forEach { need ->
                            val selected = need.key in draft.keys
                            AccessibilityNeedChip(
                                need = need,
                                selected = selected,
                                enabled = !applying && (need.isAvailable || selected),
                            ) {
                                val next = draft.keys.toCollection(linkedSetOf())
                                if (!next.add(need.key)) next.remove(need.key)
                                draft = draft.copy(keys = next)
                            }
                        }
                    }
                }
                if (limitedAvailable) {
                    AccessibilityToggleOption(
                        label = scope.strings.limitedView,
                        selected = draft.limitedView,
                        enabled = !applying,
                    ) { draft = draft.copy(limitedView = !draft.limitedView) }
                }
                if (colorblindAvailable) {
                    AccessibilityToggleOption(
                        label = scope.strings.colorblindSafe,
                        selected = draft.colorblindSafe,
                        enabled = !applying,
                    ) { draft = draft.copy(colorblindSafe = !draft.colorblindSafe) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { expanded = false },
                    enabled = !applying,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(scope.strings.cancel) }
                Button(
                    onClick = {
                        val mutations = SeatLayerPickerAccessibility.plan(
                            draft = draft,
                            initial = initial,
                            available = availability,
                        )
                        applying = true
                        coroutineScope.launch {
                            val succeeded = scope.performActionResult {
                                mutations.forEach { mutation ->
                                    when (mutation) {
                                        is SeatLayerPickerAccessibilityMutation.Accessibility ->
                                            controller.setAccessibilityFilter(mutation.keys)
                                        is SeatLayerPickerAccessibilityMutation.LimitedView ->
                                            controller.setLimitedViewFilter(mutation.enabled)
                                        is SeatLayerPickerAccessibilityMutation.ColorblindSafe ->
                                            controller.setColorblindSafe(mutation.enabled)
                                    }
                                }
                                if (
                                    SeatLayerPickerAccessibility.shouldFocusSeats(mutations) &&
                                    snapshot.map.rung != "seats" &&
                                    controller.supportsCommand("picker.setRung")
                                ) controller.setRung("seats")
                            }
                            applying = false
                            if (succeeded) expanded = false
                        }
                    },
                    enabled = !applying,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(scope.strings.apply) }
            }
        }
    }
}


@Composable
private fun LegacySeatLayerPickerAccessibilityFilters(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val needs = scope.state.snapshot?.map?.accessNeeds.orEmpty()
    val active = scope.state.snapshot?.map?.accessibilityFilter.orEmpty().toSet()
    val coroutineScope = rememberCoroutineScope()
    if (compact) {
        var expanded by remember { mutableStateOf(false) }
        Surface(
            onClick = { expanded = true },
            modifier = modifier
                .size(48.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = scope.strings.accessibilityTitle
                },
            shape = CircleShape,
            color = Color.Transparent,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = scope.theme.surface.copy(alpha = 0.94f),
                    contentColor = scope.theme.onSurface,
                    border = BorderStroke(1.dp, scope.theme.divider),
                    shadowElevation = 3.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SeatLayerPickerIcon(
                            SeatLayerPickerGlyph.Accessibility,
                            scope.theme.onSurface,
                            Modifier.size(21.dp).clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
        if (expanded) {
            Dialog(onDismissRequest = { expanded = false }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = scope.theme.surface,
                    contentColor = scope.theme.onSurface,
                    shape = RoundedCornerShape(scope.styles.cardRadius),
                    shadowElevation = 12.dp,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            scope.strings.accessibilityTitle,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        needs.forEach { need ->
                            AccessibilityOption(
                                label = "${need.key} · ${need.count}",
                                selected = need.key in active,
                            ) {
                                val next = if (need.key in active) active - need.key else active + need.key
                                coroutineScope.launch {
                                    scope.performAction {
                                        scope.controller.setAccessibilityFilter(next.toList())
                                    }
                                }
                            }
                        }
                        AccessibilityOption(
                            label = scope.strings.limitedView,
                            selected = scope.state.snapshot?.map?.hideLimitedView == true,
                        ) {
                            coroutineScope.launch {
                                scope.performAction {
                                    scope.controller.setLimitedViewFilter(
                                        scope.state.snapshot?.map?.hideLimitedView != true,
                                    )
                                }
                            }
                        }
                        if (scope.options.chrome.colorblind) {
                            AccessibilityOption(
                                label = scope.strings.colorblindSafe,
                                selected = scope.state.snapshot?.map?.colorblindSafe == true,
                            ) {
                                coroutineScope.launch {
                                    scope.performAction {
                                        scope.controller.setColorblindSafe(
                                            scope.state.snapshot?.map?.colorblindSafe != true,
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = { expanded = false },
                            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                        ) { Text(scope.strings.dismiss) }
                    }
                }
            }
        }
        return
    }
    if (needs.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(needs, key = SeatLayerPickerAccessNeed::key) { need ->
            val selected = need.key in active
            Surface(
                onClick = {
                    val next = if (selected) active - need.key else active + need.key
                    coroutineScope.launch {
                        scope.performAction {
                            scope.controller.setAccessibilityFilter(next.toList())
                        }
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        role = Role.Checkbox
                        toggleableState = if (selected) {
                            ToggleableState.On
                        } else {
                            ToggleableState.Off
                        }
                    },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = if (selected) {
                    scope.theme.accent.copy(alpha = 0.14f)
                } else {
                    scope.theme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) scope.theme.accent else scope.theme.divider,
                ),
            ) {
                Text(
                    "${need.key} · ${need.count}",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun AccessibilityOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Checkbox
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            },
        color = Color.Transparent,
        contentColor = scope.theme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Surface(
                modifier = Modifier.size(24.dp),
                shape = RoundedCornerShape(7.dp),
                color = if (selected) scope.theme.accent else Color.Transparent,
                border = BorderStroke(1.dp, if (selected) scope.theme.accent else scope.theme.divider),
            ) {
                if (selected) {
                    Box(contentAlignment = Alignment.Center) {
                        SeatLayerPickerIcon(
                            SeatLayerPickerGlyph.Check,
                            scope.theme.onAccent,
                            Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
