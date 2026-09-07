package io.seatlayer.android

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Bridge error codes this SDK knows by name.
 *
 * **The set is open.** The runtime promotes an inventory conflict's own reason
 * (`sold_out`, `not_enough_together`, `event_closed`, …) into `code`, because
 * buyer UI has to branch on the specific reason rather than on the bucket. Read
 * [SeatLayerException.code] as a string and treat an unknown value as a plain
 * failure; never exhaust over these constants.
 */
public object SeatLayerPickerErrorCodes {
    public const val UNSUPPORTED_COMMAND: String = "unsupported_command"
    public const val BAD_PAYLOAD: String = "bad_payload"
    public const val NOT_READY: String = "not_ready"
    public const val DESTROYED: String = "destroyed"
    public const val READ_ONLY: String = "read_only"
    public const val HOLD_ALREADY_ACTIVE: String = "hold_already_active"
    public const val HOLD_UNAVAILABLE: String = "hold_unavailable"

    /**
     * The live hold does not cover the current selection.
     *
     * A runtime that treats a hold as checkout's refuses to hand off a
     * selection it does not hold. It is a state with a way out, not a failure:
     * the hold is replaced and the checkout asked for again.
     */
    public const val HOLD_SELECTION_MISMATCH: String = "hold_selection_mismatch"
    public const val HANDOFF_NOT_OWNED: String = "handoff_not_owned"
    public const val HOLD_OWNED_BY_HOST: String = "hold_owned_by_host"
    public const val HOLD_STATE_INCOMPLETE: String = "hold_state_incomplete"
    public const val CART_LINE_RELEASE_FAILED: String = "cart_line_release_failed"
    public const val TABLE_QUANTITY_REJECTED: String = "table_quantity_rejected"
}

/**
 * One rectangle of native chrome the map must not answer taps through.
 *
 * Coordinates are in the map view's own pixels, origin top-left.
 */
public data class SeatLayerPickerBlockedRegion(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    init {
        require(listOf(x, y, width, height).all(Double::isFinite)) {
            "a blocked region must be finite"
        }
        require(width >= 0 && height >= 0) {
            "a blocked region must not have negative extent"
        }
    }

    internal fun toJson(): JsonObject = jsonObject(
        "x" to JsonPrimitive(x),
        "y" to JsonPrimitive(y),
        "w" to JsonPrimitive(width),
        "h" to JsonPrimitive(height),
    )
}

/**
 * The renderer's own record of where the buyer has dragged the map.
 *
 * Opaque by design: it is read when a card opens and handed straight back on
 * every re-frame, so the map never argues with a finger.
 */
public class SeatLayerPickerSeatGestures internal constructor(
    internal val raw: JsonElement,
) {
    override fun equals(other: Any?): Boolean =
        other is SeatLayerPickerSeatGestures && other.raw == raw

    override fun hashCode(): Int = raw.hashCode()

    override fun toString(): String = "SeatLayerPickerSeatGestures"
}

/** How far the map moved to clear a seat, and the gestures to hand back. */
public data class SeatLayerPickerSeatFrame(
    val dy: Double,
    val gestures: SeatLayerPickerSeatGestures?,
)

/** One step of the accessible-section tour. */
public data class SeatLayerPickerAccessibleStep(
    val id: String,
    val label: String,
    val free: Int,
    val index: Int,
    val total: Int,
)

internal fun decodeSeatLayerPickerSeatFrame(value: JsonElement?): SeatLayerPickerSeatFrame? {
    val root = value as? JsonObject ?: return null
    val dy = finitePickerDouble(root["dy"]) ?: return null
    return SeatLayerPickerSeatFrame(
        dy = dy,
        gestures = root["gestures"]?.let(::SeatLayerPickerSeatGestures),
    )
}

internal fun decodeSeatLayerPickerAccessibleStep(
    value: JsonElement?,
): SeatLayerPickerAccessibleStep? {
    val root = (value as? JsonObject)?.objectValue("step") ?: value as? JsonObject ?: return null
    val id = root.string("id")?.takeIf(String::isNotEmpty) ?: return null
    val index = exactPickerInteger(root["index"]) ?: return null
    val total = exactPickerInteger(root["total"]) ?: return null
    return SeatLayerPickerAccessibleStep(
        id = id,
        label = root.string("label") ?: id,
        free = (exactPickerInteger(root["free"]) ?: 0).coerceAtLeast(0),
        index = index,
        total = total,
    )
}
