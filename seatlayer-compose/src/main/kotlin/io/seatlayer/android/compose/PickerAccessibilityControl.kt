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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        contentColor = scope.theme.text,
        border = if (compact) null else BorderStroke(1.dp, scope.theme.divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 0.dp else 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeatLayerPickerIcon(
                SeatLayerPickerGlyph.Accessibility,
                if (activeCount > 0) scope.theme.accent else scope.theme.text,
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
