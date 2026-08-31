package io.seatlayer.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

public const val SEATLAYER_ANDROID_VERSION: String = "0.3.4"
public const val SEATLAYER_HOSTED_WEB_VERSION: String = "0.71.5"
public const val SEATLAYER_LEGACY_FIXTURE_WEB_VERSION: String = "0.59.0"
@Deprecated(
    message = "Production uses the hosted runtime; use SEATLAYER_HOSTED_WEB_VERSION.",
    replaceWith = ReplaceWith("SEATLAYER_HOSTED_WEB_VERSION"),
)
public const val SEATLAYER_BUNDLED_WEB_VERSION: String = SEATLAYER_HOSTED_WEB_VERSION
public const val SEATLAYER_MOBILE_ORIGIN: String = "https://cdn.seatlayer.io"
public const val SEATLAYER_MOBILE_PAGE_URL: String = "$SEATLAYER_MOBILE_ORIGIN/seatlayer-js@$SEATLAYER_HOSTED_WEB_VERSION/mobile.html"

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
    /** One-shot private buyer bearer. Prefer [buyerAccessTokenProvider]. */
    val buyerAccessToken: BuyerAccessToken? = null,
    /** Renews private buyer access in memory without rebuilding the view. */
    val buyerAccessTokenProvider: BuyerAccessTokenProvider? = null,
    val selectedObjects: List<String>? = null,
    /** `null` means every otherwise eligible object remains selectable. */
    val selectableObjects: List<String>? = null,
    val numberOfPlacesToSelect: Int? = null,
    val selectionValidators: List<SelectionValidator>? = null,
    val commandTimeoutMillis: Long = 15_000,
    val handshakeTimeoutMillis: Long = 30_000,
    val hostInfo: Map<String, String> = emptyMap(),
) {
    init {
        require(event.isNotBlank()) { "event is required" }
        require(maxSelection == null || maxSelection > 0) {
            "maxSelection must be positive"
        }
        require(numberOfPlacesToSelect == null || numberOfPlacesToSelect > 0) {
            "numberOfPlacesToSelect must be positive"
        }
        require(buyerAccessToken == null || buyerAccessToken.token.isNotBlank()) {
            "buyerAccessToken.token must not be blank"
        }
        require(buyerAccessToken?.expiresAt?.isFinite() != false) {
            "buyerAccessToken.expiresAt must be finite"
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
                "buyerAccessToken" to buyerAccessToken?.toJson(),
                "nativeAccessProvider" to buyerAccessTokenProvider?.let { JsonPrimitive(true) },
                "selectedObjects" to selectedObjects?.let(::jsonStrings),
                "selectableObjects" to selectableObjects?.let(::jsonStrings),
                "numberOfPlacesToSelect" to jsonNumber(numberOfPlacesToSelect),
                "selectionValidators" to selectionValidators?.let {
                    kotlinx.serialization.json.JsonArray(it.map(SelectionValidator::toJson))
                },
            ),
        )
    }

    internal val usesPrivateAccess: Boolean
        get() = buyerAccessToken != null || buyerAccessTokenProvider != null

    internal val usesSelectionPolicy: Boolean
        get() = selectedObjects != null || selectableObjects != null ||
            numberOfPlacesToSelect != null || selectionValidators != null
}

private fun BuyerAccessToken.toJson(): JsonObject = jsonObject(
    "token" to JsonPrimitive(token),
    "expiresAt" to jsonNumber(expiresAt),
)

private fun SelectionValidator.toJson(): JsonObject = when (this) {
    is SelectionValidator.MinimumSelectedPlaces -> jsonObject(
        "type" to JsonPrimitive("minimumSelectedPlaces"),
        "minimum" to JsonPrimitive(minimum),
    )
    SelectionValidator.ConsecutiveSeats -> jsonObject(
        "type" to JsonPrimitive("consecutiveSeats"),
    )
    SelectionValidator.NoOrphanSeats -> jsonObject(
        "type" to JsonPrimitive("noOrphanSeats"),
    )
}
