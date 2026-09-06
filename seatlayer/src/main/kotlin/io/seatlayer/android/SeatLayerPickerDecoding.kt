package io.seatlayer.android

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Tolerant on additive fields and malformed optional entries. The schema,
 * session id, exact revision, and event key are the only whole-snapshot gates.
 */
internal fun decodeSeatLayerPickerSnapshot(
    value: JsonElement?,
): SeatLayerPickerSnapshot? {
    val root = value as? JsonObject ?: return null
    if (root.string("schema") != SEATLAYER_PICKER_SNAPSHOT_SCHEMA) return null
    val sessionId = root.string("sessionId")?.takeIf(String::isNotEmpty) ?: return null
    val revision = exactPickerInteger(root["revision"]) ?: return null
    val event = decodePickerEvent(root["event"]) ?: return null

    val catalog = root.objectValue("catalog")
    val selectionNode = root.objectValue("selection")
    val cart = root.objectValue("cart")
    val hold = root.objectValue("hold")
    val access = root.objectValue("access")
    val reported = decodePickerList(
        cart?.get("items") ?: cart?.get("lines"),
        ::decodePickerCartLine,
    )
    val selection = decodePickerList(
        selectionNode?.get("seats"),
        ::decodePickerSelectedSeat,
    )
    // A runtime that treats the live hold as checkout's reports only that
    // hold's lines, so a seat chosen after the hold was made is drawn selected
    // on the map, missing from the cart, and refused at Continue. Complete the
    // cart from the selection: the line invents nothing, every field is the
    // seat's own, and the runtime's next hold corrects the price it could not
    // know. Matched by label, because a reported line may carry no seat id.
    val reportedLabels = reported.mapTo(mutableSetOf(), SeatLayerPickerCartLine::label)
    val missing = selection
        .filterNot { seat -> seat.label in reportedLabels }
        .map(::pickerCartLineFromSeat)
    val cartLines = reported + missing
    val cartCompletedFromSelection = missing.isNotEmpty()

    return SeatLayerPickerSnapshot(
        schema = SEATLAYER_PICKER_SNAPSHOT_SCHEMA,
        sessionId = sessionId,
        revision = revision,
        event = event,
        branding = decodePickerBranding(root["branding"]),
        categories = decodePickerList(
            catalog?.get("categories"),
            ::decodePickerCategory,
        ),
        zones = decodePickerList(catalog?.get("zones"), ::decodePickerZone),
        sections = decodePickerList(
            catalog?.get("sections"),
            ::decodePickerSection,
        ),
        generalAdmissionAreas = decodePickerList(
            catalog?.get("gaAreas"),
            ::decodeSeatLayerPickerGeneralAdmissionArea,
        ),
        bestAvailableZones = decodePickerList(
            catalog?.get("bestAvailableZones"),
            ::decodePickerZone,
        ),
        map = decodePickerMap(root["map"]),
        selection = selection,
        selectionValidity = decodeSelectionValidity(selectionNode?.get("validity")),
        maxSelection = exactPickerInteger(selectionNode?.get("maxSelection")) ?: 10,
        ticketCount = if (cartCompletedFromSelection) {
            cartLines.sumOf { it.quantity.coerceAtLeast(1) }
        } else {
            exactPickerInteger(cart?.get("quantity")) ?: selection.size
        },
        cartLines = cartLines,
        cartCompletedFromSelection = cartCompletedFromSelection,
        cartTotal = if (cartCompletedFromSelection) {
            cartLines.sumOf(SeatLayerPickerCartLine::total)
        } else {
            finitePickerDouble(cart?.get("total"))
                ?: cartLines.sumOf(SeatLayerPickerCartLine::total)
        },
        currency = cart?.string("currency") ?: event.currency,
        hold = SeatLayerPickerHold(
            active = hold?.boolean("active") ?: false,
            expiresAt = finitePickerDouble(hold?.get("expiresAt")),
            owner = hold?.string("ownership"),
        ),
        accessConfigured = access?.boolean("configured") ?: false,
        accessStatus = access?.string("status") ?: "public",
        accessReason = access?.string("reason"),
        capabilities = enabledPickerCapabilities(root["features"]),
        raw = root,
    )
}

