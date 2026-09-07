package io.seatlayer.android.compose

import io.seatlayer.android.BuyerAccessRequestContext
import io.seatlayer.android.BuyerAccessToken
import io.seatlayer.android.BuyerAccessTokenProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The buyer's own image transport.
 *
 * A seat photograph is named as an event-scoped API path that a private event
 * answers only for the buyer's bearer, so this is where the bearer is handled —
 * and the one place it must never end up is anywhere a bearer can be read back.
 */
class PickerBuyerAssetLoaderTest {
    @Test
    fun onlyThisEventsOwnAssetPathIsEverRequested() {
        val loader = loader()

        assertEquals(
            "https://api.example.test/pub/events/evt-1/assets/seat-a12.jpg",
            loader.resolve("/pub/events/evt-1/assets/seat-a12.jpg"),
        )
        // Another event's asset, a path that is not an asset path, a traversal
        // and an absolute URL are all refused WITHOUT a request being made.
        assertNull(loader.resolve("/pub/events/evt-2/assets/seat-a12.jpg"))
        assertNull(loader.resolve("/pub/events/evt-1/seats/seat-a12.jpg"))
        assertNull(loader.resolve("/pub/events/evt-1/assets/../../secrets"))
        assertNull(loader.resolve("https://elsewhere.test/pub/events/evt-1/assets/a.jpg"))
        assertNull(loader.resolve(""))
    }

    @Test
    fun aRefusedReferenceNeverReachesTheTransport() = runBlocking {
        val calls = AtomicInteger()
        val loader = loader(fetch = { _, _ -> calls.incrementAndGet(); ByteArray(1) })

        assertNull(loader.load("/pub/events/evt-2/assets/seat-a12.jpg"))
        assertEquals(0, calls.get())
    }

    @Test
    fun theBearerRidesTheHeaderAndNothingElse() = runBlocking {
        var seenUrl: String? = null
        var seenHeaders: Map<String, String> = emptyMap()
        val loader = loader(
            token = BuyerAccessToken(token = SECRET),
            fetch = { url, headers ->
                seenUrl = url
                seenHeaders = headers
                ByteArray(3)
            },
        )

        assertNotNull(loader.load(REFERENCE))
        assertEquals("Bearer $SECRET", seenHeaders["Authorization"])
        assertFalse(seenUrl.orEmpty().contains(SECRET))
        assertFalse(seenUrl.orEmpty().contains("Bearer"))
        // And the resolved URL a diagnostic might print carries none of it.
        assertFalse(loader.resolve(REFERENCE).orEmpty().contains(SECRET))
    }

    @Test
    fun aBearerWithinHalfAMinuteOfExpiryIsMintedAgain() = runBlocking {
        val now = 1_000_000L
        val asked = mutableListOf<String>()
        val provider = BuyerAccessTokenProvider { context: BuyerAccessRequestContext ->
            asked += context.reason.raw
            BuyerAccessToken(token = "fresh", expiresAt = (now + 600_000).toDouble())
        }
        var authorization: String? = null
        val loader = loader(
            // Twenty-nine seconds of life left is not enough: a picture that
            // arrives with a token the server has just stopped accepting is a
            // photograph the buyer never sees.
            token = BuyerAccessToken(token = SECRET, expiresAt = (now + 29_000).toDouble()),
            tokenProvider = provider,
            now = { now },
            fetch = { _, headers -> authorization = headers["Authorization"]; ByteArray(1) },
        )

        assertNotNull(loader.load(REFERENCE))
        assertEquals("Bearer fresh", authorization)
        assertEquals(listOf("asset"), asked)

        // The fresh one is then held: a second reference asks the host nothing.
        assertNotNull(loader.load("/pub/events/evt-1/assets/seat-b02.jpg"))
        assertEquals(listOf("asset"), asked)
    }

    @Test
    fun aBearerWithRoomLeftIsUsedAsItIs() = runBlocking {
        val now = 1_000_000L
        val asked = AtomicInteger()
        var authorization: String? = null
        val loader = loader(
            token = BuyerAccessToken(token = SECRET, expiresAt = (now + 31_000).toDouble()),
            tokenProvider = {
                asked.incrementAndGet()
                BuyerAccessToken(token = "fresh")
            },
            now = { now },
            fetch = { _, headers -> authorization = headers["Authorization"]; ByteArray(1) },
        )

        assertNotNull(loader.load(REFERENCE))
        assertEquals("Bearer $SECRET", authorization)
        assertEquals(0, asked.get())
    }

    @Test
    fun aHostThatCannotMintOneIsNoPhotograph() = runBlocking {
        val loader = loader(
            tokenProvider = { error("the host said no") },
            fetch = { _, _ -> error("must not be reached") },
        )

        assertNull(loader.load(REFERENCE))
    }

    @Test
    fun aPublicEventWithNoBearerAtAllIsAlsoNoPhotograph() = runBlocking {
        val loader = loader(fetch = { _, _ -> error("must not be reached") })

        assertNull(loader.load(REFERENCE))
    }

