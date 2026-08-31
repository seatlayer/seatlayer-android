package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.seatlayer.android.SEATLAYER_ALL_FLOORS
import io.seatlayer.android.SeatLayerPickerAccessNeed
import io.seatlayer.android.SeatLayerPickerFloorInfo
import io.seatlayer.android.SeatLayerPickerSectionSummary
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerPickerFloorSelector(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val floors = scope.state.snapshot?.map?.floors.orEmpty().pickerAuthoredFloors()
    if (!scope.controller.supportsFloorStack || floors.size < 2) return
    var expanded by remember { mutableStateOf(false) }
    val current = scope.state.snapshot?.map?.activeFloorId
    val label = if (scope.state.snapshot?.map?.showsAllFloors == true) {
        scope.strings.allFloors
    } else {
        floors.firstOrNull { it.id == current }?.name ?: scope.strings.floors
    }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.sizeIn(minHeight = 48.dp),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(scope.strings.allFloors) },
                onClick = {
                    expanded = false
                    coroutineScope.launch {
                        scope.performAction { scope.controller.showAllFloors() }
                    }
                },
            )
            floors.forEach { floor ->
                DropdownMenuItem(
                    text = { Text(floor.name) },
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

@Composable
public fun SeatLayerFloorStrip(
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    val scope = LocalSeatLayerPickerScope.current
    val map = scope.state.snapshot?.map ?: return
    val floors = map.floors.pickerAuthoredFloors()
    if (!scope.controller.supportsFloorStack || floors.size < 2) return
    val coroutineScope = rememberCoroutineScope()
    val choices = listOf(
        SeatLayerPickerFloorInfo(SEATLAYER_ALL_FLOORS, scope.strings.allFloors, null),
    ) + floors
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 48.dp else 52.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(choices, key = SeatLayerPickerFloorInfo::id) { floor ->
            val selected = if (floor.id == SEATLAYER_ALL_FLOORS) {
                map.showsAllFloors
            } else {
                !map.showsAllFloors && map.activeFloorId == floor.id
            }
            Surface(
                onClick = {
                    coroutineScope.launch {
                        scope.performAction { scope.controller.setFloor(floor.id) }
                    }
                },
                modifier = Modifier
                    .height(if (compact) 30.dp else 36.dp)
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) scope.theme.accent else scope.theme.surface,
                contentColor = if (selected) scope.theme.onAccent else scope.theme.onSurface,
                border = BorderStroke(
                    1.dp,
                    if (selected) scope.theme.accent else scope.theme.divider,
                ),
            ) {
                Text(
                    floor.name,
                    modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp),
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

internal fun List<SeatLayerPickerFloorInfo>.pickerAuthoredFloors():
    List<SeatLayerPickerFloorInfo> = distinctBy(SeatLayerPickerFloorInfo::id)
    .filterNot { it.id == SEATLAYER_ALL_FLOORS }

@Composable
public fun SeatLayerPickerSectionNavigator(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val sections = scope.state.snapshot?.sections.orEmpty()
    if (sections.isEmpty()) return
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            scope.strings.sections,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        sections.forEach { section ->
            SectionRow(section) {
                coroutineScope.launch {
                    scope.performAction { scope.controller.focusSection(section.id) }
                }
            }
        }
    }
}

@Composable
private fun SectionRow(
    section: SeatLayerPickerSectionSummary,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        color = scope.theme.surface,
        contentColor = scope.theme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(section.displayLabel ?: section.label, maxLines = 1)
                section.zoneLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = scope.theme.muted,
                    )
                }
            }
            section.seatsLeft?.let {
                Text(
                    scope.strings.seatsLeft(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = scope.theme.muted,
                )
            }
        }
    }
}

@Composable
public fun SeatLayerDockBar(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.DockBar]
    val snapshot = scope.state.snapshot ?: return
    if (snapshot.map.rung != "seats") return
    val sections = snapshot.sections
    val focused = snapshot.map.focusedSection
        ?: sections.firstOrNull { it.id == snapshot.map.focusedSectionId }
        ?: return
    val focusedIndex = sections.indexOfFirst { it.id == focused.id }
    val categoryColor = snapshot.categories
        .firstOrNull { it.key == focused.dominantCategoryKey }
        ?.color
    val dotColor = parseColor(focused.color ?: categoryColor) ?: scope.theme.accent
    val coroutineScope = rememberCoroutineScope()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = resolvedStyle.containerColor ?: scope.theme.surface,
        contentColor = resolvedStyle.contentColor ?: scope.theme.onSurface,
        shadowElevation = resolvedStyle.elevation ?: 4.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            resolvedStyle.cornerRadius ?: 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = resolvedStyle.horizontalPadding ?: 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = dotColor) { }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                focused.displayLabel ?: focused.label,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            focused.seatsLeft?.let { count ->
                Text(
                    " ·  ${scope.strings.seatsLeft(count)}",
                    color = scope.theme.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            DockIconButton(
                label = "Previous section",
                glyph = SeatLayerPickerGlyph.Back,
                onClick = {
                    sections.getOrNull(focusedIndex - 1)?.let { section ->
                        coroutineScope.launch {
                            scope.performAction { scope.controller.focusSection(section.id) }
                        }
                    }
                },
                enabled = focusedIndex > 0,
            )
            DockIconButton(
                label = "Next section",
                glyph = SeatLayerPickerGlyph.Forward,
                onClick = {
                    sections.getOrNull(focusedIndex + 1)?.let { section ->
                        coroutineScope.launch {
                            scope.performAction { scope.controller.focusSection(section.id) }
                        }
                    }
                },
                enabled = focusedIndex >= 0 && focusedIndex < sections.lastIndex,
            )
            Surface(
                onClick = {
                    coroutineScope.launch {
                        scope.performAction { scope.controller.overview() }
                    }
                },
                modifier = Modifier
                    .height(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = scope.strings.venue
                    },
                color = Color.Transparent,
                contentColor = scope.theme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeatLayerPickerIcon(
                        SeatLayerPickerGlyph.Back,
                        scope.theme.onSurface,
                        Modifier.size(16.dp).clearAndSetSemantics { },
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        scope.strings.venue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DockIconButton(
    label: String,
    glyph: SeatLayerPickerGlyph,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        color = Color.Transparent,
        contentColor = scope.theme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            SeatLayerPickerIcon(
                glyph,
                if (enabled) scope.theme.onSurface else scope.theme.muted.copy(alpha = 0.45f),
                Modifier.size(18.dp).clearAndSetSemantics { },
            )
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
