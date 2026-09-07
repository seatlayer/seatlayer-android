package io.seatlayer.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.seatlayer.android.BridgeErrorDetails
import io.seatlayer.android.SeatLayerException
import io.seatlayer.android.SeatLayerPickerErrorCodes
import io.seatlayer.android.SeatLayerPickerHold
import io.seatlayer.android.SeatLayerPickerPresentationState
import io.seatlayer.android.SeatLayerPickerThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The buyer-facing states: each is a designed statement rather than a screen
 * full of disabled controls, and each is withheld silently when its own
 * condition is false.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SeatLayerPickerStatesTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun soldOutMeansEverySeatedCategoryIsGoneAndThereIsNoStandingRoom() {
        assertTrue(
            seatLayerPickerIsSoldOut(
                pickerAccessScope(categories = listOf(fixtureCategory(available = 0))).state,
            ),
        )
        // A venue whose stalls are gone but whose floor is not is nearly sold
        // out, and saying otherwise sends a buyer away from tickets that exist.
        assertFalse(
            seatLayerPickerIsSoldOut(
                pickerAccessScope(
                    categories = listOf(fixtureCategory(available = 0)),
                    generalAdmissionAreas = listOf(fixtureStandingArea()),
                ).state,
            ),
        )
        assertFalse(
            seatLayerPickerIsSoldOut(
                pickerAccessScope(categories = listOf(fixtureCategory(available = 3))).state,
            ),
        )
        // Nothing seated at all is not a sell-out; it is a chart with no seats.
        assertFalse(seatLayerPickerIsSoldOut(pickerAccessScope().state))
        assertFalse(
            seatLayerPickerIsSoldOut(
                pickerAccessScope(
                    categories = listOf(fixtureCategory(available = 0, notForSale = true)),
                ).state,
            ),
        )
    }

    @Test
    public fun theSoldOutOverlaySaysItOnceAndOffersNoWaitlist() {
        show(pickerAccessScope(categories = listOf(fixtureCategory(available = 0)))) {
            SeatLayerPickerSoldOutOverlay()
        }

        composeRule.onNodeWithText("Sold out").assertExists()
        // The eyebrow is the brand where the account has one, the event name
        // otherwise, and the picker's own word where neither is reported.
        composeRule.onNodeWithText("SEATLAYER").assertExists()
    }

    @Test
    public fun theSalesClosedStatementIsDrawnOnlyWhenSalesAreClosed() {
        show(pickerAccessScope(salesClosed = true)) { SeatLayerPickerSalesClosedStatement() }
        composeRule.onNodeWithText("Sales are closed").assertExists()
    }

    @Test
    public fun everyAccessReasonHasExactlyOneActionAndNamesWhatHappened() {
        val strings = SeatLayerPickerStrings.localized("en")
        val paused = seatLayerPickerAccessTelling(strings, "paused")
        assertEquals(strings.accessPausedTitle, paused.title)
        // "Try again" is the paused screen's verb; the other three refresh.
        assertEquals(strings.retry, paused.action)
        assertEquals(strings.accessRefresh, seatLayerPickerAccessTelling(strings, "revoked").action)
        assertEquals(
            strings.accessExpiredTitle,
            seatLayerPickerAccessTelling(strings, "no_token").title,
        )
        assertEquals(
            strings.accessExpiredTitle,
            seatLayerPickerAccessTelling(strings, "provider_failed").title,
        )
        // An unknown reason still gets a telling and a way forward.
        val unknown = seatLayerPickerAccessTelling(strings, "something-new")
        assertEquals(strings.accessUnverifiedTitle, unknown.title)
        assertEquals(strings.accessRefresh, unknown.action)
    }

    @Test
    public fun theAccessPanelStandsOnlyWhileTheSessionIsUnanswered() {
        show(pickerAccessScope(accessStatus = "unavailable", accessReason = "revoked")) {
            SeatLayerPickerAccessPanel()
        }
        val strings = SeatLayerPickerStrings.localized("en")
        composeRule.onNodeWithText(strings.accessRevokedTitle).assertExists()
        composeRule.onNodeWithText(strings.accessRefresh).assertHasClickAction()
    }

    @Test
    public fun theAccessPanelIsAbsentWhileTheSessionIsFine() {
        show(pickerAccessScope()) { SeatLayerPickerAccessPanel() }
        val strings = SeatLayerPickerStrings.localized("en")
        composeRule.onAllNodesWithText(strings.accessRefresh).assertCountEquals(0)
    }

    @Test
    public fun theBookedOverlayIsTheSaleAndNotTheHandoff() {
        val strings = SeatLayerPickerStrings.localized("en")
        // A hold still on its way to checkout is a buyer who has not paid.
        show(
            pickerAccessScope(
                presentation = SeatLayerPickerPresentationState(
                    checkoutHandoff = fixtureHandoff(),
                ),
            ),
        ) { SeatLayerPickerBookedOverlay() }
        composeRule.onAllNodesWithText(strings.allSetTitle).assertCountEquals(0)
    }

    @Test
    public fun theBookedOverlayNamesTheTicketsAndHandsTheMapBack() {
        val strings = SeatLayerPickerStrings.localized("en")
        show(
            pickerAccessScope(
                presentation = SeatLayerPickerPresentationState(
                    bookedHandoff = fixtureHandoff(quantity = 2),
                ),
            ),
        ) { SeatLayerPickerBookedOverlay() }

        composeRule.onNodeWithText(strings.allSetTitle).assertExists()
        composeRule.onNodeWithText("102 · A · 12").assertExists()
        composeRule.onNodeWithText(strings.backToMap).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(strings.allSetTitle).assertCountEquals(0)
    }

    @Test
    public fun aHostWithItsOwnConfirmationScreenGetsNoOverlay() {
        val strings = SeatLayerPickerStrings.localized("en")
        show(
            pickerAccessScope(
                presentation = SeatLayerPickerPresentationState(
                    bookedHandoff = fixtureHandoff(),
                ),
                options = SeatLayerPickerOptions(showBookedOverlay = false),
            ),
        ) { SeatLayerPickerBookedOverlay() }

        composeRule.onAllNodesWithText(strings.allSetTitle).assertCountEquals(0)
    }

    @Test
    public fun theExtendPromptIsOffOnThePhoneAndOnInTheWideLayout() {
        assertFalse(SeatLayerPickerOptions().resolvedExtendHoldPrompt(wide = false))
        assertTrue(SeatLayerPickerOptions().resolvedExtendHoldPrompt(wide = true))
        assertTrue(
            SeatLayerPickerOptions(showExtendHoldPrompt = true)
                .resolvedExtendHoldPrompt(wide = false),
        )
        assertFalse(
            SeatLayerPickerOptions(showExtendHoldPrompt = false)
                .resolvedExtendHoldPrompt(wide = true),
        )
    }

    @Test
    public fun aHostOwnedHoldIsNeverOfferedMoreTime() {
        val strings = SeatLayerPickerStrings.localized("en")
        show(
            pickerAccessScope(
                hold = SeatLayerPickerHold(
                    active = true,
                    expiresAt = nowPlusSeconds(30),
                    owner = "host",
                ),
            ),
        ) { SeatLayerPickerExtendHoldPrompt() }

        composeRule.onAllNodesWithText(strings.addMinutes(SEATLAYER_HOLD_EXTEND_STEP_MINUTES))
            .assertCountEquals(0)
    }

    @Test
    public fun aHostOwnedHoldStillShowsTheBuyerTheClock() {
        val strings = SeatLayerPickerStrings.localized("en")
        // Whoever holds the seats, the buyer's time is running. Only the
        // OFFER of more time is withheld from a hold this picker cannot
        // mutate; the countdown itself is the buyer's, not the owner's.
        val pinned = 1_800_000_000_000L
        show(
            pickerAccessScope(
                hold = SeatLayerPickerHold(
                    active = true,
                    expiresAt = (pinned + 120_000L).toDouble(),
                    owner = "host",
                ),
            ),
        ) {
            CompositionLocalProvider(LocalSeatLayerPickerClock provides { pinned }) {
                SeatLayerPickerHoldCountdown()
            }
        }

        composeRule.onNodeWithText(strings.heldFor(seatLayerHoldClock(120))).assertIsDisplayed()
    }

    @Test
    public fun moreTimeIsOfferedOnlyInsideTheLastMinute() {
        val strings = SeatLayerPickerStrings.localized("en")
        show(
            pickerAccessScope(
                hold = SeatLayerPickerHold(true, nowPlusSeconds(300), "picker"),
            ),
        ) { SeatLayerPickerExtendHoldPrompt() }
        composeRule.onAllNodesWithText(strings.addMinutes(SEATLAYER_HOLD_EXTEND_STEP_MINUTES))
            .assertCountEquals(0)
    }

    @Test
    public fun oneOfferPerHoldWithThreeEndings() {
        val strings = SeatLayerPickerStrings.localized("en")
        val granted = seatLayerHoldExtendAnswer(strings, SeatLayerHoldExtendOutcome.Granted)
        assertTrue(granted.retires)
        assertEquals(strings.moreTimeAdded, granted.announcement)
        // A refusal is not a fault: a hold resumed from a host carries
        // extensions this picker never offered.
        val refused = seatLayerHoldExtendAnswer(strings, SeatLayerHoldExtendOutcome.Refused)
        assertTrue(refused.retires)
        assertNull(refused.announcement)
        // A transport failure decided nothing, so it alone is offered again.
        val failed = seatLayerHoldExtendAnswer(strings, SeatLayerHoldExtendOutcome.TransportFailure)
        assertFalse(failed.retires)
        assertEquals(strings.couldNotAddMoreTime, failed.announcement)
    }

    @Test
    public fun seatsAlreadyInCheckoutSayTheStateAndNeverTheRuntimesSentence() {
        val strings = SeatLayerPickerStrings.localized("en")
        show(
            pickerAccessScope(
                presentation = SeatLayerPickerPresentationState(
                    checkoutHandoff = fixtureHandoff(),
                    lastActionError = bridgeError(
                        SeatLayerPickerErrorCodes.HOLD_OWNED_BY_HOST,
                        "hold is owned by the host session",
                    ),
                ),
            ),
        ) { SeatLayerPickerActionError() }

        composeRule.onNodeWithText(strings.holdInCheckoutTitle).assertExists()
        composeRule.onNodeWithText(strings.holdInCheckoutBody).assertExists()
        composeRule.onNodeWithText(strings.releaseAndChangeSeats).assertHasClickAction()
        composeRule.onAllNodesWithText("hold is owned by the host session").assertCountEquals(0)
    }

    @Test
    public fun aPickerOwnedHoldWithNothingToGiveBackOffersOnlyDismiss() {
        val strings = SeatLayerPickerStrings.localized("en")
        show(
            pickerAccessScope(
                presentation = SeatLayerPickerPresentationState(
                    lastActionError = bridgeError(
                        SeatLayerPickerErrorCodes.HOLD_ALREADY_ACTIVE,
                        "a hold is already active",
                    ),
                ),
            ),
        ) { SeatLayerPickerActionError() }

        composeRule.onNodeWithText(strings.holdAlreadyHeldTitle).assertExists()
        composeRule.onNodeWithText(strings.holdAlreadyHeldBody).assertExists()
        composeRule.onAllNodesWithText(strings.releaseAndChangeSeats).assertCountEquals(0)
        composeRule.onAllNodesWithText("a hold is already active").assertCountEquals(0)
        composeRule.onNodeWithText(strings.dismiss).assertHasClickAction()
    }

    @Test
    public fun anOrdinaryFailureStillSaysWhatTheRuntimeSaid() {
        show(
            pickerAccessScope(
                presentation = SeatLayerPickerPresentationState(
                    lastActionError = SeatLayerException.Transport("The connection dropped."),
                ),
            ),
        ) { SeatLayerPickerActionError() }

        composeRule.onNodeWithText("The connection dropped.").assertExists()
    }

    @Test
    public fun theMapFailureSaysWhatDidNotLoadAndOffersOneRetry() {
        val strings = SeatLayerPickerStrings.localized("en")
        var retried = 0
        show(pickerAccessScope()) { SeatLayerPickerErrorView(onRetry = { retried += 1 }) }

        composeRule.onNodeWithText(strings.mapDidNotLoad).assertExists()
        composeRule.onNodeWithText(strings.checkConnection).assertExists()
        composeRule.onNodeWithText(strings.retry).performClick()
        composeRule.waitForIdle()
        assertEquals(1, retried)
    }

    @Test
    public fun theLoadingSurfaceSpeaksWithoutDrawingItsSentence() {
        val strings = SeatLayerPickerStrings.localized("en")
        show(pickerAccessScope()) { SeatLayerPickerLoadingView() }

        composeRule.onNodeWithContentDescription(strings.loading).assertExists()
        composeRule.onAllNodesWithText(strings.loading).assertCountEquals(0)
    }

    private fun nowPlusSeconds(seconds: Long): Double =
        (System.currentTimeMillis() + seconds * 1_000L).toDouble()

    private fun bridgeError(code: String, message: String): SeatLayerException.Bridge =
        SeatLayerException.Bridge(
            BridgeErrorDetails(
                code = code,
                message = message,
                retryable = false,
                conflicts = emptyList(),
                metadata = null,
            ),
        )

    private fun show(scope: SeatLayerPickerScope, content: @Composable () -> Unit) {
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = SeatLayerPickerThemeMode.Light,
                explicitTheme = scope.theme,
                branding = null,
            ) {
                CompositionLocalProvider(LocalSeatLayerPickerScope provides scope) {
                    Box(Modifier.fillMaxSize().background(scope.theme.background)) { content() }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
    }
}
