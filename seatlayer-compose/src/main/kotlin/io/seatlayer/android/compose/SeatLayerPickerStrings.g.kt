// GENERATED — do not edit.
//
// Source: design/tokens.json, design/locale_strings.json, design/components.md
// Regenerate: node tool/generate-picker-locales.mjs
//
// tokens.json         SHA-256: defb411c1dc94b1071a70bc92dfa7eff0c7ab443fc110dd86c1cfbd8f54182f4
// locale_strings.json SHA-256: 340645082f4280ce445375fc1675601b78665cb8b0fadb3622bdc8d69561215f
// components.md       SHA-256: 4b3b7b83e5502633cbbbbcfdb9e1be4cd07e86dc18e22d651dde3711b98b494d

package io.seatlayer.android.compose

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * Every buyer-facing string the native picker chrome draws.
 *
 * The map itself is localized by the runtime; this covers the native chrome
 * around it. Every entry has an English default, so a host that overrides
 * nothing still gets a complete interface, and [localized] starts from the
 * runtime's own wording so the drawn map and the chrome agree.
 */
@Immutable
@Suppress("LargeClass")
public data class SeatLayerPickerStrings(
    val languageTag: String = "en",
    val aboutBestSeats: String = SeatLayerStringTokens.ABOUT_BEST_SEATS,
    val accessExpiredCopy: String = SeatLayerStringTokens.ACCESS_EXPIRED_COPY,
    val accessExpiredTitle: String = SeatLayerStringTokens.ACCESS_EXPIRED_TITLE,
    val accessFreeCount: String = SeatLayerStringTokens.ACCESS_FREE_COUNT,
    val accessJumpFirstSection: String = SeatLayerStringTokens.ACCESS_JUMP_FIRST_SECTION,
    val accessJumpNextSection: String = SeatLayerStringTokens.ACCESS_JUMP_NEXT_SECTION,
    val accessNeedWithCount: String = SeatLayerStringTokens.ACCESS_NEED_WITH_COUNT,
    val accessPausedCopy: String = SeatLayerStringTokens.ACCESS_PAUSED_COPY,
    val accessPausedTitle: String = SeatLayerStringTokens.ACCESS_PAUSED_TITLE,
    val accessRefresh: String = SeatLayerStringTokens.ACCESS_REFRESH,
    val accessRevokedCopy: String = SeatLayerStringTokens.ACCESS_REVOKED_COPY,
    val accessRevokedTitle: String = SeatLayerStringTokens.ACCESS_REVOKED_TITLE,
    val accessUnverifiedCopy: String = SeatLayerStringTokens.ACCESS_UNVERIFIED_COPY,
    val accessUnverifiedTitle: String = SeatLayerStringTokens.ACCESS_UNVERIFIED_TITLE,
    val accessibility: String = SeatLayerStringTokens.ACCESSIBILITY,
    val accessibilityTitle: String = SeatLayerStringTokens.ACCESSIBILITY_TITLE,
    val accessiblePhysicalSeat: String = SeatLayerStringTokens.ACCESSIBLE_PHYSICAL_SEAT,
    val accessibleSections: String = SeatLayerStringTokens.ACCESSIBLE_SECTIONS,
    val accessibleStep: String = SeatLayerStringTokens.ACCESSIBLE_STEP,
    val actionFailed: String = "That action could not be completed.",
    val addMinutes: String = SeatLayerStringTokens.ADD_MINUTES,
    val addSeat: String = SeatLayerStringTokens.ADD_SEAT,
    val addTickets: String = SeatLayerStringTokens.ADD_TICKETS,
    val addTime: String = SeatLayerStringTokens.ADD_TIME,
    val added: String = SeatLayerStringTokens.ADDED,
    val addingEllipsis: String = SeatLayerStringTokens.ADDING_ELLIPSIS,
    val adjustSelection: String = SeatLayerStringTokens.ADJUST_SELECTION,
    val allFloors: String = SeatLayerStringTokens.ALL_FLOORS,
    val allPrices: String = SeatLayerStringTokens.ALL_PRICES,
    val allSetTitle: String = SeatLayerStringTokens.ALL_SET_TITLE,
    val anyTicketType: String = SeatLayerStringTokens.ANY_TICKET_TYPE,
    val anyVenueZone: String = SeatLayerStringTokens.ANY_VENUE_ZONE,
    val backToMap: String = SeatLayerStringTokens.BACK_TO_MAP,
    val backToVenue: String = SeatLayerStringTokens.BACK_TO_VENUE,
    val bestSeats: String = SeatLayerStringTokens.BEST_SEATS,
    val cancel: String = SeatLayerStringTokens.CANCEL,
    val checkConnection: String = SeatLayerStringTokens.CHECK_CONNECTION,
    val chooseGuestsCopy: String = SeatLayerStringTokens.CHOOSE_GUESTS_COPY,
    val chooseMinMaxGuests: String = SeatLayerStringTokens.CHOOSE_MIN_MAX_GUESTS,
    val chooseMore: String = SeatLayerStringTokens.CHOOSE_MORE,
    val chooseSeats: String = SeatLayerStringTokens.CHOOSE_SEATS,
    val chooseTableGuests: String = SeatLayerStringTokens.CHOOSE_TABLE_GUESTS,
    val chooseTickets: String = SeatLayerStringTokens.CHOOSE_TICKETS,
    val close: String = SeatLayerStringTokens.CLOSE,
    val closestGroupChosenInstantly: String = SeatLayerStringTokens.CLOSEST_GROUP_CHOSEN_INSTANTLY,
    val collapseCart: String = SeatLayerStringTokens.COLLAPSE_CART,
    val colorblindSafe: String = SeatLayerStringTokens.COLORBLIND_SAFE,
    val companionSeatsNote: String = SeatLayerStringTokens.COMPANION_SEATS_NOTE,
    val confirm: String = "Confirm",
    val confirmOrCancelSeat: String = SeatLayerStringTokens.CONFIRM_OR_CANCEL_SEAT,
    val confirmTable: String = SeatLayerStringTokens.CONFIRM_TABLE,
    val confirmYourTickets: String = SeatLayerStringTokens.CONFIRM_YOUR_TICKETS,
    val confirmedAndOnWay: String = SeatLayerStringTokens.CONFIRMED_AND_ON_WAY,
    val continueToCheckout: String = SeatLayerStringTokens.CONTINUE_TO_CHECKOUT,
    val continueWithTotal: String = SeatLayerStringTokens.CONTINUE_WITH_TOTAL,
    val continueWord: String = SeatLayerStringTokens.CONTINUE_WORD,
    val couldNotAddMoreTime: String = SeatLayerStringTokens.COULD_NOT_ADD_MORE_TIME,
    val dismiss: String = "Dismiss",
    val displayOptions: String = SeatLayerStringTokens.DISPLAY_OPTIONS,
    val emptyTrayHint: String = SeatLayerStringTokens.EMPTY_TRAY_HINT,
    val emptyWheelchairSpace: String = SeatLayerStringTokens.EMPTY_WHEELCHAIR_SPACE,
    val errorMessage: String = SeatLayerStringTokens.ERROR_MESSAGE,
    val expandCart: String = SeatLayerStringTokens.EXPAND_CART,
    val fewerGuests: String = SeatLayerStringTokens.FEWER_GUESTS,
    val fewerTickets: String = SeatLayerStringTokens.FEWER_TICKETS,
    val findBestSeatsCta: String = SeatLayerStringTokens.FIND_BEST_SEATS_CTA,
    val findBestSeatsOne: String = SeatLayerStringTokens.FIND_BEST_SEATS_ONE,
    val findBestSeatsOther: String = SeatLayerStringTokens.FIND_BEST_SEATS_OTHER,
    val findSeats: String = SeatLayerStringTokens.FIND_SEATS,
    val findSeatsTogether: String = SeatLayerStringTokens.FIND_SEATS_TOGETHER,
    val findingBestSeats: String = SeatLayerStringTokens.FINDING_BEST_SEATS,
    val fitVenue: String = SeatLayerStringTokens.FIT_VENUE,
    val fitWholeVenue: String = SeatLayerStringTokens.FIT_WHOLE_VENUE,
    val flat2dMap: String = SeatLayerStringTokens.FLAT2D_MAP,
    val floors: String = "Floors",
    val from: String = "From",
    val fromPrice: String = SeatLayerStringTokens.FROM_PRICE,
    val generalAdmission: String = SeatLayerStringTokens.GENERAL_ADMISSION,
    val generatedSeatView: String = "Generated preview",
    val heldFor: String = SeatLayerStringTokens.HELD_FOR,
    val hideLimitedView: String = SeatLayerStringTokens.HIDE_LIMITED_VIEW,
    val holdAlreadyHeldBody: String = SeatLayerStringTokens.HOLD_ALREADY_HELD_BODY,
    val holdAlreadyHeldTitle: String = SeatLayerStringTokens.HOLD_ALREADY_HELD_TITLE,
    val holdAndCheckout: String = SeatLayerStringTokens.HOLD_AND_CHECKOUT,
    val holdExpired: String = SeatLayerStringTokens.HOLD_EXPIRED,
    val holdInCheckoutBody: String = SeatLayerStringTokens.HOLD_IN_CHECKOUT_BODY,
    val holdInCheckoutTitle: String = SeatLayerStringTokens.HOLD_IN_CHECKOUT_TITLE,
    val holdLapsedAllTakenOne: String = SeatLayerStringTokens.HOLD_LAPSED_ALL_TAKEN_ONE,
    val holdLapsedAllTakenOther: String = SeatLayerStringTokens.HOLD_LAPSED_ALL_TAKEN_OTHER,
    val holdLapsedBody: String = SeatLayerStringTokens.HOLD_LAPSED_BODY,
    val holdLapsedSomeTakenOne: String = SeatLayerStringTokens.HOLD_LAPSED_SOME_TAKEN_ONE,
    val holdLapsedSomeTakenOther: String = SeatLayerStringTokens.HOLD_LAPSED_SOME_TAKEN_OTHER,
    val holdLapsedStillFreeOne: String = SeatLayerStringTokens.HOLD_LAPSED_STILL_FREE_ONE,
    val holdLapsedStillFreeOther: String = SeatLayerStringTokens.HOLD_LAPSED_STILL_FREE_OTHER,
    val holdLapsedTitle: String = SeatLayerStringTokens.HOLD_LAPSED_TITLE,
    val holdMinutesLeftOne: String = SeatLayerStringTokens.HOLD_MINUTES_LEFT_ONE,
    val holdMinutesLeftOther: String = SeatLayerStringTokens.HOLD_MINUTES_LEFT_OTHER,
    val holdSecondsLeftOne: String = SeatLayerStringTokens.HOLD_SECONDS_LEFT_ONE,
    val holdSecondsLeftOther: String = SeatLayerStringTokens.HOLD_SECONDS_LEFT_OTHER,
    val interactive3dVenueView: String = SeatLayerStringTokens.INTERACTIVE3D_VENUE_VIEW,
    val loading: String = SeatLayerStringTokens.LOADING,
    val mapDidNotLoad: String = SeatLayerStringTokens.MAP_DID_NOT_LOAD,
    val mapView: String = SeatLayerStringTokens.MAP_VIEW,
    val moreCount: String = SeatLayerStringTokens.MORE_COUNT,
    val moreGuests: String = SeatLayerStringTokens.MORE_GUESTS,
    val moreTickets: String = SeatLayerStringTokens.MORE_TICKETS,
    val moreTimeAdded: String = SeatLayerStringTokens.MORE_TIME_ADDED,
    val moveVenue: String = SeatLayerStringTokens.MOVE_VENUE,
    val nextSeat: String = SeatLayerStringTokens.NEXT_SEAT,
    val nextSection: String = SeatLayerStringTokens.NEXT_SECTION,
    val noSeatsSelected: String = SeatLayerStringTokens.NO_SEATS_SELECTED,
    val noSelectableSeats: String = SeatLayerStringTokens.NO_SELECTABLE_SEATS,
    val notAvailable: String = SeatLayerStringTokens.NOT_AVAILABLE,
    val numberOfGuests: String = SeatLayerStringTokens.NUMBER_OF_GUESTS,
    val obstructedView: String = SeatLayerStringTokens.OBSTRUCTED_VIEW,
    val onlyLeft: String = SeatLayerStringTokens.ONLY_LEFT,
    val openVenue360: String = SeatLayerStringTokens.OPEN_VENUE360,
    val openingCheckout: String = SeatLayerStringTokens.OPENING_CHECKOUT,
    val orbitMode: String = SeatLayerStringTokens.ORBIT_MODE,
    val organizerNote: String = SeatLayerStringTokens.ORGANIZER_NOTE,
    val overview: String = SeatLayerStringTokens.OVERVIEW,
    val panMode: String = SeatLayerStringTokens.PAN_MODE,
    val passport: String = SeatLayerStringTokens.PASSPORT,
    val peekSecured: String = SeatLayerStringTokens.PEEK_SECURED,
    val pickYourSeats: String = SeatLayerStringTokens.PICK_YOUR_SEATS,
    val placeNumberIdentity: String = SeatLayerStringTokens.PLACE_NUMBER_IDENTITY,
    val placeWord: String = SeatLayerStringTokens.PLACE_WORD,
    val placesAvailable: String = SeatLayerStringTokens.PLACES_AVAILABLE,
    val poweredBy: String = SeatLayerStringTokens.POWERED_BY,
    val premiumSeat: String = SeatLayerStringTokens.PREMIUM_SEAT,
    val preview: String = "Preview",
    val previousSeat: String = SeatLayerStringTokens.PREVIOUS_SEAT,
    val previousSection: String = SeatLayerStringTokens.PREVIOUS_SECTION,
    val prices: String = "Prices",
    val quantity: String = "Quantity",
    val realSeatView: String = "Real view",
    val recentre: String = SeatLayerStringTokens.RECENTRE,
    val releaseAndChangeSeats: String = SeatLayerStringTokens.RELEASE_AND_CHANGE_SEATS,
    val reloadSeatMap: String = SeatLayerStringTokens.RELOAD_SEAT_MAP,
    val removeSeat: String = SeatLayerStringTokens.REMOVE_SEAT,
    val removeTicketsOne: String = SeatLayerStringTokens.REMOVE_TICKETS_ONE,
    val removeTicketsOther: String = SeatLayerStringTokens.REMOVE_TICKETS_OTHER,
    val removeWord: String = SeatLayerStringTokens.REMOVE_WORD,
    val reselectSeatsOne: String = SeatLayerStringTokens.RESELECT_SEATS_ONE,
    val reselectSeatsOther: String = SeatLayerStringTokens.RESELECT_SEATS_OTHER,
    val restrictedView: String = SeatLayerStringTokens.RESTRICTED_VIEW,
    val retry: String = SeatLayerStringTokens.RETRY,
    val rotateVenue: String = SeatLayerStringTokens.ROTATE_VENUE,
    val rowIdentity: String = SeatLayerStringTokens.ROW_IDENTITY,
    val rowWord: String = SeatLayerStringTokens.ROW_WORD,
    val salesClosed: String = SeatLayerStringTokens.SALES_CLOSED,
    val salesClosedCopy: String = SeatLayerStringTokens.SALES_CLOSED_COPY,
    val salesClosedCta: String = SeatLayerStringTokens.SALES_CLOSED_CTA,
    val salesClosedPill: String = SeatLayerStringTokens.SALES_CLOSED_PILL,
    val salesClosedToast: String = SeatLayerStringTokens.SALES_CLOSED_TOAST,
    val seatIdentity: String = SeatLayerStringTokens.SEAT_IDENTITY,
    val seatJustTakenByAnother: String = SeatLayerStringTokens.SEAT_JUST_TAKEN_BY_ANOTHER,
    val seatNumberIdentity: String = SeatLayerStringTokens.SEAT_NUMBER_IDENTITY,
    val seatRemoved: String = SeatLayerStringTokens.SEAT_REMOVED,
    val seatView: String = "Seat view",
    val seatWord: String = SeatLayerStringTokens.SEAT_WORD,
    val seatsFreeOne: String = SeatLayerStringTokens.SEATS_FREE_ONE,
    val seatsFreeOther: String = SeatLayerStringTokens.SEATS_FREE_OTHER,
    val seatsHeldForNeedMoreTime: String = SeatLayerStringTokens.SEATS_HELD_FOR_NEED_MORE_TIME,
    val seatsJustTaken: String = SeatLayerStringTokens.SEATS_JUST_TAKEN,
    val seatsLeft: String = SeatLayerStringTokens.SEATS_LEFT,
    val seatsLeftInSectionOne: String = SeatLayerStringTokens.SEATS_LEFT_IN_SECTION_ONE,
    val seatsLeftInSectionOther: String = SeatLayerStringTokens.SEATS_LEFT_IN_SECTION_OTHER,
    val seatsNotRecovered: String = SeatLayerStringTokens.SEATS_NOT_RECOVERED,
    val seatsSecuredOpeningCheckout: String = SeatLayerStringTokens.SEATS_SECURED_OPENING_CHECKOUT,
    val sectionWord: String = SeatLayerStringTokens.SECTION_WORD,
    val sections: String = "Sections",
    val secureMore: String = SeatLayerStringTokens.SECURE_MORE,
    val secureMoreAndCheckout: String = SeatLayerStringTokens.SECURE_MORE_AND_CHECKOUT,
    val securingSeats: String = SeatLayerStringTokens.SECURING_SEATS,
    val seeItIn3D: String = SeatLayerStringTokens.SEE_IT_IN3D,
    val select: String = SeatLayerStringTokens.SELECT,
    val selectSeats: String = SeatLayerStringTokens.SELECT_SEATS,
    val selectTable: String = SeatLayerStringTokens.SELECT_TABLE,
    val selectTicketTier: String = SeatLayerStringTokens.SELECT_TICKET_TIER,
    val showLess: String = SeatLayerStringTokens.SHOW_LESS,
    val sightline: String = SeatLayerStringTokens.SIGHTLINE,
    val soldOutCopy: String = SeatLayerStringTokens.SOLD_OUT_COPY,
    val soldOutEyebrow: String = SeatLayerStringTokens.SOLD_OUT_EYEBROW,
    val soldOutTitle: String = SeatLayerStringTokens.SOLD_OUT_TITLE,
    val testMode: String = SeatLayerStringTokens.TEST_MODE,
    val testModeExplained: String = SeatLayerStringTokens.TEST_MODE_EXPLAINED,
    val testModeLong: String = SeatLayerStringTokens.TEST_MODE_LONG,
    val ticketCountOne: String = SeatLayerStringTokens.TICKET_COUNT_ONE,
    val ticketCountOther: String = SeatLayerStringTokens.TICKET_COUNT_OTHER,
    val ticketType: String = SeatLayerStringTokens.TICKET_TYPE,
    val tickets: String = "tickets",
    val tierCompanionGuidance: String = SeatLayerStringTokens.TIER_COMPANION_GUIDANCE,
    val timeRemaining: String = "Time remaining",
    val total: String = "Total",
    val undo: String = SeatLayerStringTokens.UNDO,
    val updateTable: String = SeatLayerStringTokens.UPDATE_TABLE,
    val venue3D: String = SeatLayerStringTokens.VENUE3D,
    val venueMap: String = SeatLayerStringTokens.VENUE_MAP,
    val venueMapHint: String = SeatLayerStringTokens.VENUE_MAP_HINT,
    val venueView: String = SeatLayerStringTokens.VENUE_VIEW,
    val venueZone: String = SeatLayerStringTokens.VENUE_ZONE,
    val viewFromHere: String = SeatLayerStringTokens.VIEW_FROM_HERE,
    val viewFromThisSeat: String = SeatLayerStringTokens.VIEW_FROM_THIS_SEAT,
    val viewFromYourSeat: String = SeatLayerStringTokens.VIEW_FROM_YOUR_SEAT,
    val viewGroupTitle: String = SeatLayerStringTokens.VIEW_GROUP_TITLE,
    val zoomIn: String = SeatLayerStringTokens.ZOOM_IN,
    val zoomOut: String = SeatLayerStringTokens.ZOOM_OUT,
    val accessNeeds: Map<String, String> = defaultAccessNeeds,
) {
    public fun accessFreeCount(count: Int): String = accessFreeCount
        .replace("{count}", count.toString())

    public fun accessNeedWithCount(need: String, count: Int): String = accessNeedWithCount
        .replace("{need}", need)
        .replace("{count}", count.toString())

    public fun accessibleSections(count: Int): String = accessibleSections
        .replace("{count}", count.toString())

    public fun accessibleStep(index: Int, total: Int): String = accessibleStep
        .replace("{index}", index.toString())
        .replace("{total}", total.toString())

    public fun addMinutes(minutes: Int): String = addMinutes
        .replace("{count}", minutes.toString())

    public fun chooseMinMaxGuests(minimum: Int, maximum: Int): String = chooseMinMaxGuests
        .replace("{min}", minimum.toString())
        .replace("{max}", maximum.toString())

    public fun chooseMore(count: Int): String = chooseMore
        .replace("{count}", count.toString())

    public fun continueWithTotal(money: String): String = continueWithTotal
        .replace("{money}", money)

    public fun fromPrice(money: String): String = fromPrice
        .replace("{price}", money)

    public fun heldFor(clock: String): String = heldFor
        .replace("{clock}", clock)

    public fun holdLapsedBody(minutes: Int): String = holdLapsedBody
        .replace("{n}", minutes.toString())

    public fun moreCount(count: Int): String = moreCount
        .replace("{count}", count.toString())

    public fun onlyLeft(count: Int): String = onlyLeft
        .replace("{count}", count.toString())

    public fun peekSecured(count: Int, total: String): String = peekSecured
        .replace("{count}", count.toString())
        .replace("{total}", total)

    public fun placesAvailable(count: Int): String = placesAvailable
        .replace("{count}", count.toString())

    public fun seatIdentity(parts: List<String>): String = seatIdentity
        .replace("{parts}", parts.joinToString(separator = " \u00b7 "))

    public fun seatJustTakenByAnother(label: String): String = seatJustTakenByAnother
        .replace("{label}", label)

    public fun seatsHeldForNeedMoreTime(time: String): String = seatsHeldForNeedMoreTime
        .replace("{time}", time)

    public fun seatsLeft(count: Int): String = seatsLeft
        .replace("{count}", count.toString())

    public fun seatsNotRecovered(count: Int): String = seatsNotRecovered
        .replace("{n}", count.toString())

    public fun secureMoreAndCheckout(count: Int): String = secureMoreAndCheckout
        .replace("{count}", count.toString())

    public fun sightline(metres: String): String = sightline
        .replace("{m}", metres)

    public fun venueMap(venue: String): String = venueMap
        .replace("{venue}", venue)

    public fun findBestSeats(count: Int): String = (if (count == 1) findBestSeatsOne else findBestSeatsOther)
        .replace("{count}", count.toString())

    public fun holdLapsedAllTaken(count: Int): String = (if (count == 1) holdLapsedAllTakenOne else holdLapsedAllTakenOther)
        .replace("{count}", count.toString())

    public fun holdLapsedSomeTaken(count: Int): String = (if (count == 1) holdLapsedSomeTakenOne else holdLapsedSomeTakenOther)
        .replace("{count}", count.toString())

    public fun holdLapsedStillFree(count: Int): String = (if (count == 1) holdLapsedStillFreeOne else holdLapsedStillFreeOther)
        .replace("{count}", count.toString())

    public fun holdMinutesLeft(count: Int): String = (if (count == 1) holdMinutesLeftOne else holdMinutesLeftOther)
        .replace("{count}", count.toString())

    public fun holdSecondsLeft(count: Int): String = (if (count == 1) holdSecondsLeftOne else holdSecondsLeftOther)
        .replace("{count}", count.toString())

    public fun removeTickets(count: Int): String = (if (count == 1) removeTicketsOne else removeTicketsOther)
        .replace("{count}", count.toString())

    public fun reselectSeats(count: Int): String = (if (count == 1) reselectSeatsOne else reselectSeatsOther)
        .replace("{count}", count.toString())

    public fun seatsFree(count: Int): String = (if (count == 1) seatsFreeOne else seatsFreeOther)
        .replace("{count}", count.toString())

    public fun seatsLeftInSection(count: Int): String = (if (count == 1) seatsLeftInSectionOne else seatsLeftInSectionOther)
        .replace("{count}", count.toString())

    public fun ticketCount(count: Int): String = (if (count == 1) ticketCountOne else ticketCountOther)
        .replace("{count}", count.toString())

    /** The translated name of one accommodation, or its wire key. */
    public fun accessNeed(key: String): String = accessNeeds[key] ?: key

    public companion object {
        /** The English name of every accommodation the runtime reports. */
        @JvmStatic
        public val defaultAccessNeeds: Map<String, String> = mapOf(
            "wheelchair" to SeatLayerStringTokens.ACCESS_WHEELCHAIR,
            "companion" to SeatLayerStringTokens.ACCESS_COMPANION,
            "semi-ambulatory" to SeatLayerStringTokens.ACCESS_SEMI_AMBULATORY,
            "designated-aisle" to SeatLayerStringTokens.ACCESS_DESIGNATED_AISLE,
            "step-free" to SeatLayerStringTokens.ACCESS_STEP_FREE,
            "hearing" to SeatLayerStringTokens.ACCESS_HEARING,
            "cart" to SeatLayerStringTokens.ACCESS_CART,
            "sign-language" to SeatLayerStringTokens.ACCESS_SIGN_LANGUAGE,
            "low-vision" to SeatLayerStringTokens.ACCESS_LOW_VISION,
            "sensory-friendly" to SeatLayerStringTokens.ACCESS_SENSORY_FRIENDLY,
            "plus-size" to SeatLayerStringTokens.ACCESS_PLUS_SIZE,
            "lift-armrest" to SeatLayerStringTokens.ACCESS_LIFT_ARMREST,
        )

        @JvmStatic
        public val supportedLanguageTags: Set<String>
            get() = SeatLayerPickerLocaleData.dictionaries.keys

        /** Resolves an exact tag, then its base language, then English. */
        @JvmStatic
        @JvmOverloads
        public fun localized(
            languageTag: String = Locale.getDefault().toLanguageTag(),
            overrides: Map<String, String> = emptyMap(),
        ): SeatLayerPickerStrings {
            val tag = resolveLanguageTag(languageTag)
            val dictionary = SeatLayerPickerLocaleData.dictionaries.getValue(tag) +
                overrides.filterValues(String::isNotEmpty)
            val defaults = SeatLayerPickerStrings(languageTag = tag)
            fun text(key: String, fallback: String): String = dictionary[key] ?: fallback
            return defaults.copy(
                aboutBestSeats = text("aboutBestSeats", defaults.aboutBestSeats),
                accessRefresh = text("accessRefresh", defaults.accessRefresh),
                accessibility = text("accessibility", defaults.accessibility),
                accessibilityTitle = text("accessibilityTitle", defaults.accessibilityTitle),
                accessiblePhysicalSeat = text("accessiblePhysicalSeat", defaults.accessiblePhysicalSeat),
                addMinutes = text("addMinutes", defaults.addMinutes),
                anyTicketType = text("anyTicketType", defaults.anyTicketType),
                anyVenueZone = text("anyVenueZone", defaults.anyVenueZone),
                backToVenue = text("backToVenue", defaults.backToVenue),
                bestSeats = text("bestSeats", defaults.bestSeats),
                cancel = text("cancel", defaults.cancel),
                close = text("close", defaults.close),
                closestGroupChosenInstantly = text("closestGroupChosenInstantly", defaults.closestGroupChosenInstantly),
                colorblindSafe = text("colorblindSafe", defaults.colorblindSafe),
                continueWord = text("continueWord", defaults.continueWord),
                emptyTrayHint = text("emptyTrayHint", defaults.emptyTrayHint),
                emptyWheelchairSpace = text("emptyWheelchairSpace", defaults.emptyWheelchairSpace),
                errorMessage = text("errorMessage", defaults.errorMessage),
                findBestSeatsOne = text("findBestSeats.one", defaults.findBestSeatsOne),
                findBestSeatsOther = text("findBestSeats.other", defaults.findBestSeatsOther),
                findBestSeatsCta = text("findBestSeatsCta", defaults.findBestSeatsCta),
                findSeatsTogether = text("findSeatsTogether", defaults.findSeatsTogether),
                fitVenue = text("fitVenue", defaults.fitVenue),
                fitWholeVenue = text("fitWholeVenue", defaults.fitWholeVenue),
                fromPrice = text("fromPrice", defaults.fromPrice),
                hideLimitedView = text("hideLimitedView", defaults.hideLimitedView),
                holdAndCheckout = text("holdAndCheckout", defaults.holdAndCheckout),
                loading = text("loading", defaults.loading),
                moreCount = text("moreCount", defaults.moreCount),
                noSeatsSelected = text("noSeatsSelected", defaults.noSeatsSelected),
                notAvailable = text("notAvailable", defaults.notAvailable),
                obstructedView = text("obstructedView", defaults.obstructedView),
                openVenue360 = text("openVenue360", defaults.openVenue360),
                organizerNote = text("organizerNote", defaults.organizerNote),
                overview = text("overview", defaults.overview),
                poweredBy = text("poweredBy", defaults.poweredBy),
                premiumSeat = text("premiumSeat", defaults.premiumSeat),
                recentre = text("recentre", defaults.recentre),
                removeSeat = text("removeSeat", defaults.removeSeat),
                reselectSeatsOne = text("reselectSeats.one", defaults.reselectSeatsOne),
                reselectSeatsOther = text("reselectSeats.other", defaults.reselectSeatsOther),
                restrictedView = text("restrictedView", defaults.restrictedView),
                retry = text("retry", defaults.retry),
                seatsLeft = text("seatsLeft", defaults.seatsLeft),
                select = text("select", defaults.select),
                showLess = text("showLess", defaults.showLess),
                sightline = text("sightline", defaults.sightline),
                testMode = text("testMode", defaults.testMode),
                ticketCountOne = text("ticketCount.one", defaults.ticketCountOne),
                ticketCountOther = text("ticketCount.other", defaults.ticketCountOther),
                undo = text("undo", defaults.undo),
                viewFromHere = text("viewFromHere", defaults.viewFromHere),
                viewFromYourSeat = text("viewFromYourSeat", defaults.viewFromYourSeat),
                continueWithTotal = text("continueWord", defaults.continueWord)
                    .let { word -> "$word \u00b7 {money}" },
                accessNeeds = defaults.accessNeeds.mapValues { (key, name) ->
                    text("accessNeeds.$key", name)
                },
            )
        }

        private fun resolveLanguageTag(requested: String): String {
            val normalized = requested.trim().replace('_', '-')
            SeatLayerPickerLocaleData.dictionaries.keys.firstOrNull {
                it.equals(normalized, ignoreCase = true)
            }?.let { return it }
            val locale = Locale.forLanguageTag(normalized)
            if (locale.language == "zh") {
                val traditional = locale.script.equals("Hant", ignoreCase = true) ||
                    locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
                return if (traditional) "zh-Hant" else "zh-Hans"
            }
            return locale.language.takeIf(
                SeatLayerPickerLocaleData.dictionaries::containsKey,
            ) ?: "en"
        }
    }
}
