package io.seatlayer.android.compose

import io.seatlayer.android.BuyerAccessRequestContext
import io.seatlayer.android.BuyerAccessRefreshReason
import io.seatlayer.android.BuyerAccessToken
import io.seatlayer.android.BuyerAccessTokenProvider
import io.seatlayer.android.SeatLayerConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL

/*
 * How the buyer's own images reach a native surface.
 *
 * A seat photograph is NOT a URL a view can load on its own: the runtime names
 * it as an event-scoped API path, and on a private event that path answers
 * nothing without the buyer's bearer. So the bytes are fetched here — the same
 * transport the web uses — and the bearer stays in a header that never reaches
 * an image URL, an image cache key, a crash report or a log line.
 */

/**
 * How the bytes for one event-scoped image are actually fetched.
 *
 * Injected so tests never open a socket, and so a host with its own HTTP stack
 * — a proxy, a pinned client, an offline cache — can supply one.
 */
internal fun interface SeatLayerAssetFetch {
    suspend fun fetch(url: String, headers: Map<String, String>): ByteArray
}

/**
 * Fetches the buyer-scoped images the native chrome draws itself.
 *
 * Every failure resolves to null. "No photograph" is an ordinary state of the
 * seat card, and a thrown error there would take down a card the buyer is in
 * the middle of answering.
 */
