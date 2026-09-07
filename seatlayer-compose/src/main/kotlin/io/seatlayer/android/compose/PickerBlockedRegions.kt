package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SeatLayerPickerBlockedRegion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Where the native chrome lies over the map, told to the runtime.
 *
 * The map is a web view inside an `AndroidView`, and a tap on a control drawn
 * over it can still reach the page: the platform view's own gesture boundary
 * resigns the touch after the finger has already lifted. A guard sent on
 * pointer-down loses that race — a bridge round trip outlasts a tap — so the
 * only guard that works is one standing before the finger lands. Every piece of
 * chrome on the map therefore reports its rectangle once per layout, and the
 * runtime swallows any pointer sequence that starts inside one.
 *
 * This is deliberately not `setMapInteractionEnabled(false)`: that is for a
 * surface that owns the WHOLE map, and turning the map off to protect a 36 dp
 * disc also stops the buyer panning around it.
 */
internal class PickerBlockedRegionRegistry {
    private val rects = LinkedHashMap<Any, SeatLayerPickerBlockedRegion>()
    private val leaving = LinkedHashMap<Any, Job>()
    /**
     * The keys whose owner has said it is not standing on the map.
     *
     * The lowering is authoritative: a layout pass that lands mid-take-down —
     * and one always does, because the cover is still on screen while it fades
     * — must not put the rectangle back. A cover that re-registered itself as
     * it left leaves the map inert for the rest of the session, which is the
     * one failure a buyer cannot work around.
     */
    private val lowered = LinkedHashSet<Any>()
    private val reported = MutableStateFlow<List<SeatLayerPickerBlockedRegion>>(emptyList())
    private var map: LayoutCoordinates? = null

    /** The settled list, whole every time — the runtime replaces, never merges. */
    val regions: StateFlow<List<SeatLayerPickerBlockedRegion>> get() = reported

    /** The map surface's own box; every rectangle is measured against it. */
    fun bindMap(coordinates: LayoutCoordinates?) {
        map = coordinates
    }

    /** The map's box, for a surface that has to cover the whole of it. */
    fun mapCoordinates(): LayoutCoordinates? = map?.takeIf(LayoutCoordinates::isAttached)

    /**
     * Records where [key] now stands, in the map's own coordinates.
     *
     * Non-finite edges and non-positive sizes are floored rather than sent: the
     * runtime refuses the WHOLE list for one bad rectangle, and one
     * mis-measured disc must not drop the guard over everything else.
     */
    fun measure(key: Any, coordinates: LayoutCoordinates?, density: Float) {
        // Told, never asked: the enabled state is delivered once by [raise] and
        // [release], and a measurement arriving after a lowering is dropped
        // rather than allowed to decide for itself.
        if (key in lowered) return
        val surface = map
        if (
            coordinates == null ||
            !coordinates.isAttached ||
            surface == null ||
            !surface.isAttached
        ) {
            forget(key)
            return
        }
        // In the MAP's own coordinates: the map is the web view, and the page
        // inside it fills the view edge to edge, so the runtime only ever
        // understands a rectangle expressed against the map's own origin.
        val origin = surface.localPositionOf(coordinates, Offset.Zero)
        // Logical pixels, like the viewport insets beside them: the runtime
        // lays its page out in CSS pixels and knows nothing of this device's
        // pixel ratio.
        val scale = if (density.isFinite() && density > 0) density else 1f
        set(
            key,
            SeatLayerPickerBlockedRegion(
                x = pickerFiniteEdge(origin.x / scale),
                y = pickerFiniteEdge(origin.y / scale),
                width = pickerFiniteExtent(coordinates.size.width / scale.toDouble()),
                height = pickerFiniteExtent(coordinates.size.height / scale.toDouble()),
            ),
        )
    }

    /** Records one rectangle already in the map's coordinates. */
    fun set(key: Any, region: SeatLayerPickerBlockedRegion) {
        if (key in lowered) return
        if (rects[key] == region) return
        rects[key] = region
        publish()
    }

    /** Drops [key] immediately; the control is gone and so is its guard. */
    fun forget(key: Any) {
        leaving.remove(key)?.cancel()
        lowered.remove(key)
        if (rects.remove(key) != null) publish()
    }

    /**
     * [key] is standing on the map again, and may be measured.
     *
     * Cancels a lowering still in its linger, so a control raised, lowered and
     * raised again inside the window settles on ONE registration rather than
     * losing the one it has to a timer nobody stopped.
     */
    fun raise(key: Any) {
        leaving.remove(key)?.cancel()
        lowered.remove(key)
    }

    /**
     * Drops [key] after [PICKER_BLOCKED_REGION_LINGER_MS].
     *
     * The leak this guards against is a touch delivered to the page after the
     * native side has already handled it. A control that leaves on its own tap
     * would otherwise take its rectangle away exactly where the finger is.
     */
    fun release(key: Any, scope: CoroutineScope) {
        lowered.add(key)
        if (!rects.containsKey(key) || leaving.containsKey(key)) return
        leaving[key] = scope.launch {
            delay(PICKER_BLOCKED_REGION_LINGER_MS)
            leaving.remove(key)
            if (rects.remove(key) != null) publish()
        }
    }

