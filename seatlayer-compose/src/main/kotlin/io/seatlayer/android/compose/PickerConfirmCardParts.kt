package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerCategory
import io.seatlayer.android.SeatLayerPickerSelectedSeat

/*
 * The seat card's own pieces: what the buyer reads before deciding.
 *
 * Split out of the card itself so that file stays a legible top-to-bottom
 * description of the question it asks. These are the parts that answer "where
 * is the seat, and what does it cost".
 */

/** One labelled cell of the identity grid. */
internal data class SeatLayerIdentityCell(
    val eyebrow: String,
    val value: String,
    val longSection: Boolean = false,
)

/** The em dash a cell prints where the chart named the fact but left it empty. */
private const val EMPTY_CELL = "—"

/**
 * The cells the identity grid shows for [seat], in reading order.
 *
 * The cell SET is chosen first — a section cell only where the seat names one,
 * a row cell only where it has one, the place always — and only then is a
 * present-but-empty value printed as an em dash. Choosing the set from the
 * values instead is how a seat with a blank row label lost its row column and
 * shifted the other two under the buyer's eye.
 */
internal fun seatLayerIdentityCells(
    seat: SeatLayerPickerSelectedSeat,
    strings: SeatLayerPickerStrings,
    sectionCode: String? = null,
): List<SeatLayerIdentityCell> {
    val section = seat.sectionLabel?.trim()
    val row = seat.rowLabel?.let {
        pickerRowLabel(it, seat.sectionLabel, sectionCode)
    }
    val number = seat.seatNumber?.trim()?.takeIf(String::isNotEmpty)
        ?: seat.buyerFacingLabel
    val cells = mutableListOf<SeatLayerIdentityCell>()
    if (section != null) {
        cells += SeatLayerIdentityCell(
            eyebrow = strings.sectionWord,
            value = section.takeIf(String::isNotEmpty) ?: EMPTY_CELL,
            longSection = section.length > SeatLayerSizeTokens.CONFIRM_SECTION_SHORT_MAX,
        )
    }
    if (row != null) {
        cells += SeatLayerIdentityCell(
            eyebrow = seatLayerRowWord(seat, strings),
            value = row.takeIf(String::isNotEmpty) ?: EMPTY_CELL,
        )
    }
    cells += SeatLayerIdentityCell(
        eyebrow = seatLayerPlaceWord(seat, strings),
        value = number.takeIf(String::isNotEmpty) ?: EMPTY_CELL,
    )
    return cells
}

/**
 * What the chart calls a row, where it called it anything.
 *
 * The decoder folds the row's own word and the object's display type into one
 * field, so a chart that names neither leaves the OBJECT's word standing where
 * the row's should be — and a seat card then reads "Seat A · Seat 12". A word
 * that merely repeats the object type is therefore not a row word.
 */
internal fun seatLayerRowWord(
    seat: SeatLayerPickerSelectedSeat,
    strings: SeatLayerPickerStrings,
): String {
    val named = seat.displayType?.trim()?.takeIf(String::isNotEmpty) ?: return strings.rowWord
    if (named.equals(seat.objectType?.trim(), ignoreCase = true)) return strings.rowWord
    return named
}

/** A booth holds places rather than seats, and says so. */
internal fun seatLayerPlaceWord(
    seat: SeatLayerPickerSelectedSeat,
    strings: SeatLayerPickerStrings,
): String = if (seat.objectType == "booth") strings.placeWord else strings.seatWord

/**
 * A row label with the section's own name taken off the front.
 *
 * A chart that names its rows `Stalls D` inside a section called `Stalls`
 * leaves the card reading `Stalls · Row Stalls D`. Three prefixes are stripped:
 * the section's whole name, the section's id, and a leading all-caps token that
 * abbreviates the section. Anything else is left exactly as authored — a row
 * sharing no prefix keeps its whole name, and a bare `A` is never mistaken for
 * a code.
 */
internal fun pickerRowLabel(
    rowLabel: String?,
    sectionLabel: String?,
    sectionCode: String? = null,
): String {
    val row = rowLabel?.trim().orEmpty()
    val section = sectionLabel?.trim().orEmpty()
    if (row.isEmpty()) return row
    if (section.isNotEmpty() && row.lowercase().startsWith(section.lowercase())) {
        val rest = row.substring(section.length).withoutLeadingSeparator()
        if (rest.isNotEmpty()) return rest
    }
    val separator = row.indexOfFirst { it in ROW_PREFIX_SEPARATORS }
    if (separator < 0) return row
    val head = row.substring(0, separator)
    val rest = row.substring(separator).withoutLeadingSeparator()
    if (rest.isEmpty()) return row
    return if (namesSection(head, section, sectionCode)) rest else row
}

