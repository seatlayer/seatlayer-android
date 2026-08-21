package io.seatlayer.android

import java.util.Date
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@JvmInline
public value class EventMode(public val raw: String) {
    public companion object {
        public val Live: EventMode = EventMode("live")
        public val Test: EventMode = EventMode("test")
    }
}

@JvmInline
public value class TransportName(public val raw: String) {
    public companion object {
        public val Android: TransportName = TransportName("android")
    }
}

@JvmInline
public value class SeatLayerViewMode(public val raw: String) {
    public companion object {
        public val Flat: SeatLayerViewMode = SeatLayerViewMode("flat")
        public val Isometric: SeatLayerViewMode = SeatLayerViewMode("iso")
        public val Perspective: SeatLayerViewMode = SeatLayerViewMode("perspective")
    }
}

@JvmInline
public value class BuyerAccessRefreshReason(public val raw: String) {
    public companion object {
        public val Initial: BuyerAccessRefreshReason = BuyerAccessRefreshReason("initial")
        public val Expiring: BuyerAccessRefreshReason = BuyerAccessRefreshReason("expiring")
        public val Expired: BuyerAccessRefreshReason = BuyerAccessRefreshReason("expired")
        public val Unauthorized: BuyerAccessRefreshReason = BuyerAccessRefreshReason("unauthorized")
        public val Reconnect: BuyerAccessRefreshReason = BuyerAccessRefreshReason("reconnect")
        public val Manual: BuyerAccessRefreshReason = BuyerAccessRefreshReason("manual")
    }
}

@JvmInline
public value class BuyerAccessUnavailableReason(public val raw: String) {
    public companion object {
        public val Revoked: BuyerAccessUnavailableReason = BuyerAccessUnavailableReason("revoked")
        public val Paused: BuyerAccessUnavailableReason = BuyerAccessUnavailableReason("paused")
        public val Invalid: BuyerAccessUnavailableReason = BuyerAccessUnavailableReason("invalid")
        public val OriginMismatch: BuyerAccessUnavailableReason = BuyerAccessUnavailableReason("origin_mismatch")
        public val EventMismatch: BuyerAccessUnavailableReason = BuyerAccessUnavailableReason("event_mismatch")
        public val GroupMismatch: BuyerAccessUnavailableReason = BuyerAccessUnavailableReason("group_mismatch")
        public val ModeMismatch: BuyerAccessUnavailableReason = BuyerAccessUnavailableReason("mode_mismatch")
        public val ChannelDenied: BuyerAccessUnavailableReason = BuyerAccessUnavailableReason("channel_denied")
        public val InvalidScope: BuyerAccessUnavailableReason = BuyerAccessUnavailableReason("invalid_scope")
        public val ProviderFailed: BuyerAccessUnavailableReason = BuyerAccessUnavailableReason("provider_failed")
        public val NoToken: BuyerAccessUnavailableReason = BuyerAccessUnavailableReason("no_token")
    }
}

@JvmInline
public value class SelectionViolation(public val raw: String)

@JvmInline
public value class SelectedObjectUnavailableReason(public val raw: String)

public data class BuyerAccessToken(
    val token: String,
    /** Epoch milliseconds. Omit to refresh reactively after server rejection. */
    val expiresAt: Double? = null,
)

public data class BuyerAccessRequestContext(
    val reason: BuyerAccessRefreshReason,
)

public fun interface BuyerAccessTokenProvider {
    public suspend fun provide(context: BuyerAccessRequestContext): BuyerAccessToken
}

public sealed interface SelectionValidator {
    public data class MinimumSelectedPlaces(val minimum: Int) : SelectionValidator {
        init { require(minimum > 0) { "minimum must be positive" } }
    }
    public data object ConsecutiveSeats : SelectionValidator
    public data object NoOrphanSeats : SelectionValidator
}

public data class CategoryTier(
    val id: String,
    val name: String,
    val price: Double,
)

public data class SelectedSeat(
    val id: String,
    val label: String,
    val displayLabel: String?,
    val categoryKey: String?,
    val price: Double?,
    val tiers: List<CategoryTier>,
    val tierId: String?,
) {
    public val buyerFacingLabel: String get() = displayLabel ?: label
}

public data class SelectionValidity(
    val isValid: Boolean,
    val count: Int,
    val required: Int,
    val remaining: Int,
    val seats: List<SelectedSeat>,
    val violations: List<SelectionViolation>,
)

public data class BuyerAccessExpiredEvent(
    val reason: BuyerAccessRefreshReason,
    val code: String?,
    val refreshed: Boolean,
)

public data class BuyerAccessUnavailableEvent(
    val reason: BuyerAccessUnavailableReason,
    val code: String?,
    val status: Int?,
    val retryable: Boolean,
)

