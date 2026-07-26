package io.seatlayer.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

public class SeatLayerController {
    private var client: BridgeClient? = null
    private var configuration: SeatLayerConfiguration? = null
    private var readyDeferred: CompletableDeferred<ReadyInfo>? = null
    private var destroyed = false

    private val mutableReady = MutableStateFlow<ReadyInfo?>(null)
    private val mutableBundle = MutableStateFlow<BundleInfo?>(null)
    private val mutableEvents = MutableSharedFlow<SeatLayerEvent>(
        extraBufferCapacity = 32,
    )

    public val ready: StateFlow<ReadyInfo?> = mutableReady.asStateFlow()
    public val bundle: StateFlow<BundleInfo?> = mutableBundle.asStateFlow()
    public val events: SharedFlow<SeatLayerEvent> = mutableEvents.asSharedFlow()
    public val isReady: Boolean get() = mutableReady.value != null

    internal fun beginHandshake(
        channel: BridgeChannel,
        configuration: SeatLayerConfiguration,
    ) {
        check(!destroyed) { "SeatLayerController was destroyed" }
        client?.close()
        this.configuration = configuration
        mutableReady.value = null
        mutableBundle.value = null
        readyDeferred = CompletableDeferred()
        client = BridgeClient(channel, configuration.commandTimeoutMillis).also {
            it.signalHandler = ::handleSignal
        }
    }

    internal suspend fun awaitReady(): ReadyInfo {
        val deferred = readyDeferred
            ?: throw SeatLayerException.Transport("SeatLayer handshake was not started.")
        val timeout = configuration?.handshakeTimeoutMillis ?: 30_000
        return try {
            withTimeout(timeout) { deferred.await() }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            val failure = SeatLayerException.Transport(
                "SeatLayer did not become ready within ${timeout}ms.",
                error,
            )
            deferred.completeExceptionally(failure)
            throw failure
        }
    }

    internal fun ingest(message: String) {
        Envelope.decode(message)?.let { client?.ingest(it) }
    }

    internal fun failTransport(message: String, cause: Throwable? = null) {
        val failure = SeatLayerException.Transport(message, cause)
        val waiting = readyDeferred
        if (waiting != null && !waiting.isCompleted) waiting.completeExceptionally(failure)
        else mutableEvents.tryEmit(
            SeatLayerEvent.Error(
                SeatLayerException.Bridge(
                    BridgeErrorDetails(
                        code = failure.code,
                        message = failure.message ?: message,
                        retryable = true,
                        conflicts = emptyList(),
                        metadata = null,
                    ),
                ),
            ),
        )
    }

    private fun handleSignal(signal: BridgeSignal) {
        when (signal) {
            is BridgeSignal.Hello -> handleHello(signal.payload)
            is BridgeSignal.Event -> handleEvent(signal.name, signal.payload)
            is BridgeSignal.Unhandled -> mutableEvents.tryEmit(
                SeatLayerEvent.Unknown(
                    signal.envelope.type,
                    signal.envelope.payload,
                ),
            )
        }
    }

    private fun handleHello(payload: JsonElement?) {
        val info = decodeBundle(payload)
        mutableBundle.value = info
        val agreed = negotiate(web = info.protocolRange)
        if (agreed == null) {
            finishFailure(
                SeatLayerException.Incompatible(
                    ProtocolRange.Native,
                    info.protocolRange,
                    "No shared SeatLayer bridge protocol revision.",
                ),
            )
            return
        }
        val config = configuration ?: return
        client?.sendInit(config.initPayload())
    }

    private fun handleEvent(name: String, payload: JsonElement?) {
        when (name) {
            "sys.ready" -> {
                val info = decodeReady(payload)
                mutableReady.value = info
                readyDeferred?.complete(info)
            }
            "sys.incompatible" -> {
                val root = payload as? JsonObject
                val web = ProtocolRange.decode(root?.objectValue("web"))
                    ?: ProtocolRange.Native
                finishFailure(
                    SeatLayerException.Incompatible(
                        ProtocolRange.Native,
                        web,
                        root?.string("message") ?: "The web bundle rejected this protocol.",
                    ),
                )
            }
            "sys.error" -> finishFailure(
                SeatLayerException.Bridge(BridgeErrorDetails.decode(payload)),
            )
            "selection.changed" -> {
                val seats = (payload as? JsonObject)
                    ?.array("seats")
                    .orEmpty()
                    .mapNotNull(::decodeSelectedSeat)
                mutableEvents.tryEmit(SeatLayerEvent.SelectionChanged(seats))
            }
            "hold.changed" -> decodeHold((payload as? JsonObject)?.get("hold"))
                ?.let { mutableEvents.tryEmit(SeatLayerEvent.HoldChanged(it)) }
            "hold.restored" -> decodeHold((payload as? JsonObject)?.get("hold"))
                ?.let { mutableEvents.tryEmit(SeatLayerEvent.HoldRestored(it)) }
            "hold.expired" -> mutableEvents.tryEmit(SeatLayerEvent.HoldExpired)
            "ga.click" -> decodeGeneralAdmissionArea(
                (payload as? JsonObject)?.get("area"),
            )?.let {
                mutableEvents.tryEmit(SeatLayerEvent.GeneralAdmissionClicked(it))
            }
            "hint" -> mutableEvents.tryEmit(
                SeatLayerEvent.Hint((payload as? JsonObject)?.string("message")),
            )
            "error" -> mutableEvents.tryEmit(
                SeatLayerEvent.Error(
                    SeatLayerException.Bridge(BridgeErrorDetails.decode(payload)),
                ),
            )
            "seat.hover" -> mutableEvents.tryEmit(
                SeatLayerEvent.SeatHovered((payload as? JsonObject)?.get("details")),
            )
            "deck.tap" -> (payload as? JsonObject)?.string("floorId")?.let {
                mutableEvents.tryEmit(SeatLayerEvent.DeckTapped(it))
            }
            "checkout" -> mutableEvents.tryEmit(SeatLayerEvent.Checkout(payload))
            else -> mutableEvents.tryEmit(SeatLayerEvent.Unknown(name, payload))
        }
    }

