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
