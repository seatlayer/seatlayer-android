package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerImmersive
import io.seatlayer.android.SeatLayerPickerVenue3DAction
import kotlinx.coroutines.launch

/** Native controls that belong to the immersive venue scene. */
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
    val chromeTheme = SeatLayerPickerTheme.dark().copy(
        accent = scope.theme.accent,
        onAccent = scope.theme.onAccent,
    )
    val neighbours = SeatLayerPickerImmersive.neighbours(snapshot)
    val target = neighbours.target
    val targeted = neighbours.targetSeatId != null
    val focused = SeatLayerPickerImmersive.hasFocusedView(snapshot)
    val canNavigate = scope.controller.supportsCapability("venue-3d-controls-v1") &&
        scope.controller.supportsCommand("picker.setVenue3DNavigationMode")
    Box(modifier.fillMaxSize()) {
        ImmersiveLabelAction(
            label = if (focused) scope.strings.backToVenue else scope.strings.mapView,
            glyph = SeatLayerPickerGlyph.Back,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            theme = chromeTheme,
        ) {
            scope.controller.venue3D(
                if (focused) SeatLayerPickerVenue3DAction.Back
                else SeatLayerPickerVenue3DAction.Map,
            )
        }

        if (canNavigate) {
            PickerRoundAction(
                scopeLabel = if (map.view3DNavigationMode == "pan") {
                    scope.strings.rotateVenue
                } else {
                    scope.strings.moveVenue
                },
                glyph = if (map.view3DNavigationMode == "pan") {
                    SeatLayerPickerGlyph.Orbit
                } else {
                    SeatLayerPickerGlyph.Move
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                accent = true,
                themeOverride = chromeTheme,
            ) {
                scope.controller.setVenue3DNavigationMode(
                    if (map.view3DNavigationMode == "pan") "orbit" else "pan",
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            target?.let { seat ->
                Surface(
                    color = chromeTheme.surface.copy(alpha = 0.88f),
                    contentColor = chromeTheme.onSurface,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, chromeTheme.divider),
                ) {
                    Text(
                        listOf(seat.buyerFacingLabel, scope.strings.viewFromYourSeat)
                            .joinToString(" · "),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (targeted) {
                    PickerRoundAction(
                        scopeLabel = scope.strings.previousSeat,
                        glyph = SeatLayerPickerGlyph.Back,
                        enabled = neighbours.previousSeatId != null,
                        themeOverride = chromeTheme,
                    ) {
                        scope.controller.venue3D(SeatLayerPickerVenue3DAction.Previous)
                    }
                    if (
                        scope.options.enableSeatView &&
                        scope.controller.supportsSeatView &&
                        snapshot.capabilities.contains("seatView")
                    ) {
                        ImmersiveLabelAction(
                            label = scope.strings.viewFromHere,
                            glyph = SeatLayerPickerGlyph.Eye,
                            theme = chromeTheme,
                        ) {
                            scope.controller.openSeatView(neighbours.targetSeatId!!)
                        }
                    }
                    PickerRoundAction(
                        scopeLabel = scope.strings.nextSeat,
                        glyph = SeatLayerPickerGlyph.Forward,
                        enabled = neighbours.nextSeatId != null,
                        themeOverride = chromeTheme,
                    ) {
                        scope.controller.venue3D(SeatLayerPickerVenue3DAction.Next)
                    }
                    PickerRoundAction(
                        scopeLabel = scope.strings.recentre,
                        glyph = SeatLayerPickerGlyph.Recentre,
                        themeOverride = chromeTheme,
                    ) {
                        scope.controller.venue3D(SeatLayerPickerVenue3DAction.Recenter)
                    }
                } else {
                    PickerRoundAction(
                        scopeLabel = scope.strings.zoomOut,
                        glyph = SeatLayerPickerGlyph.Minus,
                        enabled = map.canZoomOut &&
                            scope.controller.supportsCommand("picker.zoomOut"),
                        themeOverride = chromeTheme,
                    ) { scope.controller.zoomOut() }
                    ImmersiveLabelAction(
                        label = scope.strings.fitVenue,
                        glyph = SeatLayerPickerGlyph.Fit,
                        enabled = scope.controller.supportsCommand("picker.zoomToFit"),
                        theme = chromeTheme,
                    ) { scope.controller.zoomToFit() }
                    PickerRoundAction(
                        scopeLabel = scope.strings.zoomIn,
                        glyph = SeatLayerPickerGlyph.Plus,
                        enabled = map.canZoomIn &&
                            scope.controller.supportsCommand("picker.zoomIn"),
                        themeOverride = chromeTheme,
                    ) { scope.controller.zoomIn() }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveLabelAction(
    label: String,
    glyph: SeatLayerPickerGlyph,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    theme: SeatLayerPickerTheme,
    action: suspend () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val coroutineScope = rememberCoroutineScope()
    Surface(
        onClick = { coroutineScope.launch { scope.performAction(action) } },
        enabled = enabled && !scope.state.presentation.actionInFlight,
        modifier = modifier
            .height(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        shape = RoundedCornerShape(9.dp),
        color = theme.surface.copy(alpha = 0.9f),
        contentColor = theme.onSurface,
        border = BorderStroke(1.dp, theme.divider),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeatLayerPickerIcon(
                glyph = glyph,
                color = theme.onSurface,
                modifier = Modifier.size(16.dp).clearAndSetSemantics { },
            )
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** Bottom-only caption for panorama; the renderer owns the close control and default drag hint. */
@Composable
public fun SeatLayerSeatViewChrome(
    modifier: Modifier = Modifier,
    showDragHint: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (!scope.controller.supportsNativeSeatViewChrome) return
    val view = scope.state.seatView ?: return
    val title = view.title?.trim()?.takeIf(String::isNotEmpty)
    val caption = view.caption?.trim()?.takeIf(String::isNotEmpty)
    val badge = view.badge?.trim()?.takeIf(String::isNotEmpty)
        ?: when {
            view.real -> scope.strings.realSeatView
            view.generated -> scope.strings.generatedSeatView
            else -> null
        }
    val dragHint = view.dragHint?.trim()?.takeIf(String::isNotEmpty)
    val chromeTheme = SeatLayerPickerTheme.dark().copy(
        accent = scope.theme.accent,
        onAccent = scope.theme.onAccent,
    )
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    top = 12.dp,
                    end = 12.dp,
                    // The runtime keeps its own drag hint in the lowest
                    // panorama band. Leave that band free unless a custom
                    // host explicitly opts into drawing the hint natively.
                    bottom = if (showDragHint) 12.dp else 48.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (title != null || caption != null || badge != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    color = chromeTheme.surface.copy(alpha = 0.9f),
                    contentColor = chromeTheme.onSurface,
                    border = BorderStroke(1.dp, chromeTheme.divider),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            title?.let {
                                Text(
                                    it,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            caption?.let {
                                Text(
                                    it,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = chromeTheme.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        badge?.let { badge ->
                            Surface(
                                color = chromeTheme.onSurface.copy(alpha = 0.12f),
                                contentColor = chromeTheme.onSurface,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    badge,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
            dragHint?.takeIf { showDragHint }?.let { hint ->
                Surface(
                    color = chromeTheme.surface.copy(alpha = 0.82f),
                    contentColor = chromeTheme.muted,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        hint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Labelled Seat map / 3D segmented control. */
@Composable
public fun SeatLayerPickerViewModeControl(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (!scope.controller.supportsVenue3D || !scope.options.enableVenue3D) return
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
            contentColor = scope.theme.onSurface,
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
            ViewSegment(scope.strings.orbit, map.view3DNavigationMode != "pan") {
                coroutineScope.launch {
                    scope.performAction { scope.controller.setVenue3DNavigationMode("orbit") }
                }
            }
            ViewSegment(scope.strings.pan, map.view3DNavigationMode == "pan") {
                coroutineScope.launch {
                    scope.performAction { scope.controller.setVenue3DNavigationMode("pan") }
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
        contentColor = if (selected) scope.theme.onAccent else scope.theme.onSurface,
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
        scopeLabel = scope.strings.limitedView,
        glyph = SeatLayerPickerGlyph.Eye,
        modifier = modifier,
        checked = enabled,
    ) { scope.controller.setLimitedViewFilter(!enabled) }
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
