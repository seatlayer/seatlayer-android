package io.seatlayer.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerChartLoad
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff
import io.seatlayer.android.SeatLayerPickerPhase
import io.seatlayer.android.SeatLayerPickerProjections
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerPickerThemeMode
import io.seatlayer.android.compose.SeatLayerConfirmCard
import io.seatlayer.android.compose.SeatLayerBookButton
import io.seatlayer.android.compose.SeatLayerDockBar
import io.seatlayer.android.compose.SeatLayerPickerBackHandler
import io.seatlayer.android.compose.SeatLayerPickerAccessibilityFilters
import io.seatlayer.android.compose.SeatLayerPickerCallbacks
import io.seatlayer.android.compose.SeatLayerPickerCartList
import io.seatlayer.android.compose.SeatLayerPickerCartSheet
import io.seatlayer.android.compose.SeatLayerPickerEmptyView
import io.seatlayer.android.compose.SeatLayerPickerErrorView
import io.seatlayer.android.compose.SeatLayerPickerFitControl
import io.seatlayer.android.compose.SeatLayerFloorStrip
import io.seatlayer.android.compose.SeatLayerPickerGeneralAdmissionPrompt
import io.seatlayer.android.compose.SeatLayerPickerHeader
import io.seatlayer.android.compose.SeatLayerPickerHapticEffects
import io.seatlayer.android.compose.SeatLayerPickerLifecycle
import io.seatlayer.android.compose.SeatLayerPickerLoadingView
import io.seatlayer.android.compose.SeatLayerPickerMap
import io.seatlayer.android.compose.SeatLayerPickerOverviewControl
import io.seatlayer.android.compose.SeatLayerPickerScope
import io.seatlayer.android.compose.SeatLayerSeatViewChrome
import io.seatlayer.android.compose.SeatLayerPickerTablePrompt
import io.seatlayer.android.compose.SeatLayerPickerTestModeIndicator
import io.seatlayer.android.compose.SeatLayerPickerTheme
import io.seatlayer.android.compose.SeatLayerVenue3D
import io.seatlayer.android.compose.SeatLayerPickerViewModeControl
import io.seatlayer.android.compose.SeatLayerPriceLegend

/** Compiled proof that an app can own layout while reusing every SDK layer. */
@Composable
public fun CustomPickerExample(
    configuration: SeatLayerConfiguration,
    themeMode: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode.Auto,
    theme: SeatLayerPickerTheme? = null,
    onCheckout: suspend (SeatLayerPickerCheckoutHandoff) -> Unit = {},
    onError: (SeatLayerException) -> Unit = {},
    onReady: (ReadyInfo) -> Unit = {},
    onChartLoad: (SeatLayerChartLoad) -> Unit = {},
    onSnapshot: (SeatLayerPickerSnapshot) -> Unit = {},
    onClose: () -> Unit,
) {
    SeatLayerPickerScope(
        configuration = configuration,
        themeMode = themeMode,
        theme = theme,
        callbacks = SeatLayerPickerCallbacks(
            onCheckout = onCheckout,
            onClose = onClose,
            onError = onError,
            onReady = onReady,
            onChartLoad = onChartLoad,
            onSnapshot = onSnapshot,
        ),
    ) {
        val resolvedPickerTheme = this.theme
        var reloadGeneration by remember { mutableIntStateOf(0) }
        SeatLayerPickerLifecycle()
        SeatLayerPickerBackHandler()
        SeatLayerPickerHapticEffects()
        val panorama = state.seatView != null
        val venue3D = state.snapshot?.map?.isVenue3D == true
        val regularMap = !panorama && !venue3D
        val dockVisible = regularMap &&
            state.snapshot?.map?.rung == "seats" &&
            state.snapshot?.map?.focusedSectionId != null
        val mapControlBottom = if (dockVisible) 60.dp else 8.dp
        val blocked = state.presentation.cartExpanded ||
            (!panorama && !venue3D &&
                (state.presentation.pendingSeat != null ||
                    state.presentation.activePrompt != null))
        LaunchedEffect(blocked, state.isReady) {
            if (state.isReady) controller.setInteractionEnabled(!blocked)
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(resolvedPickerTheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            SeatLayerPickerHeader(onClose, compact = true)
            Box(Modifier.weight(1f)) {
                SeatLayerPickerMap(
                    modifier = Modifier.fillMaxSize(),
                    reloadKey = reloadGeneration,
                )
                if (regularMap) {
                    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Box(Modifier.weight(1f)) {
                                SeatLayerPriceLegend(compact = true)
                            }
                            SeatLayerPickerViewModeControl(
                                Modifier.padding(end = 10.dp),
                                compact = true,
                            )
                        }
                        SeatLayerFloorStrip(compact = true)
                        SeatLayerPickerTestModeIndicator(
                            Modifier.padding(start = 10.dp),
                            compact = true,
                        )
                    }
                    SeatLayerPickerAccessibilityFilters(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = mapControlBottom),
                        compact = true,
                    )
                    if (state.snapshot?.map?.focusedSectionId != null) {
                        SeatLayerPickerOverviewControl(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = mapControlBottom),
                        )
                    } else {
                        SeatLayerPickerFitControl(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = mapControlBottom),
                        )
                    }
                    SeatLayerDockBar(Modifier.align(Alignment.BottomCenter))
                    if (state.presentation.pendingSeat != null) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(resolvedPickerTheme.background.copy(alpha = 0.64f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            SeatLayerConfirmCard(
                                Modifier.padding(horizontal = 15.dp),
                                compact = true,
                            )
                        }
                    }
                }
                SeatLayerVenue3D()
                SeatLayerSeatViewChrome()
                SeatLayerPickerGeneralAdmissionPrompt()
                SeatLayerPickerTablePrompt()
                when (state.phase) {
                    SeatLayerPickerPhase.Idle,
                    SeatLayerPickerPhase.Loading,
                    -> SeatLayerPickerLoadingView()
                    is SeatLayerPickerPhase.Failed -> SeatLayerPickerErrorView(
                        onRetry = { reloadGeneration += 1 },
                    )
                    is SeatLayerPickerPhase.Ready -> if (
                        SeatLayerPickerProjections.isProvenEmpty(state.snapshot)
                    ) {
                        SeatLayerPickerEmptyView()
                    }
                    SeatLayerPickerPhase.Destroyed -> Unit
                }
            }
            if (
                state.isReady &&
                !SeatLayerPickerProjections.isProvenEmpty(state.snapshot)
            ) {
                SeatLayerPickerCartSheet(
                    compact = true,
                    cartList = { SeatLayerPickerCartList(compact = true) },
                    checkoutBar = { SeatLayerBookButton(Modifier.padding(12.dp)) },
                )
            }
        }
    }
}
