package io.seatlayer.android.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True while the chrome is being CAPTURED rather than used.
 *
 * A reference image is a single frame, and an entrance — a crossfade, a rise,
 * a stagger that lands one card after the next — makes that frame a question
 * about when the shutter opened. Advancing a test clock by a fixed number of
 * milliseconds only moves the question: the golden then depends on a frame
 * count, and a spec that lengthens one duration by ten milliseconds fails
 * images it never touched.
 *
 * So a capture says so, and every entrance settles at once. This is the same
 * idea as [LocalSeatLayerPickerClock], which stops the countdown depending on
 * the wall clock — the animations are the other half of that.
 *
 * It does not DELETE the motion: nothing is removed from the shipped chrome,
 * and the one composable that reads it is the reduced-motion accessor every
 * animated surface already consults.
 */
internal val LocalSeatLayerPickerCapture: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }
