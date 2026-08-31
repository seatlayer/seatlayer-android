package io.seatlayer.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    public val moneyFormatter: SeatLayerPickerMoneyFormatter
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
    override val moneyFormatter: SeatLayerPickerMoneyFormatter,
    override val callbacks: SeatLayerPickerCallbacks,
) : SeatLayerPickerScope {
    override val controller: SeatLayerPickerController = stateHolder.controller
}

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
    strings: SeatLayerPickerStrings = SeatLayerPickerStrings.localized(),
    styles: SeatLayerPickerStyles = SeatLayerPickerStyles(),
    moneyFormatter: SeatLayerPickerMoneyFormatter = SeatLayerPickerMoneyFormatter.localized(),
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
        LaunchedEffect(state.snapshot?.sessionId, state.snapshot?.revision) {
            state.snapshot?.let { snapshot ->
                callbacks.onSnapshot(snapshot)
                callbacks.onSelectionChanged(snapshot.selection)
                callbacks.onHoldChanged(snapshot.hold)
            }
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