/** One seat carried by an event rather than by the selection array. */
internal fun decodeSeatLayerPickerRetappedSeat(
    value: JsonElement?,
): SeatLayerPickerSelectedSeat? = value?.let(::decodePickerSelectedSeat)

private fun decodePickerSelectedSeat(value: JsonElement): SeatLayerPickerSelectedSeat? {
    val root = value as? JsonObject ?: return null
    val id = root.string("id") ?: return null
    val label = root.string("label") ?: return null
    return SeatLayerPickerSelectedSeat(
        id = id,
        label = label,
        displayLabel = root.string("displayLabel"),
        displayType = root.string("displayType") ?: root.string("rowType"),
        objectId = root.string("objectId"),
        objectType = root.string("objectType"),
        bookingMode = root.string("bookingMode"),
        sectionLabel = root.string("sectionLabel"),
        rowLabel = root.string("rowLabel"),
        seatNumber = root.string("seatNumber"),
        categoryKey = root.string("categoryKey") ?: "",
        price = finitePickerDouble(root["price"]) ?: 0.0,
        currency = root.string("currency") ?: "USD",
        tiers = decodePickerList(root["tiers"], ::decodePickerTier),
        tierId = root.string("tierId"),
        accessibility = uniquePickerStrings(root["accessibility"]),
        wheelchairSpaceType = root.string("wheelchairSpaceType"),
        quantity = exactPickerInteger(root["quantity"]),
        capacity = exactPickerInteger(root["capacity"]),
        minOccupancy = exactPickerInteger(root["minOccupancy"]),
        maxOccupancy = exactPickerInteger(root["maxOccupancy"]),
        screenPoint = decodePickerScreenPoint(root["screenPoint"]),
        commercial = decodePickerCommercial(root["commercial"]),
        seatViewThumbnail = decodePickerSeatViewThumbnail(root["seatViewThumb"]),
        // A negative distance is a renderer that could not measure, not a seat
        // behind the stage.
        sightlineMetres = finitePickerDouble(root["sightlineMetres"])?.takeIf { it >= 0 },
        seatViewConfidence = decodePickerSeatConfidence(root["seatViewConfidence"]),
    )
}

private fun decodePickerScreenPoint(value: JsonElement?): SeatLayerPickerScreenPoint? {
    val root = value as? JsonObject ?: return null
    val x = finitePickerDouble(root["x"]) ?: return null
    val y = finitePickerDouble(root["y"]) ?: return null
    return SeatLayerPickerScreenPoint(x, y)
}

private fun decodePickerCommercial(value: JsonElement?): SeatLayerPickerSeatCommercial? {
    val root = value as? JsonObject ?: return null
    val commercial = SeatLayerPickerSeatCommercial(
        restrictedView = root.boolean("restrictedView") == true,
        obstructedView = root.boolean("obstructedView") == true,
        premium = root.boolean("premium") == true,
        organizerNote = root.string("note")?.trim()?.takeIf(String::isNotEmpty),
    )
    return commercial.takeUnless { it.isEmpty }
}

/**
 * A thumbnail only when the runtime says the image is a real photograph.
 *
 * A generated preview offered as "the view from your seat" is a promise the
 * seat cannot keep, so anything but `real` is dropped.
 */
private fun decodePickerSeatViewThumbnail(
    value: JsonElement?,
): SeatLayerPickerSeatViewThumbnail? {
    val root = value as? JsonObject ?: return null
    if (root.string("kind") != "real") return null
    val reference = root.string("reference")?.takeIf(String::isNotEmpty) ?: return null
    return SeatLayerPickerSeatViewThumbnail(reference)
}

