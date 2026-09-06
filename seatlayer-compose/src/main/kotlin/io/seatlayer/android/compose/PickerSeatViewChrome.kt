package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bottom-only caption for panorama; the renderer owns the close control and default drag hint. */
@Composable
public fun SeatLayerSeatViewChrome(
    modifier: Modifier = Modifier,
    showDragHint: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    if (!scope.controller.supportsNativeSeatViewChrome) return
    val view = scope.state.seatView ?: return
    val title = view.title?.trim()?.takeIf(String::isNotEmpty)
    val caption = view.caption?.trim()?.takeIf(String::isNotEmpty)
    val badge = view.badge?.trim()?.takeIf(String::isNotEmpty)
        ?: when {
            view.real -> scope.strings.realSeatView
            view.generated -> scope.strings.generatedSeatView
            else -> null
        }
    val dragHint = view.dragHint?.trim()?.takeIf(String::isNotEmpty)
    val chromeTheme = SeatLayerPickerTheme.dark().copy(
        accent = scope.theme.accent,
        onAccent = scope.theme.onAccent,
    )
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    top = 12.dp,
                    end = 12.dp,
                    // The runtime keeps its own drag hint in the lowest
                    // panorama band. Leave that band free unless a custom
                    // host explicitly opts into drawing the hint natively.
                    bottom = if (showDragHint) 12.dp else 48.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (title != null || caption != null || badge != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    color = chromeTheme.surface.copy(alpha = 0.9f),
                    contentColor = chromeTheme.onSurface,
                    border = BorderStroke(1.dp, chromeTheme.divider),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            title?.let {
                                Text(
                                    it,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            caption?.let {
                                Text(
                                    it,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = chromeTheme.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        badge?.let { badge ->
                            Surface(
                                color = chromeTheme.onSurface.copy(alpha = 0.12f),
                                contentColor = chromeTheme.onSurface,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    badge,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
            dragHint?.takeIf { showDragHint }?.let { hint ->
                Surface(
                    color = chromeTheme.surface.copy(alpha = 0.82f),
                    contentColor = chromeTheme.muted,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        hint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
