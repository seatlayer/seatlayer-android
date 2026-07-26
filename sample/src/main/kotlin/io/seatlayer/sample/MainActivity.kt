package io.seatlayer.sample

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerEvent
import io.seatlayer.android.SeatLayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var seatLayerView: SeatLayerView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(248, 250, 252))
        }
        seatLayerView = SeatLayerView(this)
        status = TextView(this).apply {
            setTextColor(Color.rgb(51, 65, 85))
            setBackgroundColor(Color.argb(230, 255, 255, 255))
            setPadding(32, 20, 32, 20)
            text = "Loading SeatLayer…"
        }
        root.addView(
            seatLayerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            status,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        setContentView(root)

        scope.launch {
            seatLayerView.controller.events.collect { event ->
                if (event is SeatLayerEvent.SelectionChanged) {
                    status.text = if (event.seats.isEmpty()) {
                        "Choose your seats"
                    } else {
                        event.seats.joinToString { it.buyerFacingLabel }
                    }
                }
            }
        }

        scope.launch {
            runCatching {
                seatLayerView.load(
                    SeatLayerConfiguration(
                        event = "ev_your_event_key",
                        currency = "USD",
                    ),
                )
            }.onSuccess {
                status.text = "SeatLayer ready · ${it.mode.raw}"
            }.onFailure {
                status.text = "Could not load SeatLayer: ${it.message}"
            }
        }
    }

    override fun onDestroy() {
        seatLayerView.destroy()
        scope.cancel()
        super.onDestroy()
    }
}