/** Every prose field or nothing: half a disclosure still reads as a whole one. */
private fun decodePickerSeatConfidence(
    value: JsonElement?,
): SeatLayerPickerSeatConfidence? {
    val root = value as? JsonObject ?: return null
    val prose = listOf("headline", "model", "reality", "coverage", "provenance", "freshness")
        .map { key -> root.string(key)?.trim()?.takeIf(String::isNotEmpty) ?: return null }
    return SeatLayerPickerSeatConfidence(
        headline = prose[0],
        model = prose[1],
        reality = prose[2],
        coverage = prose[3],
        provenance = prose[4],
        freshness = prose[5],
        limitations = uniquePickerStrings(root["limitations"]),
        modeledTarget = root.string("modeledTarget"),
    )
}

/**
 * Accessible spaces left in a section, or null when nothing counted them.
 *
 * The runtime reports a map keyed by accommodation. An empty map is a runtime
 * that does not offer the count, so it stays null rather than becoming zero.
 */
private fun decodePickerAccessibleFree(value: JsonElement?): Int? {
    exactPickerInteger(value)?.let { return it.coerceAtLeast(0) }
    val root = value as? JsonObject ?: return null
    val counts = root.values.mapNotNull(::exactPickerInteger)
    if (counts.isEmpty()) return null
    return counts.sumOf { it.coerceAtLeast(0) }
}

internal fun decodeSeatLayerPickerCheckoutHandoff(
    value: JsonElement?,
): SeatLayerPickerCheckoutHandoff? {
    val root = value as? JsonObject ?: return null
    val holdId = root.string("holdId")?.takeIf(String::isNotEmpty) ?: return null
    val expiresAt = finitePickerDouble(root["expiresAt"]) ?: return null
    val lines = decodePickerList(root["lineItems"], ::decodePickerCartLine)
    return SeatLayerPickerCheckoutHandoff(
        holdId = holdId,
        expiresAt = expiresAt,
        currency = root.string("currency") ?: lines.firstOrNull()?.currency ?: "USD",
        lineItems = lines,
        total = finitePickerDouble(root["total"])
            ?: lines.sumOf(SeatLayerPickerCartLine::total),
    )
}

internal fun decodeSeatLayerSeatView(value: JsonElement?): SeatLayerSeatView? {
    val root = value as? JsonObject ?: return null
    return SeatLayerSeatView(
        seatId = root.string("seatId"),
        title = root.string("title"),
        caption = root.string("caption"),
        badge = root.string("badge"),
        real = root.boolean("real") ?: false,
        generated = root.boolean("generated") ?: false,
        dragHint = root.string("dragHint"),
    )
}

internal fun decodeSeatLayerPickerAvailabilityOutcome(
    value: JsonElement?,
): SeatLayerPickerAvailabilityOutcome? {
    val source = value as? JsonObject ?: return null
    val root = source.objectValue("result") ?: source
    if ("lost" !in root && "holdLapsed" !in root) return null

    val holdLapsed = root.boolean("holdLapsed") == true
    val lapsed = uniquePickerStrings(root["lapsedLabels"] ?: root["lapsed"])
    val recoverable = uniquePickerStrings(
        root["recoverableLabels"] ?: root["recoverable"],
    ).filter(lapsed.toSet()::contains)
    return SeatLayerPickerAvailabilityOutcome(
        refreshed = root.boolean("refreshed") != false,
        lostLabels = uniquePickerStrings(root["lost"]),
        holdLapsed = holdLapsed,
        lapsedLabels = if (holdLapsed) lapsed else emptyList(),
        recoverableLabels = if (holdLapsed) recoverable else emptyList(),
        revision = exactPickerInteger(root["revision"]),
        heldForMillis = exactPickerInteger(root["heldForMs"]),
    )
}

private fun decodePickerEvent(value: JsonElement?): SeatLayerPickerEventDetails? {
    val root = value as? JsonObject ?: return null
    val key = root.string("key")?.takeIf(String::isNotEmpty) ?: return null
    return SeatLayerPickerEventDetails(
        key = key,
        name = root.string("name") ?: key,
        mode = EventMode(root.string("mode") ?: EventMode.Live.raw),
        currency = root.string("currency") ?: "USD",
        venue = root.string("venue"),
        startsAt = finitePickerDouble(root["startsAt"]),
        timezone = root.string("timezone"),
        locale = root.string("locale"),
        posterUrl = root.string("posterUrl"),
        salesClosed = root.boolean("salesClosed") ?: false,
    )
}

