package io.seatlayer.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

public const val SEATLAYER_PROTOCOL_MIN: Int = 1
public const val SEATLAYER_PROTOCOL_MAX: Int = 1

public data class ProtocolRange(
    val min: Int,
    val max: Int,
) {
    init {
        require(min <= max) { "protocol min must not exceed max" }
    }

    internal fun toJson(): JsonObject = JsonObject(
        mapOf("min" to JsonPrimitive(min), "max" to JsonPrimitive(max)),
    )

    public companion object {
        public val Native: ProtocolRange =
            ProtocolRange(SEATLAYER_PROTOCOL_MIN, SEATLAYER_PROTOCOL_MAX)

        internal fun decode(value: JsonObject?): ProtocolRange? {
            if (value == null) return null
            val min = value.int("min") ?: return null
            val max = value.int("max") ?: return null
            return runCatching { ProtocolRange(min, max) }.getOrNull()
        }
    }
}

internal fun negotiate(
    host: ProtocolRange = ProtocolRange.Native,
    web: ProtocolRange,
): Int? {
    val agreed = minOf(host.max, web.max)
    return agreed.takeIf { it >= host.min && it >= web.min }
}
