package io.seatlayer.android.compose

import androidx.compose.animation.core.animate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerCartLine
import io.seatlayer.android.SeatLayerPickerHapticCue
import io.seatlayer.android.SeatLayerPickerRemovalPhase
import io.seatlayer.android.SeatLayerPickerSeatNote
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import io.seatlayer.android.seatLayerPickerSeatNotes
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * One card per ticket, and the same card on every width.
 *
 * The phone used to draw a second cart — a bordered plate of hairline-divided
 * lines with consecutive seats folded into runs behind a "+N more". It saved
 * pixels and cost the sheet its coherence, and a row a buyer cannot act on
 * directly is not a shorter list. The open sheet caps the list at three cards
 * and a sliver, and scrolls.
 */
@Composable
public fun SeatLayerPickerCartList(
    modifier: Modifier = Modifier,
    /** Retained for source compatibility: there is one card design. */
    @Suppress("UNUSED_PARAMETER") compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val snapshot = scope.state.snapshot
    val confirmed = scope.state.presentation.confirmedCart(snapshot)
    if (confirmed.items.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SeatLayerSizeTokens.CART_CARD_GAP.dp),
    ) {
        confirmed.items.forEach { line ->
            key(line.lineKey) { SeatLayerCartCard(line) }
        }
    }
}