private fun decodePickerBranding(value: JsonElement?): SeatLayerPickerBranding {
    val root = value as? JsonObject
    val tokens = root?.objectValue("tokens")
    return SeatLayerPickerBranding(
        brandName = root?.string("brandName"),
        logoUrl = root?.string("logoUrl"),
        attributionRequired = root?.boolean("attributionRequired") ?: true,
        accent = root?.string("accent") ?: tokens?.string("accent"),
        accentInk = root?.string("accentInk") ?: tokens?.string("accentInk"),
        background = root?.string("background") ?: tokens?.string("background"),
        surface = tokens?.string("surface"),
        text = root?.string("textColor") ?: tokens?.string("text"),
        muted = tokens?.string("muted"),
        line = tokens?.string("line"),
        fontFamily = tokens?.string("fontFamily"),
        radius = finitePickerDouble(tokens?.get("radius")),
    )
}

private fun decodePickerCategory(value: JsonElement): SeatLayerPickerCategory? {
    val root = value as? JsonObject ?: return null
    val key = root.string("key")?.takeIf(String::isNotEmpty) ?: return null
    val tiers = decodePickerList(root["tiers"], ::decodePickerTier)
    val prices = tiers.map(SeatLayerPickerCategoryTier::price)
    val base = finitePickerDouble(root["price"]) ?: prices.firstOrNull() ?: 0.0
    return SeatLayerPickerCategory(
        key = key,
        label = root.string("label") ?: key,
        color = root.string("color") ?: "#6e7bff",
        priceMin = finitePickerDouble(root["priceMin"]) ?: prices.minOrNull() ?: base,
        priceMax = finitePickerDouble(root["priceMax"]) ?: prices.maxOrNull() ?: base,
        available = exactPickerInteger(root["available"]) ?: 0,
        notForSale = root.boolean("notForSale") ?: false,
        tiers = tiers,
        free = exactPickerInteger(root["free"])?.coerceAtLeast(0),
    )
}

private fun decodePickerTier(value: JsonElement): SeatLayerPickerCategoryTier? {
    val root = value as? JsonObject ?: return null
    return SeatLayerPickerCategoryTier(
        id = root.string("id") ?: return null,
        name = root.string("name") ?: return null,
        price = finitePickerDouble(root["price"]) ?: return null,
        currency = root.string("currency"),
        restriction = root.string("restriction"),
        buyerMessage = root.string("buyerMessage"),
    )
}

internal fun decodeSeatLayerPickerGeneralAdmissionArea(
    value: JsonElement?,
): SeatLayerPickerGeneralAdmissionArea? {
    val root = value as? JsonObject ?: return null
    return SeatLayerPickerGeneralAdmissionArea(
        id = root.string("id") ?: return null,
        label = root.string("label"),
        capacity = exactPickerInteger(root["capacity"]),
        available = exactPickerInteger(root["available"]),
        categoryKey = root.string("categoryKey"),
        price = finitePickerDouble(root["price"]),
        currency = root.string("currency"),
        tiers = decodePickerList(root["tiers"], ::decodePickerTier),
    )
}

private fun decodePickerZone(value: JsonElement): SeatLayerPickerZone? {
    val root = value as? JsonObject ?: return null
    val id = root.string("id")?.takeIf(String::isNotEmpty) ?: return null
    return SeatLayerPickerZone(
        id = id,
        label = root.string("label") ?: id,
        color = root.string("color"),
    )
}

private fun decodePickerSection(value: JsonElement): SeatLayerPickerSectionSummary? {
    val root = value as? JsonObject ?: return null
    val id = root.string("id")?.takeIf(String::isNotEmpty) ?: return null
    return SeatLayerPickerSectionSummary(
        id = id,
        label = root.string("label") ?: id,
        displayLabel = root.string("displayLabel"),
        zoneId = root.string("zoneId"),
        zoneLabel = root.string("zoneLabel"),
        entrance = root.string("entrance"),
        color = root.string("color"),
        dominantCategoryKey = root.string("dominantCategoryKey"),
        seatsLeft = exactPickerInteger(root["seatsLeft"]),
        priceMin = finitePickerDouble(root["priceMin"]),
        priceMax = finitePickerDouble(root["priceMax"]),
        accessibleFree = decodePickerAccessibleFree(
            root["accessibleFree"] ?: root["accessibleLeft"],
        ),
    )
}

