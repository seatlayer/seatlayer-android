package io.seatlayer.android.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Ink chosen against a ground nobody picked for legibility.
 *
 * A category colour is authored by the organizer, a note's tint is mixed from
 * a warning amber, and a host's accent can be anything. Each of those is a
 * ground some words have to survive on, so the ink is measured against the
 * ground it actually paints on rather than against the surface it was mixed
 * from — the raw amber measures about 1.8:1 on a white card.
 *
 * The functions here are pure and carry no Compose state: a port implements
 * the same arithmetic, and the tests measure it directly.
 */

/** The relative luminance of [color], as the contrast formula defines it. */
internal fun pickerRelativeLuminance(color: Color): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(color.red) +
        0.7152f * channel(color.green) +
        0.0722f * channel(color.blue)
}

/** The contrast ratio between two opaque colours, from 1:1 to 21:1. */
internal fun pickerContrastRatio(first: Color, second: Color): Float {
    val a = pickerRelativeLuminance(first)
    val b = pickerRelativeLuminance(second)
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}

/** [tint] at [alpha] laid over [ground], as one opaque colour. */
internal fun pickerWash(tint: Color, alpha: Float, ground: Color): Color =
    tint.copy(alpha = alpha).compositeOver(ground)

/** The two ink candidates a category band may use, and nothing else. */
internal val SEATLAYER_BAND_INK_LIGHT: Color = Color.White
internal val SEATLAYER_BAND_INK_DARK: Color = Color(0xFF0B0F19)

/**
 * The ink a full-bleed category band prints in.
 *
 * White wherever white clears 3:1 against the authored colour, and the dark ink
 * otherwise, so a pale yellow or a light grey category keeps its name. The two
 * candidates are fixed; the band never manufactures a colour of its own.
 */
internal fun seatLayerPickerConfirmBandInk(band: Color): Color =
    if (pickerContrastRatio(SEATLAYER_BAND_INK_LIGHT, band) >= 3f) {
        SEATLAYER_BAND_INK_LIGHT
    } else {
        SEATLAYER_BAND_INK_DARK
    }

/**
 * The accent, walked toward the text ink until it is readable on both grounds.
 *
 * A host's accent is a brand colour, not a text colour. Where it already clears
 * 4.5:1 on the surface and on the tinted ground it is used as it is; otherwise
 * it is blended toward the body ink in small steps until it does, which keeps
 * the brand's hue while making the word legible.
 */
internal fun pickerReadableAccent(
    accent: Color,
    text: Color,
    surface: Color,
    ground: Color = surface,
): Color {
    var candidate = accent
    var step = 0
    while (step <= STEPS) {
        val clears = pickerContrastRatio(candidate, surface) >= READABLE &&
            pickerContrastRatio(candidate, ground) >= READABLE
        if (clears) return candidate
        step += 1
        candidate = lerp(accent, text, step.toFloat() / STEPS)
    }
    return text
}

/** How far a muted ink is walked toward the body ink for a note's second line. */
internal fun pickerNoteBodyInk(muted: Color, text: Color, fraction: Float): Color =
    lerp(muted, text, fraction.coerceIn(0f, 1f))

/** True where two colours are the same to within a rounding of one channel. */
internal fun pickerColorsAgree(first: Color, second: Color): Boolean =
    abs(first.red - second.red) < TOLERANCE &&
        abs(first.green - second.green) < TOLERANCE &&
        abs(first.blue - second.blue) < TOLERANCE &&
        abs(first.alpha - second.alpha) < TOLERANCE

private const val READABLE = 4.5f
private const val STEPS = 20
private const val TOLERANCE = 0.004f
