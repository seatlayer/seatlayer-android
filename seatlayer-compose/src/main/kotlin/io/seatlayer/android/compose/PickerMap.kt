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
    var mapView by remember(scope.stateHolder) {
        mutableStateOf<SeatLayerPickerMapView?>(null)
    }
    key(scope.stateHolder) {
        AndroidView(
            modifier = modifier.testTag("seatlayer-picker-map"),
            factory = { context ->
                SeatLayerPickerMapView(context).also { mapView = it }
            },
            update = { view ->
                if (mapView !== view) mapView = view
            },
        )
    }

    LaunchedEffect(mapView, scope.stateHolder, reloadKey) {
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
            activeMapView?.let { view ->
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                    view.close()
                }
            }
        }
    }
}