/** One cart line built from a seat the reported cart does not carry. */
private fun pickerCartLineFromSeat(
    seat: SeatLayerPickerSelectedSeat,
): SeatLayerPickerCartLine {
    val objectId = seat.objectId ?: seat.id
    return SeatLayerPickerCartLine(
        lineKey = objectId,
        label = seat.label,
        displayLabel = seat.displayLabel,
        displayType = seat.displayType,
        objectId = objectId,
        objectType = seat.objectType ?: "seat",
        categoryKey = seat.categoryKey,
        tierId = seat.tierId,
        unitPrice = seat.price,
        currency = seat.currency,
        quantity = seat.quantity ?: 1,
        seatId = seat.id,
        sectionLabel = seat.sectionLabel,
        rowLabel = seat.rowLabel,
        seatNumber = seat.seatNumber,
    )
}

private fun decodePickerCartLine(value: JsonElement): SeatLayerPickerCartLine? {
    val root = value as? JsonObject ?: return null
    val label = root.string("label")?.takeIf(String::isNotEmpty) ?: return null
    val objectId = root.string("objectId") ?: label
    return SeatLayerPickerCartLine(
        lineKey = root.string("lineKey") ?: root.string("key") ?: objectId,
        label = label,
        displayLabel = root.string("displayLabel"),
        displayType = root.string("displayType"),
        objectId = objectId,
        objectType = root.string("objectType") ?: "seat",
        categoryKey = root.string("categoryKey") ?: "",
        tierId = root.string("tierId"),
        unitPrice = finitePickerDouble(root["unitPrice"]) ?: 0.0,
        currency = root.string("currency") ?: "USD",
        quantity = exactPickerInteger(root["quantity"]) ?: 1,
        seatId = root.string("seatId"),
        sectionLabel = root.string("sectionLabel"),
        rowLabel = root.string("rowLabel"),
        seatNumber = root.string("seatNumber"),
    )
}

private fun decodePickerMap(value: JsonElement?): SeatLayerPickerMapState {
    val root = value as? JsonObject
    val rung = root?.string("rung") ?: "zones"
    val focusedSectionId = root?.string("focusedSectionId")
    val focusedSection = root?.get("focusedSection")?.let(::decodePickerSection)
    val reportsView3DPosition = listOf(
        "view3dPreviousSeatId",
        "view3dNextSeatId",
        "view3dFocusedSectionId",
    ).any { key -> root?.containsKey(key) == true }
    return SeatLayerPickerMapState(
        rung = rung,
        viewMode = root?.string("viewMode") ?: root?.string("projection") ?: "flat",
        buyerView = root?.string("buyerView") ?: "map",
        view3DNavigationMode = root?.string("view3dNavigationMode") ?: "orbit",
        view3DTargetSeatId = root?.string("view3dTargetSeatId"),
        activeFloorId = root?.string("activeFloorId") ?: root?.string("floorId"),
        focusedSectionId = focusedSectionId,
        focusedSection = focusedSection,
        colorblindSafe = root?.boolean("colorblindSafe") ?: false,
        hideLimitedView = root?.boolean("hideLimitedView") ?: false,
        canZoomIn = root?.boolean("canZoomIn") ?: true,
        // Present-only: absent is "the renderer cannot answer", never false.
        atVenueFit = root?.boolean("atVenueFit"),
        // Older runtimes could report false while already below the venue.
        // Explicit true still wins, while semantic 2D evidence preserves one
        // safe step-out during a rolling runtime/native rollout.
        canZoomOut = root?.boolean("canZoomOut") == true ||
            rung == "seats" ||
            focusedSectionId != null ||
            focusedSection != null,
        categoryFilter = uniquePickerStrings(root?.get("categoryFilter")),
        accessibilityFilter = uniquePickerStrings(root?.get("accessibilityFilter")),
        accessNeeds = decodePickerAccessNeeds(root?.get("accessNeeds")),
        floors = decodePickerList(root?.get("floors"), ::decodePickerFloor),
        floorMode = root?.string("floorMode"),
        floorLabelStyle = root?.string("floorLabelStyle"),
        viewportInsets = decodePickerInsets(root?.get("viewportInsets")),
        view3DTargetSeat = root?.get("view3dTargetSeat")?.let(
            ::decodePickerSelectedSeat,
        ),
        view3DPreviousSeatId = root?.string("view3dPreviousSeatId"),
        view3DNextSeatId = root?.string("view3dNextSeatId"),
        view3DFocusedSectionId = root?.string("view3dFocusedSectionId"),
        reportsView3DPosition = reportsView3DPosition,
    )
}

