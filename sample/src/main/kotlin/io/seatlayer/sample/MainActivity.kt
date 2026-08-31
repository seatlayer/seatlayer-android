package io.seatlayer.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerChartLoad
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerView
import io.seatlayer.android.compose.SeatLayerPicker
import io.seatlayer.android.compose.SeatLayerPickerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runnable proof for every supported integration path.
 *
 * Select one with the `seatlayerIntegration` intent extra. The default remains
 * the ready-made Compose picker used by device smoke tests.
 */
public class MainActivity : ComponentActivity() {
    private var releaseResources: (() -> Unit)? = null
    private var fullyDrawnReported = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configuration = SeatLayerConfiguration(
            event = intent.getStringExtra(EXTRA_EVENT) ?: DEFAULT_EVENT,
            currency = "USD",
            maxSelection = 4,
            hostInfo = mapOf("app" to "SeatLayerAndroidSample"),
        )
        when (intent.getStringExtra(EXTRA_INTEGRATION) ?: READY_COMPOSE) {
            READY_COMPOSE -> showReadyCompose(configuration)
            BRANDED_COMPOSE -> showBrandedCompose(configuration)
            CUSTOM_COMPOSE -> showCustomCompose(configuration)
            READY_VIEW -> showReadyView(configuration)
            CUSTOM_VIEW -> showCustomView(configuration)
            RAW_VIEW -> showRawView(configuration)
            else -> showReadyCompose(configuration)
        }
    }

    override fun onDestroy() {
        releaseResources?.invoke()
        releaseResources = null
        super.onDestroy()
    }

    private fun showReadyCompose(configuration: SeatLayerConfiguration) {
        setContent {
            SeatLayerPicker(
                configuration = configuration,
                modifier = Modifier.fillMaxSize(),
                onCheckout = checkoutHandler,
                onReady = ::logReady,
                onChartLoad = ::logChartLoad,
                onSnapshot = ::logSnapshot,
                onError = ::logError,
                onClose = ::finish,
            )
        }
    }

    private fun showBrandedCompose(configuration: SeatLayerConfiguration) {
        setContent {
            BrandedPickerExample(
                configuration = configuration,
                onCheckout = checkoutHandler,
                onError = ::logError,
                onReady = ::logReady,
                onSnapshot = ::logSnapshot,
                onClose = ::finish,
            )
        }
    }

    private fun showCustomCompose(configuration: SeatLayerConfiguration) {
        setContent {
            CustomPickerExample(
                configuration = configuration,
                onCheckout = checkoutHandler,
                onError = ::logError,
                onReady = ::logReady,
                onSnapshot = ::logSnapshot,
                onClose = ::finish,
            )
        }
    }

    private fun showReadyView(configuration: SeatLayerConfiguration) {
        val picker = SeatLayerPickerView(this)
        setContentView(picker)
        picker.bind(
            lifecycleOwner = this,
            configuration = configuration,
            onCheckout = checkoutHandler,
            onError = ::logError,
            onReady = ::logReady,
            onChartLoad = ::logChartLoad,
            onSnapshot = ::logSnapshot,
            onClose = ::finish,
        )
        releaseResources = { releaseAsynchronously { picker.close() } }
    }

    private fun showCustomView(configuration: SeatLayerConfiguration) {
        val picker = CustomPickerViewExample(this, configuration)
        setContentView(picker)
        picker.bind(
            lifecycleOwner = this,
            onCheckout = checkoutHandler,
            onError = ::logError,
            onReady = ::logReady,
            onSnapshot = ::logSnapshot,
            onClose = ::finish,
        )
        releaseResources = { releaseAsynchronously { picker.close() } }
    }

    private fun showRawView(configuration: SeatLayerConfiguration) {
        val raw = SeatLayerView(this)
        setContentView(raw)
        lifecycleScope.launch {
            try {
                logReady(raw.load(configuration))
            } catch (error: SeatLayerException) {
                logError(error)
            }
        }
        releaseResources = raw::destroy
    }

    private val checkoutHandler: suspend (SeatLayerPickerCheckoutHandoff) -> Unit =
        { handoff -> Log.i(TAG, "checkout lines=${handoff.lineItems.size}") }

    private fun logReady(info: ReadyInfo) {
        Log.i(
            TAG,
            "ready protocol=${info.protocolRevision} " +
                "mode=${info.mode.raw} transport=${info.transport.raw}",
        )
        findViewById<android.view.View>(android.R.id.content)?.contentDescription =
            READY_MARKER
        if (!fullyDrawnReported) {
            fullyDrawnReported = true
            reportFullyDrawn()
        }
    }

    private fun logChartLoad(load: SeatLayerChartLoad) {
        Log.i(
            TAG,
            "chart load outcome=${load.trace.outcome} bootMs=${load.trace.bootMs} " +
                "tapToReadyMs=${load.tapToReadyMs} hostMs=${load.hostMs} " +
                "helloMs=${load.readyTiming?.timeToHelloMs} " +
                "readyMs=${load.readyTiming?.timeToReadyMs}",
        )
    }

    private fun logSnapshot(snapshot: SeatLayerPickerSnapshot) {
        Log.i(
            TAG,
            "snapshot revision=${snapshot.revision} " +
                "categories=${snapshot.categories.size} " +
                "selection=${snapshot.selection.size} " +
                "rung=${snapshot.map.rung} " +
                "buyerView=${snapshot.map.buyerView}",
        )
    }

    private fun logError(error: SeatLayerException) {
        Log.e(TAG, "picker error", error)
    }

    private fun releaseAsynchronously(block: suspend () -> Unit) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch { block() }
    }

    private companion object {
        const val TAG = "SeatLayerSample"
        const val EXTRA_EVENT = "seatlayerEvent"
        const val EXTRA_INTEGRATION = "seatlayerIntegration"
        const val DEFAULT_EVENT = "ev_test_event"
        const val READY_COMPOSE = "ready-compose"
        const val BRANDED_COMPOSE = "branded-compose"
        const val CUSTOM_COMPOSE = "custom-compose"
        const val READY_VIEW = "ready-view"
        const val CUSTOM_VIEW = "custom-view"
        const val RAW_VIEW = "raw-view"
        const val READY_MARKER = "seatlayer-picker-ready"
    }
}
