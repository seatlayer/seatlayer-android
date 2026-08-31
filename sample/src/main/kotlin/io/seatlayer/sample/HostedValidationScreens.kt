package io.seatlayer.sample

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SeatLayerChartLoad
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff
import io.seatlayer.android.SeatLayerPickerThemeMode
import io.seatlayer.android.compose.SeatLayerPicker
import io.seatlayer.android.compose.SeatLayerPickerTheme
import java.text.NumberFormat
import java.util.Currency

@Composable
internal fun HostedValidationApp(
    model: HostedValidationModel,
    pickerMode: HostedPickerMode,
    useExplicitValidationTheme: Boolean,
    onOpenReadyView: (HostedPickerSession) -> Unit,
    onPickerReady: () -> Unit,
    onFinish: () -> Unit,
) {
    if (!model.isConfigured) {
        MissingLocalConfigurationScreen(
            error = model.eventsError,
            onClose = onFinish,
        )
        return
    }

    when (model.route) {
        HostedDemoRoute.Events -> EventListScreen(
            events = model.events,
            loading = model.eventsLoading,
            error = model.eventsError,
            onOpen = model::openDetails,
            onRetry = model::loadEvents,
            onClose = onFinish,
        )

        HostedDemoRoute.Details -> {
            BackHandler(onBack = model::backToEvents)
            val event = model.detail ?: model.selected
            if (event == null) {
                LoadingScreen("Loading event details…")
            } else {
                EventDetailScreen(
                    event = event,
                    detailReady = model.detail != null,
                    loading = model.detailLoading,
                    detailError = model.detailError,
                    authorizing = model.authorizing,
                    accessError = model.accessError,
                    onBack = model::backToEvents,
                    onBookNow = model::bookNow,
                    onRetryDetails = model::retryDetails,
                )
            }
        }

        HostedDemoRoute.Picker -> {
            val picker = model.picker
            if (picker == null) {
                LoadingScreen("Authorising seat map…")
            } else if (pickerMode == HostedPickerMode.ReadyView) {
                LaunchedEffect(picker) { onOpenReadyView(picker) }
            } else {
                HostedPickerSurface(
                    picker = picker,
                    pickerMode = pickerMode,
                    themeMode = model.pickerThemeMode,
                    useExplicitValidationTheme = useExplicitValidationTheme,
                    onCheckout = { handoff ->
                        Log.i(TAG, "hosted checkout lines=${handoff.lineItems.size}")
                        model.acceptCheckout(handoff)
                    },
                    onReady = { ready ->
                        onPickerReady()
                        Log.i(
                            TAG,
                            "hosted ready integration=${pickerMode.value} " +
                                "protocol=${ready.protocolRevision} " +
                                "mode=${ready.mode.raw} transport=${ready.transport.raw}",
                        )
                    },
                    onChartLoad = { load ->
                        Log.i(
                            TAG,
                            "hosted chart load outcome=${load.trace.outcome} " +
                                "bootMs=${load.trace.bootMs} " +
                                "tapToReadyMs=${load.tapToReadyMs} hostMs=${load.hostMs} " +
                                "helloMs=${load.readyTiming?.timeToHelloMs} " +
                                "readyMs=${load.readyTiming?.timeToReadyMs}",
                        )
                    },
                    onError = { error -> Log.e(TAG, safeSdkError(error)) },
                    onClose = {
                        Log.i(TAG, "hosted close callback")
                        model.leavePicker()
                    },
                )
            }
        }

        HostedDemoRoute.Checkout -> {
            BackHandler(onBack = model::finishCheckout)
            val picker = model.picker
            val handoff = model.checkout
            if (picker == null || handoff == null) {
                LoadingScreen("Preparing checkout handoff…")
            } else {
                CheckoutEvidence(
                    event = picker.event,
                    handoff = handoff,
                    onDone = model::finishCheckout,
                )
            }
        }
    }
}

@Composable
private fun HostedPickerSurface(
    picker: HostedPickerSession,
    pickerMode: HostedPickerMode,
    themeMode: SeatLayerPickerThemeMode,
    useExplicitValidationTheme: Boolean,
    onCheckout: suspend (SeatLayerPickerCheckoutHandoff) -> Unit,
    onReady: (ReadyInfo) -> Unit,
    onChartLoad: (SeatLayerChartLoad) -> Unit,
    onError: (SeatLayerException) -> Unit,
    onClose: () -> Unit,
) {
    val explicitValidationTheme = if (useExplicitValidationTheme) {
        when (themeMode) {
            SeatLayerPickerThemeMode.Light -> SeatLayerPickerTheme.light()
            SeatLayerPickerThemeMode.Dark -> SeatLayerPickerTheme.dark()
            else -> null
        }
    } else {
        null
    }
    Box(Modifier.fillMaxSize()) {
        when (pickerMode) {
            HostedPickerMode.ReadyCompose -> SeatLayerPicker(
                configuration = picker.configuration,
                modifier = Modifier.fillMaxSize(),
                themeMode = themeMode,
                theme = explicitValidationTheme,
                onCheckout = onCheckout,
                onReady = onReady,
                onChartLoad = onChartLoad,
                onError = onError,
                onClose = onClose,
            )

            HostedPickerMode.CustomCompose -> CustomPickerExample(
                configuration = picker.configuration,
                themeMode = themeMode,
                theme = explicitValidationTheme,
                onCheckout = onCheckout,
                onReady = onReady,
                onChartLoad = onChartLoad,
                onError = onError,
                onClose = onClose,
            )

            HostedPickerMode.ReadyView -> Unit // Installed directly by the Activity.
        }
    }
}

