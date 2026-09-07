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
                    color = scope.theme.mutedText,
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
                        Modifier.size(16.dp).clearAndSetSemantics { },
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        scope.strings.overview,
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
        contentColor = scope.theme.text,
    ) {
        Box(contentAlignment = Alignment.Center) {
            SeatLayerPickerIcon(
                glyph,
                if (enabled) scope.theme.text else scope.theme.mutedText.copy(alpha = 0.45f),
                Modifier.size(18.dp).clearAndSetSemantics { },
            )
        }
    }
}
