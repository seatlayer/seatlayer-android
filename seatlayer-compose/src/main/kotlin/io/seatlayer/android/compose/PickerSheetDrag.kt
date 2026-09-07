package io.seatlayer.android.compose

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Where the sheet rests.
 *
 * Three positions, and the heights are the CART REGION's height above the
 * collapsed block — which is the head strip and the whole foot. [Peek] is
 * therefore zero by construction, not a measured band, so an add never unrolls
 * a panel the buyer did not open.
 *
 * [Full] is a place the finger reaches. Nothing settles there on the picker's
 * own initiative, and it is only offered when the content is taller than the
 * ordinary ceiling.
 */
internal enum class PickerSheetRest {
    Peek,
    Content,
    Full,
}

/**
 * The detent table for one measurement of the sheet.
 *
 * Pure: it takes the two computed ceilings and answers where a release lands.
 * Nothing here touches Compose, so the settle rule, the fling rule and the
 * rubber band are all testable without a screen.
 */
internal data class PickerSheetDetents(
    val content: Float,
    val full: Float,
) {
    val peek: Float get() = 0f

    /** A [Full] below [content] is not a detent; it is clamped up to it. */
    val resolvedFull: Float get() = maxOf(full, content)

    val offersFull: Boolean get() = resolvedFull > content + EPSILON

    /** The highest position the finger may reach. */
    val top: Float get() = if (offersFull) resolvedFull else content

    val positions: List<PickerSheetRest>
        get() = if (offersFull) {
            listOf(PickerSheetRest.Peek, PickerSheetRest.Content, PickerSheetRest.Full)
        } else {
            listOf(PickerSheetRest.Peek, PickerSheetRest.Content)
        }

    fun heightOf(rest: PickerSheetRest): Float = when (rest) {
        PickerSheetRest.Peek -> peek
        PickerSheetRest.Content -> content
        PickerSheetRest.Full -> resolvedFull
    }

    fun nearest(height: Float): PickerSheetRest =
        positions.minByOrNull { abs(heightOf(it) - height) } ?: PickerSheetRest.Peek

    /**
     * Where a release lands.
     *
     * Below [SeatLayerPhysicsTokens.SHEET_FLING_VELOCITY] the sheet goes to the
     * nearest detent. At or above it a flick is an instruction rather than a
     * measurement, so it steps to the next detent in the thrown direction
     * whatever the distance travelled.
     */
    fun settle(height: Float, velocity: Float): PickerSheetRest {
        val nearest = nearest(height)
        if (abs(velocity) < SeatLayerPhysicsTokens.SHEET_FLING_VELOCITY) return nearest
        return step(nearest, if (velocity > 0f) 1 else -1)
    }

    fun step(from: PickerSheetRest, direction: Int): PickerSheetRest {
        val index = positions.indexOf(from)
        if (index < 0) return from
        return positions.getOrElse(index + direction) { positions[index] }
    }

    internal companion object {
        /** Two detents are the same detent when they differ by less than this. */
        const val EPSILON: Float = 0.5f
    }
}

/**
 * The floor under a deliberate short drag.
 *
 * A buyer who pulls the sheet a clear distance and lets go has answered, even
 * when the nearest detent is still the one they started at. Below the
 * threshold the drag was a wobble and the sheet stays where it was.
 *
 * The threshold is a file-local constant in the reference too — tokens.json
 * carries no drag threshold, and inventing a token here would put a number in
 * the design source that the design source never agreed to.
 */
internal const val PICKER_SHEET_DRAG_THRESHOLD: Float = 18f

internal fun PickerSheetDetents.settleWithFloor(
    height: Float,
    velocity: Float,
    started: PickerSheetRest,
    travel: Float,
): PickerSheetRest {
    val settled = settle(height, velocity)
    if (settled != started) return settled
    if (abs(travel) < PICKER_SHEET_DRAG_THRESHOLD) return settled
    return step(started, if (travel > 0f) 1 else -1)
}

/**
 * How far past an end the surface still moves.
 *
 * A hard clamp tells the buyer their finger has stopped working. Past either
 * end the sheet keeps travelling at [SeatLayerPhysicsTokens.RUBBER_BAND] of the
 * finger, and the spring puts it back.
 */
internal fun pickerRubberBand(raw: Float, minimum: Float, maximum: Float): Float = when {
    raw < minimum -> minimum - (minimum - raw) * SeatLayerPhysicsTokens.RUBBER_BAND
    raw > maximum -> maximum + (raw - maximum) * SeatLayerPhysicsTokens.RUBBER_BAND
    else -> raw
}

/**
 * The design source's sheet spring, in the terms Compose animates in.
 *
 * The source names mass, stiffness and damping; Compose names stiffness and a
 * damping RATIO, so the ratio is derived rather than guessed — the critical
 * damping of the source's own spring is `2 * sqrt(stiffness * mass)`.
 */
internal fun pickerSheetDampingRatio(): Float {
    val critical = 2f * sqrt(
        SeatLayerPhysicsTokens.SHEET_SPRING_STIFFNESS * SeatLayerPhysicsTokens.SHEET_SPRING_MASS,
    )
    return SeatLayerPhysicsTokens.SHEET_SPRING_DAMPING / critical
}

internal fun pickerSheetSpring(): SpringSpec<Float> = spring(
    dampingRatio = pickerSheetDampingRatio(),
    stiffness = SeatLayerPhysicsTokens.SHEET_SPRING_STIFFNESS,
    visibilityThreshold = PickerSheetDetents.EPSILON,
)

/** The same spring, or no animation at all when the device asks for none. */
internal fun pickerSheetSettleSpec(reducedMotion: Boolean): AnimationSpec<Float> =
    if (reducedMotion) {
        spring(stiffness = Float.MAX_VALUE, visibilityThreshold = PickerSheetDetents.EPSILON)
    } else {
        pickerSheetSpring()
    }
