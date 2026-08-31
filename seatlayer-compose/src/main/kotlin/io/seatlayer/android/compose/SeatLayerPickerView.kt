package io.seatlayer.android.compose

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import io.seatlayer.android.ReadyInfo
import io.seatlayer.android.SeatLayerChartLoad
import io.seatlayer.android.SeatLayerConfiguration
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff
import io.seatlayer.android.SeatLayerPickerHold
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import io.seatlayer.android.SeatLayerPickerSnapshot
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** View/XML host of the same canonical Compose picker tree. */
public class SeatLayerPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val composeView = ComposeView(context)
    private var boundStateHolder: SeatLayerPickerStateHolder? = null
    private var boundThemeMode: SeatLayerPickerThemeMode by
        mutableStateOf(SeatLayerPickerThemeMode.Auto)
    private var boundTheme: SeatLayerPickerTheme? by mutableStateOf(null)

    public val stateHolder: SeatLayerPickerStateHolder?
        get() = boundStateHolder

    init {
        addView(
            composeView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    public fun bind(
        lifecycleOwner: LifecycleOwner,
        configuration: SeatLayerConfiguration,
        themeMode: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode.Auto,
        theme: SeatLayerPickerTheme? = null,
        strings: SeatLayerPickerStrings = SeatLayerPickerStrings.localized(),
        options: SeatLayerPickerOptions = SeatLayerPickerOptions(),
        styles: SeatLayerPickerStyles = SeatLayerPickerStyles(),
        moneyFormatter: SeatLayerPickerMoneyFormatter =
            SeatLayerPickerMoneyFormatter.localized(),
        builders: SeatLayerPickerBuilders = SeatLayerPickerBuilders(),
        onCheckout: suspend (SeatLayerPickerCheckoutHandoff) -> Unit = {},
        onError: (io.seatlayer.android.SeatLayerException) -> Unit = {},
        onReady: (ReadyInfo) -> Unit = {},
        onSnapshot: (SeatLayerPickerSnapshot) -> Unit = {},
        onSelectionChanged: (List<SeatLayerPickerSelectedSeat>) -> Unit = {},
        onHoldChanged: (SeatLayerPickerHold) -> Unit = {},
        onClose: () -> Unit,
        onChartLoad: (SeatLayerChartLoad) -> Unit = {},
    ) {
        check(boundStateHolder == null) {
            "SeatLayerPickerView is already bound; call close() before rebinding."
        }
        val holder = SeatLayerPickerStateHolder(
            configuration = configuration,
            behavior = options.behavior(),
        )
        boundStateHolder = holder
        boundThemeMode = themeMode
        boundTheme = theme
        setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        composeView.setContent {
            SeatLayerPicker(
                configuration = configuration,
                themeMode = boundThemeMode,
                theme = boundTheme,
                strings = strings,
                options = options,
                styles = styles,
                moneyFormatter = moneyFormatter,
                builders = builders,
                stateHolder = holder,
                onCheckout = onCheckout,
                onError = onError,
                onReady = onReady,
                onSnapshot = onSnapshot,
                onSelectionChanged = onSelectionChanged,
                onHoldChanged = onHoldChanged,
                onClose = onClose,
                onChartLoad = onChartLoad,
            )
        }
    }

    /** Updates native and renderer theme state without rebinding or remounting the picker. */
    public fun setThemeMode(themeMode: SeatLayerPickerThemeMode) {
        check(boundStateHolder != null) {
            "SeatLayerPickerView must be bound before changing its theme mode."
        }
        if (boundThemeMode != themeMode) boundThemeMode = themeMode
    }

    /** Updates an explicit native and renderer theme without rebinding or remounting. */
    public fun setTheme(theme: SeatLayerPickerTheme?) {
        check(boundStateHolder != null) {
            "SeatLayerPickerView must be bound before changing its theme."
        }
        if (boundTheme != theme) boundTheme = theme
    }

    /** Performs hold-aware shutdown before disposing the Compose renderer. */
    public suspend fun close() {
        boundStateHolder?.close()
        withContext(Dispatchers.Main.immediate) {
            composeView.disposeComposition()
            boundStateHolder = null
            boundThemeMode = SeatLayerPickerThemeMode.Auto
            boundTheme = null
        }
    }
}
