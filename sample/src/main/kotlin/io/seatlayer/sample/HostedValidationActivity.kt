package io.seatlayer.sample

import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff
import io.seatlayer.android.SeatLayerPickerPrewarmer
import io.seatlayer.android.SeatLayerPickerThemeMode
import io.seatlayer.android.compose.SeatLayerPickerTheme
import io.seatlayer.android.compose.SeatLayerPickerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * DesiPass-shaped Android demo and owner-assisted real-inventory gate.
 *
 * The development client key is sourced from an ignored local environment for
 * debug builds. Release and benchmark variants always receive an empty value.
 * The sample passes only renewable buyer access to the SeatLayer picker.
 */
public class HostedValidationActivity : ComponentActivity() {
    private lateinit var hostedModel: HostedValidationModel
    private var hostedPickerMode: HostedPickerMode = HostedPickerMode.ReadyCompose
    private var validationHardwareControls: Boolean = false
    private var hostedReadyView: SeatLayerPickerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hostedModel = ViewModelProvider(this)[HostedValidationModel::class.java]
        hostedModel.configure(
            endpoint = BuildConfig.DESIPASS_GRAPHQL_URL,
            apiKey = BuildConfig.DESIPASS_API_KEY,
        )
        hostedPickerMode = HostedPickerMode.from(
            intent.getStringExtra(HOSTED_INTEGRATION_EXTRA),
        )
        validationHardwareControls = intent.getBooleanExtra(
            HOSTED_VALIDATION_CONTROLS_EXTRA,
            false,
        )
        setSystemBarsForLightSurface(true)
        showComposeContent()
    }

    private fun showComposeContent() {
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = DesiPassRed,
                    onPrimary = Color.White,
                    secondary = DesiPassInk,
                    background = DesiPassBackground,
                    surface = Color.White,
                ),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    HostedValidationApp(
                        model = hostedModel,
                        pickerMode = hostedPickerMode,
                        useExplicitValidationTheme = validationHardwareControls,
                        onOpenReadyView = ::showHostedReadyView,
                        onPickerReady = { setSystemBarsForLightSurface(false) },
                        onFinish = ::finish,
                    )
                }
            }
            LaunchedEffect(hostedModel.route) {
                if (hostedModel.route != HostedDemoRoute.Picker) {
                    setSystemBarsForLightSurface(true)
                }
            }
        }
    }

    private fun showHostedReadyView(picker: HostedPickerSession) {
        if (hostedReadyView != null) return
        window.decorView.post {
            if (
                hostedReadyView != null ||
                hostedPickerMode != HostedPickerMode.ReadyView ||
                hostedModel.route != HostedDemoRoute.Picker ||
                hostedModel.picker != picker
            ) {
                return@post
            }
            val explicitTheme = if (validationHardwareControls) {
                when (hostedModel.pickerThemeMode) {
                    SeatLayerPickerThemeMode.Light -> SeatLayerPickerTheme.light()
                    SeatLayerPickerThemeMode.Dark -> SeatLayerPickerTheme.dark()
                    else -> null
                }
            } else {
                null
            }
            val pickerView = SeatLayerPickerView(this)
            hostedReadyView = pickerView
            setContentView(pickerView)
            pickerView.bind(
                lifecycleOwner = this,
                configuration = picker.configuration,
                themeMode = hostedModel.pickerThemeMode,
                theme = explicitTheme,
                onCheckout = { handoff ->
                    Log.i("SeatLayerHosted", "hosted checkout lines=${handoff.lineItems.size}")
                    hostedModel.acceptCheckout(handoff)
                    window.decorView.post { restoreComposeFromReadyView() }
                },
                onReady = { ready ->
                    setSystemBarsForLightSurface(false)
                    Log.i(
                        "SeatLayerHosted",
                        "hosted ready integration=${hostedPickerMode.value} " +
                            "protocol=${ready.protocolRevision} " +
                            "mode=${ready.mode.raw} transport=${ready.transport.raw}",
                    )
                },
                onChartLoad = { load ->
                    Log.i(
                        "SeatLayerHosted",
                        "hosted chart load outcome=${load.trace.outcome} " +
                            "bootMs=${load.trace.bootMs} " +
                            "tapToReadyMs=${load.tapToReadyMs} hostMs=${load.hostMs} " +
                            "helloMs=${load.readyTiming?.timeToHelloMs} " +
                            "readyMs=${load.readyTiming?.timeToReadyMs}",
                    )
                },
                onError = { error -> Log.e("SeatLayerHosted", safeSdkError(error)) },
                onClose = {
                    Log.i("SeatLayerHosted", "hosted close callback")
                    hostedModel.leavePicker()
                    window.decorView.post { restoreComposeFromReadyView() }
                },
            )
        }
    }

    private fun restoreComposeFromReadyView() {
        val pickerView = hostedReadyView ?: return
        hostedReadyView = null
        setSystemBarsForLightSurface(true)
        lifecycleScope.launch {
            pickerView.close()
            showComposeContent()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (
            validationHardwareControls &&
            hostedModel.route == HostedDemoRoute.Picker &&
            keyCode == KeyEvent.KEYCODE_VOLUME_UP
        ) {
            val mode = hostedModel.cyclePickerTheme()
            setSystemBarsForLightSurface(mode == SeatLayerPickerThemeMode.Light)
            hostedReadyView?.setThemeMode(mode)
            hostedReadyView?.setTheme(
                when (mode) {
                    SeatLayerPickerThemeMode.Light -> SeatLayerPickerTheme.light()
                    SeatLayerPickerThemeMode.Dark -> SeatLayerPickerTheme.dark()
                    else -> null
                },
            )
            Log.i(
                "SeatLayerHosted",
                "hosted theme integration=${hostedPickerMode.value} mode=${mode.raw}",
            )
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setSystemBarsForLightSurface(lightSurface: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightSurface
            isAppearanceLightNavigationBars = lightSurface
        }
    }
}

internal enum class HostedDemoRoute { Events, Details, Picker, Checkout }

internal enum class HostedPickerMode(internal val value: String) {
    ReadyCompose("ready-compose"),
    CustomCompose("custom-compose"),
    ReadyView("ready-view"),
    ;

    internal companion object {
        internal fun from(value: String?): HostedPickerMode =
            entries.firstOrNull { it.value == value } ?: ReadyCompose
    }
}

internal data class HostedPickerSession(
    val event: HostValidationEvent,
    val configuration: SeatLayerConfiguration,
)

internal class HostedValidationModel(application: Application) : AndroidViewModel(application) {
    private var client: HostValidationClient? by mutableStateOf(null)
    private var accessPreparation: AccessPreparation? = null

    var route: HostedDemoRoute by mutableStateOf(HostedDemoRoute.Events)
        private set
    var events: List<HostValidationEvent> by mutableStateOf(emptyList())
        private set
    var eventsLoading: Boolean by mutableStateOf(false)
        private set
    var eventsError: String? by mutableStateOf(null)
        private set
    var selected: HostValidationEvent? by mutableStateOf(null)
        private set
    var detail: HostValidationEvent? by mutableStateOf(null)
        private set
    var detailLoading: Boolean by mutableStateOf(false)
        private set
    var detailError: String? by mutableStateOf(null)
        private set
    var authorizing: Boolean by mutableStateOf(false)
        private set
    var accessError: String? by mutableStateOf(null)
        private set
    var picker: HostedPickerSession? by mutableStateOf(null)
        private set
    var checkout: SeatLayerPickerCheckoutHandoff? by mutableStateOf(null)
        private set
    var pickerThemeMode: SeatLayerPickerThemeMode by
        mutableStateOf(SeatLayerPickerThemeMode.Auto)
        private set

    val isConfigured: Boolean get() = client != null

    fun configure(endpoint: String, apiKey: String): Boolean {
        if (client != null) return true
        val next = runCatching { HostValidationClient(endpoint, apiKey) }
            .getOrElse {
                eventsError = messageOf(it)
                return false
            }
        client = next
        eventsError = null
        route = HostedDemoRoute.Events
        loadEvents()
        return true
    }

    fun loadEvents() {
        val currentClient = client ?: return
        eventsLoading = true
        eventsError = null
        viewModelScope.launch {
            try {
                events = currentClient.fetchEvents()
                if (events.isEmpty()) {
                    eventsError = "No SeatLayer demo events are available."
                }
            } catch (failure: Throwable) {
                eventsError = messageOf(failure)
            } finally {
                eventsLoading = false
            }
        }
    }

    fun openDetails(event: HostValidationEvent) {
        selected = event
        detail = null
        detailError = null
        accessError = null
        route = HostedDemoRoute.Details
        prepareSeatMap(event)
        fetchDetail(event)
    }

    private fun prepareSeatMap(event: HostValidationEvent) {
        val currentClient = client ?: return
        accessPreparation?.deferred?.cancel()
        accessPreparation = AccessPreparation(
            eventId = event.id,
            deferred = viewModelScope.async {
                currentClient.createSeatLayerAccess(event.id)
            },
        )
        viewModelScope.launch {
            try {
                val result = SeatLayerPickerPrewarmer.prewarm(getApplication())
                Log.i(
                    "SeatLayerHosted",
                    "details prewarm engine=${result.engineStarted} " +
                        "page=${result.rendererPageLoaded}",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w("SeatLayerHosted", "details prewarm unavailable", error)
            }
        }
    }

    fun retryDetails() {
        selected?.let(::fetchDetail)
    }

    private fun fetchDetail(event: HostValidationEvent) {
        val currentClient = client ?: return
        detailLoading = true
        detailError = null
        viewModelScope.launch {
            try {
                val result = currentClient.fetchEvent(event.id)
                if (selected?.id == event.id) detail = result
            } catch (failure: Throwable) {
                if (selected?.id == event.id) detailError = messageOf(failure)
            } finally {
                if (selected?.id == event.id) detailLoading = false
            }
        }
    }

    fun backToEvents() {
        accessPreparation?.deferred?.cancel()
        accessPreparation = null
        route = HostedDemoRoute.Events
        selected = null
        detail = null
        detailError = null
        accessError = null
        authorizing = false
    }

    fun bookNow() {
        if (authorizing) return
        val currentClient = client ?: return
        val currentDetail = detail ?: return
        authorizing = true
        accessError = null
        viewModelScope.launch {
            try {
                val prepared = accessPreparation
                    ?.takeIf { it.eventId == currentDetail.id }
                    ?: AccessPreparation(
                        eventId = currentDetail.id,
                        deferred = async {
                            currentClient.createSeatLayerAccess(currentDetail.id)
                        },
                    ).also { accessPreparation = it }
                val access = prepared.deferred.await()
                if (detail?.id != currentDetail.id) return@launch
                picker = HostedPickerSession(
                    event = currentDetail,
                    configuration = SeatLayerConfiguration(
                        event = requireNotNull(currentDetail.seatEventKey),
                        apiBase = access.apiBase,
                        currency = currentDetail.currency?.trim().orEmpty().ifBlank { "EUR" },
                        locale = "en-GB",
                        maxSelection = 10,
                        buyerAccessTokenProvider = access.provider,
                        hostInfo = mapOf("app" to "DesiPass Android validation"),
                    ),
                )
                route = HostedDemoRoute.Picker
            } catch (failure: Throwable) {
                if (detail?.id == currentDetail.id) {
                    accessPreparation = null
                    accessError = messageOf(failure)
                }
            } finally {
                if (detail?.id == currentDetail.id) authorizing = false
            }
        }
    }

    fun acceptCheckout(value: SeatLayerPickerCheckoutHandoff) {
        checkout = value
        route = HostedDemoRoute.Checkout
    }

    fun cyclePickerTheme(): SeatLayerPickerThemeMode {
        pickerThemeMode = when (pickerThemeMode) {
            SeatLayerPickerThemeMode.Auto -> SeatLayerPickerThemeMode.Light
            SeatLayerPickerThemeMode.Light -> SeatLayerPickerThemeMode.Dark
            else -> SeatLayerPickerThemeMode.Auto
        }
        return pickerThemeMode
    }

    fun leavePicker() {
        picker = null
        checkout = null
        route = HostedDemoRoute.Details
    }

    fun finishCheckout() {
        accessPreparation?.deferred?.cancel()
        accessPreparation = null
        picker = null
        checkout = null
        selected = null
        detail = null
        detailError = null
        accessError = null
        route = HostedDemoRoute.Events
    }

    private data class AccessPreparation(
        val eventId: String,
        val deferred: Deferred<HostSeatLayerAccess>,
    )
}

internal fun messageOf(error: Throwable): String =
    error.message?.takeIf(String::isNotBlank) ?: "Please try again."

internal val DesiPassRed: Color = Color(0xFFE54558)
internal val DesiPassInk: Color = Color(0xFF3B2D4C)
internal val DesiPassBackground: Color = Color(0xFFF8F7FA)
internal const val HOSTED_INTEGRATION_EXTRA: String = "seatlayerHostedIntegration"
internal const val HOSTED_VALIDATION_CONTROLS_EXTRA: String =
    "seatlayerHostedValidationControls"
