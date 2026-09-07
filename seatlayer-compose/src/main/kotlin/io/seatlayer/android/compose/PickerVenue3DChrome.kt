package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerImmersive
import io.seatlayer.android.SeatLayerPickerVenue3DAction
import kotlinx.coroutines.launch

/**
 * Native controls that belong to the immersive venue scene.
 *
 * All of it wears the dark glass of §3.14 whatever the resolved mode is,
 * because it floats over a rendered venue. The runtime suppresses its own
 * words under the native chrome contract, so these are the only ones.
 */
@Composable
public fun SeatLayerVenue3D(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    if (scope.state.seatView != null) return
    val snapshot = scope.state.snapshot ?: return
    val map = snapshot.map
    if (
        !map.isVenue3D ||
        !snapshot.capabilities.contains("venue3d") ||
        !scope.controller.supportsVenue3D
    ) return
    val neighbours = SeatLayerPickerImmersive.neighbours(snapshot)
    val target = neighbours.target
    // Sitting in an exact seat: the back pill is the way out of a SEAT, not
    // out of the scene, so it is drawn only here.
    val seated = neighbours.targetSeatId != null
    // The deck is the runtime's to advertise; without it there is a scene and
    // no navigation, which is a state rather than a failure.
    val deck = scope.controller.supportsCapability("venue-3d-controls-v1")
    val canSetMode = deck && scope.controller.supportsCommand("picker.setVenue3DNavigationMode")
    val panning = map.view3DNavigationMode == "pan"
    Box(modifier.fillMaxSize()) {
        if (seated) {
            PickerImmersiveBackPill(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp),
            ) { scope.controller.venue3D(SeatLayerPickerVenue3DAction.Back) }
        }

        if (canSetMode) {
            // Two states, one control, and the drag hint says what the gesture
            // will do rather than what the mode is called.
            PickerImmersiveChip(
                label = if (panning) scope.strings.rotateVenue else scope.strings.moveVenue,
                glyph = if (panning) SeatLayerPickerGlyph.Orbit else SeatLayerPickerGlyph.Move,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp),
                showsLabel = false,
            ) {
                scope.controller.set3DNavigationMode(if (panning) "orbit" else "pan")
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SeatLayerSizeTokens.MAP_ANCHOR_GAP.dp),
        ) {
            target?.let { seat ->
                PickerImmersiveCaption(
                    listOf(seat.buyerFacingLabel, scope.strings.viewFromYourSeat)
                        .joinToString(" \u00b7 "),
                )
            }
            if (!deck) return@Column
            Row(
                horizontalArrangement = Arrangement.spacedBy(
                    SeatLayerSizeTokens.MAP_ANCHOR_GAP.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (seated) {
                    // Seat-to-seat travel along the authored row. Section-to-
                    // section travel is NOT offered: the snapshot carries no
                    // stop list, and a control that cannot say where it goes
                    // is a door into an empty room (§4.9).
                    PickerImmersiveChip(
                        label = scope.strings.previousSeat,
                        glyph = SeatLayerPickerGlyph.Back,
                        enabled = neighbours.previousSeatId != null,
                        showsLabel = false,
                    ) { scope.controller.venue3D(SeatLayerPickerVenue3DAction.Previous) }
                    if (
                        scope.options.enableSeatView &&
                        scope.controller.supportsSeatView &&
                        snapshot.capabilities.contains("seatView")
                    ) {
                        PickerImmersiveChip(
                            label = scope.strings.openVenue360,
                            glyph = SeatLayerPickerGlyph.Eye,
                        ) {
                            neighbours.targetSeatId?.let { scope.controller.openSeatView(it) }
                        }
                    }
                    PickerImmersiveChip(
                        label = scope.strings.nextSeat,
                        glyph = SeatLayerPickerGlyph.Forward,
                        enabled = neighbours.nextSeatId != null,
                        showsLabel = false,
                    ) { scope.controller.venue3D(SeatLayerPickerVenue3DAction.Next) }
                    PickerImmersiveChip(
                        label = scope.strings.recentre,
                        glyph = SeatLayerPickerGlyph.Recentre,
                        showsLabel = false,
                    ) { scope.controller.venue3D(SeatLayerPickerVenue3DAction.Recenter) }
                }
                // The way home from any depth, and it is never dimmed: the
                // camera facts in a snapshot are only as fresh as the last
                // state change, and a pinch changes no state.
                PickerImmersiveChip(
                    label = scope.strings.fitWholeVenue,
                    glyph = SeatLayerPickerGlyph.Fit,
                ) { scope.controller.venue3D(SeatLayerPickerVenue3DAction.Back) }
            }
        }
    }
}

/** The way out of a seat: top-left, on the control glass, at the pill height. */
@Composable
private fun PickerImmersiveBackPill(
    modifier: Modifier = Modifier,
    action: suspend () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val glass = pickerControlGlass()
    val coroutineScope = rememberCoroutineScope()
    Surface(
        onClick = { coroutineScope.launch { scope.performAction(action) } },
        modifier = modifier
            .height(SeatLayerSizeTokens.IMMERSIVE_BACK_PILL_HEIGHT.dp)
            .semantics {
                role = Role.Button
                contentDescription = scope.strings.backToVenue
            },
        color = glass.ground,
        contentColor = glass.ink,
        shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
        border = BorderStroke(1.dp, glass.line),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SeatLayerSizeTokens.IMMERSIVE_NAV_CHIP_PADDING_X.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeatLayerPickerIcon(
                SeatLayerPickerGlyph.Back,
                glass.ink,
                Modifier
                    .size(SeatLayerSizeTokens.IMMERSIVE_BACK_ICON_SIZE.dp)
                    .clearAndSetSemantics { },
            )
            Spacer(Modifier.width(BACK_PILL_GAP.dp))
            Text(
                scope.strings.backToVenue,
                color = glass.ink,
                fontSize = SeatLayerSizeTokens.IMMERSIVE_BACK_FONT_SIZE.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = scope.theme.fontFamily,
            )
        }
    }
}

