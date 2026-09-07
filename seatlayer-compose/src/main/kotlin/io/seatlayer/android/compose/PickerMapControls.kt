package io.seatlayer.android.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SeatLayerPickerMapState
import kotlinx.coroutines.launch

/**
 * The controls that sit on the map itself.
 *
 * On a phone they go to the map's anchor regions — one column bottom-right
 * holding the ♿ filter disc, then `+`, then the whole venue.
 *
 * The ♿ disc HEADS the column, on both layouts: a buyer already looks to this
 * corner for whatever acts on the map, and a control about who can sit where is
 * an earlier question than how close the camera is. There is no `−` on the
 * phone — a `−` that only sometimes had a step to take read as a control that
 * sometimes worked. Pinch steps out; the disc below goes home.
 *
 * The wide layout keeps its vertical rail, where there is room for both.
 */
@Composable
public fun SeatLayerPickerMapControls(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    edgeInset: Dp = SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp,
    bottomInset: Dp = 0.dp,
    includeViewModeControl: Boolean = true,
    accessibilityControl: (@Composable () -> Unit)? = null,
) {
    if (compact) {
        Box(modifier) {
            PickerCornerControls(
                edgeInset = edgeInset,
                bottomInset = bottomInset,
                includeViewModeControl = includeViewModeControl,
                accessibilityControl = accessibilityControl,
            )
        }
    } else {
        PickerRailControls(modifier, accessibilityControl)
    }
}

/** How the standalone control stack was arranged before the map anchors. */
public enum class SeatLayerPickerMapControlsLayout { Vertical, Horizontal }

@Deprecated(
    "The map controls now take the map's own anchor regions.",
    ReplaceWith("SeatLayerPickerMapControls(modifier)"),
)
@Composable
public fun SeatLayerPickerMapControls(
    modifier: Modifier = Modifier,
    layout: SeatLayerPickerMapControlsLayout,
) {
    @Suppress("UNUSED_EXPRESSION")
    layout
    SeatLayerPickerMapControls(modifier = modifier, compact = false)
}

/**
 * The phone placement: four anchors, and nothing free-floating between them.
 */
@Composable
private fun PickerCornerControls(
    edgeInset: Dp,
    bottomInset: Dp,
    includeViewModeControl: Boolean,
    accessibilityControl: (@Composable () -> Unit)?,
) {
    val scope = LocalSeatLayerPickerScope.current
    val chrome = scope.options.chrome
    val snapshot = scope.state.snapshot
    // While the immersive scene is up there is no flat map to fit, filter or
    // pan, and the 3D chrome owns that corner. Only the way back stays.
    val onMap = snapshot?.map?.isVenue3D != true
    val has3D = includeViewModeControl &&
        chrome.showViewModeControl &&
        scope.options.enable3D &&
        snapshot?.capabilities?.contains("venue3d") == true
    Box(Modifier.fillMaxSize()) {
        if (has3D) {
            PickerMapAnchorRegion(PickerMapAnchor.TopEnd, inset = edgeInset) {
                SeatLayerPickerViewModeControl(compact = true)
            }
        }
        if (onMap && chrome.colorblindControlFor(phone = true)) {
            PickerMapAnchorRegion(
                anchor = PickerMapAnchor.BottomStart,
                inset = edgeInset,
                bottomInset = bottomInset,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        SeatLayerSizeTokens.MAP_ANCHOR_GAP.dp,
                    ),
                    horizontalAlignment = Alignment.Start,
                ) { SeatLayerPickerColorblindControl() }
            }
        }
        if (onMap) {
            PickerMapAnchorRegion(
                anchor = PickerMapAnchor.BottomEnd,
                inset = edgeInset,
                bottomInset = bottomInset,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        SeatLayerSizeTokens.ZOOM_COLUMN_GAP.dp,
                    ),
                    horizontalAlignment = Alignment.End,
                ) {
                    // THE ♿ DISC HEADS THE COLUMN. The stepper that walks the
                    // sections holding matching spaces rides beside it rather
                    // than above it: they are one subject, and it draws nothing
                    // at all until a filter is on and the runtime answers the
                    // tour.
                    if (chrome.showAccessibilityControl) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                SeatLayerSizeTokens.ACCESS_STEP_GAP.dp,
                            ),
                        ) {
                            SeatLayerPickerAccessibleStepper()
                            if (accessibilityControl != null) {
                                accessibilityControl()
                            } else {
                                SeatLayerPickerAccessibilityFilters(compact = true)
                            }
                        }
                    }
                    // `+` and the whole-venue disc, nothing between them. `+`
                    // retires among the seats or at the zoom ceiling but KEEPS
                    // ITS SLOT: the column is anchored at its foot, so a disc
                    // that left the tree moved the ♿ disc out from under the
                    // thumb already reaching for it.
                    if (chrome.zoomControlsFor(phone = true)) {
                        SeatLayerPickerZoomInButton()
                    }
                    if (chrome.zoomToFitControlFor(phone = true)) {
                        SeatLayerPickerShowWholeVenueButton()
                    }
                }
            }
        }
        // Back to the venue is a different question from the zoom ladder: it
        // only has an answer while a section is actually framed.
        if (
            onMap &&
            chrome.overviewControlFor(phone = true) &&
            snapshot?.map?.focusedSection != null
        ) {
            PickerMapAnchorRegion(PickerMapAnchor.TopStart, inset = edgeInset) {
                SeatLayerPickerOverviewButton()
            }
        }
    }
}

