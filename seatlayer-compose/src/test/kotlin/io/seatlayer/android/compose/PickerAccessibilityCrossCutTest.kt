package io.seatlayer.android.compose

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.seatlayer.android.SeatLayerPickerPresentationState
import io.seatlayer.android.SeatLayerPickerRemovalPhase
import io.seatlayer.android.SeatLayerPickerRemovalUndo
import io.seatlayer.android.SeatLayerPickerState
import io.seatlayer.android.SeatLayerPickerStateHolder
import io.seatlayer.android.SeatLayerPickerThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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

    @Test
    public fun theFootTotalSaysItselfWhenItChanges() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.Confirmation, LIGHT)
        render(scope)
        assertTrue(
            "the foot's total line is one of the surfaces that speak unasked: " +
                liveRegionSentences(),
            liveRegionSentences().any { it.contains("USD") },
        )
    }

    @Test
    public fun theSectionDockSaysWhereTheBuyerIsAndWhatIsLeft() {
        val scope = pickerEvidenceScope(
            PickerEvidenceScenario.SectionFocus,
            LIGHT,
            SeatLayerPickerOptions(showDockBar = true),
        )
        render(scope)
        assertTrue(
            "the dock re-writes itself as the camera moves: " + liveRegionSentences(),
            liveRegionSentences().any { it.startsWith("Section 102") },
        )
    }

    @Test
    public fun theHoldCountdownIsLive() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.Cart, LIGHT)
        render(scope)
        assertTrue(
            "the hold pill speaks the time left: " + liveRegionSentences(),
            liveRegionSentences().any { it.contains("8") },
        )
    }

    @Test
    public fun anArrivingBuyerStateSaysSoWithoutBeingAsked() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.Error, LIGHT)
        render(scope)
        assertTrue(
            "a state that arrives unasked says so: " + liveRegionSentences(),
            liveRegionSentences().isNotEmpty(),
        )
    }

    @Test
    public fun exactlyOneTransientRegionCarriesThePickersSpeech() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.Confirmation, LIGHT)
        withRemovalToast(scope)
        render(scope)
        val said = liveRegionSentences()
        val transient = said.filter { it.contains(scope.strings.seatRemoved) }
        assertTrue("the toast band owns transient speech: $said", transient.size == 1)
    }

    @Test
    public fun theCountdownSpeaksOnTheMinuteAndThroughTheLastMinute() {
        val strings = SeatLayerPickerStrings.localized("en")
        for (second in 1..59) {
            assertTrue(
                "the last minute is owed every second, and $second was silent",
                seatLayerHoldAnnouncement(second.toLong(), strings) != null,
            )
        }
        for (minute in 1..14) {
            assertTrue(
                "the $minute minute mark must be said",
                seatLayerHoldAnnouncement(minute * 60L, strings) != null,
            )
        }
        val quiet = (61L..899L).filter { it % 60L != 0L }
        assertTrue(
            "a live region fed a running clock speaks once a second for a " +
                "quarter of an hour",
            quiet.all { seatLayerHoldAnnouncement(it, strings) == null },
        )
        assertFalse(
            "an expired hold has no time left to read",
            seatLayerHoldAnnouncement(0L, strings) != null,
        )
    }

    @Test
    public fun theMapIsOneNamedRegionAndNotAListOfSeats() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.Overview, LIGHT)
        render(scope)
        val map = described(scope.strings.venueMap("North Hall"))
        assertEquals(
            "the region says how the seats ARE picked, since it cannot be explored",
            scope.strings.venueMapHint,
            map.config.getOrElseNullable(SemanticsProperties.StateDescription) { null },
        )
        assertTrue(
            "no per-seat node is invented under a canvas the runtime does not " +
                "describe",
            map.children.none(::speaks),
        )
    }

    @Test
    public fun aSurfaceGrowsWithTheTextSettingOnlyAsFarAsItsOwnCeiling() {
        var rail = 0f
        var sheet = 0f
        var grown = 0.dp
        var atDefault = 0.dp
        var smaller = 0.dp
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                rail = pickerClampedFontScale(SeatLayerTypeTokens.SCALE_CLAMP_RAIL)
                sheet = pickerClampedFontScale(SeatLayerTypeTokens.SCALE_CLAMP_SHEET)
                grown = pickerScaledExtent(BAND, SeatLayerTypeTokens.SCALE_CLAMP_RAIL)
            }
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                atDefault = pickerScaledExtent(BAND, SeatLayerTypeTokens.SCALE_CLAMP_RAIL)
            }
            // A buyer who made their text SMALLER is not asking for a shorter
            // band: the 44-point controls in it still need the room.
            CompositionLocalProvider(LocalDensity provides Density(1f, 0.85f)) {
                smaller = pickerScaledExtent(BAND, SeatLayerTypeTokens.SCALE_CLAMP_RAIL)
            }
        }
        composeRule.waitForIdle()
        assertEquals(SeatLayerTypeTokens.SCALE_CLAMP_RAIL, rail, 0f)
        assertEquals(SeatLayerTypeTokens.SCALE_CLAMP_SHEET, sheet, 0f)
        assertEquals((BAND * SeatLayerTypeTokens.SCALE_CLAMP_RAIL).dp, grown)
        assertEquals(BAND.dp, atDefault)
        assertEquals(BAND.dp, smaller)
    }

    @Test
    public fun theRailBandIsUnmovedAtTheDefaultAndClampedAboveIt() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.Overview, LIGHT)
        render(scope)
        val plain = described(scope.strings.allPrices).size.height
        fontScale.floatValue = 2f
        composeRule.waitForIdle()
        val scaled = described(scope.strings.allPrices).size.height
        assertEquals(
            "the rail stops at its own ceiling rather than clipping a price",
            SeatLayerTypeTokens.SCALE_CLAMP_RAIL.toDouble(),
            scaled.toDouble() / plain.toDouble(),
            TOLERANCE,
        )
    }

    @Test
    public fun theMapTakesTheScreenBackWhenTheCardIsAnswered() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.Confirmation, LIGHT)
        render(scope)
        answerTheCard(scope)
        assertEquals(
            "an accepted card gives the screen back to the map",
            true,
            described(scope.strings.venueMap("North Hall"))
                .config
                .getOrElseNullable(SemanticsProperties.Focused) { null },
        )
    }

    @Test
    public fun theMapTakesTheScreenBackWhenAPromptIsDismissed() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.GeneralAdmission, LIGHT)
        render(scope)
        // Dismissed is the same handover as accepted: the buyer is left where
        // they were rather than at the top of the tree.
        dismissThePrompt(scope)
        assertEquals(
            true,
            described(scope.strings.venueMap("North Hall"))
                .config
                .getOrElseNullable(SemanticsProperties.Focused) { null },
        )
    }

    @Test
    public fun boldTextIsThePlatformsToApplyAndTheSdkDoesNotApplyItTwice() {
        // Compose carries the configuration's `fontWeightAdjustment` into the
        // font resolver it installs above every picker, so a picker that also
        // stepped its own weights would apply the buyer's setting twice. Here
        // the setting is turned on around the same asked-for weight, and the
        // face that comes back is a heavier one.
        var plain: Typeface? = null
        var bold: Typeface? = null
        composeRule.setContent {
            val context = LocalContext.current
            val asking = FontWeight(SeatLayerTypeTokens.HEADER_TITLE_WEIGHT)
            plain = resolvedFace(context, asking)
            bold = resolvedFace(context.withBoldText(), asking)
        }
        composeRule.waitForIdle()
        assertTrue(
            "the platform's own setting has to reach the picker's type",
            plain?.weight != bold?.weight,
        )
        assertEquals(
            "and no further than the heaviest face there is",
            BOLD_TEXT_CEILING,
            bold?.weight,
        )
    }

    @Composable
    private fun resolvedFace(context: Context, weight: FontWeight): Typeface =
        createFontFamilyResolver(context)
            .resolve(FontFamily.Default, weight)
            .value as Typeface

    /** The same device, with the platform's `bold text` setting turned on. */
    private fun Context.withBoldText(): Context = createConfigurationContext(
        Configuration(resources.configuration).apply {
            fontWeightAdjustment = BOLD_TEXT_ADJUSTMENT
        },
    )

    /** Dismisses the standing prompt, the way its own way out does. */
    private fun dismissThePrompt(scope: SeatLayerPickerScope) {
        mutatePresentation(scope) { it.copy(activePrompt = null) }
    }

    /** Answers the standing card, the way a press on either button does. */
    private fun answerTheCard(scope: SeatLayerPickerScope) {
        mutatePresentation(scope) { it.copy(candidateSeat = null) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mutatePresentation(
        scope: SeatLayerPickerScope,
        block: (SeatLayerPickerPresentationState) -> SeatLayerPickerPresentationState,
    ) {
        val field = SeatLayerPickerStateHolder::class.java.getDeclaredField("mutableState")
        field.isAccessible = true
        val flow = field.get(scope.stateHolder) as MutableStateFlow<SeatLayerPickerState>
        flow.value = flow.value.let { state ->
            state.copy(presentation = block(state.presentation))
        }
        liveState.value = flow.value
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
    }

    @Test
    public fun everyControlOnTheMapHasAName() {
        sweepForNames(PickerEvidenceScenario.SectionFocus)
    }

    @Test
    public fun everyControlOnTheCardHasAName() {
        sweepForNames(PickerEvidenceScenario.Confirmation)
    }

    @Test
    public fun everyControlInTheOpenSheetHasAName() {
        sweepForNames(PickerEvidenceScenario.Cart)
    }

    @Test
    public fun everyControlInTheSceneHasAName() {
        sweepForNames(PickerEvidenceScenario.Venue3D)
    }

    private fun sweepForNames(scenario: PickerEvidenceScenario) {
        render(
            pickerEvidenceScope(
                scenario,
                LIGHT,
                SeatLayerPickerOptions(showDockBar = true),
            ),
        )
        val seen = mutableListOf<String>()
        val unnamed = unnamedControls(scenario.name, seen)
        assertTrue(
            "a sweep that walked no controls proves nothing",
            seen.size >= CONTROLS_PER_SCREEN,
        )
        assertTrue(
            "a control with no name is a control nobody can choose: " +
                unnamed.joinToString(),
            unnamed.isEmpty(),
        )
    }

    @Test
    public fun theEmptyTraySaysHowASeatGetsIntoIt() {
        val scope = pickerEvidenceScope(PickerEvidenceScenario.Overview, LIGHT)
        render(scope)
        described(scope.strings.emptyTrayHint)
    }

    /** Every pressable node in the rendered picker with nothing to call it. */
    private fun unnamedControls(where: String, seen: MutableList<String>): List<String> {
        val bare = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            val config = node.config
            val pressable = config.contains(SemanticsActions.OnClick) ||
                config.contains(SemanticsProperties.Role)
            if (pressable) seen += "#${node.id}"
            if (pressable && !named(node)) {
                bare += "$where #${node.id} at ${node.positionInRoot}"
            }
            node.children.forEach(::walk)
        }
        walk(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return bare
    }

    /** What every live region in the rendered picker currently says. */
    private fun liveRegionSentences(): List<String> {
        val said = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.LiveRegion)) {
                val own = node.config
                    .getOrElseNullable(SemanticsProperties.ContentDescription) { null }
                    ?.joinToString(" ")
                said += own ?: spokenText(node)
            }
            node.children.forEach(::walk)
        }
        walk(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return said
    }

    /** Everything written inside [node], in tree order. */
    private fun spokenText(node: SemanticsNode): String {
        val parts = mutableListOf<String>()
        fun walk(current: SemanticsNode) {
            current.config.getOrElseNullable(SemanticsProperties.Text) { null }
                ?.forEach { parts += it.text }
            current.children.forEach(::walk)
        }
        walk(node)
        return parts.joinToString(" ")
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

    /** Whether this subtree has a NAME — a role alone is not one. */
    private fun named(node: SemanticsNode): Boolean {
        val config = node.config
        val own = config.contains(SemanticsProperties.ContentDescription) ||
            config.contains(SemanticsProperties.Text)
        return own || node.children.any(::named)
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

    /** The platform text-size setting, as a test may move it. */
    private val fontScale = mutableFloatStateOf(1f)

    /**
     * The picker's state, as the composition can actually watch it.
     *
     * The fixture scope reads its holder outside the snapshot system, so a
     * change made mid-test would never reach a skippable composable.
     */
    private val liveState = mutableStateOf<SeatLayerPickerState?>(null)

    /** [scope], with a state the composition subscribes to. */
    private fun watchable(scope: SeatLayerPickerScope): SeatLayerPickerScope =
        object : SeatLayerPickerScope by scope {
            override val state: SeatLayerPickerState
                get() = liveState.value ?: scope.state
        }

    private fun render(scope: SeatLayerPickerScope) {
        val watched = watchable(scope)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    LocalDensity.current.density,
                    fontScale.floatValue,
                ),
                // Pinned, so the hold pill reads the same eight minutes
                // however long the composition took.
                LocalSeatLayerPickerClock provides { SEATLAYER_EVIDENCE_NOW_MILLIS.toLong() },
            ) {
                SeatLayerPickerThemeProvider(
                    mode = scope.themeMode,
                    explicitTheme = scope.theme,
                    branding = scope.state.snapshot?.branding,
                ) {
                    CompositionLocalProvider(LocalSeatLayerPickerScope provides watched) {
                        Box(Modifier.fillMaxSize().background(scope.theme.background)) {
                            with(watched) {
                                SeatLayerReadyMadePicker(
                                    modifier = Modifier.fillMaxSize(),
                                    builders = SeatLayerPickerBuilders(map = { _, _ -> }),
                                )
                            }
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

        /** The compact rail band: the minimum hit target, at type 1.0. */
        const val BAND = 44f

        /** One device pixel of rounding, as a ratio of a 44-point band. */
        const val TOLERANCE = 0.03

        /** What Android's own `bold text` setting adds to every weight. */
        const val BOLD_TEXT_ADJUSTMENT = 300

        /** Below this a name sweep has not walked a real screen. */
        const val CONTROLS_PER_SCREEN = 4

        /** No weight goes past the heaviest face the platform will make. */
        const val BOLD_TEXT_CEILING = 1000

    }
}
