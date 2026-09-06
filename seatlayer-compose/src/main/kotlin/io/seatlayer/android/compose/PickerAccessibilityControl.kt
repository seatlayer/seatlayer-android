package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerAccessNeed

@Composable
internal fun AccessibilityLauncher(
    modifier: Modifier,
    compact: Boolean,
    activeCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (compact) Modifier.size(48.dp) else Modifier.fillMaxWidth())
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Button
                contentDescription = scope.strings.accessibilityTitle
            },
        shape = if (compact) CircleShape else RoundedCornerShape(scope.styles.buttonRadius),
        color = if (compact) Color.Transparent else scope.theme.surface,
        contentColor = scope.theme.onSurface,
        border = if (compact) null else BorderStroke(1.dp, scope.theme.divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 0.dp else 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeatLayerPickerIcon(
                SeatLayerPickerGlyph.Accessibility,
                if (activeCount > 0) scope.theme.accent else scope.theme.onSurface,
                Modifier.size(21.dp).clearAndSetSemantics { },
            )
            if (!compact) {
                Spacer(Modifier.size(8.dp))
                Text(scope.strings.accessibility, fontWeight = FontWeight.Bold)
            }
            if (activeCount > 0) {
                Spacer(Modifier.size(6.dp))
                Surface(
                    shape = CircleShape,
                    color = scope.theme.accent,
                    contentColor = scope.theme.onAccent,
                ) {
                    Text(
                        activeCount.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

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
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Checkbox
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            },
        color = if (selected) scope.theme.accent.copy(alpha = 0.12f) else Color.Transparent,
        contentColor = if (selected) scope.theme.accent else scope.theme.onSurface,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) scope.theme.accent else scope.theme.divider,
        ),
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                SeatLayerPickerIcon(
                    SeatLayerPickerGlyph.Check,
                    scope.theme.accent,
                    Modifier.size(15.dp).clearAndSetSemantics { },
                )
                Spacer(Modifier.size(6.dp))
            }
            Text(
                needLabel(need),
                color = if (enabled) {
                    if (selected) scope.theme.accent else scope.theme.onSurface
                } else {
                    scope.theme.muted.copy(alpha = 0.6f)
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun needLabel(need: SeatLayerPickerAccessNeed): String =
    need.key.replace('-', ' ').replace('_', ' ')
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) } +
        " · ${need.count}"