/** The wide placement: one vertical rail, which has room for `−` as well. */
@Composable
private fun PickerRailControls(
    modifier: Modifier,
    accessibilityControl: (@Composable () -> Unit)?,
) {
    val scope = LocalSeatLayerPickerScope.current
    val chrome = scope.options.chrome
    val snapshot = scope.state.snapshot
    val map = snapshot?.map
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(PICKER_RAIL_GAP),
    ) {
        if (chrome.showAccessibilityControl) {
            if (accessibilityControl != null) {
                accessibilityControl()
            } else {
                SeatLayerPickerAccessibilityFilters(compact = true)
            }
        }
        if (chrome.overviewControlFor(phone = false) && map?.focusedSection != null) {
            SeatLayerPickerOverviewButton()
        }
        if (chrome.zoomControlsFor(phone = false)) {
            SeatLayerPickerZoomInButton(keepsSlotWhenRetired = false)
            SeatLayerPickerZoomOutButton()
        }
        if (
            chrome.showViewModeControl &&
            scope.options.enable3D &&
            snapshot?.capabilities?.contains("venue3d") == true
        ) {
            SeatLayerPickerViewModeControl(compact = true)
        }
        if (map?.isVenue3D == true) {
            SeatLayerPicker3DNavigationControl()
        }
        if (chrome.colorblindControlFor(phone = false)) {
            SeatLayerPickerColorblindControl()
        }
    }
}

/** The gap the wide rail keeps between its controls. */
private val PICKER_RAIL_GAP: Dp = 7.dp

/**
 * The last rung of the ladder on its own, reached from any depth.
 *
 * It sends `picker.overview` rather than a fit, so a framed section is released
 * by the same press that fits the chart.
 *
 * ALWAYS live. The camera facts in a snapshot are only as fresh as the last
 * state change, and a pinch changes no state: dimming this disc on a stale
 * "already at the venue" reading stranded a buyer with no way out. At the venue
 * already, the press is a harmless no-op.
 */
@Composable
public fun SeatLayerPickerShowWholeVenueButton(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val coroutineScope = rememberCoroutineScope()
    PickerMapControlDisc(
        modifier = modifier,
        label = scope.strings.fitWholeVenue,
        glyph = SeatLayerPickerGlyph.Recentre,
    ) {
        coroutineScope.launch { scope.performAction { scope.controller.overview() } }
    }
}

/** Back to the whole venue from a framed section. */
@Composable
public fun SeatLayerPickerOverviewButton(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val coroutineScope = rememberCoroutineScope()
    PickerMapControlDisc(
        modifier = modifier,
        label = scope.strings.backToVenue,
        glyph = SeatLayerPickerGlyph.Back,
    ) {
        coroutineScope.launch { scope.performAction { scope.controller.overview() } }
    }
}

/**
 * One rung in.
 *
 * Retires among the seats or at the zoom ceiling — a disc that does nothing is
 * the broken-map reading — but by default it keeps its 36 dp slot rather than
 * leaving the tree, so the column above it never reflows under a reaching
 * thumb.
 */
@Composable
public fun SeatLayerPickerZoomInButton(
    modifier: Modifier = Modifier,
    keepsSlotWhenRetired: Boolean = true,
) {
    val scope = LocalSeatLayerPickerScope.current
    val map = scope.state.snapshot?.map
    val coroutineScope = rememberCoroutineScope()
    val retired = map?.rung == "seats" || map?.canZoomIn == false
    val hidden = retired && keepsSlotWhenRetired
    Box(
        modifier = modifier
            .size(SeatLayerSizeTokens.MAP_CONTROL_SIZE.dp)
            .alpha(if (hidden) 0f else 1f)
            .then(if (hidden) Modifier.clearAndSetSemantics { } else Modifier),
    ) {
        PickerMapControlDisc(
            label = scope.strings.zoomIn,
            glyph = SeatLayerPickerGlyph.Plus,
            enabled = !retired,
        ) {
            coroutineScope.launch { scope.performAction { scope.controller.zoomIn() } }
        }
    }
}

/**
 * One rung out — the wide rail only.
 *
 * Live only once the buyer is in among the seats: at a section's own frame the
 * only step back is the whole venue, and that is a different control.
 */
