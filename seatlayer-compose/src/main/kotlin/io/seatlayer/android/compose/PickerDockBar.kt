package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
        contentColor = resolvedStyle.contentColor ?: scope.theme.text,
        shadowElevation = resolvedStyle.elevation ?: SeatLayerElevationTokens.DOCK_BAR.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            resolvedStyle.cornerRadius ?: 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SeatLayerSizeTokens.DOCK_BAR_HEIGHT.dp)
                .padding(
                    start = resolvedStyle.horizontalPadding
                        ?: SeatLayerSizeTokens.DOCK_LEADING_INSET.dp,
                    end = resolvedStyle.horizontalPadding
                        ?: SeatLayerSizeTokens.DOCK_TRAILING_INSET.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(SeatLayerSizeTokens.DOCK_DOT_SIZE.dp),
                shape = CircleShape,
                color = dotColor,
            ) { }
            Spacer(Modifier.width(DOCK_DOT_GAP))
            Text(
                focused.displayLabel ?: focused.label,
                modifier = Modifier.weight(1f),
                fontSize = SeatLayerSizeTokens.DOCK_NAME_FONT_SIZE.sp,
                fontWeight = FontWeight(SeatLayerTypeTokens.DOCK_SECTION_WEIGHT),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            focused.seatsLeft?.let { count ->
                Text(
                    " ·  ${scope.strings.seatsLeft(count)}",
                    color = scope.theme.mutedText,
                    fontSize = SeatLayerSizeTokens.DOCK_COUNT_FONT_SIZE.sp,
                    fontWeight = FontWeight(SeatLayerTypeTokens.DOCK_COUNT_WEIGHT),
                    maxLines = 1,
                )
            }
            // Absent is not zero. Only a runtime that counts accessible places
            // may say how many this section has left; a dock that prints a zero
            // from an uncounted section tells a buyer it has nothing.
            if (scope.controller.supportsSectionAccessCounts) {
                focused.accessibleFree?.let { free ->
                    Row(
                        modifier = Modifier
                            .padding(start = DOCK_ACCESS_GAP)
                            .semantics {
                                contentDescription =
                                    "${scope.strings.accessibility} $free"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "· ",
                            color = scope.theme.mutedText,
                            fontSize = SeatLayerSizeTokens.DOCK_COUNT_FONT_SIZE.sp,
                        )
                        SeatLayerPickerIcon(
                            SeatLayerPickerGlyph.Accessibility,
                            scope.theme.mutedText,
                            Modifier
                                .size(SeatLayerSizeTokens.ACCESS_ROW_ICON_SIZE.dp)
                                .clearAndSetSemantics { },
                        )
                        Text(
                            " $free",
                            color = scope.theme.mutedText,
                            fontSize = SeatLayerSizeTokens.DOCK_COUNT_FONT_SIZE.sp,
                            fontWeight = FontWeight(SeatLayerTypeTokens.DOCK_COUNT_WEIGHT),
                            maxLines = 1,
                        )
                    }
                }
            }
            DockIconButton(
                label = scope.strings.previousSection,
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
                label = scope.strings.nextSection,
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
                    .height(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = scope.strings.overview
                    },
                color = Color.Transparent,
                contentColor = scope.theme.text,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeatLayerPickerIcon(
                        SeatLayerPickerGlyph.Back,
                        scope.theme.text,
                        Modifier
                            .size(SeatLayerSizeTokens.DOCK_BACK_CHEVRON_SIZE.dp)
                            .clearAndSetSemantics { },
                    )
                    Spacer(Modifier.width(SeatLayerSizeTokens.DOCK_NAV_GAP.dp))
                    Text(
                        scope.strings.overview,
                        fontSize = SeatLayerSizeTokens.DOCK_BACK_FONT_SIZE.sp,
                        fontWeight = FontWeight(SeatLayerTypeTokens.DOCK_SECTION_WEIGHT),
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
            .height(SeatLayerSizeTokens.DOCK_NAV_HEIGHT.dp)
            .width(SeatLayerSizeTokens.DOCK_NAV_WIDTH.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        color = Color.Transparent,
        contentColor = scope.theme.text,
    ) {
        Box(contentAlignment = Alignment.Center) {
            SeatLayerPickerIcon(
                glyph,
                if (enabled) {
                    scope.theme.text
                } else {
                    scope.theme.mutedText.copy(alpha = DOCK_NAV_DISABLED_INK)
                },
                Modifier
                    .size(SeatLayerSizeTokens.DOCK_NAV_ICON_SIZE.dp)
                    .clearAndSetSemantics { },
            )
        }
    }
}

private val DOCK_DOT_GAP: Dp = 8.dp
private val DOCK_ACCESS_GAP: Dp = 6.dp
private const val DOCK_NAV_DISABLED_INK = 0.45f