public data class SelectedObjectUnavailableEvent(
    val labels: List<String>,
    val reason: SelectedObjectUnavailableReason,
    val code: String?,
)

public data class HoldLineItem(
    val label: String,
    val objectId: String?,
    val objectType: String?,
    val categoryKey: String?,
    val tierId: String?,
    val unitPrice: Double?,
    val currency: String?,
    val quantity: Int?,
)

public data class HoldResult(
    val holdId: String,
    val expiresAtMillis: Long,
    val seats: List<SelectedSeat>,
    val items: List<HoldLineItem>,
) {
    public val expiry: Date get() = Date(expiresAtMillis)
    public val timeRemainingMillis: Long
        get() = (expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
}

public data class BestAvailableResult(
    val holdId: String,
    val expiresAtMillis: Long,
    val labels: List<String>,
    val seats: List<SelectedSeat>,
    val items: List<HoldLineItem>,
)

public data class GeneralAdmissionArea(
    val id: String,
    val label: String?,
    val capacity: Int?,
    val available: Int?,
    val categoryKey: String?,
    val price: Double?,
    val currency: String?,
    val tiers: List<CategoryTier>,
)

public data class FloorInfo(
    val id: String,
    val name: String,
)

public data class ReadyInfo(
    val protocolRevision: Int,
    val mode: EventMode,
    val transport: TransportName,
    val eventKey: String?,
)

public data class BundleInfo(
    val bundle: String,
    val protocolRange: ProtocolRange,
    val capabilities: List<String>,
    val events: List<String>,
    val commands: List<String>,
) {
    public fun supportsCommand(command: String): Boolean = command in commands
    public fun supportsCapability(capability: String): Boolean = capability in capabilities
}

public sealed interface SeatLayerEvent {
    public data class SelectionChanged(val seats: List<SelectedSeat>) : SeatLayerEvent
    public data class SelectionValidityChanged(val validity: SelectionValidity) : SeatLayerEvent
    public data class SelectionValid(val seats: List<SelectedSeat>) : SeatLayerEvent
    public data class SelectionInvalid(val validity: SelectionValidity) : SeatLayerEvent
    public data class SelectionLimitReached(val maximum: Int) : SeatLayerEvent
    public data class BuyerAccessExpired(val event: BuyerAccessExpiredEvent) : SeatLayerEvent
    public data class BuyerAccessUnavailable(val event: BuyerAccessUnavailableEvent) : SeatLayerEvent
    public data class SelectedObjectsUnavailable(
        val event: SelectedObjectUnavailableEvent,
    ) : SeatLayerEvent
    public data class HoldChanged(val hold: HoldResult) : SeatLayerEvent
    public data class HoldRestored(val hold: HoldResult) : SeatLayerEvent
    public data object HoldExpired : SeatLayerEvent
    public data class GeneralAdmissionClicked(val area: GeneralAdmissionArea) : SeatLayerEvent
    public data class Hint(val message: String?) : SeatLayerEvent
    public data class Error(val error: SeatLayerException.Bridge) : SeatLayerEvent
    public data class SeatHovered(val details: JsonElement?) : SeatLayerEvent
    public data class DeckTapped(val floorId: String) : SeatLayerEvent
    public data class Checkout(val payload: JsonElement?) : SeatLayerEvent
    public data class Unknown(val name: String, val payload: JsonElement?) : SeatLayerEvent
}

internal fun decodeReady(payload: JsonElement?): ReadyInfo {
    val root = payload as? JsonObject ?: JsonObject(emptyMap())
    return ReadyInfo(
        protocolRevision = root.int("protocol") ?: SEATLAYER_PROTOCOL_MIN,
        mode = EventMode(root.string("mode") ?: EventMode.Live.raw),
        transport = TransportName(root.string("transport") ?: ""),
        eventKey = root.objectValue("chart")?.string("event"),
    )
}

internal fun decodeBundle(payload: JsonElement?): BundleInfo {
    val root = payload as? JsonObject ?: JsonObject(emptyMap())
    return BundleInfo(
        bundle = root.string("bundle") ?: "unknown",
        protocolRange = ProtocolRange.decode(root.objectValue("protocol"))
            ?: ProtocolRange.Native,
        capabilities = root.stringList("capabilities"),
        events = root.stringList("events"),
        commands = root.stringList("commands"),
    )
}

internal fun decodeSelectedSeat(value: JsonElement): SelectedSeat? {
    val root = value as? JsonObject ?: return null
    val id = root.string("id") ?: return null
    val label = root.string("label") ?: return null
    return SelectedSeat(
        id = id,
        label = label,
        displayLabel = root.string("displayLabel"),
        categoryKey = root.string("categoryKey"),
        price = root.double("price"),
        tiers = root.array("tiers").orEmpty().mapNotNull(::decodeTier),
        tierId = root.string("tierId"),
    )
}

internal fun decodeSelectionValidity(value: JsonElement?): SelectionValidity? {
    val root = value as? JsonObject ?: return null
    return SelectionValidity(
        isValid = root.boolean("isValid") ?: return null,
        count = root.int("count") ?: return null,
        required = root.int("required") ?: return null,
        remaining = root.int("remaining") ?: return null,
        seats = root.array("seats").orEmpty().mapNotNull(::decodeSelectedSeat),
        violations = root.stringList("violations").map(::SelectionViolation),
    )
}

internal fun decodeBuyerAccessExpired(value: JsonElement?): BuyerAccessExpiredEvent? {
    val root = value as? JsonObject ?: return null
    return BuyerAccessExpiredEvent(
        reason = BuyerAccessRefreshReason(root.string("reason") ?: return null),
        code = root.string("code"),
        refreshed = root.boolean("refreshed") ?: return null,
    )
}

internal fun decodeBuyerAccessUnavailable(value: JsonElement?): BuyerAccessUnavailableEvent? {
    val root = value as? JsonObject ?: return null
    return BuyerAccessUnavailableEvent(
        reason = BuyerAccessUnavailableReason(root.string("reason") ?: return null),
        code = root.string("code"),
        status = root.int("status"),
        retryable = root.boolean("retryable") ?: return null,
    )
}

internal fun decodeSelectedObjectsUnavailable(value: JsonElement?): SelectedObjectUnavailableEvent? {
    val root = value as? JsonObject ?: return null
    return SelectedObjectUnavailableEvent(
        labels = root.stringList("labels"),
        reason = SelectedObjectUnavailableReason(root.string("reason") ?: return null),
        code = root.string("code"),
    )
}

internal fun decodeHold(value: JsonElement?): HoldResult? {
    val root = value as? JsonObject ?: return null
    val holdId = root.string("holdId") ?: return null
    val expiresAt = root.long("expiresAt")
        ?: root.double("expiresAt")?.toLong()
        ?: return null
    return HoldResult(
        holdId = holdId,
        expiresAtMillis = expiresAt,
        seats = root.array("seats").orEmpty().mapNotNull(::decodeSelectedSeat),
        items = root.array("items").orEmpty().mapNotNull(::decodeHoldItem),
    )
}

internal fun decodeBestAvailable(value: JsonElement?): BestAvailableResult? {
    val root = value as? JsonObject ?: return null
    val holdId = root.string("holdId") ?: return null
    val expiresAt = root.long("expiresAt")
        ?: root.double("expiresAt")?.toLong()
        ?: return null
    return BestAvailableResult(
        holdId = holdId,
        expiresAtMillis = expiresAt,
        labels = root.stringList("labels"),
        seats = root.array("seats").orEmpty().mapNotNull(::decodeSelectedSeat),
        items = root.array("items").orEmpty().mapNotNull(::decodeHoldItem),
    )
}

internal fun decodeGeneralAdmissionArea(value: JsonElement?): GeneralAdmissionArea? {
    val root = value as? JsonObject ?: return null
    return GeneralAdmissionArea(
        id = root.string("id") ?: return null,
        label = root.string("label"),
        capacity = root.int("capacity"),
        available = root.int("available"),
        categoryKey = root.string("categoryKey"),
        price = root.double("price"),
        currency = root.string("currency"),
        tiers = root.array("tiers").orEmpty().mapNotNull(::decodeTier),
    )
}

internal fun decodeFloor(value: JsonElement): FloorInfo? {
    val root = value as? JsonObject ?: return null
    return FloorInfo(
        id = root.string("id") ?: return null,
        name = root.string("name") ?: return null,
    )
}

private fun decodeTier(value: JsonElement): CategoryTier? {
    val root = value as? JsonObject ?: return null
    return CategoryTier(
        id = root.string("id") ?: return null,
        name = root.string("name") ?: return null,
        price = root.double("price") ?: return null,
    )
}

private fun decodeHoldItem(value: JsonElement): HoldLineItem? {
    val root = value as? JsonObject ?: return null
    return HoldLineItem(
        label = root.string("label") ?: return null,
        objectId = root.string("objectId"),
        objectType = root.string("objectType"),
        categoryKey = root.string("categoryKey"),
        tierId = root.string("tierId"),
        unitPrice = root.double("unitPrice"),
        currency = root.string("currency"),
        quantity = root.int("quantity"),
    )
}

private fun JsonObject.stringList(name: String): List<String> =
    (get(name) as? JsonArray).orEmpty().mapNotNull {
        (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content
    }
