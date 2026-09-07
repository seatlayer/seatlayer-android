package io.seatlayer.android.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SeatLayerPickerCardIntent
import io.seatlayer.android.SeatLayerPickerCategoryTier
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import io.seatlayer.android.seatNotes
import kotlinx.coroutines.launch

/**
 * The phone's one-seat decision surface.
 *
 * A buyer who has just tapped a seat is answering one question — this seat,
 * this price, yes or no — so the card is read top to bottom as that question:
 * where the seat is, what it costs, what is special about it, and then the two
 * answers.
 *
 * Where it is comes first as a grid rather than a sentence. One long label is
 * something a buyer has to parse; three labelled cells let the eye land on the
 * number it came for. The category and the price share the band under it, in
 * the same colour the seat is painted on the map.
 *
 * The same card asks the opposite question about a seat already in the cart
 * ([SeatLayerPickerCardIntent.Remove]), raised by a second tap on it. Same box,
 * same identity, same price; only the primary answer changes, and Cancel keeps
 * the seat rather than dropping it. Two surfaces for one gesture would be a
 * second thing to learn for no gain.
 */
@Composable
public fun SeatLayerConfirmCard(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
    compact: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val seat = scope.state.presentation.candidateSeat ?: return
    SeatLayerConfirmCardBody(
        seat = seat,
        intent = scope.state.presentation.cardIntent,
        modifier = modifier,
        style = style,
        immersive = false,
        answered = false,
        onAnswered = {},
    )
}

/** Wide-layout form of the same confirmation state and commands. */
@Composable
public fun SeatLayerPickerSeatConfirmation(
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
) {
    val scope = LocalSeatLayerPickerScope.current
    SeatLayerConfirmCard(
        modifier = modifier,
        style = style ?: scope.styles[SeatLayerPickerPart.SeatConfirmation],
    )
}

/**
 * The card over one named seat.
 *
 * [answered] is the card's own latch rather than a snapshot fact: the cart is
 * told on the press, and only the card's departure waits for the sweep, so the
 * card has to keep drawing itself for a moment after the seat has stopped being
 * a candidate.
 */
