package io.seatlayer.android

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Trace
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public class SeatLayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    public val controller: SeatLayerController = SeatLayerController()

    private var rendererHost: SeatLayerRendererHost? = null
    private var destroyed = false

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    public suspend fun load(configuration: SeatLayerConfiguration): ReadyInfo =
        withContext(Dispatchers.Main.immediate) {
            check(!destroyed) { "SeatLayerView was destroyed" }
            SeatLayerPickerPrewarmer.awaitPendingStartup()
            val traceCookie = System.identityHashCode(this@SeatLayerView)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Trace.beginAsyncSection("SeatLayer.Raw.Load", traceCookie)
            }
            try {
                val renderer = rendererHost ?: SeatLayerRendererHost(
                    context = context,
                    parent = this@SeatLayerView,
                    requireHostedAdapter = false,
                    onMessage = controller::ingest,
                    onTransportFailure = controller::failTransport,
                ).also { rendererHost = it }
                if (!renderer.isSupported) {
                    throw SeatLayerException.Transport(
                        "This Android System WebView does not support the secure " +
                            "WEB_MESSAGE_LISTENER bridge. Update Android System WebView.",
                    )
                }
                controller.beginHandshake(renderer, configuration)
                renderer.load()
                controller.awaitReady()
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Trace.endAsyncSection("SeatLayer.Raw.Load", traceCookie)
                }
            }
        }

    /**
     * Permanently releases this view. Create a new [SeatLayerView] to load again.
     */
    public fun destroy() {
        if (destroyed) return
        destroyed = true
        controller.closeForReload()
        rendererHost?.destroy()
        rendererHost = null
    }

    // Retained on SeatLayerView so the pre-picker JVM ABI remains binary-stable.
    // Kotlin callers never saw these private implementation constants, but the
    // compiler emits public static fields for private companion const values.
    private companion object {
        const val APP_ORIGIN = SEATLAYER_MOBILE_ORIGIN
        const val PAGE_URL = SEATLAYER_MOBILE_PAGE_URL
        const val BRIDGE_OBJECT = "seatlayerAndroid"
    }
}
