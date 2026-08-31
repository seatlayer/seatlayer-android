package io.seatlayer.android

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Runtime-authored chart-load trace. Every field is optional and [raw]
 * preserves additive fields that this SDK release does not model yet.
 */
public data class SeatLayerChartLoadTrace(
    val raw: JsonObject,
    val event: String? = null,
    val scope: String? = null,
    val surface: String? = null,
    val outcome: String? = null,
    val stage: String? = null,
    val ms: Int? = null,
    val api: Int? = null,
    val scene: Int? = null,
    val panel: Int? = null,
    val paint: Int? = null,
    val normalize: Int? = null,
    val renderer: Int? = null,
    val availabilityMs: Int? = null,
    val seats: Int? = null,
    val floors: Int? = null,
    val view: String? = null,
    val load: String? = null,
    val transport: String? = null,
    val chartBytes: Int? = null,
    val chartCache: String? = null,
    val server: Int? = null,
    val r2Head: Int? = null,
    val cacheLookup: Int? = null,
    val r2Get: Int? = null,
    val transform: Int? = null,
    val host: String? = null,
    val platform: String? = null,
    val bundle: String? = null,
    val protocol: Int? = null,
    val chromeOwner: String? = null,
    val bootMs: Int? = null,
    val documentMs: Int? = null,
    val handshakeMs: Int? = null,
) {
    /** Missing outcome remains successful for older compatible runtimes. */
    public val succeeded: Boolean get() = outcome == null || outcome == "success"
}

/** One runtime trace merged with the SDK's monotonic host timing. */
public data class SeatLayerChartLoad(
    val trace: SeatLayerChartLoadTrace,
    val tapToReadyMs: Long?,
    val ready: ReadyInfo?,
    val readyTiming: SeatLayerPickerReadyTiming? = null,
) {
    /** Time outside the runtime document, clamped for cross-clock skew. */
    public val hostMs: Long?
        get() = if (tapToReadyMs == null || trace.bootMs == null) {
            null
        } else {
            (tapToReadyMs - trace.bootMs).coerceAtLeast(0)
        }
}

/** Decodes only an advertised telemetry.chartLoad payload. */
internal fun decodeSeatLayerChartLoadEvent(
    payload: JsonElement?,
): SeatLayerChartLoadTrace? {
    val root = payload as? JsonObject ?: return null
    val trace = root["trace"] as? JsonObject ?: return null
    fun metric(name: String): Int? = exactPickerInteger(trace[name])?.takeIf { it >= 0 }
    return SeatLayerChartLoadTrace(
        raw = trace,
        event = trace.string("event"),
        scope = trace.string("scope"),
        surface = trace.string("surface"),
        outcome = trace.string("outcome"),
        stage = trace.string("stage"),
        ms = metric("ms"),
        api = metric("api"),
        scene = metric("scene"),
        panel = metric("panel"),
        paint = metric("paint"),
        normalize = metric("normalize"),
        renderer = metric("renderer"),
        availabilityMs = metric("availabilityMs"),
        seats = metric("seats"),
        floors = metric("floors"),
        view = trace.string("view"),
        load = trace.string("load"),
        transport = trace.string("transport"),
        chartBytes = metric("chartBytes"),
        chartCache = trace.string("chartCache"),
        server = metric("server"),
        r2Head = metric("r2Head"),
        cacheLookup = metric("cacheLookup"),
        r2Get = metric("r2Get"),
        transform = metric("transform"),
        host = trace.string("host"),
        platform = trace.string("platform"),
        bundle = trace.string("bundle"),
        protocol = metric("protocol"),
        chromeOwner = trace.string("chromeOwner"),
        bootMs = metric("bootMs"),
        documentMs = metric("documentMs"),
        handshakeMs = metric("handshakeMs"),
    )
}