    @Test
    fun aFailedRequestIsNoPhotographRatherThanAThrow() = runBlocking {
        val loader = loader(
            token = BuyerAccessToken(token = SECRET),
            // The failure may carry the request that carried the bearer, so it
            // is swallowed whole rather than logged or rethrown.
            fetch = { url, headers -> error("403 for $url with ${headers["Authorization"]}") },
        )

        assertNull(loader.load(REFERENCE))
    }

    @Test
    fun twoCardsAskingForOnePhotographMakeOneRequest() = runBlocking(Dispatchers.Default) {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<ByteArray>()
        val calls = AtomicInteger()
        val loader = loader(
            token = BuyerAccessToken(token = SECRET),
            fetch = { _, _ ->
                calls.incrementAndGet()
                started.complete(Unit)
                gate.await()
            },
        )

        val first = async { loader.load(REFERENCE) }
        withTimeout(TIMEOUT) { started.await() }
        val second = async { loader.load(REFERENCE) }
        gate.complete(byteArrayOf(1, 2, 3))

        assertEquals(3, withTimeout(TIMEOUT) { first.await() }?.size)
        assertEquals(3, withTimeout(TIMEOUT) { second.await() }?.size)
        assertEquals(1, calls.get())
    }

    @Test
    fun aCardThatGoesMidFlightTakesOnlyItsOwnWaitWithIt() = runBlocking(Dispatchers.Default) {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<ByteArray>()
        val calls = AtomicInteger()
        val loader = loader(
            token = BuyerAccessToken(token = SECRET),
            fetch = { _, _ ->
                calls.incrementAndGet()
                started.complete(Unit)
                gate.await()
            },
        )

        val leaving = launch { loader.load(REFERENCE) }
        withTimeout(TIMEOUT) { started.await() }
        val staying = async { loader.load(REFERENCE) }
        leaving.cancelAndJoin()
        gate.complete(byteArrayOf(9))

        // The card that left cancelled its own wait, not the request the other
        // card is still on.
        assertEquals(1, withTimeout(TIMEOUT) { staying.await() }?.size)
        assertEquals(1, calls.get())
    }

    @Test
    fun aRepeatedReferenceIsAnsweredFromTheCacheUntilItIsEvicted() = runBlocking {
        val calls = AtomicInteger()
        val loader = loader(
            token = BuyerAccessToken(token = SECRET),
            fetch = { _, _ -> ByteArray(calls.incrementAndGet()) },
        )

        assertEquals(1, loader.load(REFERENCE)?.size)
        assertEquals(1, loader.load(REFERENCE)?.size)
        assertEquals(1, calls.get())

        // A miss is evicted by the card so the next open tries again.
        loader.evict(REFERENCE)
        assertEquals(2, loader.load(REFERENCE)?.size)
        assertEquals(2, calls.get())
    }

    @Test
    fun theCacheHoldsTwelveAndDropsTheLeastRecentlyRead() = runBlocking {
        val calls = AtomicInteger()
        val loader = loader(
            token = BuyerAccessToken(token = SECRET),
            fetch = { _, _ -> calls.incrementAndGet(); ByteArray(1) },
        )
        val references = (1..12).map { "/pub/events/evt-1/assets/seat-$it.jpg" }
        references.forEach { loader.load(it) }
        assertEquals(12, calls.get())

        // Reading the first again makes it the most recent, so the SECOND is
        // the one a thirteenth photograph pushes out.
        loader.load(references.first())
        assertEquals(12, calls.get())
        loader.load("/pub/events/evt-1/assets/seat-13.jpg")
        assertEquals(13, calls.get())

        loader.load(references.first())
        assertEquals(13, calls.get())
        loader.load(references[1])
        assertEquals(14, calls.get())
    }

    @Test
    fun endingTheSessionStopsEverythingItHadInTheAir() = runBlocking(Dispatchers.Default) {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<ByteArray>()
        val loader = loader(
            token = BuyerAccessToken(token = SECRET),
            fetch = { _, _ -> started.complete(Unit); gate.await() },
        )
        val waiting = async { loader.load(REFERENCE) }
        withTimeout(TIMEOUT) { started.await() }
        loader.clear()

        assertNull(withTimeout(TIMEOUT) { waiting.await() })
        assertTrue(gate.isActive)
    }

    private fun loader(
        token: BuyerAccessToken? = null,
        tokenProvider: BuyerAccessTokenProvider? = null,
        now: () -> Long = { 0L },
        fetch: suspend (String, Map<String, String>) -> ByteArray = { _, _ -> ByteArray(0) },
    ) = SeatLayerBuyerAssetLoader(
        eventKey = "evt-1",
        apiBase = "https://api.example.test/",
        token = token,
        tokenProvider = tokenProvider,
        fetch = { url, headers -> fetch(url, headers) },
        now = now,
    )

    private companion object {
        const val REFERENCE = "/pub/events/evt-1/assets/seat-a12.jpg"
        const val SECRET = "buyer-bearer-value"
        const val TIMEOUT = 5_000L
    }
}
