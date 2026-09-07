package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerAccessNeed
import io.seatlayer.android.SeatLayerPickerAccessibleStep
import io.seatlayer.android.SeatLayerPickerSnapshot
import kotlinx.coroutines.launch

/**
 * The control that opens the accessibility sheet.
 *
 * On the phone it is the disc at the HEAD of the map's control column, drawn in
 * the map's own chrome palette like every other disc there — a control about
 * who can sit where is a different and earlier question than how close the
 * camera is, and it belongs above the camera's controls rather than alone in
 * the opposite corner.
 */
@Composable
internal fun AccessibilityLauncher(
    modifier: Modifier,
    compact: Boolean,
    activeCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (compact) {
        Box(modifier) {
            PickerMapControlDisc(
                label = accessibilityControlLabel(scope, activeCount),
                glyph = SeatLayerPickerGlyph.Accessibility,
                enabled = enabled,
                active = activeCount > 0,
                onClick = onClick,
            )
            if (activeCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clearAndSetSemantics { },
                    shape = CircleShape,
                    color = scope.theme.accent,
                    contentColor = scope.theme.onAccent,
                ) {
                    Text(
                        activeCount.toString(),
                        modifier = Modifier.padding(
                            horizontal = ACCESS_BADGE_PADDING_X,
                            vertical = ACCESS_BADGE_PADDING_Y,
                        ),
                        fontSize = SeatLayerSizeTokens.ACCESS_STEP_FONT_SIZE.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
        return
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
            .semantics {
                role = Role.Button
                contentDescription = accessibilityControlLabel(scope, activeCount)
            },
        shape = RoundedCornerShape(scope.styles.buttonRadius),
        color = scope.theme.surface,
        contentColor = scope.theme.text,
        border = BorderStroke(1.dp, scope.theme.divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ACCESS_WIDE_PADDING_X),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeatLayerPickerIcon(
                SeatLayerPickerGlyph.Accessibility,
                if (activeCount > 0) scope.theme.accent else scope.theme.text,
                Modifier.size(ACCESS_WIDE_GLYPH_SIZE).clearAndSetSemantics { },
            )
            Spacer(Modifier.size(SeatLayerSizeTokens.ACCESS_ROW_GAP.dp))
            Text(
                accessibilityControlLabel(scope, activeCount),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The name the disc answers to.
 *
 * A chart that authors no provisions at all is offering colour choices, not
 * accommodations, and saying "accessibility" over a list of two display
 * switches promises something the event does not have.
 */
private fun accessibilityControlName(scope: SeatLayerPickerScope): String =
    if (scope.state.snapshot?.map?.accessNeeds.orEmpty().isEmpty()) {
        scope.strings.displayOptions
    } else {
        scope.strings.accessibility
    }

/**
 * The disc's spoken name.
 *
 * The count rides in the name rather than in a silent badge: a screen reader
 * that says only "accessibility" over a filtered map has hidden the fact that
 * the map IS filtered.
 */
private fun accessibilityControlLabel(scope: SeatLayerPickerScope, activeCount: Int): String {
    val name = accessibilityControlName(scope)
    return if (activeCount > 0) "$name · $activeCount" else name
}

private val ACCESS_BADGE_PADDING_X: Dp = 5.dp
private val ACCESS_BADGE_PADDING_Y: Dp = 1.dp
private val ACCESS_WIDE_PADDING_X: Dp = 12.dp
private val ACCESS_WIDE_GLYPH_SIZE: Dp = 18.dp

/**
 * Where a walk through the accessible sections has got to.
 *
 * The sheet that starts the walk is gone by the time the buyer takes the second
 * step, so the walk cannot live in it. `index` is 0-based on the wire and is
 * only ever DISPLAYED as `index + 1`.
 */
internal class PickerAccessibleTour {
    var step: SeatLayerPickerAccessibleStep? by mutableStateOf(null)
        private set
    var types: List<String> by mutableStateOf(emptyList())
        private set
    var exhausted: Boolean by mutableStateOf(false)
        private set
    var walking: Boolean by mutableStateOf(false)
        private set

    fun begin(next: List<String>) {
        types = next
        step = null
        exhausted = false
    }

    fun covers(active: Collection<String>): Boolean =
        types.isNotEmpty() && active.containsAll(types) && types.containsAll(active)

    fun enter(): Boolean {
        if (walking) return false
        walking = true
        return true
    }

    /** A null step means nothing matches — not an error, and never a zero total. */
    fun arrive(next: SeatLayerPickerAccessibleStep?) {
        walking = false
        if (next == null || next.total <= 0) {
            exhausted = true
            return
        }
        step = next
    }

    /** An error keeps whatever was last framed rather than losing the walk. */
    fun stumble() {
        walking = false
    }
}

/**
 * The pill that walks the sections holding a matching space.
 *
 * Draws nothing at all unless a filter is on AND the runtime answers the tour:
 * a tour that is not offered is not a failure, and an absent capability shows
 * as an absent control rather than an error.
 */
@Composable
public fun SeatLayerPickerAccessibleStepper(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val snapshot = scope.state.snapshot ?: return
    val active = snapshot.map.accessibilityFilter
    val tour = remember(scope.stateHolder) { PickerAccessibleTour() }
    val coroutineScope = rememberCoroutineScope()
    if (active.isEmpty()) return
    if (
        !scope.controller.supportsAccessibilityFocus ||
        !scope.controller.supportsCommand("picker.focusNextAccessibleSection")
    ) {
        return
    }
    val current = tour.covers(active)
    if (current && tour.exhausted) return
    val counted = scope.controller.supportsSectionAccessCounts
    val sections = pickerAccessibleSectionCount(snapshot)
    val step = tour.step.takeIf { current }
    // A runtime that counts and finds nothing has answered the question the
    // pill would ask. A runtime that does not count has not.
    if (counted && step == null && sections == 0) return
    val label = when {
        step != null -> scope.strings.accessibleStep(step.index + 1, step.total)
        counted -> scope.strings.accessibleSections(sections)
        // A runtime that flies but does not count still gets a pill, with no
        // figure on it.
        else -> null
    }
    val busy = tour.walking || scope.state.presentation.actionInFlight
    PickerAccessibleStepperPill(
        modifier = modifier,
        label = label,
        enabled = !busy,
    ) {
        if (!current) tour.begin(active)
        if (!tour.enter()) return@PickerAccessibleStepperPill
        coroutineScope.launch {
            val next = runCatching {
                scope.controller.focusNextAccessibleSection(tour.types)
            }
            if (next.isSuccess) tour.arrive(next.getOrNull()) else tour.stumble()
        }
    }
}

/**
 * How many sections still hold an accessible space.
 *
 * Null and zero are different facts: a section the runtime never counted is not
 * a section with nothing left.
 */
internal fun pickerAccessibleSectionCount(snapshot: SeatLayerPickerSnapshot): Int =
    snapshot.sections.count { (it.accessibleFree ?: 0) > 0 }

@Composable
private fun PickerAccessibleStepperPill(
    modifier: Modifier,
    label: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val spoken = scope.strings.accessJumpNextSection
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .height(SeatLayerSizeTokens.ACCESS_STEP_HEIGHT.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = if (label == null) spoken else "$label, $spoken"
                },
            shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
            color = scope.theme.surface,
            contentColor = scope.theme.text,
            border = BorderStroke(1.dp, scope.theme.divider),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = SeatLayerSizeTokens.ACCESS_STEP_PADDING_X.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    SeatLayerSizeTokens.ACCESS_STEP_GAP.dp,
                ),
            ) {
                SeatLayerPickerIcon(
                    SeatLayerPickerGlyph.Accessibility,
                    scope.theme.accent,
                    Modifier.size(ACCESS_STEP_GLYPH_SIZE).clearAndSetSemantics { },
                )
                if (label != null) {
                    Text(
                        label,
                        fontSize = SeatLayerSizeTokens.ACCESS_STEP_FONT_SIZE.sp,
                        fontWeight = FontWeight(SeatLayerTypeTokens.ACCESS_STEP_WEIGHT),
                        maxLines = 1,
                    )
                }
                SeatLayerPickerIcon(
                    SeatLayerPickerGlyph.Forward,
                    scope.theme.mutedText,
                    Modifier.size(ACCESS_STEP_CHEVRON_SIZE).clearAndSetSemantics { },
                )
            }
        }
    }
}

private val ACCESS_STEP_GLYPH_SIZE: Dp = 14.dp
private val ACCESS_STEP_CHEVRON_SIZE: Dp = 16.dp

@Composable
internal fun AccessibilityNeedChip(
    need: SeatLayerPickerAccessNeed,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .heightIn(min = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
            .semantics {
                role = Role.Checkbox
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            },
        color = if (selected) {
            scope.theme.accent.copy(alpha = ACCESS_CHIP_SELECTED_WASH)
        } else {
            Color.Transparent
        },
        contentColor = if (selected) scope.theme.accent else scope.theme.text,
        border = BorderStroke(
            if (selected) ACCESS_CHIP_SELECTED_LINE else 1.dp,
            if (selected) scope.theme.accent else scope.theme.divider,
        ),
        shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ACCESS_CHIP_PADDING_X,
                vertical = SeatLayerSizeTokens.ACCESS_ROW_PADDING_Y.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                SeatLayerPickerIcon(
                    SeatLayerPickerGlyph.Check,
                    scope.theme.accent,
                    Modifier
                        .size(SeatLayerSizeTokens.ACCESS_NOTE_ICON_SIZE.dp)
                        .clearAndSetSemantics { },
                )
                Spacer(Modifier.size(SeatLayerSizeTokens.ZOOM_COLUMN_GAP.dp))
            }
            Text(
                needLabel(need),
                color = if (enabled) {
                    if (selected) scope.theme.accent else scope.theme.text
                } else {
                    scope.theme.mutedText.copy(alpha = ACCESS_CHIP_DISABLED_INK)
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private const val ACCESS_CHIP_SELECTED_WASH = 0.12f
private const val ACCESS_CHIP_DISABLED_INK = 0.6f
private val ACCESS_CHIP_SELECTED_LINE: Dp = 1.5.dp
private val ACCESS_CHIP_PADDING_X: Dp = 14.dp

private fun needLabel(need: SeatLayerPickerAccessNeed): String =
    need.key.replace('-', ' ').replace('_', ' ')
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) } +
        " · ${need.count}"

@Composable
internal fun AccessibilityToggleOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACCESS_ROW_MIN_HEIGHT)
            .semantics {
                role = Role.Switch
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            },
        color = Color.Transparent,
        contentColor = scope.theme.text,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ACCESS_ROW_PADDING_X),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = if (enabled) scope.theme.text else scope.theme.mutedText,
                fontWeight = FontWeight.Medium,
            )
            Switch(
                checked = selected,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

private val ACCESS_ROW_MIN_HEIGHT: Dp = 52.dp
private val ACCESS_ROW_PADDING_X: Dp = 4.dp
