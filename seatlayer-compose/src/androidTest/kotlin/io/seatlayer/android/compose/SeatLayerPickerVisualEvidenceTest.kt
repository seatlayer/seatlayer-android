package io.seatlayer.android.compose

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.seatlayer.android.SeatLayerPickerThemeMode
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Full native-chrome evidence with only renderer-owned pixels substituted. */
@RunWith(AndroidJUnit4::class)
public class SeatLayerPickerVisualEvidenceTest {
    @get:Rule
    public val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    public fun overviewTestModeCompactLight() {
        render(PickerEvidenceScenario.Overview, "overview-test-light-compact-api35.png")
        composeRule.onNodeWithText("Fixture Arena").assertIsDisplayed()
        composeRule.onNodeWithText("TEST MODE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Fit to screen").assertIsDisplayed()
    }

    @Test
    public fun sectionFocusFloorsAndStepOut() {
        render(
            PickerEvidenceScenario.SectionFocus,
            "section-focus-floors-light-compact-api35.png",
        )
        composeRule.onNodeWithText("Lower").assertIsDisplayed()
        composeRule.onNodeWithText("Upper").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to venue").assertIsDisplayed()
    }

    @Test
    public fun visibleAdultChildTierConfirmation() {
        render(
            PickerEvidenceScenario.Confirmation,
            "confirmation-tiers-light-compact-api35.png",
        )
        composeRule.onNodeWithText("Adult").assertIsDisplayed()
        composeRule.onNodeWithText("Child").assertIsDisplayed()
        composeRule.onNodeWithText("Companion").assertIsDisplayed()
    }

    @Test
    public fun generalAdmissionQuantityAndTiers() {
        render(PickerEvidenceScenario.GeneralAdmission, "ga-quantity-light-compact-api35.png")
        composeRule.onNodeWithText("Standing floor").assertIsDisplayed()
        composeRule.onNodeWithText("Quantity").assertIsDisplayed()
    }

    @Test
    public fun tableQuantityDark() {
        render(
            PickerEvidenceScenario.Table,
            "table-quantity-dark-compact-api35.png",
            themeMode = SeatLayerPickerThemeMode.Dark,
        )
        composeRule.onNodeWithText("Terrace table 7").assertIsDisplayed()
        composeRule.onNodeWithText("Guests at this table").assertIsDisplayed()
    }

