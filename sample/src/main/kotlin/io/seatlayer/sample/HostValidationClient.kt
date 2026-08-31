package io.seatlayer.sample

import io.seatlayer.android.BuyerAccessToken
import io.seatlayer.android.BuyerAccessTokenProvider
import io.seatlayer.android.SEATLAYER_MOBILE_ORIGIN
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class HostValidationEvent(
    val id: String,
    val slug: String?,
    val title: String,
    val isSeatEvent: Boolean,
    val seatEngine: String?,
    val seatEventKey: String?,
    val currency: String?,
    val city: String?,
    val startDate: String?,
    val startTime: String?,
    val venue: String?,
    val venueAddress: String?,
    val imageUrl: String?,
    val isFree: Boolean,
    val minimumPrice: Double?,
)

internal data class HostSeatLayerAccess(
    val apiBase: String,
    val provider: BuyerAccessTokenProvider,
)

/**
 * Owner-validation adapter for the existing mobile pilot GraphQL contract.
 *
 * Its endpoint/client key are runtime sample inputs. Neither is passed to the
 * SDK; only a short-lived renewable buyer token remains in memory.
 */
internal class HostValidationClient(
    endpointValue: String,
    apiKeyValue: String,
) {
    private val endpoint: URL = validatedHttpsUrl(
        endpointValue,
        "The host GraphQL URL must be a valid HTTPS URL.",
    )
    private val apiKey: String = apiKeyValue.trim().also {
        if (it.isEmpty()) throw HostValidationException("The host client key is missing.")
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchEvents(): List<HostValidationEvent> {
        val data = request(
            query = EVENT_LIST_QUERY,
            variables = jsonObject(
                "filterType" to JsonPrimitive("UPCOMING"),
                "page" to JsonPrimitive(1),
                "limit" to JsonPrimitive(50),
                "upcomingFilterType" to JsonPrimitive("ALL"),
            ),
        )
        val events = data.objectOrNull("getUserEventList")
            ?.arrayOrEmpty("events")
            .orEmpty()
            .mapNotNull { value -> value.asObjectOrNull()?.let(::decodeEvent) }
            .filter(HostValidationEvent::isSeatEvent)
        val today = requireNotNull(API_DATE_FORMAT.get()).format(Date())
        val future = events.filter { event ->
            event.startDate?.let { it >= today } ?: true
        }
        return (if (future.size >= 2) future else events).take(3)
    }

    suspend fun fetchEvent(eventId: String): HostValidationEvent {
        val data = request(
            query = EVENT_DETAIL_QUERY,
            variables = jsonObject("eventId" to JsonPrimitive(eventId)),
        )
        val event = data.objectOrNull("getUserEvent")
            ?.objectOrNull("eventDetail")
            ?.let(::decodeEvent)
            ?: throw HostValidationException("This event is no longer available.")
        if (event.seatEngine != "SEATLAYER" || event.seatEventKey.isNullOrBlank()) {
            throw HostValidationException("This event has no SeatLayer map configured.")
        }
        return event
    }

    suspend fun createSeatLayerAccess(eventId: String): HostSeatLayerAccess {
        val prefetched = mintBuyerAccess(eventId)
        val source = BuyerAccessSource(this, eventId, prefetched)
        return HostSeatLayerAccess(
            apiBase = prefetched.apiBase,
            provider = BuyerAccessTokenProvider { source.nextToken() },
        )
    }

    private suspend fun mintBuyerAccess(eventId: String): BuyerAccess {
        val data = request(
            query = BUYER_ACCESS_MUTATION,
            variables = jsonObject("eventId" to JsonPrimitive(eventId)),
            origin = SEATLAYER_MOBILE_ORIGIN,
        )
        val payload = data.objectOrNull("createSeatLayerBuyerAccessSession")
            ?: throw HostValidationException("The seat map could not be authorised.")
        val token = payload.stringOrNull("token")?.trim()
        val rawApiBase = payload.stringOrNull("apiBase")?.trim()
        val expiresAt = payload.stringOrNull("expiresAt")?.let(::parseEpochMilliseconds)
        if (token.isNullOrEmpty() || rawApiBase.isNullOrEmpty() || expiresAt == null) {
            throw HostValidationException("The seat map could not be authorised.")
        }
        val apiBase = validatedHttpsUrl(
            rawApiBase,
            "The host returned an invalid SeatLayer API URL.",
        ).toString().removeSuffix("/")
        return BuyerAccess(token, expiresAt, apiBase)
    }

    private suspend fun request(
        query: String,
        variables: JsonObject,
        origin: String? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", apiKey)
            origin?.let { connection.setRequestProperty("Origin", it) }

            val requestBody = jsonObject(
                "query" to JsonPrimitive(query),
                "variables" to variables,
            ).toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { stream -> stream.write(requestBody) }

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val envelope = runCatching { json.parseToJsonElement(raw) as? JsonObject }
                .getOrNull()
                ?: throw HostValidationException("The host returned an invalid response.")
            val graphQlMessage = envelope.arrayOrEmpty("errors")
                .firstOrNull()
                ?.asObjectOrNull()
                ?.stringOrNull("message")
            if (!graphQlMessage.isNullOrBlank()) {
                throw HostValidationException(graphQlMessage)
            }
            if (status !in 200..299) {
                throw HostValidationException("The host request failed ($status).")
            }
            envelope.objectOrNull("data")
                ?: throw HostValidationException("The requested data is unavailable.")
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeEvent(value: JsonObject): HostValidationEvent? {
        val id = value.stringOrNull("id") ?: return null
        val title = value.stringOrNull("title") ?: return null
        val venue = value.objectOrNull("venueDetail")
        val tickets = value.arrayOrEmpty("eventTickets")
            .mapNotNull(JsonElement::asObjectOrNull)
        val prices = tickets.mapNotNull { item -> item.doubleOrNull("ticketPrice") }
        return HostValidationEvent(
            id = id,
            slug = value.stringOrNull("slug"),
            title = title,
            isSeatEvent = value.booleanOrNull("isSeatEvent") ?: false,
            seatEngine = value.stringOrNull("seatEngine"),
            seatEventKey = value.stringOrNull("seatEventKey"),
            currency = value.stringOrNull("currency"),
            city = value.stringOrNull("cityName"),
            startDate = value.stringOrNull("eventStartDate"),
            startTime = value.stringOrNull("eventStartTime"),
            venue = venue?.stringOrNull("venueName")
                ?: venue?.stringOrNull("locationName"),
            venueAddress = venue?.stringOrNull("venueAddress"),
            imageUrl = value.stringOrNull("eventImageUrl"),
            isFree = tickets.any { it.stringOrNull("ticketType") == "FREE" },
            minimumPrice = prices.minOrNull(),
        )
    }

    private fun parseEpochMilliseconds(value: String): Double? {
        for (format in requireNotNull(ISO_DATE_FORMATS.get())) {
            val parsed = runCatching { format.parse(value) }.getOrNull()
            if (parsed != null) return parsed.time.toDouble()
        }
        return null
    }

    private data class BuyerAccess(
        val token: String,
        val expiresAt: Double,
        val apiBase: String,
    )

    private class BuyerAccessSource(
        private val client: HostValidationClient,
        private val eventId: String,
        prefetched: BuyerAccess,
    ) {
        private val mutex = Mutex()
        private var prefetched: BuyerAccess? = prefetched

        suspend fun nextToken(): BuyerAccessToken = mutex.withLock {
            val candidate = prefetched.also { prefetched = null }
            val access = candidate?.takeIf {
                it.expiresAt - System.currentTimeMillis() > PREFETCH_FRESHNESS_MILLIS
            }
                ?: client.mintBuyerAccess(eventId)
            BuyerAccessToken(access.token, access.expiresAt)
        }

        private companion object {
            const val PREFETCH_FRESHNESS_MILLIS: Double = 30_000.0
        }
    }

    private companion object {
        val API_DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
        }

        val ISO_DATE_FORMATS = object : ThreadLocal<List<SimpleDateFormat>>() {
            override fun initialValue(): List<SimpleDateFormat> = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
            ).map { pattern ->
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
            }
        }

        const val EVENT_LIST_QUERY = """
            query getUserEventList(
              ${'$'}filterType: FilterType!
              ${'$'}page: Int
              ${'$'}limit: Int
              ${'$'}upcomingFilterType: UpcomingFilterType!
            ) {
              getUserEventList(
                filterType: ${'$'}filterType
                page: ${'$'}page
                limit: ${'$'}limit
                upcomingFilterType: ${'$'}upcomingFilterType
              ) {
                events {
                  id slug title isSeatEvent cityName eventStartDate eventStartTime
                  eventImageUrl
                  venueDetail { venueName venueAddress locationName }
                  eventTickets { ticketPrice ticketType title }
                }
              }
            }
        """

        const val EVENT_DETAIL_QUERY = """
            query getUserEvent(${'$'}eventId: String!) {
              getUserEvent(eventId: ${'$'}eventId) {
                eventDetail {
                  id slug title isSeatEvent seatEngine seatEventKey currency cityName
                  eventStartDate eventStartTime eventImageUrl
                  venueDetail { venueName venueAddress locationName }
                  eventTickets { ticketPrice ticketType title remainingTicket }
                }
              }
            }
        """

        const val BUYER_ACCESS_MUTATION = """
            mutation createSeatLayerBuyerAccessSession(${'$'}eventId: String!) {
              createSeatLayerBuyerAccessSession(eventId: ${'$'}eventId) {
                token expiresAt apiBase
              }
            }
        """
    }
}

internal class HostValidationException(message: String) : Exception(message)

private fun validatedHttpsUrl(value: String, failure: String): URL {
    val url = runCatching { URL(value.trim()) }
        .getOrElse { throw HostValidationException(failure) }
    if (
        url.protocol != "https" ||
        url.host.isBlank() ||
        url.userInfo != null ||
        url.query != null ||
        url.ref != null
    ) {
        throw HostValidationException(failure)
    }
    return url
}

private fun jsonObject(vararg values: Pair<String, JsonElement>): JsonObject =
    JsonObject(values.toMap())

private fun JsonObject.objectOrNull(key: String): JsonObject? =
    this[key]?.asObjectOrNull()

private fun JsonObject.arrayOrEmpty(key: String): JsonArray =
    (this[key] as? JsonArray) ?: JsonArray(emptyList())

private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.doubleOrNull(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

private fun JsonObject.booleanOrNull(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull
