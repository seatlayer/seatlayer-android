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
