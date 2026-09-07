package io.seatlayer.android.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.seatlayer.android.SEATLAYER_PICKER_ACCESS_NEED_KEYS
import io.seatlayer.android.SeatLayerPickerCardIntent
import io.seatlayer.android.SeatLayerPickerSeatCommercial
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import io.seatlayer.android.SeatLayerPickerThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The card as it is actually drawn and actually read out. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeatLayerPickerSeatCardRenderTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var strings: SeatLayerPickerStrings

    @Test
    fun everyGlyphTheModelCanNameHasADrawing() {
        val keys = SEATLAYER_PICKER_ACCESS_NEED_KEYS +
            listOf("restrictedView", "obstructedView", "premium", "note")
        keys.forEach { key ->
            assertNotNull("no drawing for $key", seatLayerSeatIconPath(key))
        }
        // A key this build does not know draws nothing rather than a broken box.
        assertNull(seatLayerSeatIconPath("teleportation"))
        assertNotNull(seatLayerSeatIconFillPath("contrast"))
        assertNull(seatLayerSeatIconFillPath("premium"))
    }

    @Test
    fun theAddCardNamesItselfAndCarriesBothAnswersAsActions() {
        card(SeatLayerPickerCardIntent.Add)

        val node = composeRule.onRoot().fetchSemanticsNode()
        val title = node.findValue(SemanticsProperties.PaneTitle)
        assertNotNull(title)
        assertTrue("pane title was $title", title!!.contains("Row A"))
        assertTrue(title.contains("Seat 12"))
        // The category and the price are part of the sentence the card is named
        // by, not a second thing to hunt for.
        assertTrue(title.contains("Standard"))

        val actions = node.findValue(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf(strings.addSeat, strings.cancel), actions.map { it.label })
    }

    @Test
    fun theRemoveCardAsksTheOppositeQuestionInTheSameBox() {
        card(SeatLayerPickerCardIntent.Remove)

        composeRule.onNodeWithText(strings.removeSeat).assertIsDisplayed()
        composeRule.onNodeWithText(strings.cancel).assertIsDisplayed()
        val actions = composeRule.onRoot().fetchSemanticsNode()
            .findValue(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf(strings.removeSeat, strings.cancel), actions.map { it.label })
    }

    @Test
    fun theCardIsTraversedBeforeEverythingPaintedBeforeIt() {
        card(SeatLayerPickerCardIntent.Add)

        val node = composeRule.onRoot().fetchSemanticsNode()
        assertEquals(-1f, node.findValue(SemanticsProperties.TraversalIndex))
        assertEquals(true, node.findValue(SemanticsProperties.IsTraversalGroup))
    }

    @Test
    fun aSeatsOwnAttributesAreDrawnAsBandsInOneFixedOrder() {
        card(
            SeatLayerPickerCardIntent.Add,
            seat = seat().copy(
                accessibility = listOf("hearing"),
                commercial = SeatLayerPickerSeatCommercial(
                    restrictedView = true,
                    obstructedView = true,
                    premium = true,
                    organizerNote = ORGANIZER_NOTE,
                ),
            ),
        )

        // The bands speak as one sentence each, and the organizer's line hangs
        // on the first selling mark rather than standing alone.
        val expected = listOf(
            strings.accessNeed("hearing"),
            strings.restrictedView + ": " + ORGANIZER_NOTE,
            strings.obstructedView,
            strings.premiumSeat,
        )
        val spoken = composeRule.onRoot().fetchSemanticsNode().descriptions()
        assertEquals(expected, spoken.filter { it in expected })
    }

    @Test
    fun theCardOffersNoPassportWhereTheHostTookNoCallback() {
        card(SeatLayerPickerCardIntent.Add, immersive = true)
        composeRule.onNodeWithText(strings.passport).assertIsNotDisplayed()
    }

    private fun card(
        intent: SeatLayerPickerCardIntent,
        seat: SeatLayerPickerSelectedSeat = seat(),
        immersive: Boolean = false,
    ) {
        val scope = pickerEvidenceScope(
            PickerEvidenceScenario.Confirmation,
            SeatLayerPickerThemeMode.Light,
            SeatLayerPickerOptions(),
        )
        strings = scope.strings
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = SeatLayerPickerThemeMode.Light,
                explicitTheme = scope.theme,
                branding = null,
            ) {
                CompositionLocalProvider(LocalSeatLayerPickerScope provides scope) {
                    Box(Modifier.fillMaxSize()) {
                        SeatLayerConfirmCardBody(
                            seat = seat,
                            intent = intent,
                            answered = false,
                            onAnswered = {},
                            immersive = immersive,
                        )
                    }
                }
            }
        }
    }

    private fun seat() = SeatLayerPickerSelectedSeat(
        id = "seat-1",
        label = "A-12",
        displayLabel = null,
        displayType = null,
        objectId = null,
        objectType = "seat",
        bookingMode = null,
        sectionLabel = "Stalls",
        rowLabel = "A",
        seatNumber = "12",
        categoryKey = "standard",
        price = 54.0,
        currency = "USD",
        tiers = emptyList(),
        tierId = null,
        accessibility = emptyList(),
        wheelchairSpaceType = null,
        quantity = null,
        capacity = null,
        minOccupancy = null,
        maxOccupancy = null,
    )

    private companion object {
        const val ORGANIZER_NOTE = "Pillar at the aisle end."
    }
}

private fun SemanticsNode.descriptions(): List<String> = buildList {
    config.getOrNull(SemanticsProperties.ContentDescription)?.let { addAll(it) }
    children.forEach { addAll(it.descriptions()) }
}

private fun <T> SemanticsNode.findValue(key: SemanticsPropertyKey<T>): T? {
    if (config.contains(key)) return config[key]
    children.forEach { child -> child.findValue(key)?.let { return it } }
    return null
}
