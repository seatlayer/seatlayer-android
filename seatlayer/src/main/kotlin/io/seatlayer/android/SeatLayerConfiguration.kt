package io.seatlayer.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

public const val SEATLAYER_ANDROID_VERSION: String = "0.1.3"
public const val SEATLAYER_BUNDLED_WEB_VERSION: String = "0.59.0"

public data class SeatLayerConfiguration(
    val event: String,
    val apiBase: String? = null,
    val publicKey: String? = null,
    val maxSelection: Int? = null,
    val locale: String? = null,
    val messages: Map<String, String>? = null,
    val currency: String? = null,
    val colorblindSafe: Boolean? = null,
    val initialView: SeatLayerViewMode? = null,
    val showsWebSeatTooltip: Boolean = false,
    val commandTimeoutMillis: Long = 15_000,
    val handshakeTimeoutMillis: Long = 30_000,
    val hostInfo: Map<String, String> = emptyMap(),
) {
    init {
        require(event.isNotBlank()) { "event is required" }
        require(maxSelection == null || maxSelection > 0) {
            "maxSelection must be positive"
        }
        require(commandTimeoutMillis > 0) { "commandTimeoutMillis must be positive" }
        require(handshakeTimeoutMillis > 0) { "handshakeTimeoutMillis must be positive" }
    }

    internal fun initPayload(): JsonObject {
        val host = linkedMapOf(
            "platform" to JsonPrimitive("android"),
            "sdk" to JsonPrimitive(SEATLAYER_ANDROID_VERSION),
        )
        hostInfo.forEach { (key, value) -> host[key] = JsonPrimitive(value) }

        return jsonObject(
            "protocol" to ProtocolRange.Native.toJson(),
            "host" to JsonObject(host),
            "chrome" to jsonObject(
                "seatTooltip" to JsonPrimitive(showsWebSeatTooltip),
            ),
            "config" to jsonObject(
                "event" to JsonPrimitive(event),
                "apiBase" to jsonString(apiBase),
                "publicKey" to jsonString(publicKey),
                "maxSelection" to jsonNumber(maxSelection),
                "locale" to jsonString(locale),
                "messages" to messages?.asJsonObject(),
                "currency" to jsonString(currency),
                "colorblindSafe" to jsonBoolean(colorblindSafe),
                "initialView" to jsonString(initialView?.raw),
            ),
        )
    }
}
