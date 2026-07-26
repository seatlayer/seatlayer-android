package io.seatlayer.android

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

public data class HoldConflict(
    val label: String?,
    val status: String?,
)

public data class BridgeErrorDetails(
    val code: String,
    val message: String,
    val retryable: Boolean?,
    val conflicts: List<HoldConflict>,
    val metadata: JsonElement?,
) {
    internal companion object {
        fun decode(payload: JsonElement?): BridgeErrorDetails {
            val root = payload as? JsonObject ?: JsonObject(emptyMap())
            val details = root.objectValue("details")
            val rawConflicts =
                (details?.get("conflicts") ?: root["conflicts"]) as? JsonArray
            val conflicts = rawConflicts.orEmpty().mapNotNull { item ->
                val conflict = item as? JsonObject ?: return@mapNotNull null
                val label = conflict.string("label")
                val status = conflict.string("status")
                if (label == null && status == null) null else HoldConflict(label, status)
            }
            return BridgeErrorDetails(
                code = root.string("code")
                    ?: details?.string("reason")
                    ?: "unknown",
                message = root.string("message")
                    ?: "SeatLayer reported an unknown error.",
                retryable = root.boolean("retryable"),
                conflicts = conflicts,
                metadata = root["meta"] ?: details,
            )
        }
    }
}

public sealed class SeatLayerException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    public abstract val code: String

    public class Bridge(public val details: BridgeErrorDetails) :
        SeatLayerException(details.message) {
        override val code: String = details.code
    }

    public class Timeout(public val command: String, public val timeoutMillis: Long) :
        SeatLayerException(
            "SeatLayer command \"$command\" did not reply within ${timeoutMillis}ms.",
        ) {
        override val code: String = "sl_timeout"
    }

    public class Transport(message: String, cause: Throwable? = null) :
        SeatLayerException(message, cause) {
        override val code: String = "sl_transport"
    }

    public class Incompatible(
        public val native: ProtocolRange,
        public val web: ProtocolRange,
        reason: String,
    ) : SeatLayerException(reason) {
        override val code: String = "sl_incompatible"
    }

    public class Destroyed :
        SeatLayerException("The SeatLayer controller was destroyed.") {
        override val code: String = "destroyed"
    }
}
