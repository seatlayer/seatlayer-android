package io.seatlayer.android.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

public enum class SeatLayerPickerMapControlsLayout { Vertical, Horizontal }

@Composable
public fun SeatLayerPickerMapControls(
    modifier: Modifier = Modifier,
    layout: SeatLayerPickerMapControlsLayout = SeatLayerPickerMapControlsLayout.Vertical,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (scope.state.snapshot?.map?.isVenue3D == true) {
        SeatLayerPicker3DNavigationControl(modifier)
        return
    }
    val controls: @Composable () -> Unit = {
        if (scope.options.chrome.showZoomControls) {
            SeatLayerPickerZoomControls(layout = layout)
        }
        if (
            scope.options.chrome.showOverviewControl &&
            scope.state.snapshot?.map?.canZoomOut == true
        ) {
            SeatLayerPickerStepOutControl()
        }
        if (scope.options.chrome.showColorblindControl) {
            SeatLayerPickerColorblindControl()
        }
    }
    when (layout) {
        SeatLayerPickerMapControlsLayout.Vertical -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { controls() }
        SeatLayerPickerMapControlsLayout.Horizontal -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { controls() }
    }
}

@Composable
public fun SeatLayerPickerZoomControls(
    modifier: Modifier = Modifier,
    layout: SeatLayerPickerMapControlsLayout = SeatLayerPickerMapControlsLayout.Vertical,
) {
    val scope = LocalSeatLayerPickerScope.current
    val coroutineScope = rememberCoroutineScope()
    val controls: @Composable () -> Unit = {
        MapControl(
            label = scope.strings.zoomIn,
            glyph = SeatLayerPickerGlyph.Plus,
            enabled = scope.state.snapshot?.map?.canZoomIn != false,
        ) {
            coroutineScope.launch { scope.performAction { scope.controller.zoomIn() } }
        }
        MapControl(
            label = scope.strings.zoomOut,
            glyph = SeatLayerPickerGlyph.Minus,
            enabled = scope.state.snapshot?.map?.canZoomOut != false,
        ) {
            coroutineScope.launch { scope.performAction { scope.controller.zoomOut() } }
        }
    }
    when (layout) {
        SeatLayerPickerMapControlsLayout.Vertical -> Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { controls() }
        SeatLayerPickerMapControlsLayout.Horizontal -> Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { controls() }
    }
}

@Composable
private fun MapControl(
    label: String,
    glyph: SeatLayerPickerGlyph,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val theme = LocalSeatLayerPickerTheme.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = theme.text,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = theme.surface.copy(alpha = 0.94f),
                contentColor = theme.text,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.divider),
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SeatLayerPickerIcon(
                        glyph = glyph,
                        color = theme.text,
                        modifier = Modifier
                            .size(DefaultPickerIconSize)
                            .clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}