private fun decodePickerFloor(value: JsonElement): SeatLayerPickerFloorInfo? {
    val root = value as? JsonObject ?: return null
    val id = root.string("id")?.takeIf(String::isNotEmpty) ?: return null
    val name = root.string("name")?.takeIf(String::isNotEmpty) ?: return null
    return SeatLayerPickerFloorInfo(
        id = id,
        name = name,
        level = exactPickerInteger(root["level"]),
    )
}

private fun decodePickerInsets(value: JsonElement?): SeatLayerPickerViewportInsets? {
    val root = value as? JsonObject ?: return null
    return SeatLayerPickerViewportInsets(
        top = (finitePickerDouble(root["top"]) ?: 0.0).coerceAtLeast(0.0),
        right = (finitePickerDouble(root["right"]) ?: 0.0).coerceAtLeast(0.0),
        bottom = (finitePickerDouble(root["bottom"]) ?: 0.0).coerceAtLeast(0.0),
        left = (finitePickerDouble(root["left"]) ?: 0.0).coerceAtLeast(0.0),
    )
}

private fun decodePickerAccessNeeds(value: JsonElement?): List<SeatLayerPickerAccessNeed> {
    val seen = mutableSetOf<String>()
    return decodePickerList(value) { entry ->
        val root = entry as? JsonObject ?: return@decodePickerList null
        val key = root.string("key")?.trim()?.takeIf(String::isNotEmpty)
            ?: return@decodePickerList null
        if (!seen.add(key)) return@decodePickerList null
        SeatLayerPickerAccessNeed(
            key = key,
            count = exactPickerInteger(root["count"])?.coerceAtLeast(0),
        )
    }
}

private fun enabledPickerCapabilities(value: JsonElement?): Set<String> {
    val root = value as? JsonObject ?: return emptySet()
    return root.mapNotNullTo(linkedSetOf()) { (key, feature) ->
        when {
            (feature as? JsonPrimitive)?.let { !it.isString && it.content == "true" } == true -> key
            feature is JsonArray && feature.isNotEmpty() -> key
            else -> null
        }
    }
}

internal fun uniquePickerStrings(value: JsonElement?): List<String> {
    val values = value as? JsonArray ?: return emptyList()
    val seen = linkedSetOf<String>()
    return values.mapNotNull { item ->
        val string = (item as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
            ?: return@mapNotNull null
        string.takeIf(seen::add)
    }
}

internal fun exactPickerInteger(value: JsonElement?): Int? {
    val primitive = (value as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)
        ?: return null
    primitive.intOrNull?.let { return it }
    val double = primitive.doubleOrNull ?: return null
    if (!double.isFinite() || double % 1.0 != 0.0) return null
    return double.toLong().takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
}

internal fun finitePickerDouble(value: JsonElement?): Double? {
    val primitive = (value as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)
        ?: return null
    return primitive.doubleOrNull?.takeIf(Double::isFinite)
}

private inline fun <T> decodePickerList(
    value: JsonElement?,
    decode: (JsonElement) -> T?,
): List<T> = (value as? JsonArray).orEmpty().mapNotNull(decode)
