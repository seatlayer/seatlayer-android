package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.seatlayer.android.SeatLayerPickerRemovalPhase
import io.seatlayer.android.SeatLayerPickerRemovalUndo
import io.seatlayer.android.SeatLayerPickerState
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Modality, as the layout parent sets it.
 *
 * While a decision surface holds the screen, everything painted before it is
 * inert to assistive technology; the surface itself, the toast that answers it
 * and the cart it adds to are not.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PickerAccessibilityCrossCutTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun theMapAndItsChromeAreInertWhileTheCardAsks() {
        render(pickerEvidenceScope(PickerEvidenceScenario.Confirmation, LIGHT))
        assertTrue(
            "the header is painted before the card and must be inert",
            hiddenFromAccessibility(node(text = "Fixture Arena")),
        )
    }

    @Test
    public fun theCardAndTheCartStayAudibleWhileTheCardAsks() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.Confirmation, LIGHT)
        render(scope)
        assertFalse(
            "the card is the surface asking the question",
            hiddenFromAccessibility(node(text = scope.strings.addSeat)),
        )
        assertFalse(
            "the cart is what the seat is being added to",
            hiddenFromAccessibility(node(text = scope.strings.holdAndCheckout)),
        )
    }

    @Test
    public fun theToastStaysAudibleWhileTheCardAsks() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.Confirmation, LIGHT)
        withRemovalToast(scope)
        render(scope)
        assertFalse(
            "the toast is the answer to the press",
            hiddenFromAccessibility(node(text = scope.strings.seatRemoved)),
        )
    }

    @Test
    public fun nothingIsInertWhileNoSurfaceHoldsTheScreen() {
        render(pickerEvidenceScope(PickerEvidenceScenario.Overview, LIGHT))
        assertFalse(
            "an ordinary picker hides nothing",
            hiddenFromAccessibility(node(text = "Fixture Arena")),
        )
    }

    @Test
    public fun theCartSheetMakesTheMapInert() {
        render(pickerEvidenceScope(PickerEvidenceScenario.Cart, LIGHT))
        assertTrue(
            "the open sheet is a decision surface too",
            hiddenFromAccessibility(node(text = "Fixture Arena")),
        )
    }

    @Test
    public fun everySurfaceOverTheMapDeclaresItsPlaceInTheOneOrder() {
        render(pickerEvidenceScope(PickerEvidenceScenario.Confirmation, LIGHT))
        val partial = partiallyOrderedGroups(
            composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode(),
        )
        assertTrue(
            "a group with some ordered members falls back to geometry: " +
                partial.joinToString(),
            partial.isEmpty(),
        )
    }

    @Test
    public fun theOrderIsCompleteWithTheSheetOpenToo() {
        render(pickerEvidenceScope(PickerEvidenceScenario.Cart, LIGHT))
        val partial = partiallyOrderedGroups(
            composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode(),
        )
        assertTrue(partial.joinToString(), partial.isEmpty())
    }

    @Test
    public fun theOrderIsCompleteOnAnOrdinaryPicker() {
        render(pickerEvidenceScope(PickerEvidenceScenario.Overview, LIGHT))
        val partial = partiallyOrderedGroups(
            composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode(),
        )
        assertTrue(partial.joinToString(), partial.isEmpty())
    }

    @Test
    public fun theOrderIsTheBuyersOwn() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.Confirmation, LIGHT)
        render(scope)
        assertTrue(
            "the header is read first",
            SeatLayerPickerReadingOrder.HEADER in orderPath(node(text = "Fixture Arena")),
        )
        assertTrue(
            "the prices are read after the header",
            SeatLayerPickerReadingOrder.RAIL in
                orderPath(described(scope.strings.allPrices)),
        )
        assertTrue(
            "the map's controls are read on the map's own rung",
            SeatLayerPickerReadingOrder.MAP_CHROME in
                orderPath(described(scope.strings.zoomIn)),
        )
        assertTrue(
            "the card is read where a prompt is read",
            SeatLayerPickerReadingOrder.PROMPT in orderPath(node(text = scope.strings.addSeat)),
        )
        assertTrue(
            "the cart is read last",
            SeatLayerPickerReadingOrder.SHEET in
                orderPath(node(text = scope.strings.holdAndCheckout)),
        )
    }

    /** Every traversal index between [node] and the root, nearest first. */
    private fun orderPath(node: SemanticsNode): List<Float> {
        val path = mutableListOf<Float>()
        var walk: SemanticsNode? = node
        while (walk != null) {
            walk.config.getOrElseNullable(SemanticsProperties.TraversalIndex) { null }
                ?.let(path::add)
            walk = walk.parent
        }
        return path
    }

    /**
     * Parents whose children are only PARTLY ordered.
     *
     * Compose falls back to geometry for a sibling with no index, so one
     * unordered surface in an ordered group re-orders the whole group.
     */
    private fun partiallyOrderedGroups(root: SemanticsNode): List<String> {
        val broken = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            val speaking = node.children.filter(::speaks)
            val ordered = speaking.filter {
                it.config.contains(SemanticsProperties.TraversalIndex)
            }
            if (ordered.isNotEmpty() && ordered.size != speaking.size) {
                broken += "node ${node.id} at ${node.positionInRoot}: " +
                    speaking.filterNot { it in ordered }.joinToString { "#" + it.id }
            }
            node.children.forEach(::walk)
        }
        walk(root)
        return broken
    }

    /** Whether this subtree has anything to say to a screen reader. */
    private fun speaks(node: SemanticsNode): Boolean {
        val config = node.config
        val own = config.contains(SemanticsProperties.ContentDescription) ||
            config.contains(SemanticsProperties.Text) ||
            config.contains(SemanticsProperties.Role)
        return own || node.children.any(::speaks)
    }

    private fun node(text: String): SemanticsNode =
        composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()

    private fun described(description: String): SemanticsNode =
        composeRule
            .onNodeWithContentDescription(description, useUnmergedTree = true)
            .fetchSemanticsNode()

    /** Whether [node] or anything above it has been marked inert. */
    private fun hiddenFromAccessibility(node: SemanticsNode): Boolean {
        var walk: SemanticsNode? = node
        while (walk != null) {
            if (walk.config.contains(SemanticsProperties.HideFromAccessibility)) return true
            walk = walk.parent
        }
        return false
    }

    /** Raises the one toast the picker itself owns: a removal it can take back. */
    @Suppress("UNCHECKED_CAST")
    private fun withRemovalToast(scope: SeatLayerPickerScope) {
        val field = SeatLayerPickerStateHolder::class.java.getDeclaredField("mutableState")
        field.isAccessible = true
        val flow = field.get(scope.stateHolder) as MutableStateFlow<SeatLayerPickerState>
        val state = flow.value
        val snapshot = state.snapshot ?: return
        // A ticket the cart no longer holds, or the undo has nothing to offer.
        val removed = snapshot.cartLines.first().copy(
            lineKey = "line-removed",
            objectId = "seat-removed",
            seatId = "seat-removed",
            seatNumber = "99",
        )
        flow.value = state.copy(
            presentation = state.presentation.copy(
                removalUndo = SeatLayerPickerRemovalUndo(
                    line = removed,
                    sessionId = snapshot.sessionId,
                    phase = SeatLayerPickerRemovalPhase.UndoWindow,
                ),
            ),
        )
    }

    private fun render(scope: SeatLayerPickerScope) {
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = scope.themeMode,
                explicitTheme = scope.theme,
                branding = scope.state.snapshot?.branding,
            ) {
                CompositionLocalProvider(LocalSeatLayerPickerScope provides scope) {
                    Box(Modifier.fillMaxSize().background(scope.theme.background)) {
                        with(scope) {
                            SeatLayerReadyMadePicker(
                                modifier = Modifier.fillMaxSize(),
                                builders = SeatLayerPickerBuilders(map = { _, _ -> }),
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
    }

    private companion object {
        val LIGHT = SeatLayerPickerThemeMode.Light
        const val SETTLE_MILLIS = 500L
    }
}
