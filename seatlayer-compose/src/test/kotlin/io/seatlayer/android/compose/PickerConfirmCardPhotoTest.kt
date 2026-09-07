package io.seatlayer.android.compose

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.seatlayer.android.BuyerAccessToken
import io.seatlayer.android.SeatLayerPickerCardIntent
import io.seatlayer.android.SeatLayerPickerPresentationState
import io.seatlayer.android.SeatLayerPickerSeatConfidence
import io.seatlayer.android.SeatLayerPickerSeatViewThumbnail
import io.seatlayer.android.SeatLayerPickerSelectedSeat
import io.seatlayer.android.SeatLayerPickerThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * The authored photograph, the sight line printed on it, and the honest line
 * about a view that was modelled rather than photographed.
 *
 * All three are present-only and all three are gated on the runtime saying it
 * reports them: an unadvertised capability is a feature NOT OFFERED, never an
 * empty frame and never an error.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PickerConfirmCardPhotoTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var strings: SeatLayerPickerStrings

    @Test
    fun thePhotographCarriesTheWayInAndTheMeasuredLine() {
        card(seat = seat(thumbnail = THUMB, sightlineMetres = 22.0), photograph = true)

        composeRule.onNodeWithContentDescription(strings.viewFromHere).assertIsDisplayed()
        composeRule.onNodeWithText(strings.sightline("22")).assertIsDisplayed()
    }

    @Test
    fun theFigureIsPrintedTheWayTheRuntimeRoundedIt() {
        assertEquals("22", seatLayerSightlineFigure(22.0))
        assertEquals("7.4", seatLayerSightlineFigure(7.4))
        assertEquals("0", seatLayerSightlineFigure(0.0))
    }

    @Test
    fun noPhotographMeansNoStripAndNoLineRatherThanAPlaceholder() {
        // A runtime that names no photograph on this seat. The measured line
        // rides the strip, so it leaves with it: a lone measured line is not
        // worth a row of its own on a phone.
        card(seat = seat(thumbnail = null, sightlineMetres = 22.0), photograph = true)

        composeRule.onNodeWithText(strings.sightline("22")).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(strings.viewFromHere).assertDoesNotExist()
        // The 3D way in keeps its full target: it moves into the decision row.
        composeRule.onNodeWithContentDescription(strings.seeItIn3D).assertIsDisplayed()
    }

    @Test
    fun aRuntimeThatDoesNotReportThemIsACardWithoutThem() {
        // The fields are not read at all without the capability, even when the
        // seat carries them.
        card(
            seat = seat(
                thumbnail = THUMB,
                sightlineMetres = 22.0,
                confidence = CONFIDENCE,
            ),
            photograph = false,
        )

        composeRule.onNodeWithText(strings.sightline("22")).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(strings.viewFromHere).assertDoesNotExist()
    }

    @Test
    fun theDisclosureIsAStaticRowWhereTheHostCanOpenNothing() {
        card(
            seat = seat(confidence = CONFIDENCE),
            photograph = true,
            immersive = true,
        )

        // The headline and the detail ARE the information; the word beside them
        // is a badge, not a control, because there is nothing to open.
        composeRule.onNodeWithText(CONFIDENCE.headline).assertIsDisplayed()
        composeRule.onNodeWithText(CONFIDENCE.reality).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(strings.passport).assertDoesNotExist()
    }

    @Test
    fun theDisclosureIsAChipWhereTheHostTookThePassportCallback() {
        card(
            seat = seat(confidence = CONFIDENCE),
            photograph = true,
            immersive = true,
            callbacks = SeatLayerPickerCallbacks(onSeatConfidence = { _ -> }),
        )

        composeRule.onNodeWithContentDescription(strings.passport).assertIsDisplayed()
        // The teaser's prose is not repeated beside its own chip.
        composeRule.onNodeWithText(CONFIDENCE.headline).assertDoesNotExist()
    }

    @Test
    fun halfADisclosureIsNoDisclosure() {
        // The decoder is the gate — every prose field or nothing — so the card
        // is only ever handed a whole one, and a seat with none draws none.
        card(seat = seat(confidence = null), photograph = true, immersive = true)

        composeRule.onNodeWithText(CONFIDENCE.headline).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(strings.passport).assertDoesNotExist()
    }

    private fun card(
        seat: SeatLayerPickerSelectedSeat,
        photograph: Boolean,
        immersive: Boolean = false,
        callbacks: SeatLayerPickerCallbacks? = null,
    ) {
        val scope = pickerEvidenceScope(
            scenario = PickerEvidenceScenario.Confirmation,
            themeMode = SeatLayerPickerThemeMode.Light,
            callbacks = callbacks,
            extraCapabilities = if (photograph) listOf(THUMBNAIL_CAPABILITY) else emptyList(),
            presentationOverride = SeatLayerPickerPresentationState(candidateSeat = seat),
        )
        strings = scope.strings
        val assets = SeatLayerBuyerAssetLoader(
            eventKey = scope.stateHolder.configuration.event,
            apiBase = "https://api.example.test",
            token = BuyerAccessToken(token = "test-bearer"),
            fetch = { _, _ -> onePixelPng() },
        )
        composeRule.setContent {
            SeatLayerPickerThemeProvider(
                mode = SeatLayerPickerThemeMode.Light,
                explicitTheme = scope.theme,
                branding = null,
            ) {
                CompositionLocalProvider(
                    LocalSeatLayerPickerScope provides scope,
                    LocalSeatLayerBuyerAssets provides assets,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        SeatLayerConfirmCardBody(
                            seat = seat,
                            intent = SeatLayerPickerCardIntent.Add,
                            answered = false,
                            onAnswered = {},
                            immersive = immersive,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun seat(
        thumbnail: SeatLayerPickerSeatViewThumbnail? = null,
        sightlineMetres: Double? = null,
        confidence: SeatLayerPickerSeatConfidence? = null,
    ) = SeatLayerPickerSelectedSeat(
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
        seatViewThumbnail = thumbnail,
        sightlineMetres = sightlineMetres,
        seatViewConfidence = confidence,
    )

    private fun onePixelPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val sink = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, sink)
        return sink.toByteArray()
    }

    private companion object {
        const val THUMBNAIL_CAPABILITY = "seat-view-thumbnail-v1"
        val THUMB = SeatLayerPickerSeatViewThumbnail(
            reference = "/pub/events/ev_android_visual_fixture/assets/seat-a12.jpg",
        )
        val CONFIDENCE = SeatLayerPickerSeatConfidence(
            headline = "Modelled from the venue plan",
            model = "Photogrammetry",
            reality = "Rebuilt from surveyed rows",
            coverage = "Every tier",
            provenance = "Venue-supplied drawings",
            freshness = "Checked this season",
        )
    }
}