private fun String.withoutLeadingSeparator(): String {
    var rest = trim()
    while (rest.isNotEmpty() && rest[0] in ROW_PREFIX_SEPARATORS) {
        rest = rest.substring(1).trim()
    }
    return rest
}

private fun namesSection(head: String, section: String, code: String?): Boolean {
    if (head.isEmpty()) return false
    val upper = head.uppercase()
    val trimmedCode = code?.trim().orEmpty()
    if (trimmedCode.isNotEmpty() && upper == trimmedCode.uppercase()) return true
    if (section.isEmpty()) return false
    if (upper == section.uppercase()) return true
    // An abbreviation, and only an abbreviation: a mixed-case token is a place
    // name in its own right, and one letter is a row group far more often than
    // it is a section code.
    if (head != upper || head.length < 2) return false
    if (head.none { it in 'A'..'Z' }) return false
    return section.uppercase().replace(" ", "").startsWith(upper)
}

private val ROW_PREFIX_SEPARATORS = charArrayOf('-', '–', '—', '_', ' ', '.', '/')

/**
 * Where the seat is, as labelled cells rather than one long sentence.
 *
 * Section, row and seat each get their own cell with a small eyebrow over the
 * value, so a buyer checking a row letter reads one word instead of parsing a
 * whole identity. The cells are EQUAL and centred: a section such as `209` is
 * as short as the row letter beside it, and giving it the widest track made a
 * line of three facts read as a misaligned grid. Only a long section name drops
 * to the small wrapping type, because at identity-confirmation time a clipped
 * name is a name the buyer cannot check the map against.
 *
 * A screen reader hears the sentence rather than the cells: the grid is one
 * node carrying the identity the rest of the picker reads out.
 */
@Composable
internal fun SeatLayerConfirmIdentityGrid(
    seat: SeatLayerPickerSelectedSeat,
    immersive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val cells = seatLayerIdentityCells(
        seat,
        scope.strings,
        pickerSectionCode(scope.state.snapshot, seat.sectionLabel),
    )
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = SeatLayerSizeTokens.CONFIRM_IDENTITY_HEIGHT.dp)
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cells.forEachIndexed { index, cell ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(Dp.Hairline)
                            .fillMaxHeight()
                            .background(scope.theme.divider),
                    )
                }
                SeatLayerIdentityCellView(cell, immersive, Modifier.weight(1f))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(Dp.Hairline)
                .background(scope.theme.divider),
        )
    }
}

/** The sentence the grid is read out as. */
internal fun seatLayerIdentitySentence(
    cells: List<SeatLayerIdentityCell>,
    strings: SeatLayerPickerStrings,
): String = strings.seatIdentity(
    cells.map { cell ->
        if (cell.eyebrow == strings.sectionWord) cell.value else "${cell.eyebrow} ${cell.value}"
    },
)

