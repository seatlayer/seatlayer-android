package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerCheckoutHandoff

/**
 * The moment the tickets are the buyer's.
 *
 * Shown only once the handed-off hold has settled to booked — never on the
 * hand-off itself, which is a buyer on their way to pay, not a buyer who has
 * paid. `Back to map` takes the overlay away without touching the hold,
 * because the seats are booked either way.
 */
@Composable
public fun SeatLayerPickerBookedOverlay(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    // A host with its own confirmation screen turns the telling off; the sale
    // is still known, through `onBooked`.
    if (!scope.options.showBookedOverlay) return
    val handoff = scope.state.presentation.bookedHandoff ?: return
    var dismissed by remember(scope.stateHolder) { mutableStateOf<String?>(null) }
    if (dismissed == handoff.holdId) return
    val tickets = handoff.lineItems.sumOf { it.quantity }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scope.theme.background)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(BOOKED_GUTTER.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(BOOKED_BADGE_SIZE.dp)
                    .background(scope.theme.accent, CircleShape)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                SeatLayerPickerIcon(
                    SeatLayerPickerGlyph.Check,
                    scope.theme.onAccent,
                    Modifier.size(BOOKED_BADGE_GLYPH.dp),
                )
            }
            Spacer(Modifier.height(BOOKED_GAP.dp))
            Text(
                scope.strings.allSetTitle,
                modifier = Modifier.semantics { heading() },
                color = scope.theme.text,
                fontSize = BOOKED_TITLE_SIZE.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = scope.theme.fontFamily,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(BOOKED_GAP.dp))
            Text(
                "${scope.strings.ticketCount(tickets)} ${scope.strings.confirmedAndOnWay}",
                modifier = Modifier.widthIn(max = BOOKED_COPY_WIDTH.dp),
                color = scope.theme.mutedText,
                fontSize = BOOKED_BODY_SIZE.sp,
                fontFamily = scope.theme.fontFamily,
                textAlign = TextAlign.Center,
            )
            if (handoff.lineItems.isNotEmpty()) {
                Spacer(Modifier.height(BOOKED_GAP.dp))
                BookedSeatList(handoff)
            }
            Spacer(Modifier.height(BOOKED_GAP.dp))
            OutlinedButton(
                onClick = { dismissed = handoff.holdId },
                modifier = Modifier.sizeIn(
                    minHeight = SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp,
                ),
                shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
            ) {
                SeatLayerPickerIcon(
                    SeatLayerPickerGlyph.Back,
                    scope.theme.text,
                    Modifier.size(BOOKED_BACK_GLYPH.dp).clearAndSetSemantics { },
                )
                Spacer(Modifier.size(BOOKED_BACK_GAP.dp))
                Text(
                    scope.strings.backToMap,
                    color = scope.theme.text,
                    fontSize = BOOKED_ACTION_SIZE.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = scope.theme.fontFamily,
                )
            }
        }
    }
}

/** What was booked, one pill per line, scrolling past four of them. */
@Composable
private fun BookedSeatList(handoff: SeatLayerPickerCheckoutHandoff) {
    val scope = LocalSeatLayerPickerScope.current
    Column(
        modifier = Modifier
            .widthIn(max = BOOKED_COPY_WIDTH.dp)
            .heightIn(max = BOOKED_LIST_HEIGHT.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(BOOKED_PILL_GAP.dp),
    ) {
        handoff.lineItems.forEach { line ->
            Surface(
                color = scope.theme.divider.copy(alpha = BOOKED_PILL_WASH),
                contentColor = scope.theme.text,
                shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = BOOKED_PILL_PAD_X.dp,
                        vertical = BOOKED_PILL_PAD_Y.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        line.displayLabel ?: line.label,
                        modifier = Modifier.weight(1f, fill = false),
                        color = scope.theme.text,
                        fontSize = BOOKED_ACTION_SIZE.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = scope.theme.fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (line.quantity > 1) {
                        Text(
                            "×${line.quantity}",
                            modifier = Modifier.padding(start = BOOKED_PILL_GAP.dp),
                            color = scope.theme.mutedText,
                            fontSize = BOOKED_ACTION_SIZE.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = scope.theme.fontFamily,
                        )
                    }
                }
            }
        }
    }
}

private const val BOOKED_GUTTER: Float = 28f
private const val BOOKED_GAP: Float = 12f
private const val BOOKED_BADGE_SIZE: Float = 60f
private const val BOOKED_BADGE_GLYPH: Float = 30f
private const val BOOKED_TITLE_SIZE: Float = 19f
private const val BOOKED_BODY_SIZE: Float = 13f
private const val BOOKED_ACTION_SIZE: Float = 12.5f
private const val BOOKED_COPY_WIDTH: Float = 320f
private const val BOOKED_LIST_HEIGHT: Float = 132f
private const val BOOKED_PILL_GAP: Float = 4f
private const val BOOKED_PILL_PAD_X: Float = 12f
private const val BOOKED_PILL_PAD_Y: Float = 5f
private const val BOOKED_PILL_WASH: Float = 0.32f
private const val BOOKED_BACK_GLYPH: Float = 18f
private const val BOOKED_BACK_GAP: Float = 6f