    /** The whole layout is gone: the runtime is told an empty list, once. */
    fun clear() {
        leaving.values.forEach(Job::cancel)
        leaving.clear()
        lowered.clear()
        if (rects.isEmpty()) return
        rects.clear()
        publish()
    }

    private fun publish() {
        reported.value = rects.values.toList()
    }
}

internal const val PICKER_BLOCKED_REGION_LINGER_MS: Long = 600

internal fun pickerFiniteEdge(value: Float): Double =
    if (value.isFinite()) value.toDouble() else 0.0

internal fun pickerFiniteExtent(value: Double): Double =
    if (value.isFinite() && value > 0) value else 0.0

internal val LocalPickerBlockedRegions = compositionLocalOf<PickerBlockedRegionRegistry?> { null }

/**
 * Keeps one registry and hands the runtime every settled list.
 *
 * Gated on the command being in the bundle's own `hello` table: this changes
 * nothing a snapshot reports, so the command table IS the whole contract. An
 * older runtime is left exactly as it was.
 */
@Composable
internal fun rememberPickerBlockedRegions(): PickerBlockedRegionRegistry {
    val scope = LocalSeatLayerPickerScope.current
    val registry = remember(scope.stateHolder) { PickerBlockedRegionRegistry() }
    val supported = scope.controller.supportsCommand("picker.setBlockedRegions")
    LaunchedEffect(registry, supported, scope.state.isReady) {
        if (!supported || !scope.state.isReady) return@LaunchedEffect
        registry.regions.collect { regions ->
            scope.performAction { scope.controller.setBlockedRegions(regions) }
        }
    }
    DisposableEffect(registry) {
        onDispose { registry.clear() }
    }
    return registry
}

/**
 * One piece of native chrome standing on the map.
 *
 * Draws nothing of its own and takes no pointer event: the control inside still
 * competes for the touch exactly as it did. This is the second guard, for the
 * platform that lets the touch through anyway.
 */
@Composable
internal fun PickerMapChromeRegion(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val registry = LocalPickerBlockedRegions.current
    val key = remember { Any() }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val coroutineScope = rememberPickerRegionScope()
    DisposableEffect(registry, key) {
        onDispose { registry?.forget(key) }
    }
    // The ONE place [enabled] is read. The measurement below never asks again:
    // deriving the reported value from the change being delivered is what stops
    // a cover re-registering itself on the layout pass that lowers it.
    LaunchedEffect(registry, key, enabled) {
        if (enabled) registry?.raise(key) else registry?.release(key, coroutineScope)
    }
    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            registry?.measure(key, coordinates, density)
        },
        content = content,
    )
}

@Composable
private fun rememberPickerRegionScope(): CoroutineScope =
    androidx.compose.runtime.rememberCoroutineScope()

/**
 * The seven places chrome may stand on the map.
 *
 * Named, so nothing free-floats with a padding literal of its own: a control
 * that invents its own inset drifts away from the column beside it the moment
 * either is touched.
 */
internal enum class PickerMapAnchor {
    TopStart,
    TopCenter,
    TopEnd,
    CenterEnd,
    BottomStart,
    BottomCenter,
    BottomEnd,
}

private fun PickerMapAnchor.alignment(): Alignment = when (this) {
    PickerMapAnchor.TopStart -> Alignment.TopStart
    PickerMapAnchor.TopCenter -> Alignment.TopCenter
    PickerMapAnchor.TopEnd -> Alignment.TopEnd
    PickerMapAnchor.CenterEnd -> Alignment.CenterEnd
    PickerMapAnchor.BottomStart -> Alignment.BottomStart
    PickerMapAnchor.BottomCenter -> Alignment.BottomCenter
    PickerMapAnchor.BottomEnd -> Alignment.BottomEnd
}

/** The inset each anchor keeps from the map's edges. */
internal fun PickerMapAnchor.padding(
    inset: Dp = SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp,
    bottomInset: Dp = 0.dp,
): PaddingValues = PaddingValues(
    start = inset,
    end = inset,
    top = inset,
    bottom = inset + bottomInset,
)

/**
 * Places [content] at [anchor] and reports the rectangle it covers.
 *
 * A control drawn here is guarded and inset by the same call, so the two can
 * never disagree about where it is.
 */
@Composable
internal fun BoxScope.PickerMapAnchorRegion(
    anchor: PickerMapAnchor,
    modifier: Modifier = Modifier,
    inset: Dp = SeatLayerSizeTokens.MAP_ANCHOR_INSET.dp,
    bottomInset: Dp = 0.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .align(anchor.alignment())
            .padding(anchor.padding(inset, bottomInset)),
    ) {
        // The rectangle is the CONTROL's, not the inset box's: a guard that
        // included its own padding would swallow the map out to the edge.
        PickerMapChromeRegion(enabled = enabled, content = content)
    }
}
