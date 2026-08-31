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
    val onSurface: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val divider: Color,
    val warning: Color,
    val onWarning: Color,
    val error: Color,
    val success: Color,
    val scrim: Color,
    val mapTheme: SeatLayerPickerMapTheme,
    val cornerRadius: Dp = SeatLayerPickerTokens.RADIUS_BASE.dp,
) {
    public companion object {
        public fun light(): SeatLayerPickerTheme = SeatLayerPickerTheme(
            background = Color(SeatLayerPickerTokens.LIGHT_BACKGROUND),
            surface = Color(SeatLayerPickerTokens.LIGHT_SURFACE),
            onSurface = Color(SeatLayerPickerTokens.LIGHT_TEXT),
            muted = Color(SeatLayerPickerTokens.LIGHT_MUTED_TEXT),
            accent = Color(SeatLayerPickerTokens.LIGHT_ACCENT),
            onAccent = Color(SeatLayerPickerTokens.LIGHT_ON_ACCENT),
            divider = Color(SeatLayerPickerTokens.LIGHT_DIVIDER),
            warning = Color(SeatLayerPickerTokens.LIGHT_WARNING),
            onWarning = Color(0xFF332600),
            error = Color(SeatLayerPickerTokens.LIGHT_ERROR),
            success = Color(0xFF168A5B),
            scrim = Color.Black.copy(alpha = 0.42f),
            mapTheme = SeatLayerPickerMapTheme(
                background = SeatLayerPickerTokens.LIGHT_MAP_BACKGROUND.pickerHex(),
                rowLabel = SeatLayerPickerTokens.LIGHT_MAP_ROW_LABEL.pickerHex(),
                text = SeatLayerPickerTokens.LIGHT_MAP_TEXT.pickerHex(),
                selection = SeatLayerPickerTokens.LIGHT_MAP_SELECTION.pickerHex(),
            ),
        )

        public fun dark(): SeatLayerPickerTheme = SeatLayerPickerTheme(
            background = Color(SeatLayerPickerTokens.DARK_BACKGROUND),
            surface = Color(SeatLayerPickerTokens.DARK_SURFACE),
            onSurface = Color(SeatLayerPickerTokens.DARK_TEXT),
            muted = Color(SeatLayerPickerTokens.DARK_MUTED_TEXT),
            accent = Color(SeatLayerPickerTokens.DARK_ACCENT),
            onAccent = Color(SeatLayerPickerTokens.DARK_ON_ACCENT),
            divider = Color(SeatLayerPickerTokens.DARK_DIVIDER),
            warning = Color(SeatLayerPickerTokens.DARK_WARNING),
            onWarning = Color(0xFF332600),
            error = Color(SeatLayerPickerTokens.DARK_ERROR),
            success = Color(0xFF76D6A8),
            scrim = Color.Black.copy(alpha = 0.62f),
            mapTheme = SeatLayerPickerMapTheme(
                background = SeatLayerPickerTokens.DARK_MAP_BACKGROUND.pickerHex(),
                rowLabel = SeatLayerPickerTokens.DARK_MAP_ROW_LABEL.pickerHex(),
                text = SeatLayerPickerTokens.DARK_MAP_TEXT.pickerHex(),
                selection = SeatLayerPickerTokens.DARK_MAP_SELECTION.pickerHex(),
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
            onSurface = parseColor(branding?.text) ?: base.onSurface,
            muted = parseColor(branding?.muted) ?: base.muted,
            divider = parseColor(branding?.line) ?: base.divider,
            mapTheme = base.mapTheme.copy(
                background = branding?.background ?: base.mapTheme.background,
                rowLabel = branding?.text ?: base.mapTheme.rowLabel,
                text = branding?.text ?: base.mapTheme.text,
                selection = branding?.accent ?: base.mapTheme.selection,
            ),
            cornerRadius = branding?.radius
                ?.takeIf { it.isFinite() && it >= 0 }
                ?.dp ?: base.cornerRadius,
        )
    } else {
        base
    }
    val colors = if (dark) {
        darkColorScheme(
            primary = resolved.accent,
            onPrimary = resolved.onAccent,
            surface = resolved.surface,
            onSurface = resolved.onSurface,
            background = resolved.background,
            onBackground = resolved.onSurface,
            error = resolved.error,
        )
    } else {
        lightColorScheme(
            primary = resolved.accent,
            onPrimary = resolved.onAccent,
            surface = resolved.surface,
            onSurface = resolved.onSurface,
            background = resolved.background,
            onBackground = resolved.onSurface,
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
