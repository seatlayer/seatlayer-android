package io.seatlayer.consumer.compose

import android.content.Context
import androidx.compose.runtime.Composable
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerPickerMapView
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.compose.SeatLayerPicker
import io.seatlayer.android.compose.SeatLayerPickerHeader
import io.seatlayer.android.compose.SeatLayerPickerLifecycle
import io.seatlayer.android.compose.SeatLayerPickerMap
import io.seatlayer.android.compose.SeatLayerPickerScope
import io.seatlayer.android.compose.SeatLayerPickerView
import io.seatlayer.android.compose.SeatLayerPriceLegend

public object ComposeConsumer {
    @Composable
    public fun Ready(configuration: SeatLayerConfiguration) {
        SeatLayerPicker(configuration = configuration, onClose = {})
    }

    @Composable
    public fun Custom(configuration: SeatLayerConfiguration) {
        SeatLayerPickerScope(configuration = configuration) {
            SeatLayerPickerLifecycle()
            SeatLayerPickerHeader(onClose = {})
            SeatLayerPriceLegend()
            SeatLayerPickerMap()
        }
    }

    public fun readyView(context: Context): SeatLayerPickerView = SeatLayerPickerView(context)

    public suspend fun bindCustomView(
        context: Context,
        configuration: SeatLayerConfiguration,
    ): ReadyInfo {
        val holder = SeatLayerPickerStateHolder(configuration)
        return SeatLayerPickerMapView(context).bind(holder)
    }
}
