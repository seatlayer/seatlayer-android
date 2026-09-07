package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerAccessibilityAvailability
import io.seatlayer.android.SeatLayerPickerAccessibilityDraft
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
    var applying by remember(scope.stateHolder) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val draft = currentDraft()

    // Every switch applies as it is flipped: the map narrows under the sheet,
    // so the buyer sees what the filter did before deciding on the next one.
    fun applyNow(mutation: suspend () -> Unit) {
        if (applying) return
        applying = true
        coroutineScope.launch {
            scope.performActionResult { mutation() }
            applying = false
        }
    }
    val activeCount = currentKeys.size +
        (if (snapshot.map.hideLimitedView && limitedAvailable) 1 else 0) +
        (if (snapshot.map.colorblindSafe && colorblindAvailable) 1 else 0)

    fun open() {
        if (applying || scope.state.presentation.actionInFlight) return
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
        contentColor = scope.theme.text,
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
                                applyNow { controller.setAccessibilityFilter(next.toList()) }
                            }
                        }
                    }
                }
                if (limitedAvailable) {
                    AccessibilityToggleOption(
                        label = scope.strings.hideLimitedView,
                        selected = draft.limitedView,
                        enabled = !applying,
                    ) { applyNow { controller.setLimitedViewHidden(!draft.limitedView) } }
                }
                if (colorblindAvailable) {
                    AccessibilityToggleOption(
                        label = scope.strings.colorblindSafe,
                        selected = draft.colorblindSafe,
                        enabled = !applying,
                    ) { applyNow { controller.setColorblindSafe(!draft.colorblindSafe) } }
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = { expanded = false },
                enabled = !applying,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(scope.strings.dismiss) }
        }
    }
}
