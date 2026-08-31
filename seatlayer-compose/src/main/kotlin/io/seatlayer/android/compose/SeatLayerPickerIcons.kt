package io.seatlayer.android.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class SeatLayerPickerGlyph {
    Seat,
    Close,
    Back,
    Forward,
    Check,
    Plus,
    Minus,
    Fit,
    Accessibility,
    Eye,
    Cube,
    Orbit,
    Move,
    Recentre,
    ChevronUp,
}

/** Small dependency-free glyphs shared by the picker chrome. */
@Composable
internal fun SeatLayerPickerIcon(
    glyph: SeatLayerPickerGlyph,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension
        val strokeWidth = unit * 0.105f
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        fun point(x: Float, y: Float): Offset = Offset(size.width * x, size.height * y)
        when (glyph) {
            SeatLayerPickerGlyph.Seat -> {
                drawRoundRect(
                    color = color,
                    topLeft = point(0.33f, 0.14f),
                    size = Size(size.width * 0.34f, size.height * 0.38f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 0.08f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = point(0.22f, 0.55f),
                    size = Size(size.width * 0.56f, size.height * 0.18f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 0.07f),
                )
                drawLine(color, point(0.17f, 0.43f), point(0.17f, 0.72f), strokeWidth)
                drawLine(color, point(0.83f, 0.43f), point(0.83f, 0.72f), strokeWidth)
                drawLine(color, point(0.28f, 0.73f), point(0.23f, 0.88f), strokeWidth)
                drawLine(color, point(0.72f, 0.73f), point(0.77f, 0.88f), strokeWidth)
            }
            SeatLayerPickerGlyph.Close -> {
                drawLine(color, point(0.22f, 0.22f), point(0.78f, 0.78f), strokeWidth)
                drawLine(color, point(0.78f, 0.22f), point(0.22f, 0.78f), strokeWidth)
            }
            SeatLayerPickerGlyph.Back,
            SeatLayerPickerGlyph.Forward,
            -> {
                val direction = if (glyph == SeatLayerPickerGlyph.Back) 1f else -1f
                val centre = 0.5f
                drawLine(
                    color,
                    point(centre + direction * 0.18f, 0.22f),
                    point(centre - direction * 0.12f, 0.5f),
                    strokeWidth,
                )
                drawLine(
                    color,
                    point(centre - direction * 0.12f, 0.5f),
                    point(centre + direction * 0.18f, 0.78f),
                    strokeWidth,
                )
            }
            SeatLayerPickerGlyph.Check -> {
                drawLine(color, point(0.18f, 0.52f), point(0.4f, 0.74f), strokeWidth)
                drawLine(color, point(0.4f, 0.74f), point(0.82f, 0.26f), strokeWidth)
            }
            SeatLayerPickerGlyph.Plus,
            SeatLayerPickerGlyph.Minus,
            -> {
                drawLine(color, point(0.22f, 0.5f), point(0.78f, 0.5f), strokeWidth)
                if (glyph == SeatLayerPickerGlyph.Plus) {
                    drawLine(color, point(0.5f, 0.22f), point(0.5f, 0.78f), strokeWidth)
                }
            }
            SeatLayerPickerGlyph.Fit,
            SeatLayerPickerGlyph.Recentre,
            -> {
                val edge = if (glyph == SeatLayerPickerGlyph.Fit) 0.16f else 0.12f
                val inner = 0.38f
                drawLine(color, point(edge, inner), point(edge, edge), strokeWidth)
                drawLine(color, point(edge, edge), point(inner, edge), strokeWidth)
                drawLine(color, point(1 - inner, edge), point(1 - edge, edge), strokeWidth)
                drawLine(color, point(1 - edge, edge), point(1 - edge, inner), strokeWidth)
                drawLine(color, point(edge, 1 - inner), point(edge, 1 - edge), strokeWidth)
                drawLine(color, point(edge, 1 - edge), point(inner, 1 - edge), strokeWidth)
                drawLine(color, point(1 - inner, 1 - edge), point(1 - edge, 1 - edge), strokeWidth)
                drawLine(color, point(1 - edge, 1 - edge), point(1 - edge, 1 - inner), strokeWidth)
                if (glyph == SeatLayerPickerGlyph.Recentre) {
                    drawCircle(color = color, radius = unit * 0.12f, center = point(0.5f, 0.5f))
                }
            }
            SeatLayerPickerGlyph.Accessibility -> {
                drawCircle(color = color, radius = unit * 0.09f, center = point(0.47f, 0.17f))
                drawLine(color, point(0.42f, 0.31f), point(0.5f, 0.57f), strokeWidth)
                drawLine(color, point(0.46f, 0.38f), point(0.68f, 0.38f), strokeWidth)
                drawLine(color, point(0.5f, 0.57f), point(0.7f, 0.57f), strokeWidth)
                drawLine(color, point(0.7f, 0.57f), point(0.8f, 0.79f), strokeWidth)
                drawArc(
                    color = color,
                    startAngle = -56f,
                    sweepAngle = 275f,
                    useCenter = false,
                    topLeft = point(0.18f, 0.42f),
                    size = Size(unit * 0.46f, unit * 0.46f),
                    style = stroke,
                )
            }
            SeatLayerPickerGlyph.Eye -> {
                val path = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.5f)
                    quadraticTo(
                        size.width * 0.5f,
                        size.height * 0.13f,
                        size.width * 0.88f,
                        size.height * 0.5f,
                    )
                    quadraticTo(
                        size.width * 0.5f,
                        size.height * 0.87f,
                        size.width * 0.12f,
                        size.height * 0.5f,
                    )
                }
                drawPath(path, color, style = stroke)
                drawCircle(color = color, radius = unit * 0.1f, center = point(0.5f, 0.5f))
            }
            SeatLayerPickerGlyph.Cube -> {
                val path = Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.1f)
                    lineTo(size.width * 0.84f, size.height * 0.3f)
                    lineTo(size.width * 0.84f, size.height * 0.7f)
                    lineTo(size.width * 0.5f, size.height * 0.9f)
                    lineTo(size.width * 0.16f, size.height * 0.7f)
                    lineTo(size.width * 0.16f, size.height * 0.3f)
                    close()
                    moveTo(size.width * 0.16f, size.height * 0.3f)
                    lineTo(size.width * 0.5f, size.height * 0.5f)
                    lineTo(size.width * 0.84f, size.height * 0.3f)
                    moveTo(size.width * 0.5f, size.height * 0.5f)
                    lineTo(size.width * 0.5f, size.height * 0.9f)
                }
                drawPath(path, color, style = stroke)
            }
            SeatLayerPickerGlyph.Orbit -> {
                drawArc(
                    color = color,
                    startAngle = 35f,
                    sweepAngle = 285f,
                    useCenter = false,
                    topLeft = point(0.14f, 0.14f),
                    size = Size(unit * 0.72f, unit * 0.72f),
                    style = stroke,
                )
                drawLine(color, point(0.75f, 0.16f), point(0.88f, 0.28f), strokeWidth)
                drawLine(color, point(0.88f, 0.28f), point(0.72f, 0.33f), strokeWidth)
            }
            SeatLayerPickerGlyph.Move -> {
                drawLine(color, point(0.18f, 0.5f), point(0.82f, 0.5f), strokeWidth)
                drawLine(color, point(0.5f, 0.18f), point(0.5f, 0.82f), strokeWidth)
                drawLine(color, point(0.18f, 0.5f), point(0.32f, 0.37f), strokeWidth)
                drawLine(color, point(0.18f, 0.5f), point(0.32f, 0.63f), strokeWidth)
                drawLine(color, point(0.82f, 0.5f), point(0.68f, 0.37f), strokeWidth)
                drawLine(color, point(0.82f, 0.5f), point(0.68f, 0.63f), strokeWidth)
                drawLine(color, point(0.5f, 0.18f), point(0.37f, 0.32f), strokeWidth)
                drawLine(color, point(0.5f, 0.18f), point(0.63f, 0.32f), strokeWidth)
                drawLine(color, point(0.5f, 0.82f), point(0.37f, 0.68f), strokeWidth)
                drawLine(color, point(0.5f, 0.82f), point(0.63f, 0.68f), strokeWidth)
            }
            SeatLayerPickerGlyph.ChevronUp -> {
                drawLine(color, point(0.22f, 0.65f), point(0.5f, 0.37f), strokeWidth)
                drawLine(color, point(0.5f, 0.37f), point(0.78f, 0.65f), strokeWidth)
            }
        }
    }
}

internal val DefaultPickerIconSize = 20.dp
