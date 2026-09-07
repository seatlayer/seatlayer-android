package io.seatlayer.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerPickerController
import io.seatlayer.android.SeatLayerPickerState
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerThemeMode

@Immutable
public interface SeatLayerPickerScope {
    public val stateHolder: SeatLayerPickerStateHolder
    public val state: SeatLayerPickerState
    public val controller: SeatLayerPickerController
    public val theme: SeatLayerPickerTheme
    public val themeMode: SeatLayerPickerThemeMode
    public val strings: SeatLayerPickerStrings
    public val options: SeatLayerPickerOptions
    public val styles: SeatLayerPickerStyles
    public val moneyFormatter: SeatLayerMoneyFormatter
    public val callbacks: SeatLayerPickerCallbacks
}

private data class DefaultSeatLayerPickerScope(
    override val stateHolder: SeatLayerPickerStateHolder,
    override val state: SeatLayerPickerState,
    override val theme: SeatLayerPickerTheme,
    override val themeMode: SeatLayerPickerThemeMode,
    override val strings: SeatLayerPickerStrings,
    override val options: SeatLayerPickerOptions,
    override val styles: SeatLayerPickerStyles,
    override val moneyFormatter: SeatLayerMoneyFormatter,
    override val callbacks: SeatLayerPickerCallbacks,
) : SeatLayerPickerScope {
    override val controller: SeatLayerPickerController = stateHolder.controller
}

/**
 * The reasons that mean the buyer's own session lapsed.
 *
 * A revoked link and a link that was never verified are different facts and
 * are reported only through `onAccessUnavailable`.
 */
private val SEATLAYER_ACCESS_EXPIRY_REASONS = setOf("no_token", "provider_failed")

internal val LocalSeatLayerPickerScope = compositionLocalOf<SeatLayerPickerScope> {
    error("SeatLayer picker component must be placed inside SeatLayerPickerScope")
}

@Composable
public fun rememberSeatLayerPickerStateHolder(
    configuration: SeatLayerConfiguration,
    options: SeatLayerPickerOptions = SeatLayerPickerOptions(),
): SeatLayerPickerStateHolder {
    val behavior = options.behavior()
    return remember(configuration, behavior) {
        SeatLayerPickerStateHolder(
            configuration = configuration,
            behavior = behavior,
        )
    }
}