@Composable
public fun SeatLayerPickerZoomOutButton(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val map = scope.state.snapshot?.map
    val coroutineScope = rememberCoroutineScope()
    PickerMapControlDisc(
        modifier = modifier,
        label = scope.strings.zoomOut,
        glyph = SeatLayerPickerGlyph.Minus,
        enabled = map != null && map.canStepBack && map.rung == "seats",
    ) {
        coroutineScope.launch { scope.performAction { scope.controller.zoomOut() } }
    }
}

/**
 * Whether the ladder has a rung left going out.
 *
 * `atVenueFit` is a POSE, not a scale, and it is present-only: absent is a
 * third answer and never a `false`. The fallback matters because `canZoomOut`
 * is computed from whether seats are the visible layer, so every camera a pinch
 * leaves between the venue fit and the seats reads as "already home".
 */
internal val SeatLayerPickerMapState.canStepBack: Boolean
    get() = when {
        focusedSectionId != null || focusedSection != null -> true
        else -> atVenueFit?.not() ?: canZoomOut
    }

/**
 * The disc every map control is drawn as.
 *
 * The palette is the MAP's, not the panel's: these float on the venue. On dark
 * the disc separates by its FILL, on light by its EDGE — two tokens rather than
 * one stronger opacity, because white is already as far from a light map as a
 * colour can get and still barely tell itself apart from it.
 *
 * A retired disc keeps its ground in both themes; only the glyph and the ring
 * step back, and the shadow goes. A wash over the whole disc read as a grey
 * blot on the light map and vanished on the dark one.
 */
@Composable
internal fun PickerMapControlDisc(
    label: String,
    glyph: SeatLayerPickerGlyph,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val disc = pickerMapChromeDisc(scope.theme, scope.state.snapshot?.map?.isVenue3D == true)
    val motion = tween<Color>(SeatLayerMotionTokens.POP)
    val ground by animateColorAsState(
        targetValue = if (active) {
            scope.theme.accent.copy(alpha = PICKER_DISC_ACTIVE_GROUND).compositeOver(disc.ground)
        } else {
            disc.ground
        },
        animationSpec = motion,
        label = "seatlayer-map-disc-ground",
    )
    val line by animateColorAsState(
        targetValue = when {
            active -> scope.theme.accent.copy(alpha = PICKER_DISC_ACTIVE_LINE)
            !enabled -> disc.line.copy(alpha = disc.line.alpha * PICKER_DISC_RETIRED_LINE)
            else -> disc.line
        },
        animationSpec = motion,
        label = "seatlayer-map-disc-line",
    )
    val ink by animateColorAsState(
        targetValue = when {
            active -> scope.theme.accent
            !enabled -> scope.theme.mutedText.copy(alpha = PICKER_DISC_RETIRED_INK)
            else -> scope.theme.text
        },
        animationSpec = motion,
        label = "seatlayer-map-disc-ink",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(SeatLayerSizeTokens.MAP_CONTROL_SIZE.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        shape = CircleShape,
        color = ground,
        contentColor = ink,
        border = BorderStroke(1.dp, line),
        // The disc is lifted off the map only while it is a thing to press.
        shadowElevation = if (enabled) PICKER_DISC_SHADOW else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            SeatLayerPickerIcon(
                glyph = glyph,
                color = ink,
                modifier = Modifier
                    .size(PICKER_DISC_GLYPH_SIZE)
                    .clearAndSetSemantics { },
            )
        }
    }
}

private const val PICKER_DISC_ACTIVE_GROUND = 0.13f
private const val PICKER_DISC_ACTIVE_LINE = 0.52f
private const val PICKER_DISC_RETIRED_LINE = 0.6f
private const val PICKER_DISC_RETIRED_INK = 0.55f
private val PICKER_DISC_SHADOW: Dp = 3.dp
private val PICKER_DISC_GLYPH_SIZE: Dp = 20.dp

/** The disc's own ground and edge, by the palette the map is drawn in. */
internal data class PickerMapChromeDisc(val ground: Color, val line: Color)

internal fun pickerMapChromeDisc(
    theme: SeatLayerPickerTheme,
    immersive: Boolean,
): PickerMapChromeDisc {
    // The immersive scene is dark whatever the picker's palette is: white
    // chrome over a lit stage reads as a mistake in either theme.
    val dark = immersive || theme.background.luminance() < PICKER_DARK_GROUND_LUMINANCE
    return if (dark) {
        PickerMapChromeDisc(
            ground = Color(SeatLayerDarkTokens.CHROME),
            line = Color(SeatLayerDarkTokens.CHROME_LINE),
        )
    } else {
        PickerMapChromeDisc(
            ground = Color(SeatLayerLightTokens.CHROME),
            line = Color(SeatLayerLightTokens.CHROME_LINE),
        )
    }
}

private const val PICKER_DARK_GROUND_LUMINANCE = 0.35f