internal class SeatLayerBuyerAssetLoader(
    private val eventKey: String,
    apiBase: String? = null,
    token: BuyerAccessToken? = null,
    private val tokenProvider: BuyerAccessTokenProvider? = null,
    private val fetch: SeatLayerAssetFetch = SeatLayerAssetFetch(::seatLayerHttpAssetFetch),
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** The API origin a reference is resolved against. */
    private val apiBase: String = (apiBase ?: DEFAULT_API_BASE).trimEnd('/')

    private val lock = Mutex()
    private var token: BuyerAccessToken? = token

    /** Access-ordered, so the eviction below drops the least recently read. */
    private val bytes = object : LinkedHashMap<String, ByteArray>(0, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>) =
            size > CACHE_ENTRIES
    }

    /**
     * One request per reference however many cards ask for it.
     *
     * The work runs on the loader's OWN scope rather than on a caller's, so a
     * buyer who closes a card while its photograph is in flight cancels their
     * own wait and not the fetch two other cards are also waiting on.
     */
    private val inFlight = mutableMapOf<String, Deferred<ByteArray?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Built from what the host configured the picker with. */
    constructor(
        configuration: SeatLayerConfiguration,
        fetch: SeatLayerAssetFetch = SeatLayerAssetFetch(::seatLayerHttpAssetFetch),
        now: () -> Long = System::currentTimeMillis,
    ) : this(
        eventKey = configuration.event,
        apiBase = configuration.apiBase,
        token = configuration.buyerAccessToken,
        tokenProvider = configuration.buyerAccessTokenProvider,
        fetch = fetch,
        now = now,
    )

    /**
     * The bytes for [reference], or null when there is no photograph to show.
     *
     * Null covers every reason at once — a reference this event does not own, a
     * 403 or 404, no bearer, a dead network — because the card does the same
     * thing for all of them.
     */
    suspend fun load(reference: String): ByteArray? {
        val shared = lock.withLock {
            bytes[reference]?.let { return it }
            inFlight[reference] ?: scope.async { fetchOnce(reference) }
                .also { inFlight[reference] = it }
        }
        return runCatching { shared.await() }.getOrNull()
    }

    /** Forget [reference], so the next open of the same seat tries again. */
    suspend fun evict(reference: String) {
        lock.withLock { bytes.remove(reference) }
    }

    /** Forget everything, and stop everything. Called when the session ends. */
    fun clear() {
        scope.cancel()
    }

    /**
     * The absolute URL [reference] names, or null when it is not this event's.
     *
     * The runtime applies this predicate before emitting the field; it is
     * applied again here because a reference that reached this side wrong is a
     * request that must NOT be made, not a request that fails.
     */
    fun resolve(reference: String): String? {
        val match = REFERENCE.matchEntire(reference) ?: return null
        val event = runCatching {
            java.net.URLDecoder.decode(match.groupValues[1], "UTF-8")
        }.getOrNull() ?: return null
        if (event != eventKey) return null
        return "$apiBase$reference"
    }

    private suspend fun fetchOnce(reference: String): ByteArray? {
        val url = resolve(reference) ?: return null.also { forget(reference) }
        val authorization = authorization() ?: return null.also { forget(reference) }
        return try {
            val loaded = fetch.fetch(url, mapOf("Authorization" to authorization))
            lock.withLock {
                bytes[reference] = loaded
                inFlight.remove(reference)
            }
            loaded
        } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
            // Deliberately opaque, and deliberately unlogged: the failure may
            // carry the request that carried the bearer.
            forget(reference)
            if (error is kotlinx.coroutines.CancellationException) throw error
            null
        }
    }

    private suspend fun forget(reference: String) {
        lock.withLock { inFlight.remove(reference) }
    }

    /**
     * The `Authorization` header value, or null when there is none to be had.
     *
     * The bearer is used until thirty seconds before its stated expiry and then
     * re-minted through the host's provider: a picture that arrives with a
     * token the server has just stopped accepting is a photograph the buyer
     * never sees.
     */
    private suspend fun authorization(): String? {
        val held = lock.withLock { token }
        if (held != null && !expiring(held)) return "Bearer ${held.token}"
        val provider = tokenProvider
            // A one-shot token that has already expired cannot be refreshed,
            // and a public event has no token at all. Both are "no photograph".
            ?: return held?.let { "Bearer ${it.token}" }
        val fresh = runCatching {
            provider.provide(BuyerAccessRequestContext(ASSET_REFRESH))
        }.getOrNull() ?: return null
        lock.withLock { token = fresh }
        return "Bearer ${fresh.token}"
    }

    private fun expiring(token: BuyerAccessToken): Boolean {
        val expiresAt = token.expiresAt ?: return false
        if (!expiresAt.isFinite()) return false
        return now() >= expiresAt - TOKEN_SAFETY_MARGIN_MS
    }

    private companion object {
        /** The default API origin, matching the runtime's own. */
        const val DEFAULT_API_BASE = "https://api.seatlayer.io"

        /** How many decoded references the loader keeps. */
        const val CACHE_ENTRIES = 12
        const val LOAD_FACTOR = 0.75f

        /** How long before a bearer's stated expiry it stops being used. */
        const val TOKEN_SAFETY_MARGIN_MS = 30_000L

        /** `/pub/events/{key}/assets/{asset}`, and nothing else. */
        val REFERENCE = Regex("""/pub/events/([^/]+)/assets/([a-zA-Z0-9._\-]+)""")

        /** Why the host is being asked for a bearer, in the runtime's word. */
        val ASSET_REFRESH = BuyerAccessRefreshReason("asset")
    }
}

/** How long the default transport may take before the card gives up. */
private const val ASSET_FETCH_TIMEOUT_MS = 15_000L

private const val ASSET_MAX_BYTES = 8 * 1024 * 1024

/**
 * The default transport, on `java.net`, so the SDK gains no dependency.
 *
 * Redirects are refused rather than followed: a redirect off this origin would
 * take the `Authorization` header with it.
 */
internal suspend fun seatLayerHttpAssetFetch(
    url: String,
    headers: Map<String, String>,
): ByteArray = withTimeout(ASSET_FETCH_TIMEOUT_MS) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        instanceFollowRedirects = false
        useCaches = false
        headers.forEach(::setRequestProperty)
    }
    try {
        val status = connection.responseCode
        require(status in 200..299) { "unavailable" }
        connection.inputStream.use { stream ->
            val sink = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
                require(sink.size() <= ASSET_MAX_BYTES) { "unavailable" }
            }
            sink.toByteArray()
        }
    } finally {
        connection.disconnect()
    }
}
