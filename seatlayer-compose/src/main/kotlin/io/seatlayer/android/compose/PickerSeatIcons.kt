package io.seatlayer.android.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * ONE drawing per seat attribute, shared with every other SeatLayer surface.
 *
 * The picker names twelve accommodations, three selling marks and the
 * organizer's own note. Drawing them with whatever platform icon came closest
 * — a wheelchair for every accommodation, an eye for a view restriction, a
 * filled star for premium — is how the same seat came to wear a different mark
 * on every surface, because the platform set is not the set the map and the
 * web popups draw.
 *
 * The geometry here is transcribed VERBATIM from the shared icon set in the
 * runtime, which is the designer's own artwork. That is why the box is 20
 * rather than the platform's 24, and why every glyph is stroke-only: a row's
 * tone is one colour away, with no second icon set to keep in step.
 *
 * Two rules make this safe to carry between the ports:
 *
 *  * **No emoji and no platform icon.** An emoji arrives in a colour, weight
 *    and baseline the host font decides, and no theme can reach it.
 *  * **The path data is the contract.** Ports transcribe these same strings.
 *    Redrawing a glyph "close enough" is how the surfaces drifted apart.
 */

/** The square every glyph below is authored in. */
internal const val SEATLAYER_SEAT_ICON_VIEW_BOX: Float = 20f

/** Stroke weight at the authored box size, scaled with the drawn size. */
internal const val SEATLAYER_SEAT_ICON_STROKE_WIDTH: Float = 1.45f

/**
 * One glyph: the circles the shared set draws as circle elements, then its
 * path data.
 *
 * Kept apart rather than folded into one path string so the transcription can
 * be read against the source element for element.
 */
internal data class SeatLayerSeatGlyph(
    /** `[cx, cy, r]` per circle, in the 20-unit box. */
    val circles: List<FloatArray> = emptyList(),
    /** Path data, in the 20-unit box, stroked. */
    val paths: List<String> = emptyList(),
    /** Path data painted FILLED. */
    val fills: List<String> = emptyList(),
)

/**
 * Every glyph the picker can ask for, by the runtime's own key.
 *
 * The twelve accommodation keys are the runtime's `accessibility[]` values;
 * `restrictedView`, `obstructedView` and `premium` are its commercial marks;
 * `note` is the organizer's free sentence.
 */
internal val seatLayerSeatGlyphs: Map<String, SeatLayerSeatGlyph> = mapOf(
    // A seated figure over a wheel — authored as an outline so it does not read
    // as a blob beside eleven outline siblings.
    "wheelchair" to SeatLayerSeatGlyph(
        circles = listOf(
            floatArrayOf(10.6f, 3.7f, 1.7f),
            floatArrayOf(9.7f, 13.3f, 4.7f),
        ),
        paths = listOf("M8.9 6.3v4.3a1.3 1.3 0 0 0 1.3 1.3h3.4l1.9 4.5h1.9"),
    ),
    "companion" to SeatLayerSeatGlyph(
        circles = listOf(
            floatArrayOf(6f, 6f, 2f),
            floatArrayOf(14f, 6f, 2f),
        ),
        paths = listOf(
            "M2.5 16v-3.5A3.5 3.5 0 0 1 6 9a3.5 3.5 0 0 1 3.5 3.5V16" +
                "M10.5 16v-3.5A3.5 3.5 0 0 1 14 9a3.5 3.5 0 0 1 3.5 3.5V16",
        ),
    ),
    "semi-ambulatory" to SeatLayerSeatGlyph(
        circles = listOf(floatArrayOf(8f, 4f, 1.7f)),
        paths = listOf("m8 6 2 4 3 2M10 10l-2 3-1 4M10 10l2 7M14 7l2 10"),
    ),
    "designated-aisle" to SeatLayerSeatGlyph(
        paths = listOf(
            "M3.5 5.5v7h8.5V10H6.5M5 12.5V17M11 12.5V17",
            "M13.5 6.5H19M16.5 4l2.5 2.5L16.5 9",
        ),
    ),
    "step-free" to SeatLayerSeatGlyph(
        circles = listOf(floatArrayOf(5.5f, 5f, 1.7f)),
        paths = listOf("M5.5 7v4l3 2M2.5 16.5H8l7-7h3M8 16.5h10"),
    ),
    "hearing" to SeatLayerSeatGlyph(
        paths = listOf(
            "M7 16c-1-1.2-1.5-2.4-1.5-3.8V9a5 5 0 1 1 10 0c0 2.2-1.2 3.2-2.6 4" +
                "-1.2.7-1.7 1.4-1.7 2.4A2.6 2.6 0 0 1 8.6 18",
        ),
    ),
    "cart" to SeatLayerSeatGlyph(
        paths = listOf("M8 6.5A4 4 0 1 0 8 13M17 6.5a4 4 0 1 0 0 6.5"),
    ),
    "sign-language" to SeatLayerSeatGlyph(
        paths = listOf(
            "M6 16V8.5a1 1 0 0 1 2 0v3M8 11V5.5a1 1 0 0 1 2 0V11M10 11V4.5a1 1 0 0 " +
                "1 2 0V11M12 11V6a1 1 0 0 1 2 0v6l1-1.5a1.2 1.2 0 0 1 2 1.3L14 17H9.5" +
                "A3.5 3.5 0 0 1 6 13.5",
        ),
    ),
    "low-vision" to SeatLayerSeatGlyph(
        circles = listOf(floatArrayOf(10f, 10f, 2.5f)),
        paths = listOf(
            "M2.5 10s3-5 7.5-5 7.5 5 7.5 5-3 5-7.5 5-7.5-5-7.5-5Z",
            "m15 4 .5-1.5M17 5l1.5-.8",
        ),
    ),
    "sensory-friendly" to SeatLayerSeatGlyph(
        paths = listOf(
            "M4 11v-1a6 6 0 0 1 12 0v1M4 11h2.5v5H5a1 1 0 0 1-1-1v-4ZM16 11h-2.5v5" +
                "H15a1 1 0 0 0 1-1v-4Z",
            "M8.5 11.5c.8.7 2.2.7 3 0M9 14c.6.4 1.4.4 2 0",
        ),
    ),
    "plus-size" to SeatLayerSeatGlyph(
        paths = listOf(
            "M5 9V6.5A2.5 2.5 0 0 1 7.5 4h5A2.5 2.5 0 0 1 15 6.5V9M3.5 8.5v5h13v-5" +
                "M6 13.5V17M14 13.5V17",
        ),
    ),
    "lift-armrest" to SeatLayerSeatGlyph(
        paths = listOf(
            "M5 10V6.5A2.5 2.5 0 0 1 7.5 4h4A2.5 2.5 0 0 1 14 6.5V10M4 9v4h11V9" +
                "M6 13v4M13 13v4",
            "M17 11V4M15 6l2-2 2 2",
        ),
    ),
    // An eye struck through: the view is BLOCKED, not merely poor.
    "obstructedView" to SeatLayerSeatGlyph(
        paths = listOf(
            "M2.5 10s3-5 7.5-5 7.5 5 7.5 5-3 5-7.5 5-7.5-5-7.5-5Z",
            "m4 17 12-14",
        ),
    ),
    // An eye carrying a caution mark: you can see, with a caveat.
    "restrictedView" to SeatLayerSeatGlyph(
        paths = listOf(
            "M2.5 10s3-5 7.5-5 7.5 5 7.5 5-3 5-7.5 5-7.5-5-7.5-5Z",
            "M10 7.5v3.5M10 13.5h.01",
        ),
    ),
    "premium" to SeatLayerSeatGlyph(
        paths = listOf(
            "m10 2.5 2.2 4.6 5 .7-3.6 3.5.9 5-4.5-2.4-4.5 2.4.9-5-3.6-3.5 5-.7Z",
        ),
    ),
    // The organizer's own words about this seat — an information mark.
    "note" to SeatLayerSeatGlyph(
        circles = listOf(floatArrayOf(10f, 10f, 7.5f)),
        paths = listOf("M10 9.2v4.6M10 6.3h.01"),
    ),
    // NOT a seat attribute, and deliberately not in the shared set: the
    // accessibility sheet's colour row recolours the map rather than picking
    // seats, so it wears a contrast disc rather than an eye or a seat mark.
    "contrast" to SeatLayerSeatGlyph(
        circles = listOf(floatArrayOf(10f, 10f, 7.5f)),
        fills = listOf("M10 2.5a7.5 7.5 0 0 1 0 15Z"),
    ),
)

/**
 * The glyph [key] names in the 20-unit box, or null where this build has none.
 *
 * An unknown key returns null rather than a placeholder: a row whose drawing is
 * missing prints its words alone, which is a correct row, where a broken box
 * would be a defect the buyer can see.
 */
internal fun seatLayerSeatIconPath(key: String): Path? {
    val glyph = seatLayerSeatGlyphs[key] ?: return null
    val path = Path()
    glyph.circles.forEach { circle ->
        path.addOval(
            Rect(center = Offset(circle[0], circle[1]), radius = circle[2]),
        )
    }
    glyph.paths.forEach { data ->
        path.addPath(PathParser().parsePathString(data).toPath())
    }
    return path
}

/** The filled half of the glyph [key] names, or null where it has none. */
internal fun seatLayerSeatIconFillPath(key: String): Path? {
    val fills = seatLayerSeatGlyphs[key]?.fills ?: return null
    if (fills.isEmpty()) return null
    val path = Path()
    fills.forEach { data -> path.addPath(PathParser().parsePathString(data).toPath()) }
    return path
}

/**
 * One attribute glyph, stroked in the row's own ink.
 *
 * Sized in the caller's points and scaled from the authored 20-unit box, so a
 * row can ask for one size on a card and another on the compact one without a
 * second drawing. Hidden from screen readers: every caller prints the same fact
 * in words beside it, and announcing it twice is all this could add.
 */
@Composable
internal fun SeatLayerSeatIcon(
    iconKey: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val stroked = remember(iconKey) { seatLayerSeatIconPath(iconKey) }
    val filled = remember(iconKey) { seatLayerSeatIconFillPath(iconKey) }
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val outline = stroked ?: return@Canvas
        val factor = size.minDimension / SEATLAYER_SEAT_ICON_VIEW_BOX
        scale(scale = factor, pivot = Offset.Zero) {
            filled?.let { drawPath(it, color, style = Fill) }
            drawPath(
                outline,
                color,
                style = Stroke(
                    width = SEATLAYER_SEAT_ICON_STROKE_WIDTH,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}
