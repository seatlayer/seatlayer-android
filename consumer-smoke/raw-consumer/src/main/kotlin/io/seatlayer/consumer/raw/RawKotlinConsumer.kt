package io.seatlayer.consumer.raw

import android.content.Context
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerEvent
import io.seatlayer.android.SeatLayerView
import kotlinx.serialization.json.JsonElement

public object RawKotlinConsumer {
    public fun createView(context: Context): SeatLayerView = SeatLayerView(context)

    public fun configuration(): SeatLayerConfiguration =
        SeatLayerConfiguration(event = "ev_external_consumer")

    public fun checkoutPayload(event: SeatLayerEvent.Checkout): JsonElement? = event.payload
}
