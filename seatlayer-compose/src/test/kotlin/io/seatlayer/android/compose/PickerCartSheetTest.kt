package io.seatlayer.android.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.seatlayer.android.BundleInfo
import io.seatlayer.android.SeatLayerPickerCartLine
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import io.seatlayer.android.SeatLayerPickerState
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerThemeMode
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** What the ticket panel does, drawn. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Suppress("LargeClass")
public class PickerCartSheetTest {
    @get:Rule
    public val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    public fun theCollapsedSheetIsTheFooterBlockAndNothingElse() {
        render(collapsed())
        // The foot: the summary and the one action.
        composeRule.onNodeWithText("3 tickets").assertIsDisplayed()
        composeRule.onNodeWithText("Continue to checkout").assertIsDisplayed()
        // The cards wait behind the handle: laid out so the sheet knows how
        // tall it would open to, and clipped away until the buyer opens it.
        composeRule.onAllNodesWithText("Section 102")[0].assertIsNotDisplayed()
    }

    @Test
    public fun thereIsNoPriceTheBuyerCannotActOn() {
        render(collapsed())
        composeRule.onAllNodesWithText("From", substring = true).assertCountEquals(0)
    }

    @Test
    public fun theCollapsedSummaryNamesTheSeatsAndOpensTheCards() {
        render(collapsed())
        composeRule.onNodeWithText("Section 102 · Row A · Seat 12", substring = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Section 102")[0].assertIsDisplayed()
    }

    @Test
    public fun anEmptyCartSaysSoAndOffersTheFinder() {
        render(scope(empty = true))
        composeRule.onNodeWithText("No seats selected").assertIsDisplayed()
        composeRule.onNodeWithText("Find best seats").assertIsEnabled().assertHasClickAction()
    }

    @Test
    public fun theEmptyCartsButtonOpensTheFinder() {
        render(scope(empty = true))
        composeRule.onNodeWithText("Find best seats").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Find seats together").assertIsDisplayed()
    }

    @Test
    public fun theFinderDoorIsWithheldWhereTheFormWouldBeRefused() {
        render(scope(empty = true, options = SeatLayerPickerOptions(readOnly = true)))
        composeRule.onNodeWithText("Select seats").assertIsNotEnabled()
        composeRule.onAllNodesWithText("Find best seats").assertCountEquals(0)
    }

    @Test
    public fun aSeatOnItsCardIsNotListedAndTheButtonIsHeldDown() {
        val scope = selectedCartScope()
        val candidate = requireNotNull(scope.state.snapshot).selection.first()
        mutate(scope) { state ->
            state.copy(
                presentation = state.presentation.copy(
                    cartExpanded = true,
                    candidateSeat = candidate,
                ),
            )
        }
        render(scope)
        // Two of the three lines survive: the seat under the card is not a
        // ticket the buyer has taken.
        composeRule.onNodeWithText("2 tickets").assertIsDisplayed()
        composeRule.onNodeWithText("Continue to checkout").assertIsNotEnabled()
    }

    @Test
    public fun aCardTapFramesItsSeatAndLeavesTheSheetOpen() {
        val scope = selectedCartScope()
        withCommands(scope, "picker.frameSeat")
        val commands = recordCommands(scope)
        render(scope)
        composeRule.onAllNodesWithText("Section 102")[0].performClick()
        composeRule.waitForIdle()
        assertTrue("the map is asked to frame the seat", commands.contains("picker.frameSeat"))
        assertTrue("the sheet stays where the buyer put it", scope.state.presentation.cartExpanded)
        composeRule.onAllNodesWithText("Section 102")[0].assertIsDisplayed()
    }

    @Test
    public fun aCardTapIsSilentWhereTheRuntimeCannotFrameASeat() {
        val scope = selectedCartScope()
        withoutCommands(scope, "picker.frameSeat")
        val commands = recordCommands(scope)
        render(scope)
        composeRule.onAllNodesWithText("Section 102")[0].performClick()
        composeRule.waitForIdle()
        assertFalse(
            "an unadvertised command is a feature that is not offered",
            commands.contains("picker.frameSeat"),
        )
    }

    @Test
    public fun theCartStopsAtThreeCardsAndTheRestIsScrolledTo() {
        val scope = pickerEvidenceScope(
            PickerEvidenceScenario.Cart,
            SeatLayerPickerThemeMode.Light,
        )
        mutate(scope) { state ->
            val snapshot = state.snapshot ?: return@mutate state
            val lines = snapshot.cartLines + (15..17).map { number ->
                snapshot.cartLines.first().copy(
                    lineKey = "line-a$number",
                    label = "A-$number",
                    displayLabel = "Section 102 · Row A · Seat $number",
                    objectId = "seat-A-$number",
                    seatId = "seat-A-$number",
                    seatNumber = number.toString(),
                )
            }
            state.copy(
                snapshot = snapshot.copy(
                    cartLines = lines,
                    ticketCount = lines.sumOf(SeatLayerPickerCartLine::quantity),
                    cartTotal = lines.sumOf(SeatLayerPickerCartLine::total),
                ),
            )
        }
        render(scope)
        composeRule.onNodeWithText("6 tickets").assertIsDisplayed()
        composeRule.onNodeWithText("A · 17", substring = true).assertIsNotDisplayed()
        composeRule.onNodeWithText("A · 17", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    public fun theByLineIsAbsentEntirelyOnAWhiteLabelledAccount() {
        val branded = pickerEvidenceScope(
            PickerEvidenceScenario.Cart,
            SeatLayerPickerThemeMode.Light,
        )
        render(branded)
        composeRule.onNodeWithText("Powered by SeatLayer").assertIsDisplayed()
    }

    @Test
    public fun whiteLabelledAccountsSayNothing() {
        val scope = pickerEvidenceScope(
            PickerEvidenceScenario.Cart,
            SeatLayerPickerThemeMode.Light,
        )
        mutate(scope) { state ->
            val snapshot = state.snapshot ?: return@mutate state
            state.copy(
                snapshot = snapshot.copy(
                    branding = snapshot.branding.copy(attributionRequired = false),
                ),
            )
        }
        render(scope)
        composeRule.onAllNodesWithText("Powered by SeatLayer").assertCountEquals(0)
    }

    @Test
    public fun theRemovalCanBeTakenBackFromAFullSizeAction() {
        val scope = pickerEvidenceScope(
            PickerEvidenceScenario.Recovery,
            SeatLayerPickerThemeMode.Light,
        )
        withCommands(scope, "picker.selectObjects")
        val commands = recordCommands(scope)
        render(scope)
        composeRule.onNodeWithContentDescription("Undo")
            .assertHasClickAction()
            .assertWidthIsAtLeast(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
            .assertHeightIsAtLeast(SeatLayerSizeTokens.MINIMUM_HIT_TARGET.dp)
            .performClick()
        composeRule.waitForIdle()
        assertTrue(commands.contains("picker.selectObjects"))
    }

    @Test
    public fun theHandleIsTheOneNamedToggle() {
        render(collapsed())
        composeRule.onNodeWithContentDescription("Open ticket panel")
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Collapse ticket panel").assertIsDisplayed()
    }

    @Test
    public fun theHandleLeavesWhileASeatCardIsAsking() {
        val scope = pickerEvidenceScope(
            PickerEvidenceScenario.Confirmation,
            SeatLayerPickerThemeMode.Light,
        )
        render(scope)
        composeRule.onAllNodesWithText("Open ticket panel").assertCountEquals(0)
        composeRule.onAllNodesWithText("Collapse ticket panel").assertCountEquals(0)
    }

    // ---- fixtures -------------------------------------------------------

    /** A cart whose lines are joined to seats the runtime says are selected. */
    private fun selectedCartScope(): SeatLayerPickerScope {
        val scope = pickerEvidenceScope(
            PickerEvidenceScenario.Cart,
            SeatLayerPickerThemeMode.Light,
        )
        mutate(scope) { state ->
            val snapshot = state.snapshot ?: return@mutate state
            val template = snapshot.selection.firstOrNull()
                ?: SeatLayerPickerSelectedSeat(
                    id = "",
                    label = "",
                    displayLabel = null,
                    displayType = null,
                    objectId = null,
                    objectType = "seat",
                    bookingMode = null,
                    sectionLabel = "Section 102",
                    rowLabel = "A",
                    seatNumber = null,
                    categoryKey = "standard",
                    price = 78.0,
                    currency = "USD",
                    tiers = emptyList(),
                    tierId = null,
                    accessibility = emptyList(),
                    wheelchairSpaceType = null,
                    quantity = 1,
                    capacity = null,
                    minOccupancy = null,
                    maxOccupancy = null,
                )
            state.copy(
                snapshot = snapshot.copy(
                    selection = snapshot.cartLines.map { line ->
                        template.copy(
                            id = line.seatId ?: line.objectId,
                            label = line.label,
                            objectId = line.objectId,
                            seatNumber = line.seatNumber,
                        )
                    },
                ),
            )
        }
        return scope
    }

    private fun collapsed(): SeatLayerPickerScope {
        val scope = pickerEvidenceScope(
            PickerEvidenceScenario.Cart,
            SeatLayerPickerThemeMode.Light,
        )
        mutate(scope) { state ->
            state.copy(presentation = state.presentation.copy(cartExpanded = false))
        }
        return scope
    }

    private fun scope(
        empty: Boolean = false,
        options: SeatLayerPickerOptions = SeatLayerPickerOptions(),
    ): SeatLayerPickerScope {
        val scope = pickerEvidenceScope(
            PickerEvidenceScenario.Cart,
            SeatLayerPickerThemeMode.Light,
            options,
        )
        withCommands(scope, "picker.bestAvailable")
        if (empty) {
            mutate(scope) { state ->
                val snapshot = state.snapshot ?: return@mutate state
                state.copy(
                    snapshot = snapshot.copy(
                        cartLines = emptyList(),
                        ticketCount = 0,
                        cartTotal = 0.0,
                        hold = snapshot.hold.copy(active = false, expiresAt = null, owner = null),
                    ),
                    presentation = state.presentation.copy(cartExpanded = false),
                )
            }
        }
        return scope
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
                                builders = SeatLayerPickerBuilders(
                                    map = { _, _ -> },
                                ),
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mutate(
        scope: SeatLayerPickerScope,
        block: (SeatLayerPickerState) -> SeatLayerPickerState,
    ) {
        val field = SeatLayerPickerStateHolder::class.java.getDeclaredField("mutableState")
        field.isAccessible = true
        val flow = field.get(scope.stateHolder) as MutableStateFlow<SeatLayerPickerState>
        flow.value = block(flow.value)
    }

    /** Adds commands to the hello table this fixture's runtime advertises. */
    private fun withCommands(scope: SeatLayerPickerScope, vararg commands: String) {
        val controller = scope.controller
        val field = controller.javaClass.getDeclaredField("bundleInfo")
        field.isAccessible = true
        val current = field.get(controller) as BundleInfo
        field.set(controller, current.copy(commands = current.commands + commands.toList()))
    }

    /** Takes commands back out of the hello table a runtime advertised. */
    private fun withoutCommands(scope: SeatLayerPickerScope, vararg commands: String) {
        val controller = scope.controller
        val field = controller.javaClass.getDeclaredField("bundleInfo")
        field.isAccessible = true
        val current = field.get(controller) as BundleInfo
        field.set(controller, current.copy(commands = current.commands - commands.toSet()))
    }

    /** Swaps the transport for one that writes down what it was asked. */
    private fun recordCommands(scope: SeatLayerPickerScope): MutableList<String> {
        val recorded = mutableListOf<String>()
        val transportType = Class.forName("io.seatlayer.android.SeatLayerPickerCommandTransport")
        val transport = Proxy.newProxyInstance(
            transportType.classLoader,
            arrayOf(transportType),
        ) { proxy, method, args ->
            when (method.name) {
                "command" -> {
                    recorded += args?.get(0) as? String ?: ""
                    JsonObject(emptyMap())
                }
                "toString" -> "RecordingTransport"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> false
                else -> null
            }
        }
        val field = scope.controller.javaClass.getDeclaredField("transport")
        field.isAccessible = true
        field.set(scope.controller, transport)
        return recorded
    }
}
