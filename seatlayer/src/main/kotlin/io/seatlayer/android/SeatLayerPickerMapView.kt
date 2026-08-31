package io.seatlayer.android

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Trace
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renderer-owned map pixels for a native picker composition.
 *
 * This View never renders native chrome. Compose and custom View applications
 * place their own components around it while sharing the bound state holder.
 */
public class SeatLayerPickerMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private var session: SeatLayerPickerSession? = null
    private var boundStateHolder: SeatLayerPickerStateHolder? = null
    private var rendererHost: SeatLayerRendererHost? = null
    private var destroyed = false

    public val stateHolder: SeatLayerPickerStateHolder?
        get() = boundStateHolder

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    /** Loads or reloads this map with exactly one picker state owner. */
    public suspend fun load(stateHolder: SeatLayerPickerStateHolder): ReadyInfo =
        withContext(Dispatchers.Main.immediate) {
            check(!destroyed) { "SeatLayerPickerMapView was destroyed" }
            SeatLayerPickerPrewarmer.awaitPendingStartup()
            val traceCookie = System.identityHashCode(this@SeatLayerPickerMapView)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Trace.beginAsyncSection("SeatLayer.Picker.Load", traceCookie)
            }
            try {
                val renderer = rendererHost ?: SeatLayerRendererHost(
                    context = context,
                    parent = this@SeatLayerPickerMapView,
                    requireHostedAdapter = true,
                    onMessage = { session?.ingest(it) },
                    onTransportFailure = { session?.failTransport(it) },
                ).also { rendererHost = it }
                if (!renderer.isSupported) {
                    throw SeatLayerException.Transport(
                        "This Android System WebView does not support the secure " +
                            "SeatLayer hosted picker bridge. Update Android System WebView.",
                    )
                }
                session?.closeForReload()
                boundStateHolder = stateHolder
                val next = SeatLayerPickerSession(stateHolder)
                session = next
                next.beginHandshake(renderer)
                renderer.load()
                next.awaitReady()
            } catch (error: SeatLayerException) {
                if (stateHolder.state.value.phase !is SeatLayerPickerPhase.Failed) {
                    stateHolder.fail(error)
                }
                throw error
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Trace.endAsyncSection("SeatLayer.Picker.Load", traceCookie)
                }
            }
        }

    /** View/XML spelling for binding the same lifecycle-neutral state owner. */
    public suspend fun bind(stateHolder: SeatLayerPickerStateHolder): ReadyInfo =
        load(stateHolder)

    /** Releases picker-owned inventory before tearing down renderer resources. */
    public suspend fun close() {
        val holder = boundStateHolder
        try {
            holder?.close()
        } finally {
            withContext(Dispatchers.Main.immediate) { destroy() }
        }
    }

    /**
     * Releases renderer resources after picker-owned holds have been handled.
     * Call [SeatLayerPickerStateHolder.close] first when orderly hold cleanup is
     * possible.
     */
    public fun destroy() {
        if (destroyed) return
        destroyed = true
        session?.closeForReload()
        session = null
        boundStateHolder?.let {
            if (it.state.value.phase !is SeatLayerPickerPhase.Destroyed) {
                it.markDestroyed()
            }
        }
        boundStateHolder = null
        rendererHost?.destroy()
        rendererHost = null
    }
}
