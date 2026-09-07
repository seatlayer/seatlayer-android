package io.seatlayer.android.compose

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * What the picker owes a buyer who is not looking at it.
 *
 * The picker is one screen with many surfaces, and most of what such a buyer
 * needs is a property of the WHOLE screen rather than of any one component: the
 * order the surfaces are read in, which of them are inert while another holds
 * the screen, how far each lets the platform grow its type. Those live here,
 * once, and the composition root applies them — every component is also
 * mountable on its own, where there is no order and no screen to hold.
 *
 * Two settings are deliberately NOT decided here:
 *
 * - `bold text` is applied by the platform. Compose installs an
 *   `AndroidFontResolveInterceptor` carrying the configuration's
 *   `fontWeightAdjustment` into the font resolver, so every weight the picker
 *   asks for already arrives heavier, clamped at 900. A port that applied it
 *   again would move the picker's 600-800 range two steps twice.
 * - reduced motion has one accessor, [pickerReducedMotion], and it lives here
 *   with the rest so no surface has to guess where to read it.
 */

/**
 * The order assistive technology walks the picker in.
 *
 * The phone composition is a column with a stack in the middle, and a stack's
 * paint order is not a reading order: the dock is painted between two halves of
 * the map's own chrome, and the seat card after the toast that answers it. So
 * every surface declares where it sits, and the order is the buyer's — whose
 * event this is, what the prices are, the venue, where in it they are, then
 * what they have chosen.
 *
 * The numbers are spaced so a surface can be inserted between two of them
 * without renumbering the rest.
 */
internal object SeatLayerPickerReadingOrder {
    /** Whose event this is, the hold, and the way out. */
    internal const val HEADER: Float = 100f

    /** The price rail and the Map/3D control that shares its band. */
    internal const val RAIL: Float = 200f

    /** The drawn map itself. */
    internal const val MAP: Float = 300f

    /** Chrome standing on the map: floor rail, test chip, corner controls. */
    internal const val MAP_CHROME: Float = 400f

    /** Where in the venue the buyer is, where a host opted into a dock. */
    internal const val DOCK: Float = 500f

    /** The seat card, and the general-admission and table prompts. */
    internal const val PROMPT: Float = 600f

    /** Toasts, hold notices and the buyer-facing state overlays. */
    internal const val NOTICE: Float = 700f

    /** The cart, at peek or open. */
    internal const val SHEET: Float = 800f
}

/**
 * Puts this subtree at [order] in the picker's one reading order.
 *
 * A traversal index only orders siblings inside the nearest traversal group, so
 * the subtree declares itself a group as well: the surface moves whole, and its
 * own internals keep their geometric order.
 *
 * Sibling surfaces are either ALL ordered or none are. A group with some
 * ordered members falls back to geometry for the rest, which is how the map
 * came to be read before the prices.
 */
internal fun Modifier.pickerReadingOrder(order: Float): Modifier = semantics {
    isTraversalGroup = true
    traversalIndex = order
}

/**
 * Hides this subtree from assistive technology while [hidden].
 *
 * Modality is a property of the layout parent, not of the surface that took the
 * screen: while a decision surface is up, everything painted before it — the
 * map, its chrome, the dock, the header and the rail — is hidden, exactly as a
 * modal route hides the page under it. The toast band and the cart stay
 * audible: the toast is the answer to the press, and the cart is what the seat
 * is being added to.
 */
internal fun Modifier.pickerHiddenFromAccessibility(hidden: Boolean): Modifier =
    if (!hidden) this else semantics { hideFromAccessibility() }

/**
 * Whether a surface is holding the screen and everything under it is inert.
 *
 * One predicate, read by the composition root. The accessibility sheet is
 * deliberately absent: it is a modal bottom sheet in its own window, and the
 * platform already hides the window beneath it.
 */
@Composable
internal fun pickerDecisionSurfaceUp(): Boolean {
    val scope = LocalSeatLayerPickerScope.current
    val state = scope.state
    val options = scope.options
    val presentation = state.presentation
    val immersive = state.seatView != null || state.snapshot?.map?.isVenue3D == true
    val cardUp = presentation.candidateSeat != null &&
        options.chrome.showConfirmCard &&
        !immersive
    val promptUp = presentation.activePrompt != null && !immersive
    val bookedUp = options.showBookedOverlay && presentation.bookedHandoff != null
    val soldOut = state.isReady && !immersive && seatLayerPickerIsSoldOut(state)
    return cardUp ||
        promptUp ||
        presentation.cartExpanded ||
        bookedUp ||
        soldOut ||
        state.pickerAccessUnavailable() ||
        !state.isReady
}

/**
 * How far the platform's text-size setting has actually grown the type in this
 * subtree, once [clamp] has had its say.
 *
 * Never below 1: a buyer who has made their text SMALLER is not asking for a
 * shorter dock bar, and a bar that shrank with the type would leave its
 * 44-point controls without room.
 */
@Composable
internal fun pickerClampedFontScale(clamp: Float): Float {
    val scale = LocalDensity.current.fontScale
    if (!scale.isFinite() || scale <= 1f) return 1f
    return if (scale > clamp) clamp else scale
}

/**
 * [base] grown by the surface's clamped text scale.
 *
 * The fixed heights in the design source are heights for type at 1.0. Left
 * fixed they become ceilings the moment a buyer scales their text up, and a bar
 * whose contents are taller than the bar clips. Used as the height wherever the
 * runtime is also being told what the chrome covers — the reported band and the
 * drawn band have to be the same number.
 *
 * At the platform default of 1.0 this is [base], which is what keeps the
 * goldens still.
 */
@Composable
internal fun pickerScaledExtent(base: Float, clamp: Float): Dp =
    (base * pickerClampedFontScale(clamp)).dp

/**
 * Whether this device has asked for animations to be removed.
 *
 * Read once per composition from the platform's own animator scale: a buyer who
 * has turned animations off in Accessibility has said it for every app, and the
 * picker has no business asking again. Every duration in the picker is routed
 * through this one accessor, and motion with no reduced form is skipped while
 * anything sequenced behind it runs immediately.
 */
@Composable
internal fun pickerReducedMotion(): Boolean {
    val context = LocalContext.current
    // A capture is one frame of a moving surface, so it asks for the settled
    // one. See [LocalSeatLayerPickerCapture].
    if (LocalSeatLayerPickerCapture.current) return true
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}
