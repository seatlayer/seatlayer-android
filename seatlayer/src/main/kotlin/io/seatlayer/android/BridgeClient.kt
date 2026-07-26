package io.seatlayer.android

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement

internal fun interface BridgeChannel {
    fun send(envelope: Envelope)
}

internal sealed interface BridgeSignal {
    data class Hello(val payload: JsonElement?) : BridgeSignal
    data class Event(
        val name: String,
        val payload: JsonElement?,
        val sequence: Int?,
    ) : BridgeSignal
    data class Unhandled(val envelope: Envelope) : BridgeSignal
}

private data class PendingCommand(
    val command: String,
    val order: Int,
    val result: CompletableDeferred<JsonElement?>,
)

internal class BridgeClient(
    private val channel: BridgeChannel,
    private val timeoutMillis: Long,
    private val failableCommands: Set<String> = DEFAULT_FAILABLE_COMMANDS,
) {
    private val nextId = AtomicInteger()
    private val lock = Any()
    private val pending = linkedMapOf<String, PendingCommand>()
    private val lastSequence = mutableMapOf<String, Int>()
    private var closed = false
    var signalHandler: ((BridgeSignal) -> Unit)? = null

    suspend fun command(name: String, payload: JsonElement? = null): JsonElement? {
        val order = nextId.incrementAndGet()
        val id = "a$order"
        val deferred = CompletableDeferred<JsonElement?>()
        synchronized(lock) {
            if (closed) throw SeatLayerException.Destroyed()
            pending[id] = PendingCommand(name, order, deferred)
        }

        runCatching {
            channel.send(Envelope(kind = "cmd", type = name, id = id, payload = payload))
        }.onFailure {
            synchronized(lock) { pending.remove(id) }
            throw SeatLayerException.Transport("Could not send SeatLayer command.", it)
        }

        return try {
            withTimeout(timeoutMillis) { deferred.await() }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            synchronized(lock) { pending.remove(id) }
            throw SeatLayerException.Timeout(name, timeoutMillis)
        }
    }

    fun sendInit(payload: JsonElement) {
        synchronized(lock) {
            if (closed) return
        }
        channel.send(Envelope(kind = "init", type = "init", payload = payload))
    }

    fun ingest(envelope: Envelope) {
        synchronized(lock) {
            if (closed) return
        }
        when (envelope.kind) {
            "res" -> resolve(envelope, null)
            "err" -> resolve(
                envelope,
                SeatLayerException.Bridge(BridgeErrorDetails.decode(envelope.payload)),
            )
            "evt" -> ingestEvent(envelope)
            "hello" -> signalHandler?.invoke(BridgeSignal.Hello(envelope.payload))
            else -> signalHandler?.invoke(BridgeSignal.Unhandled(envelope))
        }
    }

    private fun resolve(envelope: Envelope, failure: SeatLayerException?) {
        val id = envelope.id ?: return
        val entry = synchronized(lock) { pending.remove(id) } ?: return
        if (failure == null) entry.result.complete(envelope.payload)
        else entry.result.completeExceptionally(failure)
    }

    private fun ingestEvent(envelope: Envelope) {
        if (envelope.type == "error") {
            val failable = synchronized(lock) {
                pending.entries
                    .filter { it.value.command in failableCommands }
                    .maxByOrNull { it.value.order }
                    ?.also { pending.remove(it.key) }
            }
            if (failable != null) {
                failable.value.result.completeExceptionally(
                    SeatLayerException.Bridge(
                        BridgeErrorDetails.decode(envelope.payload),
                    ),
                )
                return
            }
        }

        val sequence = envelope.sequence
        if (sequence != null) {
            val fresh = synchronized(lock) {
                val seen = lastSequence[envelope.type]
                if (seen != null && sequence <= seen) false
                else {
                    lastSequence[envelope.type] = sequence
                    true
                }
            }
            if (!fresh) return
        }
        signalHandler?.invoke(
            BridgeSignal.Event(envelope.type, envelope.payload, envelope.sequence),
        )
    }

    fun close() {
        val open = synchronized(lock) {
            if (closed) return
            closed = true
            pending.values.toList().also {
                pending.clear()
                lastSequence.clear()
            }
        }
        open.forEach { it.result.completeExceptionally(SeatLayerException.Destroyed()) }
    }

    companion object {
        val DEFAULT_FAILABLE_COMMANDS: Set<String> = setOf(
            "hold",
            "holdGA",
            "bestAvailable",
            "resumeHold",
            "extendHold",
            "release",
            "releaseLabels",
        )
    }
}
