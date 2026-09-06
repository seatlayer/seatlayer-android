package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SEATLAYER_ALL_FLOORS
import io.seatlayer.android.SeatLayerPickerFloorInfo
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