@Composable
internal fun SeatLayerConfirmCardBody(
    seat: SeatLayerPickerSelectedSeat,
    intent: SeatLayerPickerCardIntent,
    answered: Boolean,
    onAnswered: (SeatLayerPickerSelectedSeat) -> Unit,
    modifier: Modifier = Modifier,
    style: SeatLayerPickerPartStyle? = null,
    immersive: Boolean = false,
) {
    val scope = LocalSeatLayerPickerScope.current
    val resolvedStyle = style ?: scope.styles[SeatLayerPickerPart.ConfirmCard]
    val coroutineScope = rememberCoroutineScope()
    val removing = intent == SeatLayerPickerCardIntent.Remove
    var selectedTierId by remember(
        seat.id,
        seat.label,
        seat.tierId,
        seat.tiers.map(SeatLayerPickerCategoryTier::id),
    ) {
        mutableStateOf(
            seat.tierId?.takeIf { id -> seat.tiers.any { it.id == id } }
                ?: seat.tiers.firstOrNull()?.id,
        )
    }
    val selectedTier = seat.tiers.firstOrNull { it.id == selectedTierId }
    val shownPrice = selectedTier?.price ?: seat.price
    val shownCurrency = selectedTier?.currency ?: seat.currency
    val category = scope.state.snapshot?.categories?.firstOrNull { it.key == seat.categoryKey }
    val categoryColor = parseColor(category?.color.orEmpty()) ?: scope.theme.accent
    val notes = remember(seat) { seat.seatNotes() }
    val hasSeatView = scope.options.enableSeatView && scope.controller.supportsSeatView
    val hasVenue3D = scope.options.enable3D && scope.controller.supportsVenue3D
    // The photograph, the sight line and the disclosure are read only where
    // the runtime says it reports them. An older runtime that says nothing is
    // a card with no strip, never a card with an empty frame — and a stand-in
    // the card could draw for any seat is never offered at all.
    val thumbnails = scope.controller.supportsSeatViewThumbnail
    val thumbnail = if (thumbnails) seat.seatViewThumbnail else null
    // The strip is what `View from here` opens, so it is drawn only where that
    // action AND an authored photograph both exist.
    val realPhoto = thumbnail != null && hasSeatView
    var photoMissed by remember(seat.id, thumbnail?.reference) { mutableStateOf(false) }
    // The sight line rides the strip. With no photograph the strip leaves the
    // card, and a lone measured line is not worth a row of its own.
    val sightlineMetres = if (thumbnails) seat.sightlineMetres else null
    val confidence = if (thumbnails) seat.seatViewConfidence else null
    val onConfidence = scope.callbacks.onSeatConfidence
    val primaryFocus = remember { FocusRequester() }
    val busy = scope.state.presentation.actionInFlight
    val sweep = rememberPressSweep(answered, !pickerReducedMotion())
    var engaged by remember(seat.id) { mutableStateOf(false) }

    val primaryLabel = when {
        removing -> scope.strings.removeSeat
        answered -> scope.strings.added
        seat.objectType == "seat" || seat.objectType == null -> scope.strings.addSeat
        else -> scope.strings.select
    }
    val onCancelAction = {
        engaged = true
        coroutineScope.launch {
            if (removing) {
                // Cancel is a way OUT of the question, not an answer to it:
                // the seat stays.
                scope.controller.dismissSeatRemoval()
            } else {
                scope.controller.cancelPending()
            }
        }
        Unit
    }
    val onPrimaryAction = {
        engaged = true
        if (!answered) onAnswered(seat)
    }
    // The identity reads as ONE sentence — section, row, seat, category, price
    // — rather than as six unlabelled cells, and that sentence IS the card's
    // name to assistive technology.
    val identity = seatLayerIdentitySentence(
        seatLayerIdentityCells(
            seat,
            scope.strings,
            pickerSectionCode(scope.state.snapshot, seat.sectionLabel),
        ),
        scope.strings,
    )
    val spokenName = listOfNotNull(
        identity,
        category?.label,
        scope.moneyFormatter.format(shownPrice, shownCurrency),
    ).joinToString(separator = " \u00b7 ")

    val loneTierNote = seat.tiers.singleOrNull()?.let { tier ->
        tier.buyerMessage?.takeIf(String::isNotBlank)
            ?: scope.strings.tierCompanionGuidance.takeIf { tier.restriction == "companion" }
    }
    val tierContent = seat.tiers.size > 1 || loneTierNote != null
    val bodyContent = notes.isNotEmpty() || tierContent
    // Inside the scene the passport and the view from the seat are ONE row of
    // compact chips; the 3D square is never drawn there, because the buyer is
    // already in the scene it opens.
    val openPassport = onConfidence
    val onPassport: (() -> Unit)? = if (immersive && confidence != null && openPassport != null) {
        { openPassport(confidence) }
    } else {
        null
    }
    val passportUp = onPassport != null
    val inspectRow = immersive && (passportUp || hasSeatView)
    // No photograph, no strip — so the 3D way in takes its place in the
    // decision row. It is still one tap away, and it costs no row of its own.
    val show3DSquare = !immersive && hasVenue3D && !(realPhoto && !photoMissed)
    val open3D: () -> Unit = {
        coroutineScope.launch {
            scope.performAction {
                scope.controller.setBuyerView(view = "venue3d", flyToSeatId = seat.id)
            }
        }
        Unit
    }
    val openSeatView: () -> Unit = {
        coroutineScope.launch {
            scope.performAction { scope.controller.openSeatView(seat.id) }
            scope.callbacks.onSeatViewOpened(seat.id)
        }
        Unit
    }

    SeatLayerPickerTypeClamp(SeatLayerTypeTokens.SCALE_CLAMP_CARD) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(
                    max = if (immersive) {
                        SeatLayerSizeTokens.CONFIRM_CARD_IMMERSIVE_MAX_WIDTH.dp
                    } else {
                        SeatLayerSizeTokens.CONFIRM_CARD_MAX_WIDTH.dp
                    },
                )
                // A dialog: it names itself, it is traversed first, and both
                // answers are also actions a rotor can reach without hunting
                // for the buttons.
                .semantics {
                    dialog()
                    isTraversalGroup = true
                    traversalIndex = -1f
                    paneTitle = spokenName
                    customActions = listOf(
                        CustomAccessibilityAction(primaryLabel) {
                            onPrimaryAction()
                            true
                        },
                        CustomAccessibilityAction(scope.strings.cancel) {
                            onCancelAction()
                            true
                        },
                    )
                },
            shape = RoundedCornerShape(
                resolvedStyle.cornerRadius ?: SeatLayerRadiusTokens.CONFIRM_CARD.dp,
            ),
            color = resolvedStyle.containerColor ?: scope.theme.surface,
            contentColor = resolvedStyle.contentColor ?: scope.theme.text,
            // The card's edge is the divider pulled toward the ink, so it holds
            // against a map of any colour behind it.
            border = BorderStroke(
                Dp.Hairline,
                lerp(scope.theme.divider, scope.theme.text, EDGE_TOWARD_INK),
            ),
            shadowElevation = resolvedStyle.elevation
                ?: SeatLayerElevationTokens.CONFIRM_CARD.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                SeatLayerConfirmIdentityGrid(seat, immersive)
                // The band is the category speaking for itself: its colour, its
                // name and what it costs. Without a category there is nothing
                // for it to say, and a price with no name beside it belongs
                // nowhere on this card.
                if (category != null) {
                    SeatLayerConfirmCategoryBand(
                        category = category,
                        color = categoryColor,
                        price = shownPrice,
                        currency = shownCurrency,
                        immersive = immersive,
                    )
                }
                if (!immersive && realPhoto && thumbnail != null) {
                    SeatLayerConfirmPhotoSlot(
                        thumbnail = thumbnail,
                        missed = photoMissed,
                        sightlineMetres = sightlineMetres,
                        onMissed = { photoMissed = true },
                        onViewFromSeat = openSeatView.takeIf { !busy },
                        onShow3D = open3D.takeIf { hasVenue3D && !busy },
                    )
                }
                // The notes are BANDS, and they sit directly under the category
                // band: below the tier chooser they read as a footnote to the
                // price list rather than as facts about the seat. So the body
                // owns no padding of its own — the bands reach both edges and
                // the tier chooser carries the card's gutter itself.
                if (bodyContent) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SeatLayerSeatNotes(notes, compact = immersive)
                        if (tierContent) {
                            Column(
                                Modifier.padding(
                                    start = GUTTER,
                                    end = GUTTER,
                                    top = if (notes.isEmpty()) 8.dp else 10.dp,
                                ),
                            ) {
                                if (seat.tiers.size > 1) {
                                    SeatLayerPickerTierChoices(
                                        seat = seat,
                                        selectedTierId = selectedTierId,
                                        enabled = !busy,
                                        onTierSelected = { selectedTierId = it },
                                    )
                                } else {
                                    SeatLayerConfirmTierNote(loneTierNote.orEmpty())
                                }
                            }
                        }
                    }
                }
                // A disclosure with nowhere to open stays the teaser it has
                // always been: the headline and the detail ARE the information,
                // and a chip saying only one word beside a dead target would
                // say nothing and do nothing.
                if (immersive && confidence != null && onConfidence == null) {
                    SeatLayerConfidenceTeaser(
                        confidence,
                        Modifier.padding(horizontal = GUTTER),
                    )
                }
                if (inspectRow) {
                    SeatLayerInspectionRow(
                        onPassport = onPassport,
                        onViewFromSeat = openSeatView.takeIf { hasSeatView && !busy },
                        modifier = Modifier.padding(
                            start = GUTTER,
                            end = GUTTER,
                            top = if (bodyContent) {
                                SeatLayerSizeTokens.CONFIRM_IMMERSIVE_INSPECT_GAP.dp
                            } else {
                                (
                                    SeatLayerSizeTokens.CONFIRM_IMMERSIVE_BODY_TOP +
                                        SeatLayerSizeTokens.CONFIRM_IMMERSIVE_INSPECT_GAP
                                    ).dp
                            },
                        ),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = GUTTER,
                            end = GUTTER,
                            top = when {
                                immersive && (bodyContent || inspectRow) ->
                                    SeatLayerSizeTokens.CONFIRM_IMMERSIVE_ACTION_GAP.dp
                                immersive -> SeatLayerSizeTokens.CONFIRM_IMMERSIVE_BODY_TOP.dp
                                bodyContent -> 10.dp
                                else -> 8.dp
                            },
                            bottom = if (immersive) {
                                SeatLayerSizeTokens.CONFIRM_IMMERSIVE_BODY_BOTTOM.dp
                            } else {
                                GUTTER
                            },
                        )
                        // The row is a HEIGHT rather than a minimum because the
                        // primary paints its sweep behind the label; it grows
                        // with the buyer's type setting up to the card's clamp
                        // rather than clipping the word it exists to offer.
                        .height(
                            pickerScaledExtent(
                                SeatLayerSizeTokens.CONFIRM_ACTION_HEIGHT,
                                SeatLayerTypeTokens.SCALE_CLAMP_CARD,
                            ),
                        ),
                ) {
                    SeatLayerConfirmActions(
                        primaryLabel = primaryLabel,
                        cancelLabel = scope.strings.cancel,
                        destructive = removing,
                        answered = answered,
                        sweep = sweep,
                        invite = !engaged && !answered,
                        enabled = !answered,
                        onCancel = onCancelAction,
                        onPrimary = onPrimaryAction,
                        show3DSquare = show3DSquare,
                        on3D = open3D.takeIf { show3DSquare && !busy },
                        primaryFocus = primaryFocus,
                    )
                }
            }
        }
    }
    SeatLayerFocusPrimaryOnArrival(primaryFocus, seat.id)
}

/** The card's own gutter: the boxes inside it stand this far from its edge. */
private val GUTTER: Dp = 10.dp

/** How far the card's hairline is pulled from the divider toward the ink. */
private const val EDGE_TOWARD_INK = 0.3f
