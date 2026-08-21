package io.seatlayer.sample

import android.app.Activity
import android.os.Bundle
import android.util.Log
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerEvent
import io.seatlayer.android.SeatLayerView
import io.seatlayer.android.SelectionValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Minimal consumer app used by the release build and manual device smoke test. */
public class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var seatLayerView: SeatLayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        seatLayerView = SeatLayerView(this)
        setContentView(seatLayerView)

        scope.launch {
            seatLayerView.controller.events.collect { event ->
                when (event) {
                    is SeatLayerEvent.SelectionChanged ->
                        Log.i(TAG, "selection=${event.seats.size}")
                    is SeatLayerEvent.SelectionValidityChanged ->
                        Log.i(TAG, "selection-valid=${event.validity.isValid}")
                    is SeatLayerEvent.Error ->
                        Log.e(TAG, "picker-error=${event.error.code}")
                    else -> Unit
                }
            }
        }

        scope.launch {
            runCatching {
                seatLayerView.load(
                    SeatLayerConfiguration(
                        event = intent.getStringExtra(EXTRA_EVENT) ?: DEFAULT_EVENT,
                        currency = "USD",
                        maxSelection = 4,
                        numberOfPlacesToSelect = 2,
                        selectionValidators = listOf(
                            SelectionValidator.MinimumSelectedPlaces(2),
                            SelectionValidator.ConsecutiveSeats,
                        ),
                        hostInfo = mapOf("app" to "SeatLayerAndroidSample/0.2.0"),
                    ),
                )
            }.onSuccess { ready ->
                Log.i(TAG, "ready protocol=${ready.protocolRevision} mode=${ready.mode.raw}")
            }.onFailure { error ->
                Log.e(TAG, "load failed", error)
            }
        }
    }

    override fun onDestroy() {
        if (::seatLayerView.isInitialized) seatLayerView.destroy()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SeatLayerSample"
        const val EXTRA_EVENT = "seatlayerEvent"
        const val DEFAULT_EVENT = "ev_test_event"
    }
}