@Composable
private fun SeatLayerIdentityCellView(
    cell: SeatLayerIdentityCell,
    immersive: Boolean,
    modifier: Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val eyebrowSize = SeatLayerSizeTokens.CONFIRM_IDENTITY_KEY_FONT_SIZE
    val valueSize = when {
        cell.longSection && immersive -> SeatLayerSizeTokens.CONFIRM_IMMERSIVE_SECTION_FONT_SIZE
        cell.longSection -> SeatLayerSizeTokens.CONFIRM_IDENTITY_LONG_SECTION_FONT_SIZE
        immersive -> SeatLayerSizeTokens.CONFIRM_IMMERSIVE_VALUE_FONT_SIZE
        else -> SeatLayerSizeTokens.CONFIRM_IDENTITY_VALUE_FONT_SIZE
    }
    Column(
        modifier = modifier.padding(
            start = if (immersive) SeatLayerSizeTokens.CONFIRM_IMMERSIVE_CELL_SIDE.dp else 6.dp,
            end = if (immersive) SeatLayerSizeTokens.CONFIRM_IMMERSIVE_CELL_SIDE.dp else 6.dp,
            top = if (immersive) SeatLayerSizeTokens.CONFIRM_IMMERSIVE_CELL_TOP.dp else 8.dp,
            bottom = if (immersive) {
                SeatLayerSizeTokens.CONFIRM_IMMERSIVE_CELL_BOTTOM.dp
            } else {
                7.dp
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SeatLayerPickerText(
            cell.eyebrow.uppercase(),
            color = scope.theme.mutedText,
            fontSize = eyebrowSize.sp,
            fontWeight = FontWeight.W800,
            maxLines = 1,
            style = androidx.compose.material3.LocalTextStyle.current.copy(
                letterSpacing = (eyebrowSize * 0.1f).sp,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(2.dp))
        SeatLayerPickerText(
            cell.value,
            color = scope.theme.text,
            fontSize = valueSize.sp,
            fontWeight = FontWeight.W800,
            // Only a LONG section name is worth a second line, and only it
            // gives up the big type to get one.
            maxLines = if (cell.longSection) 2 else 1,
            softWrap = cell.longSection,
            style = androidx.compose.material3.LocalTextStyle.current.copy(
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/**
 * The category, in the category's own colour, and what it costs.
 *
 * The map is already painted in these colours, so the band is the one place on
 * the card where naming the category earns its line: the buyer matches the
 * colour to the seat they just tapped. It is the colour ITSELF, full bleed — a
 * tint under a small disc said the colour twice and loudly enough neither time.
 * The words take the ink chosen per colour, so a pale category keeps its name.
 *
 * It prints the name and the price and NOTHING else. A remaining count beside
 * them said nothing a buyer choosing one seat could act on, and it pushed the
 * price into the card's edge.
 */
@Composable
internal fun SeatLayerConfirmCategoryBand(
    category: SeatLayerPickerCategory,
    color: Color,
    price: Double?,
    currency: String,
    immersive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val ink = seatLayerPickerConfirmBandInk(color)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color)
            .heightIn(min = SeatLayerSizeTokens.CONFIRM_BAND_HEIGHT.dp)
            .padding(
                start = if (immersive) {
                    SeatLayerSizeTokens.CONFIRM_IMMERSIVE_BAND_PAD_X.dp
                } else {
                    SeatLayerSizeTokens.CONFIRM_BAND_PAD_LEADING.dp
                },
                end = if (immersive) {
                    SeatLayerSizeTokens.CONFIRM_IMMERSIVE_BAND_PAD_X.dp
                } else {
                    SeatLayerSizeTokens.CONFIRM_BAND_PAD_TRAILING.dp
                },
                top = if (immersive) {
                    SeatLayerSizeTokens.CONFIRM_IMMERSIVE_BAND_PAD_Y.dp
                } else {
                    SeatLayerSizeTokens.CONFIRM_BAND_PAD_TOP.dp
                },
                bottom = if (immersive) {
                    SeatLayerSizeTokens.CONFIRM_IMMERSIVE_BAND_PAD_Y.dp
                } else {
                    SeatLayerSizeTokens.CONFIRM_BAND_PAD_BOTTOM.dp
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The name takes the room and gives way first; the price is the fact
        // the buyer came for and never truncates.
        SeatLayerPickerText(
            category.label,
            modifier = Modifier.weight(1f),
            color = ink,
            fontSize = SeatLayerSizeTokens.CONFIRM_BAND_NAME_FONT_SIZE.sp,
            fontWeight = FontWeight.W800,
            maxLines = 1,
        )
        if (price != null) {
            Spacer(Modifier.width(8.dp))
            SeatLayerPickerText(
                scope.moneyFormatter.format(price, currency),
                color = ink,
                fontSize = if (immersive) {
                    SeatLayerSizeTokens.CONFIRM_IMMERSIVE_BAND_PRICE_FONT_SIZE.sp
                } else {
                    SeatLayerSizeTokens.CONFIRM_BAND_PRICE_FONT_SIZE.sp
                },
                fontWeight = FontWeight.W800,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The one thing a lone tier has to say.
 *
 * A single ticket type is not a choice, so it is never drawn as one. Where that
 * type carries a condition — a companion place that needs the wheelchair space
 * beside it — the condition is the line; where it carries none, there is
 * nothing to print.
 */
@Composable
internal fun SeatLayerConfirmTierNote(note: String, modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    SeatLayerPickerText(
        note,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = note },
        color = scope.theme.mutedText,
        fontSize = SeatLayerTypeTokens.NOTE_BODY_SIZE.sp,
        fontWeight = FontWeight(SeatLayerTypeTokens.NOTE_BODY_WEIGHT),
    )
}

/** The code the snapshot gave the section named [sectionLabel], if any. */
internal fun pickerSectionCode(
    snapshot: io.seatlayer.android.SeatLayerPickerSnapshot?,
    sectionLabel: String?,
): String? {
    val label = sectionLabel?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
    return snapshot?.sections?.firstOrNull { section ->
        section.label.trim().lowercase() == label ||
            section.displayLabel?.trim()?.lowercase() == label
    }?.id
}

internal fun compactSeatLabel(
    seat: SeatLayerPickerSelectedSeat,
    strings: SeatLayerPickerStrings,
): String = listOfNotNull(
    seat.sectionLabel ?: seat.displayLabel ?: seat.label,
    seat.rowLabel?.let { "${strings.rowWord} $it" },
    seat.seatNumber?.let { "${strings.seatWord} $it" },
).distinct().joinToString(" · ")