/** Installs one state/controller/theme scope for host-owned composition. */
@Composable
public fun SeatLayerPickerScope(
    configuration: SeatLayerConfiguration,
    options: SeatLayerPickerOptions = SeatLayerPickerOptions(),
    stateHolder: SeatLayerPickerStateHolder = rememberSeatLayerPickerStateHolder(
        configuration,
        options,
    ),
    themeMode: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode.Auto,
    theme: SeatLayerPickerTheme? = null,
    strings: SeatLayerPickerStrings = pickerDefaultStrings(),
    styles: SeatLayerPickerStyles = SeatLayerPickerStyles(),
    moneyFormatter: SeatLayerMoneyFormatter = SeatLayerMoneyFormatter.localized(),
    callbacks: SeatLayerPickerCallbacks = SeatLayerPickerCallbacks(),
    content: @Composable SeatLayerPickerScope.() -> Unit,
) {
    val state by stateHolder.state.collectAsStateWithLifecycle()
    SeatLayerPickerThemeProvider(
        mode = themeMode,
        explicitTheme = theme,
        branding = state.snapshot?.branding,
    ) {
        val resolvedTheme = LocalSeatLayerPickerTheme.current
        val scope = remember(
            stateHolder,
            state,
            resolvedTheme,
            themeMode,
            strings,
            options,
            styles,
            moneyFormatter,
            callbacks,
        ) {
            DefaultSeatLayerPickerScope(
                stateHolder = stateHolder,
                state = state,
                theme = resolvedTheme,
                themeMode = themeMode,
                strings = strings,
                options = options,
                styles = styles,
                moneyFormatter = moneyFormatter,
                callbacks = callbacks,
            )
        }

        LaunchedEffect(
            state.isReady,
            themeMode.raw,
            resolvedTheme.mapTheme,
            stateHolder,
        ) {
            if (state.isReady) {
                scope.performAction {
                    stateHolder.controller.setThemeMode(
                        mode = themeMode,
                        mapTheme = resolvedTheme.mapTheme,
                    )
                }
            }
        }

        LaunchedEffect(state.phase) {
            (state.phase as? io.seatlayer.android.SeatLayerPickerPhase.Ready)
                ?.info
                ?.let(callbacks.onReady)
        }
        LaunchedEffect(stateHolder, callbacks.onChartLoad) {
            stateHolder.chartLoads.collect(callbacks.onChartLoad)
        }
        LaunchedEffect(state.snapshot?.map?.focusedSectionId, state.snapshot?.sessionId) {
            state.snapshot?.let { snapshot ->
                callbacks.onSectionFocused(
                    snapshot.map.focusedSection
                        ?: snapshot.sections.firstOrNull {
                            it.id == snapshot.map.focusedSectionId
                        },
                )
            }
        }
        LaunchedEffect(state.snapshot?.sessionId, state.snapshot?.revision) {
            state.snapshot?.let { snapshot ->
                callbacks.onSnapshot(snapshot)
                callbacks.onSelectionChanged(snapshot.selection)
                callbacks.onHoldChanged(snapshot.hold)
            }
        }
        // The palette the picker actually resolved, after branding and the
        // host's own theme have had their say — a host matching its checkout
        // to the map cannot compute this itself.
        LaunchedEffect(resolvedTheme, callbacks.onThemeResolved) {
            callbacks.onThemeResolved(resolvedTheme)
        }
        LaunchedEffect(state.snapshot?.selectionValidity, callbacks.onSelectionValidityChanged) {
            callbacks.onSelectionValidityChanged(state.snapshot?.selectionValidity)
        }
        // The sale, not the hand-off: a buyer on the way to pay has not paid.
        LaunchedEffect(state.presentation.bookedHandoff, callbacks.onBooked) {
            state.presentation.bookedHandoff?.let(callbacks.onBooked)
        }
        // The runtime's own expiry signal. A snapshot only shows a hold going
        // inactive, which is also what a deliberate release and a sale look
        // like, so the host is told from the event rather than from state.
        LaunchedEffect(stateHolder, callbacks.onHoldExpired) {
            stateHolder.holdExpiries.collect { callbacks.onHoldExpired() }
        }
        // Seats taken from under the buyer. Also an event rather than a state:
        // the next snapshot shows a shorter cart, which is what removing a
        // seat looks like too.
        LaunchedEffect(stateHolder, callbacks.onSelectedObjectUnavailable) {
            stateHolder.unavailableSelections.collect(callbacks.onSelectedObjectUnavailable)
        }
        // One session the server will no longer answer for, told once per
        // reason. `onAccessExpired` is the narrower of the two: it fires only
        // where the session itself lapsed rather than where the link was
        // revoked or was never verified.
        val accessReason = state.snapshot
            ?.takeIf { it.accessConfigured && it.accessStatus == "unavailable" }
            ?.accessReason
        LaunchedEffect(state.snapshot?.accessStatus, accessReason) {
            if (state.snapshot?.accessStatus != "unavailable") return@LaunchedEffect
            if (state.snapshot?.accessConfigured != true) return@LaunchedEffect
            callbacks.onAccessUnavailable(accessReason)
            if (accessReason in SEATLAYER_ACCESS_EXPIRY_REASONS) callbacks.onAccessExpired()
        }

        androidx.compose.runtime.CompositionLocalProvider(
            LocalSeatLayerPickerScope provides scope,
        ) {
            scope.content()
        }
    }
}

@Composable
internal fun SeatLayerPickerPart(
    part: SeatLayerPickerPart,
    builder: SeatLayerPickerPartBuilder?,
    content: @Composable () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (builder == null) {
        content()
    } else {
        builder(
            SeatLayerPickerPartContext(
                part = part,
                state = scope.state,
                snapshot = scope.state.snapshot,
                presentation = scope.state.presentation,
                controller = scope.controller,
                themeMode = scope.themeMode,
                theme = scope.theme,
                strings = scope.strings,
                options = scope.options,
                styles = scope.styles,
                style = scope.styles[part],
            ),
            content,
        )
    }
}

/**
 * The wash drawn over the map while a card is up.
 *
 * A host's own [SeatLayerPickerStyles.scrimColor] wins outright; otherwise the
 * theme's background carries the design source's confirm scrim, so a rebranded
 * picker dims to its own ground rather than to somebody else's grey.
 */
internal fun SeatLayerPickerScope.confirmScrim(): Color =
    styles.scrimColor
        ?: theme.background.copy(alpha = SeatLayerOpacityTokens.CONFIRM_SCRIM)
