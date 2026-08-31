package io.seatlayer.android

import kotlinx.serialization.json.JsonElement

internal fun interface SeatLayerPickerCommandTransport {
    suspend fun command(name: String, payload: JsonElement?): JsonElement?
}

internal class BridgePickerCommandTransport(
    private val client: BridgeClient,
) : SeatLayerPickerCommandTransport {
    override suspend fun command(name: String, payload: JsonElement?): JsonElement? =
        client.command(name, payload)
}
