package io.seatlayer.android.compose

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seatlayer.android.SeatLayerPickerSeatViewThumbnail

/*
 * The seat photograph, the sight line printed on it, and the two ways into the
 * view it shows.
 *
 * The picture is AUTHORED: the runtime names it on the seat as an event-scoped
 * API path, and the bytes come back through the buyer asset loader because a
 * private event answers that path only for the buyer's own bearer. Until they
 * land the strip is the neutral gradient; if they never land the strip animates
 * itself away and leaves the card entirely, so the buyer is never shown an
 * empty frame where a view was promised.
 */

/**
 * The photo strip, and the nothing it collapses into, as one animated slot.
 *
 * [missed] is the CARD's state rather than this composable's: when the
 * photograph goes, the 3D way in has to appear in the decision row below, and
 * only the card can put it there.
 */
@Composable
internal fun SeatLayerConfirmPhotoSlot(
    thumbnail: SeatLayerPickerSeatViewThumbnail,
    missed: Boolean,
    sightlineMetres: Double?,
    onMissed: () -> Unit,
    onViewFromSeat: (() -> Unit)?,
    onShow3D: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val reduced = pickerReducedMotion()
    val collapse = if (reduced) 0 else SeatLayerMotionTokens.THUMB_OUT
    AnimatedVisibility(
        visible = !missed,
        modifier = modifier,
        enter = fadeIn(tween(collapse)),
        exit = shrinkVertically(tween(collapse)) + fadeOut(tween(collapse)),
    ) {
        SeatLayerConfirmPhotoStrip(
            thumbnail = thumbnail,
            sightlineMetres = sightlineMetres,
            onMissed = onMissed,
            onViewFromSeat = onViewFromSeat,
            onShow3D = onShow3D,
        )
    }
}

/**
 * The photograph with both ways into it riding its bottom corners.
 *
 * Full-bleed inside the card's own radius: a photograph inset from the card's
 * edge reads as an illustration in an article rather than as the view being
 * sold.
 */
@Composable
internal fun SeatLayerConfirmPhotoStrip(
    thumbnail: SeatLayerPickerSeatViewThumbnail,
    sightlineMetres: Double?,
    onMissed: () -> Unit,
    onViewFromSeat: (() -> Unit)?,
    onShow3D: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSeatLayerPickerScope.current
    val loader = rememberSeatLayerBuyerAssetLoader()
    val reduced = pickerReducedMotion()
    var image by remember(thumbnail.reference) { mutableStateOf<ImageBitmap?>(null) }

    // Asked for as soon as the card is composed rather than when it settles:
    // a buyer looking at a card should not wait for an animation to end before
    // the picture starts arriving. The wait is the card's; the request is the
    // loader's, and it outlives this composable so reopening the seat is
    // instant.
    LaunchedEffect(thumbnail.reference) {
        val bytes = loader.load(thumbnail.reference)
        val decoded = bytes?.let(::seatLayerDecodeImage)
        if (decoded == null) {
            // A miss is evicted so the next open of the same seat tries again:
            // the usual reason for one is a bearer that had just expired.
            loader.evict(thumbnail.reference)
            onMissed()
        } else {
            image = decoded
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SeatLayerSizeTokens.CONFIRM_PHOTO_HEIGHT.dp)
            .background(
                Brush.linearGradient(
                    listOf(
                        pickerWash(
                            scope.theme.accent,
                            ConfirmPhotoLiterals.GRADIENT_ACCENT_WASH,
                            scope.theme.surface,
                        ),
                        pickerWash(
                            scope.theme.text,
                            ConfirmPhotoLiterals.GRADIENT_INK_WASH,
                            scope.theme.surface,
                        ),
                    ),
                ),
            ),
    ) {
        val bitmap = image
        if (bitmap != null) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(
                    tween(if (reduced) 0 else SeatLayerMotionTokens.CROSSFADE),
                ),
            ) {
                Image(
                    bitmap = bitmap,
                    // The picture is what the pills open, and the pills say
                    // what they open: a second name would be read out twice.
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clearAndSetSemantics { },
                    contentScale = ContentScale.Crop,
                )
            }
        }
        if (sightlineMetres != null) {
            SeatLayerSightlinePill(
                metres = sightlineMetres,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(ConfirmPhotoLiterals.INSET),
            )
        }
        if (onViewFromSeat != null) {
            SeatLayerPhotoPill(
                glyph = SeatLayerPickerGlyph.Eye,
                label = scope.strings.viewFromHere,
                spoken = scope.strings.viewFromHere,
                onClick = onViewFromSeat,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(ConfirmPhotoLiterals.INSET),
            )
        }
        if (onShow3D != null) {
            SeatLayerPhotoPill(
                glyph = SeatLayerPickerGlyph.Cube,
                label = scope.strings.venue3D,
                // The pill says "3D"; a screen reader hears the sentence.
                spoken = scope.strings.seeItIn3D,
                onClick = onShow3D,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(ConfirmPhotoLiterals.INSET),
            )
        }
    }
}