private const val BACK_PILL_GAP: Float = 6f

/** Labelled Seat map / 3D segmented control. */
@Composable
public fun SeatLayerPickerViewModeControl(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (!scope.controller.supportsVenue3D || !scope.options.enable3D) return
    val snapshot = scope.state.snapshot
    if (snapshot?.capabilities?.contains("venue3d") != true) return
    val is3D = snapshot.map.isVenue3D
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = modifier.then(if (compact) Modifier.height(48.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = scope.theme.surface.copy(alpha = 0.94f),
            contentColor = scope.theme.text,
            border = BorderStroke(1.dp, scope.theme.divider),
            shadowElevation = 3.dp,
        ) {
            Row {
                ViewSegment(scope.strings.mapView, selected = !is3D, compact = compact) {
                    coroutineScope.launch {
                        scope.performAction { scope.controller.setBuyerView("map") }
                    }
                }
                ViewSegment(scope.strings.venue3D, selected = is3D, compact = compact) {
                    coroutineScope.launch {
                        scope.performAction { scope.controller.setBuyerView("venue3d") }
                    }
                }
            }
        }
    }
}

@Composable
public fun SeatLayerPicker3DNavigationControl(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val map = scope.state.snapshot?.map ?: return
    if (!map.isVenue3D || !scope.controller.supportsVenue3D) return
    val coroutineScope = rememberCoroutineScope()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = scope.theme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, scope.theme.divider),
    ) {
        Row {
            ViewSegment(scope.strings.orbitMode, map.view3DNavigationMode != "pan") {
                coroutineScope.launch {
                    scope.performAction { scope.controller.set3DNavigationMode("orbit") }
                }
            }
            ViewSegment(scope.strings.panMode, map.view3DNavigationMode == "pan") {
                coroutineScope.launch {
                    scope.performAction { scope.controller.set3DNavigationMode("pan") }
                }
            }
        }
    }
}

@Composable
private fun ViewSegment(
    label: String,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(if (compact) 32.dp else 48.dp)
            .widthIn(min = 46.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        shape = RoundedCornerShape(999.dp),
        color = if (selected) scope.theme.accent else Color.Transparent,
        contentColor = if (selected) scope.theme.onAccent else scope.theme.text,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
public fun SeatLayerPickerFitControl(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    PickerRoundAction(
        scopeLabel = scope.strings.fitVenue,
        glyph = SeatLayerPickerGlyph.Fit,
        modifier = modifier,
    ) { scope.controller.zoomToFit() }
}

@Composable
public fun SeatLayerPickerOverviewControl(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    PickerRoundAction(
        scopeLabel = scope.strings.overview,
        glyph = SeatLayerPickerGlyph.Back,
        modifier = modifier,
    ) { scope.controller.overview() }
}

/** One semantic 2D step outward; it never jumps straight to full overview. */
@Composable
public fun SeatLayerPickerStepOutControl(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val canStepOut = scope.state.snapshot?.map?.canZoomOut == true &&
        scope.controller.supportsCommand("picker.zoomOut")
    PickerRoundAction(
        scopeLabel = scope.strings.backToVenue,
        glyph = SeatLayerPickerGlyph.Back,
        modifier = modifier,
        enabled = canStepOut,
    ) { scope.controller.zoomOut() }
}

@Composable
public fun SeatLayerPickerColorblindControl(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val enabled = scope.state.snapshot?.map?.colorblindSafe == true
    PickerRoundAction(
        scopeLabel = scope.strings.colorblindSafe,
        glyph = SeatLayerPickerGlyph.Eye,
        modifier = modifier,
        checked = enabled,
    ) { scope.controller.setColorblindSafe(!enabled) }
}

@Composable
public fun SeatLayerPickerLimitedViewControl(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val enabled = scope.state.snapshot?.map?.hideLimitedView == true
    PickerRoundAction(
        scopeLabel = scope.strings.hideLimitedView,
        glyph = SeatLayerPickerGlyph.Eye,
        modifier = modifier,
        checked = enabled,
    ) { scope.controller.setLimitedViewHidden(!enabled) }
}

@Composable
private fun PickerRoundAction(
    scopeLabel: String,
    glyph: SeatLayerPickerGlyph,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    enabled: Boolean = true,
    accent: Boolean = false,
    themeOverride: SeatLayerPickerTheme? = null,
    action: suspend () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val colors = themeOverride ?: scope.theme
    val coroutineScope = rememberCoroutineScope()
    Surface(
        onClick = { coroutineScope.launch { scope.performAction(action) } },
        enabled = enabled && !scope.state.presentation.actionInFlight,
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = scopeLabel
                role = if (checked == null) Role.Button else Role.Switch
                checked?.let {
                    toggleableState = if (it) ToggleableState.On else ToggleableState.Off
                }
            },
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val selected = checked == true || accent
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (selected) {
                    colors.accent.copy(alpha = if (accent) 0.18f else 0.14f)
                } else {
                    colors.surface.copy(alpha = 0.94f)
                },
                contentColor = if (selected) colors.accent else colors.onSurface,
                border = BorderStroke(
                    1.dp,
                    if (selected) colors.accent else colors.divider,
                ),
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SeatLayerPickerIcon(
                        glyph = glyph,
                        color = if (selected) colors.accent else colors.onSurface,
                        modifier = Modifier.size(DefaultPickerIconSize).clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}