/** One ticket, resolved from the cart line and the seat behind it. */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
public fun SeatLayerCartCard(
    line: SeatLayerPickerCartLine,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val snapshot = scope.state.snapshot
    val theme = scope.theme
    val coroutineScope = rememberCoroutineScope()
    val reducedMotion = pickerReducedMotion()
    val seat = pickerSeatBehind(line, snapshot?.selection.orEmpty())
    val category = snapshot?.categories?.firstOrNull { it.key == line.categoryKey }
    val name = line.sectionLabel ?: category?.label ?: (line.displayLabel ?: line.label)
    val position = pickerCardPositionLine(line, category?.label, name)
    val amount = scope.moneyFormatter.format(line.total, line.currency)
    val held = snapshot?.hold?.owner == "host"
    val removable = !scope.options.readOnly
    val undo = scope.state.presentation.removalUndo
    val removing = undo?.line?.lineKey == line.lineKey &&
        undo.phase == SeatLayerPickerRemovalPhase.AwaitingRemove
    val notes = seat?.let {
        seatLayerPickerSeatNotes(it.accessibility, it.wheelchairSpaceType, it.commercial)
    }.orEmpty()
    val ground = if (held) {
        lerp(theme.surface, theme.accent, CartCardLiterals.HELD_WASH)
    } else {
        theme.surface
    }
    val border = if (held) {
        lerp(theme.divider, theme.accent, CartCardLiterals.HELD_BORDER)
    } else {
        theme.divider
    }
    val spoken = pickerCardSpokenLabel(scope.strings, line, category?.label, name, amount, notes)

    // Swipe travel is kept in pixels: it is compared against the card's own
    // measured width and moved by the finger, both of which arrive in pixels.
    var slide by remember(line.lineKey) { mutableFloatStateOf(0f) }
    var width by remember(line.lineKey) { mutableFloatStateOf(0f) }
    val swipeEnabled = removable && !held && !removing
    val swipeState = rememberDraggableState { delta ->
        slide = pickerRubberBand(slide - delta, 0f, width)
    }

    fun remove() {
        if (removing) return
        scope.controller.emitHaptic(SeatLayerPickerHapticCue.TicketRemoved)
        coroutineScope.launch {
            val removed = scope.controller.removeWithUndo(line)
            // The host is told which seat left, and only where the runtime
            // named one: a line with no seat behind it — a best-available
            // result, a resumed hold — has no seat to hand over.
            if (removed && seat != null) scope.callbacks.onSeatRemoved(seat)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (slide > 0f) {
            // The one place in the picker that is never the accent.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(SeatLayerSizeTokens.CART_CARD_RADIUS.dp))
                    .background(theme.error),
                contentAlignment = Alignment.CenterEnd,
            ) {
                SeatLayerPickerIcon(
                    glyph = SeatLayerPickerGlyph.Close,
                    color = Color.White,
                    modifier = Modifier
                        .padding(end = CartCardLiterals.SWIPE_GLYPH_INSET.dp)
                        .size(CartCardLiterals.SWIPE_GLYPH_SIZE.dp),
                )
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(-slide.roundToInt(), 0) }
                .heightIn(min = SeatLayerSizeTokens.CART_CARD_MIN_HEIGHT.dp)
                .alpha(if (removing) SeatLayerOpacityTokens.REMOVING else 1f)
                .onSizeChanged { width = it.width.toFloat() }
                .then(
                    if (swipeEnabled) {
                        Modifier.draggable(
                            state = swipeState,
                            orientation = Orientation.Horizontal,
                            onDragStopped = { velocity ->
                                val commit = pickerSwipeCommits(slide, width, -velocity)
                                if (commit) {
                                    animate(slide, width) { value, _ -> slide = value }
                                    remove()
                                } else {
                                    animate(
                                        initialValue = slide,
                                        targetValue = 0f,
                                        animationSpec = pickerSheetSettleSpec(reducedMotion),
                                    ) { value, _ -> slide = value }
                                }
                            },
                        )
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (seat != null) {
                        Modifier.pointerInput(seat.id) {
                            detectTapGestures {
                                // The map pans the seat into the middle of the
                                // band the chrome leaves, and the sheet stays
                                // where the buyer put it: checking one seat
                                // after another should not cost four taps.
                                coroutineScope.launch {
                                    scope.performAction {
                                        scope.controller.frameSeat(
                                            seatId = seat.id,
                                            fraction = SEATLAYER_SHEET_RESTORE_FRACTION,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            shape = RoundedCornerShape(SeatLayerSizeTokens.CART_CARD_RADIUS.dp),
            color = ground,
            contentColor = theme.text,
            border = BorderStroke(1.dp, border),
        ) {
            Row(
                modifier = Modifier.padding(
                    start = CartCardLiterals.PAD_LEADING.dp,
                    top = CartCardLiterals.PAD_TOP.dp,
                    end = CartCardLiterals.PAD_TRAILING.dp,
                    bottom = CartCardLiterals.PAD_BOTTOM.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PickerCardMark(
                    held = held,
                    color = parseColor(category?.color.orEmpty()) ?: theme.accent,
                )
                Spacer(Modifier.width(CartCardLiterals.MARK_GAP.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = theme.text,
                        fontSize = SeatLayerTypeTokens.CART_CARD_NAME_SIZE.sp,
                        fontWeight = FontWeight(SeatLayerTypeTokens.CART_CARD_NAME_WEIGHT),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (position.isNotEmpty()) {
                        Text(
                            text = position,
                            color = theme.mutedText,
                            fontSize = SeatLayerTypeTokens.CART_CARD_POSITION_SIZE.sp,
                            fontWeight = FontWeight(
                                SeatLayerTypeTokens.CART_CARD_POSITION_WEIGHT,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (notes.isNotEmpty()) PickerCardNotes(notes)
                }
                Spacer(Modifier.width(CartCardLiterals.AMOUNT_GAP.dp))
                Text(
                    text = amount,
                    color = theme.text,
                    fontSize = SeatLayerTypeTokens.CART_CARD_AMOUNT_SIZE.sp,
                    fontWeight = FontWeight(SeatLayerTypeTokens.CART_CARD_AMOUNT_WEIGHT),
                    maxLines = 1,
                    softWrap = false,
                )
                if (pickerCanOpenSeatView(seat)) {
                    PickerCardAction(
                        glyph = SeatLayerPickerGlyph.Eye,
                        label = scope.strings.viewFromHere,
                        enabled = true,
                        onClick = {
                            val seatId = seat?.id ?: return@PickerCardAction
                            coroutineScope.launch {
                                scope.performAction { scope.controller.openSeatView(seatId) }
                            }
                        },
                    )
                }
                if (removable) {
                    PickerCardAction(
                        glyph = SeatLayerPickerGlyph.Close,
                        // Named down to the seat: three cards in one section
                        // all reading "Remove seat" is three identical buttons.
                        label = listOfNotNull(
                            scope.strings.removeSeat,
                            name,
                            line.seatNumber ?: line.displayLabel ?: line.label,
                        ).joinToString(separator = " "),
                        enabled = !removing,
                        onClick = ::remove,
                    )
                }
            }
        }
    }
}

/** The eye's three gates: the host allows it, the runtime offers it, the seat has one. */
@Composable
internal fun pickerCanOpenSeatView(seat: SeatLayerPickerSelectedSeat?): Boolean {
    val scope = LocalSeatLayerPickerScope.current
    return seat?.seatViewThumbnail != null &&
        scope.options.enableSeatView &&
        scope.controller.supportsSeatView &&
        scope.controller.supportsSeatViewThumbnail
}

/** A press box that is tight to the touch floor, not the platform's own 48. */
@Composable
private fun PickerCardAction(
    glyph: SeatLayerPickerGlyph,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalSeatLayerPickerTheme.current
    // Tight, not a minimum: the platform's own icon button pads itself to 48,
    // and four points per card is what puts the fourth card past the open cap.
    Box(
        modifier = Modifier
            .size(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
            .clip(RoundedCornerShape(CartCardLiterals.ACTION_RADIUS.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        SeatLayerPickerIcon(
            glyph = glyph,
            color = theme.text,
            modifier = Modifier.size(CartCardLiterals.ACTION_GLYPH_SIZE.dp),
        )
    }
}

/** A lock is not a colour: a held card gets a mark that survives greyscale. */
@Composable
private fun PickerCardMark(held: Boolean, color: Color) {
    val theme = LocalSeatLayerPickerTheme.current
    if (held) {
        Box(
            modifier = Modifier
                .size(CartCardLiterals.LOCK_DISC_SIZE.dp)
                .background(
                    lerp(theme.surface, theme.accent, CartCardLiterals.LOCK_DISC_WASH),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            PickerLockGlyph(
                color = theme.accent,
                modifier = Modifier.size(CartCardLiterals.LOCK_GLYPH_SIZE.dp),
            )
        }
        return
    }
    val ring = theme.text.copy(alpha = CartCardLiterals.DOT_RING_ALPHA)
    Box(
        modifier = Modifier
            .size(CartCardLiterals.DOT_SIZE.dp)
            .background(color, CircleShape)
            .drawBehind {
                drawCircle(
                    color = ring,
                    radius = size.minDimension / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                )
            },
    )
}

/** What the organizer said about the seat, said once and in words. */
@Composable
private fun PickerCardNotes(notes: List<SeatLayerPickerSeatNote>) {
    val scope = LocalSeatLayerPickerScope.current
    val theme = scope.theme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SeatLayerSizeTokens.CART_NOTE_PAD_TOP.dp),
        verticalArrangement = Arrangement.spacedBy(SeatLayerSizeTokens.CART_NOTE_GAP.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = SeatLayerSizeTokens.CART_NOTE_PAD_TOP.dp),
            color = theme.divider.copy(alpha = SeatLayerOpacityTokens.NOTE_HAIRLINE),
        )
        notes.forEach { note ->
            val title = pickerSeatNoteTitle(scope.strings, note.labelKey)
            Text(
                text = if (note.note.isNullOrBlank()) title else "$title ${note.note}",
                color = theme.mutedText,
                fontSize = SeatLayerTypeTokens.CART_NOTE_TEXT_SIZE.sp,
                fontWeight = FontWeight(SeatLayerTypeTokens.CART_NOTE_TEXT_WEIGHT),
            )
        }
    }
}

/** A swipe commits past a share of the card's width, or on a throw. */
internal fun pickerSwipeCommits(slide: Float, width: Float, velocity: Float): Boolean {
    if (width <= 0f) return false
    if (slide >= width * SeatLayerPhysicsTokens.SWIPE_COMMIT_FRACTION) return true
    return velocity >= SeatLayerPhysicsTokens.SWIPE_FLING_VELOCITY && slide > 0f
}

/**
 * The seat a cart line is about.
 *
 * Best-available results and resumed holds are in no renderer selection, so a
 * line legitimately finds nothing and the card simply offers less.
 */
internal fun pickerSeatBehind(
    line: SeatLayerPickerCartLine,
    selection: List<SeatLayerPickerSelectedSeat>,
): SeatLayerPickerSelectedSeat? = selection.firstOrNull { it.id == line.seatId }
    ?: selection.firstOrNull { it.label == line.label }
    ?: selection.firstOrNull { it.objectId == line.objectId }
    ?: selection.firstOrNull { it.id == line.objectId }

/** Row, seat and ticket type, the line's own address first and the seat's second. */
internal fun pickerCardPositionLine(
    line: SeatLayerPickerCartLine,
    categoryLabel: String?,
    name: String,
): String = listOfNotNull(
    line.rowLabel?.takeIf(String::isNotBlank),
    line.seatNumber?.takeIf(String::isNotBlank),
    categoryLabel?.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) },
).joinToString(separator = " · ")

/** The whole card as one sentence, for a reader who cannot see the columns. */
internal fun pickerCardSpokenLabel(
    strings: SeatLayerPickerStrings,
    line: SeatLayerPickerCartLine,
    categoryLabel: String?,
    name: String,
    amount: String,
    notes: List<SeatLayerPickerSeatNote>,
): String {
    val identity = listOfNotNull(
        name.takeIf(String::isNotBlank),
        line.rowLabel?.takeIf(String::isNotBlank),
        line.seatNumber?.takeIf(String::isNotBlank),
    )
    return buildList {
        categoryLabel
            ?.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
            ?.let(::add)
        add(strings.seatIdentity(identity))
        add(amount)
        notes.forEach { note ->
            val title = pickerSeatNoteTitle(strings, note.labelKey)
            add(if (note.note.isNullOrBlank()) title else "$title: ${note.note}")
        }
    }.joinToString(separator = ", ")
}

/** One note row's title, in the buyer's own language. */
internal fun pickerSeatNoteTitle(strings: SeatLayerPickerStrings, labelKey: String): String =
    when (labelKey) {
        "emptyWheelchairSpace" -> strings.emptyWheelchairSpace
        "accessiblePhysicalSeat" -> strings.accessiblePhysicalSeat
        "restrictedView" -> strings.restrictedView
        "obstructedView" -> strings.obstructedView
        "premiumSeat" -> strings.premiumSeat
        "organizerNote" -> strings.organizerNote
        else -> strings.accessNeed(labelKey)
    }

/** The mark a held card wears instead of its category dot. */
@Composable
private fun PickerLockGlyph(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.14f
        val bodyTop = size.height * 0.44f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.14f, bodyTop),
            size = androidx.compose.ui.geometry.Size(
                size.width * 0.72f,
                size.height * 0.44f,
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke),
        )
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.28f, size.height * 0.16f),
            size = androidx.compose.ui.geometry.Size(
                size.width * 0.44f,
                size.height * 0.44f,
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
    }
}

@Composable
internal fun PickerDivider() {
    HorizontalDivider(color = LocalSeatLayerPickerTheme.current.divider)
}

/** Card-local numbers the design source keeps as literals. Reported as a gap. */
private object CartCardLiterals {
    const val PAD_LEADING = 12f
    const val PAD_TOP = 4f
    const val PAD_TRAILING = 4f
    const val PAD_BOTTOM = 4f
    const val MARK_GAP = 10f
    const val AMOUNT_GAP = 8f
    const val DOT_SIZE = 9f
    const val DOT_RING_ALPHA = 0.22f
    const val LOCK_DISC_SIZE = 17f
    const val LOCK_DISC_WASH = 0.18f
    const val LOCK_GLYPH_SIZE = 10f
    const val HELD_WASH = 0.07f
    const val HELD_BORDER = 0.45f
    const val ACTION_GLYPH_SIZE = 18f
    const val ACTION_RADIUS = 8f
    const val SWIPE_GLYPH_SIZE = 16f
    const val SWIPE_GLYPH_INSET = 14f
}