/** The measured line, where there is a photograph to print it on. */
@Composable
private fun SeatLayerSightlinePill(metres: Double, modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    Box(
        modifier = modifier
            .background(
                ConfirmPhotoLiterals.plate(),
                RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
            )
            .padding(
                horizontal = SeatLayerSizeTokens.CONFIRM_SIGHT_PAD_X.dp,
                vertical = SeatLayerSizeTokens.CONFIRM_SIGHT_PAD_Y.dp,
            ),
    ) {
        SeatLayerPickerText(
            scope.strings.sightline(seatLayerSightlineFigure(metres)),
            color = ConfirmPhotoLiterals.plateInk(),
            fontSize = SeatLayerSizeTokens.CONFIRM_SIGHT_FONT.sp,
            fontWeight = FontWeight.W700,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The metre figure as the runtime rounded it.
 *
 * The number arrives already rounded, so this only decides whether to print a
 * decimal point at all: `7` rather than `7.0`, `7.4` unchanged.
 */
internal fun seatLayerSightlineFigure(metres: Double): String =
    if (metres == Math.rint(metres)) metres.toLong().toString() else metres.toString()

/**
 * A way into the view, on the photograph's own dark plate.
 *
 * Dark plate and white ink in both themes: a photograph can be any colour, and
 * the one pairing that survives all of them is white on near-black.
 */
@Composable
private fun SeatLayerPhotoPill(
    glyph: SeatLayerPickerGlyph,
    label: String,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = ConfirmPhotoLiterals.plateInk()
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = SeatLayerSizeTokens.CONFIRM_PILL_HEIGHT.dp)
            .semantics { contentDescription = spoken },
        shape = RoundedCornerShape(SeatLayerRadiusTokens.PILL.dp),
        color = ConfirmPhotoLiterals.plate(),
        contentColor = ink,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ConfirmPhotoLiterals.PILL_PAD_X,
                vertical = ConfirmPhotoLiterals.PILL_PAD_Y,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeatLayerPickerIcon(glyph, ink, Modifier.size(ConfirmPhotoLiterals.PILL_GLYPH))
            Spacer(Modifier.width(ConfirmPhotoLiterals.PILL_GAP))
            SeatLayerPickerText(
                label,
                color = ink,
                fontSize = ConfirmPhotoLiterals.PILL_FONT.sp,
                fontWeight = FontWeight.W800,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Bytes to a picture, or null for anything that is not one. */
internal fun seatLayerDecodeImage(bytes: ByteArray): ImageBitmap? = runCatching {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

/**
 * The photo strip's own measures, which the design source names no token for.
 *
 * Kept together and off the composables so a value that later earns a token
 * has one place to move from.
 */
internal object ConfirmPhotoLiterals {
    /** How far the pills stand off the photograph's edges. */
    val INSET: Dp = 6.dp
    val PILL_PAD_X: Dp = 10.dp
    val PILL_PAD_Y: Dp = 5.dp
    val PILL_GLYPH: Dp = 13.dp
    val PILL_GAP: Dp = 5.dp
    const val PILL_FONT = 11f
    const val GRADIENT_ACCENT_WASH = 0.22f
    const val GRADIENT_INK_WASH = 0.12f

    /** The photo scrim, dark in both themes because a photograph can be anything. */
    fun plate(): Color = Color(red = 0x0A, green = 0x0E, blue = 0x16, alpha = 0xB8)

    /** Its ink. Measured at 7.5:1 over a pure-white photograph. */
    fun plateInk(): Color = Color.White
}

/**
 * The session's buyer-scoped image transport.
 *
 * Provided once for the whole picker scope rather than remembered by the card,
 * so the small cache behind it survives a card closing and reopening the same
 * seat is instant. A part composed outside a scope — which the picker itself
 * never does — falls back to one of its own rather than failing.
 */
@Composable
internal fun rememberSeatLayerBuyerAssetLoader(): SeatLayerBuyerAssetLoader {
    val provided = LocalSeatLayerBuyerAssets.current
    val holder = LocalSeatLayerPickerScope.current.stateHolder
    val fallback = remember(holder, provided) {
        if (provided == null) SeatLayerBuyerAssetLoader(holder.configuration) else null
    }
    DisposableEffect(fallback) { onDispose { fallback?.clear() } }
    return provided ?: fallback!!
}

/** The loader the picker scope installs for every surface under it. */
internal val LocalSeatLayerBuyerAssets =
    staticCompositionLocalOf<SeatLayerBuyerAssetLoader?> { null }