    private fun finishFailure(error: SeatLayerException) {
        val waiting = readyDeferred
        if (waiting != null && !waiting.isCompleted) waiting.completeExceptionally(error)
        else if (error is SeatLayerException.Bridge) {
            mutableEvents.tryEmit(SeatLayerEvent.Error(error))
        }
    }

    private suspend fun run(command: String, payload: JsonElement? = null): JsonElement? =
        client?.command(command, payload)
            ?: throw SeatLayerException.Transport("No SeatLayer chart is loaded.")

    public suspend fun hold(ttlMillis: Int? = null): HoldResult? {
        val result = run("hold", jsonObject("ttlMs" to jsonNumber(ttlMillis)))
        return decodeHold((result as? JsonObject)?.get("hold"))
    }

    public suspend fun resumeHold(holdId: String): HoldResult? {
        val result = run(
            "resumeHold",
            jsonObject("holdId" to JsonPrimitive(holdId)),
        )
        return decodeHold((result as? JsonObject)?.get("hold"))
    }

    public suspend fun extendHold(ttlMillis: Int? = null): HoldResult? {
        val result = run("extendHold", jsonObject("ttlMs" to jsonNumber(ttlMillis)))
        return decodeHold((result as? JsonObject)?.get("hold"))
    }

    public suspend fun release(): Unit = run("release").let {}

    public suspend fun releaseLabels(labels: List<String>): Boolean {
        val result = run(
            "releaseLabels",
            jsonObject("labels" to jsonStrings(labels)),
        ) as? JsonObject
        return result?.boolean("released") ?: false
    }

    public suspend fun bestAvailable(
        quantity: Int,
        categoryKey: String? = null,
        zoneId: String? = null,
        preferPremium: Boolean? = null,
        ttlMillis: Int? = null,
    ): BestAvailableResult? {
        val result = run(
            "bestAvailable",
            jsonObject(
                "qty" to JsonPrimitive(quantity),
                "categoryKey" to jsonString(categoryKey),
                "zoneId" to jsonString(zoneId),
                "preferPremium" to jsonBoolean(preferPremium),
                "ttlMs" to jsonNumber(ttlMillis),
            ),
        )
        return decodeBestAvailable((result as? JsonObject)?.get("hold"))
    }

    public suspend fun holdGeneralAdmission(
        areaId: String,
        quantity: Int,
        tierId: String? = null,
        includeTier: Boolean = false,
        ttlMillis: Int? = null,
    ): HoldResult? {
        val entries = linkedMapOf<String, JsonElement>(
            "areaId" to JsonPrimitive(areaId),
            "qty" to JsonPrimitive(quantity),
        )
        if (includeTier) entries["tierId"] = tierId?.let(::JsonPrimitive) ?: JsonNull
        ttlMillis?.let { entries["ttlMs"] = JsonPrimitive(it) }
        val result = run("holdGA", JsonObject(entries))
        return decodeHold((result as? JsonObject)?.get("hold"))
    }

    public suspend fun setSeatTier(seatId: String, tierId: String?) {
        run(
            "setSeatTier",
            jsonObject(
                "seatId" to JsonPrimitive(seatId),
                "tierId" to tierId?.let(::JsonPrimitive).orJsonNull(),
            ),
        )
    }

    public suspend fun getSelection(): List<SelectedSeat> =
        ((run("getSelection") as? JsonObject)?.get("seats") as? JsonArray)
            .orEmpty()
            .mapNotNull(::decodeSelectedSeat)

    public suspend fun getCurrentHold(): HoldResult? =
        decodeHold((run("getCurrentHold") as? JsonObject)?.get("hold"))

    public suspend fun getGeneralAdmissionAreas(): List<GeneralAdmissionArea> =
        ((run("getGAAreas") as? JsonObject)?.get("areas") as? JsonArray)
            .orEmpty()
            .mapNotNull(::decodeGeneralAdmissionArea)

    public suspend fun getFloors(): List<FloorInfo> =
        ((run("getFloors") as? JsonObject)?.get("floors") as? JsonArray)
            .orEmpty()
            .mapNotNull(::decodeFloor)

    public suspend fun setFloor(floorId: String) {
        run("setFloor", jsonObject("floorId" to JsonPrimitive(floorId)))
    }

    public suspend fun setColorblindSafe(enabled: Boolean) {
        run("setColorblindSafe", jsonObject("on" to JsonPrimitive(enabled)))
    }

    public suspend fun setViewMode(mode: SeatLayerViewMode) {
        run("setViewMode", jsonObject("mode" to JsonPrimitive(mode.raw)))
    }

    public suspend fun getViewMode(): SeatLayerViewMode {
        val result = run("getViewMode") as? JsonObject
        return SeatLayerViewMode(result?.string("mode") ?: SeatLayerViewMode.Flat.raw)
    }

    public suspend fun zoomIn(): Unit = run("zoomIn").let {}
    public suspend fun zoomOut(): Unit = run("zoomOut").let {}
    public suspend fun zoomToFit(): Unit = run("zoomToFit").let {}

    public suspend fun destroy() {
        runCatching { run("destroy") }
        client?.close()
        client = null
        mutableReady.value = null
        destroyed = true
    }

    internal fun closeForReload() {
        client?.close()
        client = null
        mutableReady.value = null
    }
}
