package io.seatlayer.android.compose

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.seatlayer.android.EventMode
import io.seatlayer.android.SeatLayerPickerCategory
import io.seatlayer.android.SeatLayerPickerMapView
import io.seatlayer.android.SeatLayerPickerPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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

@Composable
public fun SeatLayerPickerHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    holdContent: @Composable () -> Unit = { SeatLayerPickerHoldCountdown() },
    style: SeatLayerPickerPartStyle? = null,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.Header]
    val event = scope.state.snapshot?.event
    val branding = scope.state.snapshot?.branding
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = resolvedStyle.containerColor ?: scope.theme.surface,
        contentColor = resolvedStyle.contentColor ?: scope.theme.onSurface,
        tonalElevation = resolvedStyle.elevation ?: 0.dp,
        shape = RoundedCornerShape(resolvedStyle.cornerRadius ?: 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (compact) {
                        Modifier.height(SeatLayerPickerTokens.SIZE_HEADER_HEIGHT.dp)
                    } else {
                        Modifier.heightIn(min = SeatLayerPickerTokens.SIZE_HEADER_HEIGHT.dp)
                    },
                )
                .padding(
                    start = resolvedStyle.horizontalPadding ?: 12.dp,
                    end = resolvedStyle.horizontalPadding ?: 4.dp,
                    top = resolvedStyle.verticalPadding ?: if (compact) 0.dp else 8.dp,
                    bottom = resolvedStyle.verticalPadding ?: if (compact) 0.dp else 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val brandLabel = branding?.brandName?.trim()?.takeIf(String::isNotEmpty)
            val brandSize = if (compact) {
                SeatLayerPickerTokens.SIZE_HEADER_LOGO_SIZE.dp
            } else {
                36.dp
            }
            Surface(
                modifier = Modifier.size(brandSize),
                shape = RoundedCornerShape(if (compact) 6.dp else 10.dp),
                color = scope.theme.accent,
                contentColor = scope.theme.onAccent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SeatLayerPickerIcon(
                        glyph = SeatLayerPickerGlyph.Seat,
                        color = scope.theme.onAccent,
                        modifier = Modifier.size(brandSize * 0.56f),
                    )
                }
            }
            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (scope.options.hideEventDetails) {
                        brandLabel ?: scope.strings.chooseSeats
                    } else {
                        event?.name ?: brandLabel ?: scope.strings.chooseSeats
                    },
                    style = if (compact) {
                        MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                event?.venue
                    ?.takeUnless { compact || scope.options.hideEventDetails }
                    ?.let { venue ->
                    Text(
                        text = venue,
                        style = MaterialTheme.typography.bodySmall,
                        color = scope.theme.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            holdContent()
            Surface(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = scope.strings.close
                    },
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = scope.theme.onSurface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SeatLayerPickerIcon(
                        glyph = SeatLayerPickerGlyph.Close,
                        color = scope.theme.onSurface,
                        modifier = Modifier.size(if (compact) 20.dp else 22.dp),
                    )
                }
            }
        }
    }
}