@Composable
private fun MissingLocalConfigurationScreen(
    error: String?,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("DESIPASS DEV", color = DesiPassRed, fontWeight = FontWeight.Bold)
        Text("Android SDK demo", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Local DesiPass development configuration was not found. Point " +
                "desipass.envFile in ignored local.properties to an existing .env.local " +
                "file, then rebuild the debug sample.",
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Close") }
    }
}

@Composable
private fun EventListScreen(
    events: List<HostValidationEvent>,
    loading: Boolean,
    error: String?,
    onOpen: (HostValidationEvent) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DesiPassBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text("DESIPASS DEV", color = DesiPassRed, fontWeight = FontWeight.Bold)
                Text("Events", style = MaterialTheme.typography.headlineMedium)
                Text("Choose an event to validate SeatLayer.")
            }
            OutlinedButton(onClick = onClose) { Text("Close") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            loading && events.isEmpty() -> LoadingScreen("Loading events…", safeInsets = false)
            error != null && events.isEmpty() -> ErrorState(error, onRetry)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                error?.let { message -> item { ErrorState(message, onRetry, compact = true) } }
                items(events, key = HostValidationEvent::id) { event ->
                    EventCard(event = event, onOpen = { onOpen(event) })
                }
                item {
                    OutlinedButton(
                        onClick = onRetry,
                        enabled = !loading,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Reload events") }
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: HostValidationEvent,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(DesiPassRed),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "DP",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
            )
        }
        Column(Modifier.padding(16.dp)) {
            Text(eventDate(event), color = DesiPassRed, fontWeight = FontWeight.Bold)
            Text(
                event.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    eventLocation(event),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    eventPrice(event),
                    color = DesiPassInk,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EventDetailScreen(
    event: HostValidationEvent,
    detailReady: Boolean,
    loading: Boolean,
    detailError: String?,
    authorizing: Boolean,
    accessError: String?,
    onBack: () -> Unit,
    onBookNow: () -> Unit,
    onRetryDetails: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DesiPassBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        BackBar(title = "Event details", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(DesiPassRed),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "DP",
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(Modifier.padding(20.dp)) {
                Text(eventDate(event), color = DesiPassRed, fontWeight = FontWeight.Bold)
                Text(
                    event.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                DetailBlock("WHEN", eventDate(event))
                DetailBlock("WHERE", eventLocation(event), event.venueAddress)
                if (loading) {
                    CircularProgressIndicator(Modifier.padding(top = 20.dp))
                }
                detailError?.let {
                    ErrorState(it, onRetryDetails, compact = true)
                }
                accessError?.let {
                    ErrorState(it, onBookNow, compact = true)
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(eventPrice(event), style = MaterialTheme.typography.titleLarge)
                Text("onwards", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                enabled = detailReady && !authorizing,
                onClick = onBookNow,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    when {
                        authorizing -> "AUTHORISING…"
                        loading -> "LOADING…"
                        else -> "BOOK NOW"
                    },
                )
            }
        }
    }
}

@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .background(Color.White)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .size(width = 56.dp, height = 48.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Text("←", style = MaterialTheme.typography.titleLarge) }
        Text(
            title,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailBlock(label: String, value: String, secondary: String? = null) {
    Spacer(Modifier.height(22.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    Text(label, color = Color(0xFF918A97), fontWeight = FontWeight.Bold)
    Text(value, color = DesiPassInk, style = MaterialTheme.typography.titleMedium)
    secondary?.takeIf(String::isNotBlank)?.let {
        Text(it, color = Color(0xFF766F7C))
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (compact) 12.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Something went wrong", fontWeight = FontWeight.Bold)
        Text(message)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Try again") }
    }
}

@Composable
private fun LoadingScreen(message: String, safeInsets: Boolean = true) {
    val modifier = if (safeInsets) {
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
    } else {
        Modifier.fillMaxSize()
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(message)
        }
    }
}

@Composable
private fun CheckoutEvidence(
    event: HostValidationEvent,
    handoff: SeatLayerPickerCheckoutHandoff,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesiPassBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(DesiPassRed, MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = Color.White, style = MaterialTheme.typography.headlineLarge)
        }
        Spacer(Modifier.height(16.dp))
        Text("SELECTION READY", color = DesiPassRed, fontWeight = FontWeight.Bold)
        Text(
            event.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text("${handoff.lineItems.sumOf { it.quantity }} ticket(s) held for checkout.")
        Spacer(Modifier.height(16.dp))
        Text("Total", style = MaterialTheme.typography.labelLarge)
        Text(
            formatMoney(handoff.total, handoff.currency),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This demo stops at the secure inventory handoff. The opaque hold id " +
                "is intentionally neither rendered nor logged.",
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("BACK TO EVENTS") }
    }
}

private fun eventDate(event: HostValidationEvent): String =
    listOfNotNull(
        event.startDate?.takeIf(String::isNotBlank),
        event.startTime?.take(5)?.takeIf(String::isNotBlank),
    ).joinToString(" · ").ifBlank { "Date to be announced" }

private fun eventLocation(event: HostValidationEvent): String =
    event.venue?.takeIf(String::isNotBlank)
        ?: event.city?.takeIf(String::isNotBlank)
        ?: "Venue to be announced"

private fun eventPrice(event: HostValidationEvent): String = when {
    event.isFree -> "Free"
    event.minimumPrice != null -> formatMoney(event.minimumPrice, event.currency ?: "EUR")
    else -> "—"
}

private fun formatMoney(amount: Double, currency: String?): String {
    val formatter = NumberFormat.getCurrencyInstance()
    runCatching { currency?.let(Currency::getInstance) }
        .getOrNull()
        ?.let { formatter.currency = it }
    return formatter.format(amount)
}

internal fun safeSdkError(error: SeatLayerException): String =
    "hosted picker error type=${error::class.java.simpleName}"

private const val TAG = "SeatLayerHosted"
