package io.seatlayer.sample

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.seatlayer.android.EventMode
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff
import io.seatlayer.android.SeatLayerPickerMapView
import io.seatlayer.android.SeatLayerPickerPhase
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerPickerState
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerViewportInsets
import kotlinx.coroutines.launch

/** Pure View/XML-style composition over the public headless picker APIs. */
public class CustomPickerViewExample(
    context: Context,
    private val configuration: SeatLayerConfiguration,
) : FrameLayout(context) {
    public val stateHolder: SeatLayerPickerStateHolder =
        SeatLayerPickerStateHolder(configuration)

    private val map = SeatLayerPickerMapView(context)
    private val title = TextView(context)
    private val status = TextView(context)
    private val continueButton = Button(context)
    private val topBar = LinearLayout(context)
    private val bottomBar = LinearLayout(context)
    private var lifecycleOwner: LifecycleOwner? = null
    private var errorHandler: (SeatLayerException) -> Unit = {}
    private var lastInsets: SeatLayerPickerViewportInsets? = null
    private var lastReadyInfo: ReadyInfo? = null
    private var lastSnapshotRevision: Int? = null
    private var lastLifecycleState: String? = null
    private var lifecycleObserver: LifecycleEventObserver? = null
    private var backCallback: OnBackPressedCallback? = null

    init {
        addView(map, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(createTopBar(), overlayParams(Gravity.TOP))
        addView(createBottomBar(), overlayParams(Gravity.BOTTOM))
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            topBar.setPadding(dp(16), dp(8) + systemBars.top, dp(8), dp(8))
            bottomBar.setPadding(dp(16), dp(8), dp(8), dp(8) + systemBars.bottom)
            post(::updateInsets)
            windowInsets
        }
        ViewCompat.requestApplyInsets(this)
    }

    public fun bind(
        lifecycleOwner: LifecycleOwner,
        onCheckout: suspend (SeatLayerPickerCheckoutHandoff) -> Unit = {},
        onError: (SeatLayerException) -> Unit = {},
        onReady: (ReadyInfo) -> Unit = {},
        onSnapshot: (SeatLayerPickerSnapshot) -> Unit = {},
        onClose: () -> Unit,
    ) {
        check(this.lifecycleOwner == null) { "Custom picker example is already bound." }
        this.lifecycleOwner = lifecycleOwner
        errorHandler = onError
        installLifecycleAndBackHandling(lifecycleOwner, onClose)
        topBar.findViewWithTag<Button>(CLOSE_TAG).setOnClickListener { onClose() }
        continueButton.setOnClickListener {
            lifecycleOwner.lifecycleScope.launch {
                try {
                    stateHolder.controller.handoffCheckout(onCheckout)
                } catch (error: SeatLayerException) {
                    onError(error)
                }
            }
        }
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                stateHolder.state.collect { state ->
                    render(state)
                    (state.phase as? SeatLayerPickerPhase.Ready)?.info?.let { info ->
                        if (info != lastReadyInfo) {
                            lastReadyInfo = info
                            onReady(info)
                        }
                    }
                    state.snapshot?.let { snapshot ->
                        if (snapshot.revision != lastSnapshotRevision) {
                            lastSnapshotRevision = snapshot.revision
                            onSnapshot(snapshot)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycleScope.launch {
            try {
                map.bind(stateHolder)
            } catch (error: SeatLayerException) {
                onError(error)
            }
        }
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateInsets() }
    }

    public suspend fun close() {
        lifecycleObserver?.let { lifecycleOwner?.lifecycle?.removeObserver(it) }
        lifecycleObserver = null
        backCallback?.remove()
        backCallback = null
        map.close()
        lifecycleOwner = null
    }

    private fun installLifecycleAndBackHandling(
        owner: LifecycleOwner,
        onClose: () -> Unit,
    ) {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> dispatchLifecycle("foreground")
                Lifecycle.Event.ON_STOP -> dispatchLifecycle("background")
                else -> Unit
            }
        }.also { observer ->
            lifecycleObserver = observer
            owner.lifecycle.addObserver(observer)
        }
        (owner as? OnBackPressedDispatcherOwner)?.let { backOwner ->
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    owner.lifecycleScope.launch {
                        try {
                            stateHolder.controller.back { onClose() }
                        } catch (error: SeatLayerException) {
                            errorHandler(error)
                        }
                    }
                }
            }.also { callback ->
                backCallback = callback
                backOwner.onBackPressedDispatcher.addCallback(owner, callback)
            }
        }
    }

    private fun createTopBar(): View = topBar.apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(8), dp(8), dp(8))
        setBackgroundColor(Color.WHITE)
        minimumHeight = dp(56)
        title.text = configuration.event
        title.textSize = 18f
        addView(title, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(
            Button(context).apply {
                tag = CLOSE_TAG
                text = "Close"
                minimumHeight = dp(48)
            },
        )
    }

    private fun createBottomBar(): View = bottomBar.apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(8), dp(8), dp(8))
        setBackgroundColor(Color.WHITE)
        minimumHeight = dp(64)
        status.text = "Powered by SeatLayer"
        addView(status, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        continueButton.text = "Continue"
        continueButton.isEnabled = false
        continueButton.minimumHeight = dp(48)
        addView(continueButton)
    }

    private fun render(state: SeatLayerPickerState) {
        title.text = state.snapshot?.event?.name ?: configuration.event
        val count = state.snapshot?.ticketCount ?: 0
        val modeDisclosure = state.snapshot?.event?.mode
            ?.takeUnless { it == EventMode.Live }
            ?.raw
            ?.let { "$it · " }
            .orEmpty()
        status.text = "${modeDisclosure}Powered by SeatLayer · $count tickets"
        continueButton.isEnabled = stateHolder.controller.canCheckout &&
            !state.presentation.actionInFlight
        if (state.phase is SeatLayerPickerPhase.Ready) {
            updateInsets()
            dispatchLifecycle(
                if (
                    lifecycleOwner?.lifecycle?.currentState
                        ?.isAtLeast(Lifecycle.State.RESUMED) == true
                ) {
                    "foreground"
                } else {
                    "background"
                },
            )
        }
    }

    private fun dispatchLifecycle(state: String) {
        val owner = lifecycleOwner ?: return
        if (!stateHolder.state.value.isReady || lastLifecycleState == state) return
        lastLifecycleState = state
        owner.lifecycleScope.launch {
            try {
                stateHolder.controller.lifecycle(state)
            } catch (error: SeatLayerException) {
                if (lastLifecycleState == state) lastLifecycleState = null
                errorHandler(error)
            }
        }
    }

    private fun updateInsets() {
        val owner = lifecycleOwner ?: return
        if (!stateHolder.controller.supportsViewportInsets) return
        val density = resources.displayMetrics.density.toDouble()
        val next = SeatLayerPickerViewportInsets(
            top = topBar.height / density,
            bottom = bottomBar.height / density,
        )
        if (next == lastInsets) return
        lastInsets = next
        owner.lifecycleScope.launch {
            try {
                stateHolder.controller.setViewportInsets(next)
            } catch (error: SeatLayerException) {
                lastInsets = null
                errorHandler(error)
            }
        }
    }

    private fun overlayParams(gravity: Int): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, gravity)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val CLOSE_TAG = "seatlayer-close"
    }
}
