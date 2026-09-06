package io.seatlayer.android.compose

/*
 * The four SeatLayer buyer SDKs draw the same chrome and are supposed to name
 * the same string the same way. Where this SDK's name had drifted, the shared
 * name is now the field and the old name reads through to it for one release.
 *
 * These are read-only forwarders: an override that used to travel through
 * `copy(oldName = …)` moves to the new field name.
 */

@Deprecated("Renamed to bestSeats.", ReplaceWith("bestSeats"))
public val SeatLayerPickerStrings.bestAvailable: String get() = bestSeats

@Deprecated("Renamed to chooseTickets.", ReplaceWith("chooseTickets"))
public val SeatLayerPickerStrings.cart: String get() = chooseTickets

@Deprecated("Renamed to numberOfGuests.", ReplaceWith("numberOfGuests"))
public val SeatLayerPickerStrings.chooseQuantity: String get() = numberOfGuests

@Deprecated("Renamed to numberOfGuests.", ReplaceWith("numberOfGuests"))
public val SeatLayerPickerStrings.guests: String get() = numberOfGuests

@Deprecated("Renamed to collapseCart.", ReplaceWith("collapseCart"))
public val SeatLayerPickerStrings.hideCart: String get() = collapseCart

@Deprecated("Renamed to hideLimitedView.", ReplaceWith("hideLimitedView"))
public val SeatLayerPickerStrings.limitedView: String get() = hideLimitedView

@Deprecated("Renamed to emptyTrayHint.", ReplaceWith("emptyTrayHint"))
public val SeatLayerPickerStrings.noTickets: String get() = emptyTrayHint

@Deprecated("Renamed to orbitMode.", ReplaceWith("orbitMode"))
public val SeatLayerPickerStrings.orbit: String get() = orbitMode

@Deprecated("Renamed to panMode.", ReplaceWith("panMode"))
public val SeatLayerPickerStrings.pan: String get() = panMode

@Deprecated("Renamed to removeWord.", ReplaceWith("removeWord"))
public val SeatLayerPickerStrings.remove: String get() = removeWord

@Deprecated("Use reselectSeats(count).", ReplaceWith("reselectSeats(count)"))
public val SeatLayerPickerStrings.reselectAvailable: String get() = reselectSeatsOther

@Deprecated("Renamed to rowWord.", ReplaceWith("rowWord"))
public val SeatLayerPickerStrings.row: String get() = rowWord

@Deprecated("Renamed to seatWord.", ReplaceWith("seatWord"))
public val SeatLayerPickerStrings.seat: String get() = seatWord

@Deprecated("Renamed to sectionWord.", ReplaceWith("sectionWord"))
public val SeatLayerPickerStrings.section: String get() = sectionWord

@Deprecated("Renamed to expandCart.", ReplaceWith("expandCart"))
public val SeatLayerPickerStrings.showCart: String get() = expandCart

@Deprecated("Renamed to soldOutTitle.", ReplaceWith("soldOutTitle"))
public val SeatLayerPickerStrings.soldOut: String get() = soldOutTitle

@Deprecated("Renamed to chooseTableGuests.", ReplaceWith("chooseTableGuests"))
public val SeatLayerPickerStrings.tableGuests: String get() = chooseTableGuests

@Deprecated("Renamed to errorMessage.", ReplaceWith("errorMessage"))
public val SeatLayerPickerStrings.unavailable: String get() = errorMessage

@Deprecated("Renamed to overview.", ReplaceWith("overview"))
public val SeatLayerPickerStrings.venue: String get() = overview

@Deprecated("Renamed to fromPrice.", ReplaceWith("fromPrice"))
public val SeatLayerPickerStrings.fromPricePattern: String get() = fromPrice

@Deprecated("Renamed to moreCount.", ReplaceWith("moreCount"))
public val SeatLayerPickerStrings.moreCountPattern: String get() = moreCount

@Deprecated("Renamed to seatsLeft.", ReplaceWith("seatsLeft"))
public val SeatLayerPickerStrings.seatsLeftPattern: String get() = seatsLeft