@Composable
public fun SeatLayerPriceLegend(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.Legend]
    val categories = scope.state.snapshot?.categories.orEmpty()
    val active = scope.state.snapshot?.map?.categoryFilter.orEmpty().toSet()
    val coroutineScope = rememberCoroutineScope()
    if (categories.isEmpty()) return

    val content: @Composable () -> Unit = {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.height(48.dp) else Modifier),
            contentPadding = PaddingValues(
                horizontal = resolvedStyle.horizontalPadding ?: if (compact) 10.dp else 12.dp,
                vertical = resolvedStyle.verticalPadding ?: if (compact) 0.dp else 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            items(categories, key = SeatLayerPickerCategory::key) { category ->
                val selected = category.key in active
                val price = categoryPrice(
                    category,
                    scope.state.snapshot?.currency ?: "USD",
                    scope.moneyFormatter,
                )
                PickerLegendChip(
                    category = category,
                    price = price,
                    selected = selected,
                    compact = compact,
                    onClick = {
                        coroutineScope.launch {
                            scope.performAction {
                                scope.controller.setCategoryFilter(
                                    if (selected) emptyList() else listOf(category.key),
                                    focus = !selected,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
    if (compact) {
        Box(modifier = modifier.fillMaxWidth()) { content() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = resolvedStyle.containerColor ?: scope.theme.surface,
            contentColor = resolvedStyle.contentColor ?: scope.theme.onSurface,
            tonalElevation = resolvedStyle.elevation ?: 1.dp,
            shape = RoundedCornerShape(resolvedStyle.cornerRadius ?: 0.dp),
        ) { content() }
    }
}

@Composable
private fun PickerLegendChip(
    category: SeatLayerPickerCategory,
    price: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (compact) {
        Box(
            modifier = Modifier
                .height(48.dp)
                .widthIn(min = 48.dp)
                .clickable(onClick = onClick)
                .semantics {
                    role = Role.Checkbox
                    toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                    contentDescription = "${category.label}, $price"
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) scope.theme.accent
                        else scope.theme.surface.copy(alpha = 0.94f),
                    )
                    .border(
                        1.dp,
                        if (selected) scope.theme.accent else scope.theme.divider,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(parseColor(category.color) ?: scope.theme.accent),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = price,
                    color = if (selected) scope.theme.onAccent else scope.theme.onSurface,
                    fontSize = SeatLayerPickerTokens.SIZE_LEGEND_CHIP_FONT_SIZE.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
        }
        return
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Checkbox
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            },
        shape = RoundedCornerShape(scope.theme.cornerRadius),
        color = if (selected) scope.theme.accent.copy(alpha = 0.14f)
        else scope.theme.background,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) scope.theme.accent else scope.theme.divider,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(parseColor(category.color) ?: scope.theme.accent),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(category.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(price, style = MaterialTheme.typography.labelSmall, color = scope.theme.muted)
            }
        }
    }
}

public enum class SeatLayerPickerMapControlsLayout { Vertical, Horizontal }

@Composable
public fun SeatLayerPickerMapControls(
    modifier: Modifier = Modifier,
    layout: SeatLayerPickerMapControlsLayout = SeatLayerPickerMapControlsLayout.Vertical,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (scope.state.snapshot?.map?.isVenue3D == true) {
        SeatLayerPicker3DNavigationControl(modifier)
        return
    }
    val controls: @Composable () -> Unit = {
        if (scope.options.chrome.zoom) {
            SeatLayerPickerZoomControls(layout = layout)
        }
        if (
            scope.options.chrome.overview &&
            scope.state.snapshot?.map?.canZoomOut == true
        ) {
            SeatLayerPickerStepOutControl()
        } else if (scope.options.chrome.fit) {
            SeatLayerPickerFitControl()
        }
        if (scope.options.chrome.colorblind) {
            SeatLayerPickerColorblindControl()
        }
    }
    when (layout) {
        SeatLayerPickerMapControlsLayout.Vertical -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { controls() }
        SeatLayerPickerMapControlsLayout.Horizontal -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { controls() }
    }
}

@Composable
public fun SeatLayerPickerZoomControls(
    modifier: Modifier = Modifier,
    layout: SeatLayerPickerMapControlsLayout = SeatLayerPickerMapControlsLayout.Vertical,
) {
    val scope = LocalSeatLayerPickerScope.current
    val coroutineScope = rememberCoroutineScope()
    val controls: @Composable () -> Unit = {
        MapControl(
            label = scope.strings.zoomIn,
            glyph = SeatLayerPickerGlyph.Plus,
            enabled = scope.state.snapshot?.map?.canZoomIn != false,
        ) {
            coroutineScope.launch { scope.performAction { scope.controller.zoomIn() } }
        }
        MapControl(
            label = scope.strings.zoomOut,
            glyph = SeatLayerPickerGlyph.Minus,
            enabled = scope.state.snapshot?.map?.canZoomOut != false,
        ) {
            coroutineScope.launch { scope.performAction { scope.controller.zoomOut() } }
        }
    }
    when (layout) {
        SeatLayerPickerMapControlsLayout.Vertical -> Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { controls() }
        SeatLayerPickerMapControlsLayout.Horizontal -> Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { controls() }
    }
}

@Composable
private fun MapControl(
    label: String,
    glyph: SeatLayerPickerGlyph,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val theme = LocalSeatLayerPickerTheme.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = theme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = theme.surface.copy(alpha = 0.94f),
                contentColor = theme.onSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.divider),
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SeatLayerPickerIcon(
                        glyph = glyph,
                        color = theme.onSurface,
                        modifier = Modifier
                            .size(DefaultPickerIconSize)
                            .clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

@Composable
public fun SeatLayerPickerTestModeIndicator(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val mode = scope.state.snapshot?.event?.mode ?: return
    if (mode == EventMode.Live) return
    val dark = scope.theme.background.luminance() < 0.35f
    Surface(
        modifier = modifier,
        color = if (dark) scope.theme.background.copy(alpha = 0.94f) else scope.theme.warning,
        contentColor = if (dark) scope.theme.warning else scope.theme.onWarning,
        border = androidx.compose.foundation.BorderStroke(1.dp, scope.theme.warning),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = if (mode == EventMode.Test) scope.strings.testMode else mode.raw,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.1.sp,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = if (compact) 9.dp else 11.dp,
                vertical = if (compact) 3.dp else 5.dp,
            ),
        )
    }
}

@Composable
public fun SeatLayerPickerAttribution(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (scope.state.snapshot?.branding?.attributionRequired != true) return
    Surface(
        modifier = modifier,
        color = if (compact) Color.Transparent else scope.theme.surface.copy(alpha = 0.94f),
        contentColor = scope.theme.muted,
        shape = RoundedCornerShape(if (compact) 0.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier
                .then(if (compact) Modifier.height(18.dp) else Modifier)
                .padding(
                    horizontal = if (compact) 4.dp else 10.dp,
                    vertical = if (compact) 1.dp else 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeatLayerPickerPoweredMark(
                background = scope.theme.muted,
                ink = scope.theme.surface,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = scope.strings.poweredBy,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = if (compact) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SeatLayerPickerPoweredMark(
    background: Color,
    ink: Color,
) {
    Canvas(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(background)
            .padding(2.dp),
    ) {
        val barHeight = 2.dp.toPx()
        val radius = CornerRadius(1.dp.toPx())
        listOf(8.dp, 5.5.dp, 3.dp).forEachIndexed { index, width ->
            drawRoundRect(
                color = ink,
                topLeft = Offset(0f, index * 3.dp.toPx()),
                size = Size(width.toPx(), barHeight),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
public fun SeatLayerPickerLoadingView(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scope.theme.background)
            .clearAndSetSemantics {
                contentDescription = scope.strings.loading
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        contentAlignment = Alignment.Center,
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(2.dp),
            color = scope.theme.accent,
            trackColor = scope.theme.accent.copy(alpha = 0.12f),
        )
        VenueLoadingSilhouette(
            color = scope.theme.accent,
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .widthIn(max = 420.dp)
                .aspectRatio(10f / 7f),
        )
    }
}

/** The same generic venue silhouette the web picker uses before chart readiness. */
@Composable
private fun VenueLoadingSilhouette(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = remember {
        Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
    }
    val transition = rememberInfiniteTransition(label = "seatlayer-venue-loading")
    val sweep by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "seatlayer-venue-loading-sweep",
    )
    val emphasis = if (animationsEnabled) sweep else 0.72f
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val scale = minOf(size.width / 200f, size.height / 140f)
        val left = (size.width - 200f * scale) / 2f
        val top = (size.height - 140f * scale) / 2f
        val shellBrush = Brush.linearGradient(
            0f to color.copy(alpha = 0.08f),
            0.5f to color.copy(alpha = 0.12f + emphasis * 0.08f),
            1f to color.copy(alpha = 0.08f),
            start = Offset(left, top),
            end = Offset(left + 200f * scale, top + 140f * scale),
        )
        fun shell(x: Float, y: Float, width: Float, height: Float, stroke: Float) {
            drawArc(
                brush = shellBrush,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(left + x * scale, top + y * scale),
                size = Size(width * scale, height * scale),
                style = Stroke(width = stroke * scale),
            )
        }
        shell(x = 20f, y = 40f, width = 160f, height = 104f, stroke = 14f)
        shell(x = 32f, y = 32f, width = 136f, height = 84f, stroke = 12f)
        shell(x = 46f, y = 26f, width = 108f, height = 64f, stroke = 10f)
        drawRoundRect(
            color = color.copy(alpha = 0.14f + emphasis * 0.1f),
            topLeft = Offset(left + 62f * scale, top + 16f * scale),
            size = Size(76f * scale, 16f * scale),
            cornerRadius = CornerRadius(4f * scale),
        )
    }
}

@Composable
public fun SeatLayerPickerErrorView(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val failure = (scope.state.phase as? SeatLayerPickerPhase.Failed)?.error
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scope.theme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = scope.strings.unavailable,
                style = MaterialTheme.typography.titleMedium,
                color = scope.theme.onSurface,
            )
            failure?.message?.takeIf(String::isNotBlank)?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = scope.theme.muted,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.sizeIn(minHeight = 48.dp),
            ) {
                Text(scope.strings.retry)
            }
        }
    }
}

@Composable
public fun SeatLayerPickerEmptyView(
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scope.theme.background.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = scope.strings.soldOut,
            style = MaterialTheme.typography.titleMedium,
            color = scope.theme.onSurface,
            modifier = Modifier.padding(28.dp),
        )
    }
}

@Composable
internal fun PickerDivider() {
    HorizontalDivider(color = LocalSeatLayerPickerTheme.current.divider)
}

private fun categoryPrice(
    category: SeatLayerPickerCategory,
    currency: String,
    formatter: SeatLayerPickerMoneyFormatter,
): String {
    if (category.notForSale || category.available <= 0) return "—"
    return if (category.priceMin == category.priceMax) {
        formatter.format(category.priceMin, currency)
    } else {
        "${formatter.format(category.priceMin, currency)}–" +
            formatter.format(category.priceMax, currency)
    }
}