    @Test
    public fun expandedDenseCartWithHold() {
        render(PickerEvidenceScenario.Cart, "cart-hold-light-compact-api35.png")
        composeRule.onNodeWithText("12–14", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Hold seats & checkout").assertIsDisplayed()
    }

    @Test
    public fun holdLapseAndUndoNotices() {
        render(PickerEvidenceScenario.Recovery, "hold-lapse-undo-light-compact-api35.png")
        composeRule.onNodeWithText("Your hold expired.").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    public fun venue3DTargetAndNeighboursDark() {
        render(
            PickerEvidenceScenario.Venue3D,
            "venue-3d-target-dark-compact-api35.png",
            themeMode = SeatLayerPickerThemeMode.Dark,
        )
        composeRule.onNodeWithContentDescription("Previous seat").assertIsDisplayed()
        composeRule.onNodeWithText("View from here").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next seat").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Recentre on the stage").assertIsDisplayed()
    }

    @Test
    public fun panoramaRestoresTargetChromeDark() {
        render(
            PickerEvidenceScenario.Panorama,
            "panorama-target-dark-compact-api35.png",
            themeMode = SeatLayerPickerThemeMode.Dark,
        )
        composeRule.onNodeWithText("Section 102 · Row A · Seat 12").assertIsDisplayed()
        composeRule.onNodeWithText("Real view").assertIsDisplayed()
    }

    @Test
    public fun panoramaUnavailableIsActionable() {
        render(
            PickerEvidenceScenario.PanoramaUnavailable,
            "panorama-unavailable-light-compact-api35.png",
        )
        composeRule.onNodeWithText("Seat view is unavailable for this seat.").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").assertIsDisplayed()
    }

    @Test
    public fun loadingState() {
        render(PickerEvidenceScenario.Loading, "loading-light-compact-api35.png")
        composeRule.onNodeWithContentDescription("Loading seat map…").assertIsDisplayed()
    }

    @Test
    public fun failureRetryState() {
        render(PickerEvidenceScenario.Error, "error-retry-light-compact-api35.png")
        composeRule.onNodeWithText("Choose your seats").assertIsDisplayed()
        composeRule.onNodeWithText("The seat map didn’t load").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    public fun emptyInventoryState() {
        render(PickerEvidenceScenario.Empty, "empty-inventory-light-compact-api35.png")
        composeRule.onNodeWithText("Sold out").assertIsDisplayed()
    }

    @Test
    public fun salesClosedState() {
        render(PickerEvidenceScenario.SalesClosed, "sales-closed-light-compact-api35.png")
        composeRule.onNodeWithText("Sold out").assertIsDisplayed()
    }

    @Test
    public fun confirmationAtLargeFontScale() {
        render(
            PickerEvidenceScenario.Confirmation,
            "confirmation-large-font-light-api35.png",
            fontScale = 1.5f,
        )
        composeRule.onNodeWithText("Select").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    public fun rtlCompactChrome() {
        render(
            PickerEvidenceScenario.Overview,
            "overview-rtl-light-compact-api35.png",
            layoutDirection = LayoutDirection.Rtl,
        )
        composeRule.onNodeWithText("Fixture Arena").assertIsDisplayed()
    }

    @Test
    public fun narrowSplitScreenChrome() {
        render(
            PickerEvidenceScenario.Overview,
            "overview-split-320dp-light-api35.png",
            viewportWidthDp = 320,
        )
        composeRule.onNodeWithText("Fixture Arena").assertIsDisplayed()
    }

    @Test
    public fun compactLandscapeChrome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        render(
            PickerEvidenceScenario.Overview,
            "overview-compact-landscape-light-api35.png",
            options = SeatLayerPickerOptions(layout = SeatLayerPickerLayoutMode.Compact),
        )
        composeRule.onNodeWithText("Fixture Arena").assertIsDisplayed()
        composeRule.onNodeWithText("TEST MODE").assertIsDisplayed()
    }

    @Test
    public fun wideLandscapeChrome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        render(
            PickerEvidenceScenario.Cart,
            "cart-wide-landscape-light-api35.png",
            options = SeatLayerPickerOptions(layout = SeatLayerPickerLayoutMode.Wide),
        )
        composeRule.onNodeWithText("Section 102", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    private fun render(
        scenario: PickerEvidenceScenario,
        fileName: String,
        themeMode: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode.Light,
        options: SeatLayerPickerOptions = SeatLayerPickerOptions(),
        fontScale: Float = 1f,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        viewportWidthDp: Int? = null,
    ) {
        val scope = pickerEvidenceScope(scenario, themeMode, options)
        val builders = SeatLayerPickerBuilders(
            map = { _, _ -> PickerEvidenceRenderer(scenario) },
        )
        composeRule.setContent {
            val platformDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(platformDensity.density, fontScale),
                LocalLayoutDirection provides layoutDirection,
            ) {
                SeatLayerPickerThemeProvider(
                    mode = themeMode,
                    explicitTheme = scope.theme,
                    branding = scope.state.snapshot?.branding,
                ) {
                    CompositionLocalProvider(LocalSeatLayerPickerScope provides scope) {
                        Box(
                            Modifier.fillMaxSize().background(Color(0xFF252B38)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = if (viewportWidthDp == null) {
                                    Modifier.fillMaxSize()
                                } else {
                                    Modifier.width(viewportWidthDp.dp).fillMaxHeight()
                                },
                            ) {
                                with(scope) {
                                    SeatLayerReadyMadePicker(
                                        modifier = Modifier.fillMaxSize(),
                                        builders = builders,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            val transparent = android.graphics.Color.TRANSPARENT
            val lightBars = themeMode != SeatLayerPickerThemeMode.Dark
            composeRule.activity.enableEdgeToEdge(
                statusBarStyle = if (lightBars) {
                    SystemBarStyle.light(transparent, transparent)
                } else {
                    SystemBarStyle.dark(transparent)
                },
                navigationBarStyle = if (lightBars) {
                    SystemBarStyle.light(transparent, transparent)
                } else {
                    SystemBarStyle.dark(transparent)
                },
            )
            WindowCompat.getInsetsController(
                composeRule.activity.window,
                composeRule.activity.window.decorView,
            ).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
        saveScreenshot(fileName)
    }

    private fun saveScreenshot(fileName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val outputDirectory = instrumentation.targetContext.getExternalFilesDir("evidence")
            ?: error("No external evidence directory")
        val output = outputDirectory.resolve(fileName)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(400)
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            assertTrue(
                "Screenshot $fileName did not contain rendered application content",
                bitmap.hasRenderedApplicationContent(),
            )
            output.outputStream().use { stream ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
        assertTrue("Screenshot $fileName is empty", output.length() > 10_000)
    }

    private fun Bitmap.hasRenderedApplicationContent(): Boolean {
        val colours = hashSetOf<Int>()
        val xStep = (width / 32).coerceAtLeast(1)
        val yStep = (height / 48).coerceAtLeast(1)
        for (y in height / 8 until height * 7 / 8 step yStep) {
            for (x in width / 10 until width * 9 / 10 step xStep) {
                colours += getPixel(x, y)
                if (colours.size >= 12) return true
            }
        }
        return false
    }
}
