package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerSeatNote
import io.seatlayer.android.SeatLayerPickerSeatNoteTone

/*
 * What the organizer has marked on this seat, as full-bleed bands.
 *
 * The rows themselves are decided in the core module, once, so the card, the
 * cart and the scene's caption cannot disagree about which facts a seat has.
 * This file only draws them: a tone picks a ground and an ink, and the glyph
 * inherits whichever ink the row landed on.
 */

/** Ground, title ink and glyph ink for one note row. */
internal data class SeatLayerSeatNoteColors(
    val band: Color,
    val title: Color,
    val glyph: Color,
    val body: Color,
)

/**
 * The colours [tone] paints in, measured on the tint they actually make.
 *
 * The washes are composited over the surface rather than left translucent, so
 * a contrast measurement reads the ground the buyer sees instead of the colour
 * it was mixed from.
 */
internal fun seatLayerSeatNoteColors(
    tone: SeatLayerPickerSeatNoteTone,
    theme: SeatLayerPickerTheme,
): SeatLayerSeatNoteColors = when (tone) {
    SeatLayerPickerSeatNoteTone.Warn -> {
        val band = pickerWash(
            theme.warning,
            SeatLayerOpacityTokens.NOTE_TONE_WASH,
            theme.surface,
        )
        SeatLayerSeatNoteColors(
            band = band,
            title = theme.warnText,
            glyph = theme.warnText,
            body = pickerNoteBodyInk(
                theme.mutedText,
                theme.text,
                SeatLayerOpacityTokens.NOTE_BODY_INK,
            ),
        )
    }
    SeatLayerPickerSeatNoteTone.Premium -> {
        val band = pickerWash(
            theme.premium,
            SeatLayerOpacityTokens.NOTE_TONE_WASH,
            theme.surface,
        )
        SeatLayerSeatNoteColors(
            band = band,
            title = theme.premiumText,
            glyph = theme.premiumText,
            body = pickerNoteBodyInk(
                theme.mutedText,
                theme.text,
                SeatLayerOpacityTokens.NOTE_BODY_INK,
            ),
        )
    }
    SeatLayerPickerSeatNoteTone.Access,
    SeatLayerPickerSeatNoteTone.Note,
    -> SeatLayerSeatNoteColors(
        band = pickerWash(
            theme.text,
            SeatLayerOpacityTokens.NOTE_NEUTRAL_WASH,
            theme.surface,
        ),
        title = theme.text,
        glyph = theme.mutedText,
        body = pickerNoteBodyInk(
            theme.mutedText,
            theme.text,
            SeatLayerOpacityTokens.NOTE_BODY_INK,
        ),
    )
}

/**
 * The words one row's [SeatLayerPickerSeatNote.labelKey] stands for.
 *
 * The accommodation keys go through the runtime's own dictionary, so the card
 * and the map say the same thing in the buyer's language; the rest are named
 * strings. A key this build has no name for prints the key rather than an empty
 * row, which is what the model already promised by keeping the row.
 */
internal fun seatLayerSeatNoteTitle(
    labelKey: String,
    strings: SeatLayerPickerStrings,
): String = when (labelKey) {
    "emptyWheelchairSpace" -> strings.emptyWheelchairSpace
    "accessiblePhysicalSeat" -> strings.accessiblePhysicalSeat
    "restrictedView" -> strings.restrictedView
    "obstructedView" -> strings.obstructedView
    "premiumSeat" -> strings.premiumSeat
    "organizerNote" -> strings.organizerNote
    else -> strings.accessNeed(labelKey)
}

/**
 * A seat's notes, as the stack of bands the card and the tap card both draw.
 *
 * Bands, not plates: no radius, no border, no inset, a hairline on each join
 * and none above the first, which sits flush against the category band. The
 * caller owns no padding of its own — that is what keeps them full bleed.
 */
@Composable
internal fun SeatLayerSeatNotes(
    notes: List<SeatLayerPickerSeatNote>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (notes.isEmpty()) return
    val scope = LocalSeatLayerPickerScope.current
    Column(modifier.fillMaxWidth()) {
        notes.forEachIndexed { index, note ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Dp.Hairline)
                        .background(
                            scope.theme.divider.copy(
                                alpha = scope.theme.divider.alpha *
                                    SeatLayerOpacityTokens.NOTE_HAIRLINE,
                            ),
                        ),
                )
            }
            SeatLayerSeatNoteBand(note, compact)
        }
    }
}

@Composable
private fun SeatLayerSeatNoteBand(
    note: SeatLayerPickerSeatNote,
    compact: Boolean,
) {
    val scope = LocalSeatLayerPickerScope.current
    val colors = seatLayerSeatNoteColors(note.tone, scope.theme)
    val title = seatLayerSeatNoteTitle(note.labelKey, scope.strings)
    val body = note.note
    val leading = if (compact) {
        SeatLayerSizeTokens.NOTE_COMPACT_PAD_LEADING
    } else {
        SeatLayerSizeTokens.NOTE_PAD_X
    }
    val trailing = if (compact) {
        SeatLayerSizeTokens.NOTE_COMPACT_PAD_X
    } else {
        SeatLayerSizeTokens.NOTE_PAD_X
    }
    val vertical = if (compact) {
        SeatLayerSizeTokens.NOTE_COMPACT_PAD_Y
    } else {
        SeatLayerSizeTokens.NOTE_PAD_Y
    }
    val glyphSize = if (compact) {
        SeatLayerSizeTokens.NOTE_COMPACT_ICON_SIZE
    } else {
        SeatLayerSizeTokens.NOTE_ICON_SIZE
    }
    val titleSize = if (compact) {
        SeatLayerTypeTokens.NOTE_TITLE_COMPACT_SIZE
    } else {
        SeatLayerTypeTokens.NOTE_TITLE_SIZE
    }
    val titleWeight = if (compact) {
        SeatLayerTypeTokens.NOTE_TITLE_COMPACT_WEIGHT
    } else {
        SeatLayerTypeTokens.NOTE_TITLE_WEIGHT
    }
    val bodySize = if (compact) {
        SeatLayerTypeTokens.NOTE_BODY_COMPACT_SIZE
    } else {
        SeatLayerTypeTokens.NOTE_BODY_SIZE
    }
    val bodyWeight = if (compact) {
        SeatLayerTypeTokens.NOTE_BODY_COMPACT_WEIGHT
    } else {
        SeatLayerTypeTokens.NOTE_BODY_WEIGHT
    }
    // Spoken as one sentence: the glyph adds nothing a screen reader can use,
    // and the organizer's line belongs to the mark above it.
    val spoken = listOfNotNull(title, body?.takeIf(String::isNotBlank))
        .joinToString(separator = ": ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.band)
            .padding(
                start = leading.dp,
                end = trailing.dp,
                top = vertical.dp,
                bottom = vertical.dp,
            )
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.Top,
    ) {
        SeatLayerSeatIcon(
            iconKey = note.iconKey,
            color = colors.glyph,
            modifier = Modifier.size(glyphSize.dp),
        )
        Spacer(Modifier.width(SeatLayerSizeTokens.NOTE_ICON_GAP.dp))
        Column(Modifier.weight(1f).clearAndSetSemantics { }) {
            SeatLayerPickerText(
                title,
                color = colors.title,
                fontSize = titleSize.sp,
                fontWeight = FontWeight(titleWeight),
            )
            if (!body.isNullOrBlank()) {
                SeatLayerPickerText(
                    body,
                    color = colors.body,
                    fontSize = bodySize.sp,
                    fontWeight = FontWeight(bodyWeight),
                )
            }
        }
    }
}
