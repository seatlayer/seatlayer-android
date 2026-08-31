package io.seatlayer.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerPickerThemeMode
import io.seatlayer.android.compose.SeatLayerPicker
import io.seatlayer.android.compose.SeatLayerPickerBuilders
import io.seatlayer.android.compose.SeatLayerPickerPart
import io.seatlayer.android.compose.SeatLayerPickerPartStyle
import io.seatlayer.android.compose.SeatLayerPickerStyles
import io.seatlayer.android.compose.SeatLayerPickerTheme

/** Theme, per-part style, and whole-part builder customization proof. */
@Composable
public fun BrandedPickerExample(
    configuration: SeatLayerConfiguration,
    onCheckout: suspend (SeatLayerPickerCheckoutHandoff) -> Unit = {},
    onError: (SeatLayerException) -> Unit = {},
    onReady: (ReadyInfo) -> Unit = {},
    onSnapshot: (SeatLayerPickerSnapshot) -> Unit = {},
    onClose: () -> Unit,
) {
    val accent = Color(0xFF006C67)
    SeatLayerPicker(
        configuration = configuration,
        modifier = Modifier.fillMaxSize(),
        themeMode = SeatLayerPickerThemeMode.Light,
        theme = SeatLayerPickerTheme.light().copy(accent = accent),
        styles = SeatLayerPickerStyles(
            parts = mapOf(
                SeatLayerPickerPart.CheckoutBar to SeatLayerPickerPartStyle(
                    cornerRadius = 18.dp,
                    horizontalPadding = 16.dp,
                ),
            ),
        ),
        builders = SeatLayerPickerBuilders(
            header = { _, defaultHeader ->
                Box(Modifier.background(accent.copy(alpha = 0.08f)).padding(top = 2.dp)) {
                    defaultHeader()
                }
            },
        ),
        onCheckout = onCheckout,
        onError = onError,
        onReady = onReady,
        onSnapshot = onSnapshot,
        onClose = onClose,
    )
}
