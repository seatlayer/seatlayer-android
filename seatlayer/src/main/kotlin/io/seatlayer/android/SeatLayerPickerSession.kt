package io.seatlayer.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToLong

/** Owns the protocol-2 handshake while state and commands remain public. */
internal class SeatLayerPickerSession(
    private val stateHolder: SeatLayerPickerStateHolder,
    private val monotonicNanos: () -> Long = System::nanoTime,
) {
    private var client: BridgeClient? = null
    private var readyDeferred: CompletableDeferred<ReadyInfo>? = null
    private var generationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var startedAtNanos: Long? = null
    private var helloAtNanos: Long? = null
    private var tapToReadyMs: Long? = null
    private var readyInfo: ReadyInfo? = null
    private var readyTiming: SeatLayerPickerReadyTiming? = null

    fun beginHandshake(channel: BridgeChannel) {
        closeForReload()
        generationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        startedAtNanos = monotonicNanos()
        helloAtNanos = null
        tapToReadyMs = null
        readyInfo = null
        readyTiming = null
        stateHolder.beginLoading()
        readyDeferred = CompletableDeferred()
        client = BridgeClient(
            channel = channel,
            timeoutMillis = stateHolder.configuration.commandTimeoutMillis,
            failableCommands = PICKER_FAILABLE_COMMANDS,
        ).also { it.signalHandler = ::handleSignal }
    }

    suspend fun awaitReady(): ReadyInfo {
        val deferred = readyDeferred
            ?: throw SeatLayerException.Transport("SeatLayer picker handshake was not started.")
        val timeout = stateHolder.configuration.handshakeTimeoutMillis
        return try {
            withTimeout(timeout) { deferred.await() }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            val failure = SeatLayerException.Transport(
                "SeatLayer picker did not become ready within ${timeout}ms.",
                error,
            )
            finishFailure(failure)
            throw failure
        }
    }

    fun ingest(message: String) {
        Envelope.decode(message)?.let { client?.ingest(it) }
    }

    fun failTransport(message: String, cause: Throwable? = null) {
        finishFailure(SeatLayerException.Transport(message, cause))
    }

    private fun handleSignal(signal: BridgeSignal) {
        when (signal) {
            is BridgeSignal.Hello -> handleHello(signal.payload)
            is BridgeSignal.Event -> handleEvent(signal.name, signal.payload)
            is BridgeSignal.Unhandled -> Unit
        }
    }

    private fun handleHello(payload: JsonElement?) {
        if (helloAtNanos == null) helloAtNanos = monotonicNanos()
        val bundle = decodeBundle(payload)
        try {
            stateHolder.bridgeProfile.validate(bundle)
            if (
                stateHolder.configuration.usesPrivateAccess &&
                !bundle.supportsCapability("native-access-provider")
            ) {
                throw SeatLayerException.Incompatible(
                    native = stateHolder.bridgeProfile.protocolRange,
                    web = bundle.protocolRange,
                    reason = "The picker bundle does not support private buyer access.",
                )
            }
            val activeClient = client ?: return
            stateHolder.connect(BridgePickerCommandTransport(activeClient), bundle)
            activeClient.sendInit(
                stateHolder.bridgeProfile.initPayload(
                    configuration = stateHolder.configuration,
                    bundle = bundle,
                ),
            )
        } catch (error: SeatLayerException) {
            finishFailure(error)
        }
    }

    private fun handleEvent(name: String, payload: JsonElement?) {
        when (name) {
            "sys.ready" -> handleReady(payload)
            "sys.incompatible" -> {
                val root = payload as? JsonObject
                val web = ProtocolRange.decode(root?.objectValue("web"))
                    ?: stateHolder.bridgeProfile.protocolRange
                finishFailure(
                    SeatLayerException.Incompatible(
                        native = stateHolder.bridgeProfile.protocolRange,
                        web = web,
                        reason = root?.string("message")
                            ?: "The web bundle rejected the picker protocol.",
                    ),
                )
            }
            "sys.error" -> finishFailure(
                SeatLayerException.Bridge(BridgeErrorDetails.decode(payload)),
            )
            "picker.snapshot" -> stateHolder.acceptSnapshot(
                (payload as? JsonObject)?.get("snapshot") ?: payload,
            )
            "seatView.changed" -> stateHolder.acceptSeatView(
                (payload as? JsonObject)?.get("seatView") ?: payload,
            )
            "telemetry.chartLoad" -> handleChartLoad(payload)
            "ga.click" -> stateHolder.acceptGeneralAdmissionCandidate(payload)
            "access.token.request" -> provideBuyerAccessToken(payload)
            "error" -> stateHolder.record(
                SeatLayerException.Bridge(BridgeErrorDetails.decode(payload)),
            )
            else -> Unit
        }
    }

    private fun handleReady(payload: JsonElement?) {
        val root = payload as? JsonObject
        val readyAtNanos = monotonicNanos()
        val started = startedAtNanos
        val info = decodeReady(payload)
        val timing = SeatLayerPickerReadyTiming(
            timeToHelloMs = elapsedMillis(started, helloAtNanos),
            timeToReadyMs = elapsedMillis(started, readyAtNanos),
            raw = payload,
        )
        if (info.eventKey != stateHolder.configuration.event) {
            finishFailure(
                readyFailure(
                    code = "event_mismatch",
                    message = "SeatLayer picker ready event does not match configuration.",
                ),
            )
            return
        }
        val snapshot = decodeSeatLayerPickerSnapshot(root?.get("snapshot"))
        if (snapshot == null || !stateHolder.acceptSnapshot(snapshot)) {
            finishFailure(
                readyFailure(
                    code = "invalid_snapshot",
                    message = "SeatLayer picker ready event has no valid initial snapshot.",
                ),
            )
            return
        }
        tapToReadyMs = elapsedMillis(started, readyAtNanos)
        readyInfo = info
        readyTiming = timing
        stateHolder.markReady(info, timing)
        readyDeferred?.complete(info)
    }

    private fun handleChartLoad(payload: JsonElement?) {
        val controller = stateHolder.controller
        if (
            !controller.supportsCapability("chart-load-trace-v1") ||
            !controller.supportsEvent("telemetry.chartLoad")
        ) return
        val trace = decodeSeatLayerChartLoadEvent(payload) ?: return
        stateHolder.emitChartLoad(
            SeatLayerChartLoad(
                trace = trace,
                tapToReadyMs = tapToReadyMs,
                ready = readyInfo,
                readyTiming = readyTiming,
            ),
        )
    }

    private fun provideBuyerAccessToken(payload: JsonElement?) {
        val root = payload as? JsonObject ?: return
        val requestId = root.string("requestId") ?: return
        val reason = BuyerAccessRefreshReason(
            root.string("reason") ?: BuyerAccessRefreshReason.Initial.raw,
        )
        val provider = stateHolder.configuration.buyerAccessTokenProvider
        val activeClient = client ?: return

        generationScope.launch {
            if (provider == null) {
                runCatching {
                    activeClient.command(
                        "access.token.unavailable",
                        jsonObject("requestId" to kotlinx.serialization.json.JsonPrimitive(requestId)),
                    )
                }
                return@launch
            }
            try {
                val token = provider.provide(BuyerAccessRequestContext(reason))
                check(token.token.isNotBlank() && token.expiresAt?.isFinite() != false) {
                    "buyer access provider returned an invalid token"
                }
                activeClient.command(
                    "access.token.provide",
                    jsonObject(
                        "requestId" to kotlinx.serialization.json.JsonPrimitive(requestId),
                        "token" to kotlinx.serialization.json.JsonPrimitive(token.token),
                        "expiresAt" to jsonNumber(token.expiresAt),
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Provider errors and bearer content never cross into diagnostics.
                runCatching {
                    activeClient.command(
                        "access.token.unavailable",
                        jsonObject(
                            "requestId" to kotlinx.serialization.json.JsonPrimitive(requestId),
                        ),
                    )
                }
            }
        }
    }

    private fun finishFailure(error: SeatLayerException) {
        val waiting = readyDeferred
        if (waiting != null && !waiting.isCompleted) waiting.completeExceptionally(error)
        stateHolder.fail(error)
    }

    private fun readyFailure(code: String, message: String): SeatLayerException.Bridge =
        SeatLayerException.Bridge(
            BridgeErrorDetails(
                code = code,
                message = message,
                retryable = false,
                conflicts = emptyList(),
                metadata = null,
            ),
        )

    fun closeForReload() {
        generationScope.cancel()
        client?.close()
        client = null
        readyDeferred?.cancel()
        readyDeferred = null
        startedAtNanos = null
        helloAtNanos = null
        tapToReadyMs = null
        readyInfo = null
    }

    private fun elapsedMillis(start: Long?, end: Long?): Long? {
        if (start == null || end == null) return null
        return (((end - start).coerceAtLeast(0)) / 1_000_000.0).roundToLong()
    }

    private companion object {
        val PICKER_FAILABLE_COMMANDS = setOf(
            "picker.holdGA",
            "picker.holdSelection",
            "picker.bestAvailable",
            "picker.resumeHold",
            "picker.extendHold",
            "picker.continue",
            "picker.rejectHandoff",
            "picker.abort",
        )
    }
}
