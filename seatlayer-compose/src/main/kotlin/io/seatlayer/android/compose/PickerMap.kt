package io.seatlayer.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import io.seatlayer.android.SeatLayerPickerMapView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerPickerMap(
    modifier: Modifier = Modifier,
    reloadKey: Any? = Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    val regions = LocalPickerBlockedRegions.current
    var mapView by remember(scope.stateHolder) {
        mutableStateOf<SeatLayerPickerMapView?>(null)
    }
    // Three separate things, in this order: the engine is warmed, the surface
    // is laid out, and only THEN is the chart loaded into it. A page adopted
    // before the picker has a size is framed against nothing and re-fitted the
    // moment it gets one, which a buyer reads as the screen loading twice.
    var sized by remember(scope.stateHolder) { mutableStateOf(false) }
    key(scope.stateHolder) {
        AndroidView(
            modifier = modifier
                .testTag("seatlayer-picker-map")
                .onGloballyPositioned { coordinates ->
                    regions?.bindMap(coordinates)
                    if (coordinates.size.width > 0 && coordinates.size.height > 0) {
                        sized = true
                    }
                },
            factory = { context ->
                SeatLayerPickerMapView(context).also { mapView = it }
            },
            update = { view ->
                if (mapView !== view) mapView = view
            },
        )
    }

    LaunchedEffect(mapView, scope.stateHolder, reloadKey, sized) {
        if (!sized) return@LaunchedEffect
        mapView?.let { view ->
            try {
                view.load(scope.stateHolder)
            } catch (error: CancellationException) {
                throw error
            } catch (error: io.seatlayer.android.SeatLayerException) {
                scope.callbacks.onError(error)
            }
        }
    }
    val activeMapView = mapView
    DisposableEffect(activeMapView) {
        onDispose {
            regions?.bindMap(null)
            activeMapView?.let { view ->
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                    view.close()
                }
            }
        }
    }
}
