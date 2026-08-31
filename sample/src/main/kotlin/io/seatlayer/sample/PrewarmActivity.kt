package io.seatlayer.sample

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.seatlayer.android.SeatLayerPickerPrewarmer
import kotlinx.coroutines.launch

/** Credential-free process warmup target used by the physical benchmark lane. */
public class PrewarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            gravity = Gravity.CENTER
            text = "Starting Android WebView engine…"
        }
        setContentView(status)
        val startedAt = SystemClock.elapsedRealtime()
        lifecycleScope.launch {
            runCatching { SeatLayerPickerPrewarmer.prewarm(this@PrewarmActivity) }
                .onSuccess { result ->
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    status.text = "SeatLayer WebView engine ready"
                    status.contentDescription = PREWARM_READY_MARKER
                    Log.i(
                        TAG,
                        "engine=true page=${result.rendererPageLoaded} " +
                            "session=${result.pickerSessionCreated} " +
                            "elapsedMs=$elapsed uiMs=${result.totalUiThreadMillis}",
                    )
                    reportFullyDrawn()
                }
                .onFailure { error ->
                    status.text = "SeatLayer WebView engine startup failed"
                    status.contentDescription = PREWARM_FAILED_MARKER
                    Log.e(TAG, "prewarm failed", error)
                    reportFullyDrawn()
                }
        }
    }

    public companion object {
        public const val PREWARM_READY_MARKER: String = "seatlayer-prewarm-ready"
        public const val PREWARM_FAILED_MARKER: String = "seatlayer-prewarm-failed"
        private const val TAG: String = "SeatLayerPrewarm"
    }
}
