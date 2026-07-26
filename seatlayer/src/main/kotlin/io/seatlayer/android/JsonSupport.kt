package io.seatlayer.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal val bridgeJson = Json {
    ignoreUnknownKeys = true
    isLenient = false
    explicitNulls = true
}

internal fun JsonObject.string(name: String): String? =
    (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.int(name: String): Int? =
    (get(name) as? JsonPrimitive)?.intOrNull

internal fun JsonObject.long(name: String): Long? =
    (get(name) as? JsonPrimitive)?.longOrNull

internal fun JsonObject.double(name: String): Double? =
    (get(name) as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.boolean(name: String): Boolean? =
    (get(name) as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.objectValue(name: String): JsonObject? = get(name) as? JsonObject

internal fun JsonObject.array(name: String): JsonArray? = get(name) as? JsonArray

internal fun jsonObject(vararg entries: Pair<String, JsonElement?>): JsonObject =
    JsonObject(entries.mapNotNull { (key, value) -> value?.let { key to it } }.toMap())

internal fun jsonString(value: String?): JsonElement? = value?.let(::JsonPrimitive)

internal fun jsonNumber(value: Number?): JsonElement? = value?.let(::JsonPrimitive)

internal fun jsonBoolean(value: Boolean?): JsonElement? = value?.let(::JsonPrimitive)

internal fun jsonStrings(values: List<String>): JsonArray =
    JsonArray(values.map(::JsonPrimitive))

internal fun Map<String, String>.asJsonObject(): JsonObject =
    JsonObject(mapValues { JsonPrimitive(it.value) })

internal fun JsonElement?.orJsonNull(): JsonElement = this ?: JsonNull

internal fun JsonElement.objectOrNull(): JsonObject? = this as? JsonObject
