package io.seatlayer.android.compose

import androidx.compose.runtime.Immutable
import java.util.Locale

/** Buyer-facing native chrome copy. Locale generation expands this surface. */
@Immutable
public data class SeatLayerPickerStrings(
    val languageTag: String = "en",
    val close: String = "Close",
    val chooseSeats: String = "Choose your seats",
    val loading: String = "Loading seat map…",
    val retry: String = "Try again",
    val unavailable: String = "The seat map is unavailable.",
    val testMode: String = "TEST MODE",
    val poweredBy: String = "Powered by SeatLayer",
    val prices: String = "Prices",
    val allPrices: String = "All prices",
    val soldOut: String = "Sold out",
    val zoomIn: String = "Zoom in",
    val zoomOut: String = "Zoom out",
    val overview: String = "Venue overview",
    val fitVenue: String = "Fit venue",
    val venue: String = "Venue",
    val floors: String = "Floors",
    val allFloors: String = "All floors",
    val sections: String = "Sections",
    val accessibility: String = "Accessibility",
    val bestAvailable: String = "Best available",
    val findSeats: String = "Find seats",
    val quantity: String = "Quantity",
    val confirm: String = "Confirm",
    val select: String = "Select",
    val ticketType: String = "Ticket type",
    val tierCompanionGuidance: String = "Requires the adjacent wheelchair place.",
    val cancel: String = "Cancel",
    val apply: String = "Apply",
    val remove: String = "Remove",
    val undo: String = "Undo",
    val showLess: String = "Show less",
    val cart: String = "Your tickets",
    val continueWord: String = "Continue",
    val total: String = "Total",
    val chooseQuantity: String = "Choose quantity",
    val guests: String = "Guests",
    val holdExpired: String = "Your hold expired.",
    val reselectAvailable: String = "Reselect available seats",
    val dismiss: String = "Dismiss",
    val actionFailed: String = "That action could not be completed.",
    val mapView: String = "Seat map",
    val venue3D: String = "3D",
    val seatView: String = "Seat view",
    val realSeatView: String = "Real view",
    val generatedSeatView: String = "Generated preview",
    val preview: String = "Preview",
    val viewFromHere: String = "View from here",
    val backToVenue: String = "Back to venue",
    val openVenue360: String = "Open venue 360°",
    val recentre: String = "Recentre",
    val previousSeat: String = "Previous seat",
    val nextSeat: String = "Next seat",
    val rotateVenue: String = "Rotate venue",
    val moveVenue: String = "Move around venue",
    val orbit: String = "Orbit",
    val pan: String = "Pan",
    val showCart: String = "Show cart",
    val hideCart: String = "Hide cart",
    val tickets: String = "tickets",
    val from: String = "From",
    val noTickets: String = "Choose seats from the map or let us find the best available.",
    val holdAndCheckout: String = "Hold seats & checkout",
    val timeRemaining: String = "Time remaining",
    val limitedView: String = "Hide limited-view seats",
    val colorblindSafe: String = "Colorblind-safe colours",
    val tableGuests: String = "Guests at this table",
    val generalAdmission: String = "General admission",
    val seat: String = "Seat",
    val row: String = "Row",
    val section: String = "Section",
    val accessibilityTitle: String = "Accessibility and colour options",
    val anyTicketType: String = "Any ticket type",
    val anyVenueZone: String = "Any venue zone",
    val viewFromYourSeat: String = "view from your seat",
    val seatsLeftPattern: String = "{count} left",
    val fromPricePattern: String = "From {price}",
    val moreCountPattern: String = "+{count} more",
    val ticketCountOne: String = "{count} ticket",
    val ticketCountOther: String = "{count} tickets",
    val findBestSeatsOne: String = "Find {count} best seat",
    val findBestSeatsOther: String = "Find {count} best seats",
    val reselectSeatsOne: String = "Reselect it",
    val reselectSeatsOther: String = "Reselect them",
) {
    public fun seatsLeft(count: Int): String = seatsLeftPattern.count(count)

    public fun fromPrice(price: String): String = fromPricePattern.replace("{price}", price)

    public fun moreCount(count: Int): String = moreCountPattern.count(count)

    public fun ticketCount(count: Int): String =
        (if (count == 1) ticketCountOne else ticketCountOther).count(count)

    public fun findBestSeats(count: Int): String =
        (if (count == 1) findBestSeatsOne else findBestSeatsOther).count(count)

    public fun reselectSeats(count: Int): String =
        (if (count == 1) reselectSeatsOne else reselectSeatsOther).count(count)

    public companion object {
        /** Resolves an exact BCP-47 dictionary, then its base language, then English. */
        @JvmStatic
        public fun localized(
            languageTag: String = Locale.getDefault().toLanguageTag(),
            overrides: Map<String, String> = emptyMap(),
        ): SeatLayerPickerStrings {
            val resolvedTag = resolveLanguageTag(languageTag)
            val dictionary = SeatLayerPickerLocaleData.dictionaries.getValue(resolvedTag) +
                overrides.filterValues(String::isNotEmpty)
            val defaults = SeatLayerPickerStrings(languageTag = resolvedTag)
            fun text(key: String, fallback: String): String = dictionary[key] ?: fallback
            return defaults.copy(
                close = text("close", defaults.close),
                loading = text("loading", defaults.loading),
                retry = text("retry", defaults.retry),
                unavailable = text("errorMessage", defaults.unavailable),
                testMode = text("testMode", defaults.testMode),
                poweredBy = text("poweredBy", defaults.poweredBy),
                allPrices = text("anyTicketType", defaults.allPrices),
                overview = text("overview", defaults.overview),
                fitVenue = text("fitVenue", defaults.fitVenue),
                venue = text("overview", defaults.venue),
                accessibility = text("accessibility", defaults.accessibility),
                bestAvailable = text("bestSeats", defaults.bestAvailable),
                select = text("select", defaults.select),
                cancel = text("cancel", defaults.cancel),
                undo = text("undo", defaults.undo),
                showLess = text("showLess", defaults.showLess),
                continueWord = text("continueWord", defaults.continueWord),
                noTickets = text("emptyTrayHint", defaults.noTickets),
                holdAndCheckout = text("holdAndCheckout", defaults.holdAndCheckout),
                limitedView = text("hideLimitedView", defaults.limitedView),
                colorblindSafe = text("colorblindSafe", defaults.colorblindSafe),
                viewFromHere = text("viewFromHere", defaults.viewFromHere),
                preview = text("preview", defaults.preview),
                backToVenue = text("backToVenue", defaults.backToVenue),
                openVenue360 = text("openVenue360", defaults.openVenue360),
                recentre = text("recentre", defaults.recentre),
                accessibilityTitle = text(
                    "accessibilityTitle",
                    defaults.accessibilityTitle,
                ),
                anyTicketType = text("anyTicketType", defaults.anyTicketType),
                anyVenueZone = text("anyVenueZone", defaults.anyVenueZone),
                viewFromYourSeat = text("viewFromYourSeat", defaults.viewFromYourSeat),
                seatsLeftPattern = text("seatsLeft", defaults.seatsLeftPattern),
                fromPricePattern = text("fromPrice", defaults.fromPricePattern),
                moreCountPattern = text("moreCount", defaults.moreCountPattern),
                ticketCountOne = text("ticketCount.one", defaults.ticketCountOne),
                ticketCountOther = text("ticketCount.other", defaults.ticketCountOther),
                findBestSeatsOne = text("findBestSeats.one", defaults.findBestSeatsOne),
                findBestSeatsOther = text("findBestSeats.other", defaults.findBestSeatsOther),
                reselectSeatsOne = text("reselectSeats.one", defaults.reselectSeatsOne),
                reselectSeatsOther = text("reselectSeats.other", defaults.reselectSeatsOther),
            )
        }

        @JvmStatic
        public val supportedLanguageTags: Set<String>
            get() = SeatLayerPickerLocaleData.dictionaries.keys

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
            return locale.language.takeIf(SeatLayerPickerLocaleData.dictionaries::containsKey)
                ?: "en"
        }
    }
}

private fun String.count(count: Int): String = replace("{count}", count.toString())
