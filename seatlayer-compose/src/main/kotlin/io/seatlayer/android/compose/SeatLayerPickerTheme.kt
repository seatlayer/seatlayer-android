package io.seatlayer.android.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SeatLayerPickerBranding
import io.seatlayer.android.SeatLayerPickerMapTheme
import io.seatlayer.android.SeatLayerPickerThemeMode

/** Semantic native-chrome theme; renderer map pixels remain runtime-owned. */
@Immutable
public data class SeatLayerPickerTheme(
    val background: Color,
    val surface: Color,
    /** Body ink on [surface]. */
    val text: Color,
    /** Secondary ink: eyebrows, captions, the words under a total. */
    val mutedText: Color,
    val accent: Color,
    val onAccent: Color,
    val divider: Color,
    val warning: Color,
    val onWarning: Color,
    val error: Color,
    val success: Color,
    val scrim: Color,
    val mapTheme: SeatLayerPickerMapTheme,
    val radius: Dp = SeatLayerRadiusTokens.BASE.dp,
    /** Ink for a caution note, on the surface's own ground. */
    val warnText: Color = Color(SeatLayerLightTokens.WARN_TEXT),
    val premium: Color = Color(SeatLayerLightTokens.PREMIUM),
    val premiumText: Color = Color(SeatLayerLightTokens.PREMIUM_TEXT),
    /** The typeface the chrome uses; null keeps the platform's own. */
    val fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    val buttonRadius: Dp = SeatLayerRadiusTokens.BUTTON.dp,
    /** The brand mark drawn in the header, when a host supplies one. */
    val logo: Any? = null,
) {
    @Deprecated("Renamed to text.", ReplaceWith("text"))
    val onSurface: Color get() = text

    @Deprecated("Renamed to mutedText.", ReplaceWith("mutedText"))
    val muted: Color get() = mutedText

    @Deprecated("Renamed to radius.", ReplaceWith("radius"))
    val cornerRadius: Dp get() = radius

    public companion object {
        public fun light(): SeatLayerPickerTheme = SeatLayerPickerTheme(
            background = Color(SeatLayerLightTokens.BACKGROUND),
            surface = Color(SeatLayerLightTokens.SURFACE),
            text = Color(SeatLayerLightTokens.TEXT),
            mutedText = Color(SeatLayerLightTokens.MUTED_TEXT),
            accent = Color(SeatLayerLightTokens.ACCENT),
            onAccent = Color(SeatLayerLightTokens.ON_ACCENT),
            divider = Color(SeatLayerLightTokens.DIVIDER),
            warning = Color(SeatLayerLightTokens.WARNING),
            onWarning = Color(0xFF332600),
            error = Color(SeatLayerLightTokens.ERROR),
            success = Color(0xFF168A5B),
            scrim = Color.Black.copy(alpha = SeatLayerOpacityTokens.CONFIRM_SCRIM),
            mapTheme = SeatLayerPickerMapTheme(
                background = SeatLayerLightTokens.MAP_BACKGROUND.pickerHex(),
                rowLabel = SeatLayerLightTokens.MAP_ROW_LABEL.pickerHex(),
                text = SeatLayerLightTokens.MAP_TEXT.pickerHex(),
                selection = SeatLayerLightTokens.MAP_SELECTION.pickerHex(),
            ),
        )

        public fun dark(): SeatLayerPickerTheme = SeatLayerPickerTheme(
            background = Color(SeatLayerDarkTokens.BACKGROUND),
            surface = Color(SeatLayerDarkTokens.SURFACE),
            text = Color(SeatLayerDarkTokens.TEXT),
            mutedText = Color(SeatLayerDarkTokens.MUTED_TEXT),
            warnText = Color(SeatLayerDarkTokens.WARN_TEXT),
            premium = Color(SeatLayerDarkTokens.PREMIUM),
            premiumText = Color(SeatLayerDarkTokens.PREMIUM_TEXT),
            accent = Color(SeatLayerDarkTokens.ACCENT),
            onAccent = Color(SeatLayerDarkTokens.ON_ACCENT),
            divider = Color(SeatLayerDarkTokens.DIVIDER),
            warning = Color(SeatLayerDarkTokens.WARNING),
            onWarning = Color(0xFF332600),
            error = Color(SeatLayerDarkTokens.ERROR),
            success = Color(0xFF76D6A8),
            scrim = Color.Black.copy(alpha = SeatLayerOpacityTokens.CONFIRM_SCRIM_FLAT),
            mapTheme = SeatLayerPickerMapTheme(
                background = SeatLayerDarkTokens.MAP_BACKGROUND.pickerHex(),
                rowLabel = SeatLayerDarkTokens.MAP_ROW_LABEL.pickerHex(),
                text = SeatLayerDarkTokens.MAP_TEXT.pickerHex(),
                selection = SeatLayerDarkTokens.MAP_SELECTION.pickerHex(),
            ),
        )
    }
}

internal val LocalSeatLayerPickerTheme = compositionLocalOf {
    SeatLayerPickerTheme.light()
}

@Composable
internal fun SeatLayerPickerThemeProvider(
    mode: SeatLayerPickerThemeMode,
    explicitTheme: SeatLayerPickerTheme?,
    branding: SeatLayerPickerBranding?,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        SeatLayerPickerThemeMode.Dark -> true
        SeatLayerPickerThemeMode.Light -> false
        else -> isSystemInDarkTheme()
    }
    val base = explicitTheme ?: if (dark) {
        SeatLayerPickerTheme.dark()
    } else {
        SeatLayerPickerTheme.light()
    }
    val resolved = if (explicitTheme == null) {
        base.copy(
            accent = parseColor(branding?.accent) ?: base.accent,
            onAccent = parseColor(branding?.accentInk) ?: base.onAccent,
            background = parseColor(branding?.background) ?: base.background,
            surface = parseColor(branding?.surface) ?: base.surface,
            text = parseColor(branding?.text) ?: base.text,
            mutedText = parseColor(branding?.muted) ?: base.mutedText,
            divider = parseColor(branding?.line) ?: base.divider,
            mapTheme = base.mapTheme.copy(
                background = branding?.background ?: base.mapTheme.background,
                rowLabel = branding?.text ?: base.mapTheme.rowLabel,
                text = branding?.text ?: base.mapTheme.text,
                selection = branding?.accent ?: base.mapTheme.selection,
            ),
            radius = branding?.radius
                ?.takeIf { it.isFinite() && it >= 0 }
                ?.dp ?: base.radius,
        )
    } else {
        base
    }
    val colors = if (dark) {
        darkColorScheme(
            primary = resolved.accent,
            onPrimary = resolved.onAccent,
            surface = resolved.surface,
            onSurface = resolved.text,
            background = resolved.background,
            onBackground = resolved.text,
            error = resolved.error,
        )
    } else {
        lightColorScheme(
            primary = resolved.accent,
            onPrimary = resolved.onAccent,
            surface = resolved.surface,
            onSurface = resolved.text,
            background = resolved.background,
            onBackground = resolved.text,
            error = resolved.error,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalSeatLayerPickerTheme provides resolved,
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

internal fun parseColor(value: String?): Color? {
    val raw = value?.removePrefix("#") ?: return null
    if (raw.length != 6 && raw.length != 8) return null
    val parsed = raw.toULongOrNull(16) ?: return null
    val argb = if (raw.length == 6) parsed or 0xFF000000u else parsed
    return Color(argb.toLong())
}

private fun Long.pickerHex(): String = "#%06X".format(this and 0xFFFFFF)
