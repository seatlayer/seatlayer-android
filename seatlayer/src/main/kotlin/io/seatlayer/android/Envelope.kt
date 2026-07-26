package io.seatlayer.android

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val ENVELOPE_MARKER = 1

internal data class Envelope(
    val kind: String,
    val type: String,
    val id: String? = null,
    val sequence: Int? = null,
    val payload: JsonElement? = null,
) {
    fun encode(): String {
        val fields = linkedMapOf<String, JsonElement>(
            "sl" to JsonPrimitive(ENVELOPE_MARKER),
            "k" to JsonPrimitive(kind),
            "t" to JsonPrimitive(type),
        )
        id?.let { fields["id"] = JsonPrimitive(it) }
        sequence?.let { fields["n"] = JsonPrimitive(it) }
        payload?.let { fields["p"] = it }
        return JsonObject(fields).toString()
    }

    companion object {
        fun decode(input: String): Envelope? {
            val root = runCatching { bridgeJson.parseToJsonElement(input) }.getOrNull()
            val objectValue = root as? JsonObject ?: return null
            if (objectValue.int("sl") != ENVELOPE_MARKER) return null
            val kind = objectValue.string("k")?.takeIf(String::isNotBlank) ?: return null
            val type = objectValue.string("t")?.takeIf(String::isNotBlank) ?: return null
            val idValue = objectValue["id"]
            if (idValue != null && idValue !is JsonPrimitive) return null
            val sequenceValue = objectValue["n"]
            if (sequenceValue != null && objectValue.int("n") == null) return null
            return Envelope(
                kind = kind,
                type = type,
                id = objectValue.string("id"),
                sequence = objectValue.int("n"),
                payload = objectValue["p"],
            )
        }
    }
}
