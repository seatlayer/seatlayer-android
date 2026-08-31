package io.seatlayer.android

import kotlinx.serialization.json.JsonElement

@JvmInline
public value class SeatLayerPickerThemeMode(public val raw: String) {
    public companion object {
        public val Auto: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode("auto")
        public val Light: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode("light")
        public val Dark: SeatLayerPickerThemeMode = SeatLayerPickerThemeMode("dark")
    }
}

/** Renderer colour roles sent during a live native-theme update. */
public data class SeatLayerPickerMapTheme(
    val background: String,
    val rowLabel: String,
    val text: String,
    val selection: String,
)

public data class SeatLayerPickerEventDetails(
    val key: String,
    val name: String,
    val mode: EventMode,
    val currency: String,
    val venue: String?,
    val startsAt: Double?,
    val timezone: String?,
    val locale: String?,
    val posterUrl: String?,
    val salesClosed: Boolean,
)

public data class SeatLayerPickerBranding(
    val brandName: String?,
    val logoUrl: String?,
    val attributionRequired: Boolean,
    val accent: String?,
    val accentInk: String?,
    val background: String?,
    val surface: String?,
    val text: String?,
    val muted: String?,
    val line: String?,
    val fontFamily: String?,
    val radius: Double?,
)

/** Protocol-2 tier metadata, separate from the frozen raw-map [CategoryTier] ABI. */
public data class SeatLayerPickerCategoryTier(
    val id: String,
    val name: String,
    val price: Double,
    val currency: String?,
    val restriction: String?,
    val buyerMessage: String?,
)

public data class SeatLayerPickerCategory(
    val key: String,
    val label: String,
    val color: String,
    val priceMin: Double,
    val priceMax: Double,
    val available: Int,
    val notForSale: Boolean,
    val tiers: List<SeatLayerPickerCategoryTier>,
)

/** Rich picker-only selection model without changing the raw protocol-1 seat ABI. */
public data class SeatLayerPickerSelectedSeat(
    val id: String,
    val label: String,
    val displayLabel: String?,
    val displayType: String?,
    val objectId: String?,
    val objectType: String?,
    val bookingMode: String?,
    val sectionLabel: String?,
    val rowLabel: String?,
    val seatNumber: String?,
    val categoryKey: String,
    val price: Double,
    val currency: String,
    val tiers: List<SeatLayerPickerCategoryTier>,
    val tierId: String?,
    val accessibility: List<String>,
    val wheelchairSpaceType: String?,
    val quantity: Int?,
    val capacity: Int?,
    val minOccupancy: Int?,
    val maxOccupancy: Int?,
) {
    public val buyerFacingLabel: String get() = displayLabel ?: label
    public val isVariableTable: Boolean
        get() = objectType == "table" && bookingMode == "variable"
}

public data class SeatLayerPickerZone(
    val id: String,
    val label: String,
    val color: String?,
)

public data class SeatLayerPickerSectionSummary(
    val id: String,
    val label: String,
    val displayLabel: String?,
    val zoneId: String?,
    val zoneLabel: String?,
    val entrance: String?,
    val color: String?,
    val dominantCategoryKey: String?,
    val seatsLeft: Int?,
    val priceMin: Double?,
    val priceMax: Double?,
)

public data class SeatLayerPickerCartLine(
    val lineKey: String,
    val label: String,
    val displayLabel: String?,
    val displayType: String?,
    val objectId: String,
    val objectType: String,
    val categoryKey: String,
    val tierId: String?,
    val unitPrice: Double,
    val currency: String,
    val quantity: Int,
    val seatId: String?,
    val sectionLabel: String?,
    val rowLabel: String?,
    val seatNumber: String?,
) {
    public val total: Double get() = unitPrice * quantity
}

/**
 * Opaque checkout capability transferred to the host application.
 *
 * The hold id is deliberately absent from ordinary snapshots and appears only
 * in this handoff.
 */
public data class SeatLayerPickerCheckoutHandoff(
    val holdId: String,
    val expiresAt: Double,
    val currency: String,
    val lineItems: List<SeatLayerPickerCartLine>,
    val total: Double,
)

public data class SeatLayerPickerHold(
    val active: Boolean,
    val expiresAt: Double?,
    /** `picker`, `host`, or a future additive value. */
    val owner: String?,
)

@JvmInline
public value class SeatLayerPickerRecovery(public val raw: String) {
    public companion object {
        public val All: SeatLayerPickerRecovery = SeatLayerPickerRecovery("all")
        public val Partial: SeatLayerPickerRecovery = SeatLayerPickerRecovery("partial")
        public val None: SeatLayerPickerRecovery = SeatLayerPickerRecovery("none")
    }
}

public data class SeatLayerPickerAvailabilityOutcome(
    val refreshed: Boolean,
    val lostLabels: List<String> = emptyList(),
    val holdLapsed: Boolean = false,
    val lapsedLabels: List<String> = emptyList(),
    val recoverableLabels: List<String> = emptyList(),
    val revision: Int? = null,
    val heldForMillis: Int? = null,
) {
    public val recovery: SeatLayerPickerRecovery
        get() = when {
            recoverableLabels.isEmpty() -> SeatLayerPickerRecovery.None
            recoverableLabels.size >= lapsedLabels.size -> SeatLayerPickerRecovery.All
            else -> SeatLayerPickerRecovery.Partial
        }

    public val isQuiet: Boolean get() = lostLabels.isEmpty() && !holdLapsed

    public companion object {
        public val Unsupported: SeatLayerPickerAvailabilityOutcome =
            SeatLayerPickerAvailabilityOutcome(refreshed = false)
    }
}

public data class SeatLayerPickerHoldLapse(
    val lapsedLabels: List<String>,
    val recoverableLabels: List<String>,
    val heldForMillis: Int? = null,
) {
    public val recovery: SeatLayerPickerRecovery
        get() = when {
            recoverableLabels.isEmpty() -> SeatLayerPickerRecovery.None
            recoverableLabels.size >= lapsedLabels.size -> SeatLayerPickerRecovery.All
            else -> SeatLayerPickerRecovery.Partial
        }

    public val unrecoverableCount: Int
        get() = (lapsedLabels.size - recoverableLabels.size).coerceAtLeast(0)
}

public data class SeatLayerPickerLifecycleResult(
    val snapshot: SeatLayerPickerSnapshot? = null,
    val outcome: SeatLayerPickerAvailabilityOutcome? = null,
)

public data class SeatLayerPickerViewportInsets(
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
    val left: Double = 0.0,
) {
    init {
        require(listOf(top, right, bottom, left).all { it.isFinite() && it >= 0 }) {
            "viewport insets must be finite numbers greater than or equal to zero"
        }
    }

    public companion object {
        public val Zero: SeatLayerPickerViewportInsets = SeatLayerPickerViewportInsets()
    }
}

public data class SeatLayerPickerFloorInfo(
    val id: String,
    val name: String,
    val level: Int?,
)

public data class SeatLayerPickerAccessNeed(
    val key: String,
    val count: Int,
) {
    public val isAvailable: Boolean get() = count > 0
}

/** Protocol-2 general-admission inventory with full native tier metadata. */
public data class SeatLayerPickerGeneralAdmissionArea(
    val id: String,
    val label: String?,
    val capacity: Int?,
    val available: Int?,
    val categoryKey: String?,
    val price: Double?,
    val currency: String?,
    val tiers: List<SeatLayerPickerCategoryTier>,
)

public data class SeatLayerPickerMapState(
    val rung: String,
    val viewMode: String,
    val buyerView: String,
    val view3DNavigationMode: String,
    val view3DTargetSeatId: String?,
    val activeFloorId: String?,
    val focusedSectionId: String?,
    val focusedSection: SeatLayerPickerSectionSummary?,
    val colorblindSafe: Boolean,
    val hideLimitedView: Boolean,
    val canZoomIn: Boolean,
    val canZoomOut: Boolean,
    val categoryFilter: List<String>,
    val accessibilityFilter: List<String>,
    val accessNeeds: List<SeatLayerPickerAccessNeed>,
    val floors: List<SeatLayerPickerFloorInfo>,
    val floorMode: String?,
    val floorLabelStyle: String?,
    val viewportInsets: SeatLayerPickerViewportInsets?,
    /** Explicit 3D target, including a seat outside the current selection. */
    val view3DTargetSeat: SeatLayerPickerSelectedSeat? = null,
    /** Null is an authored row boundary when [reportsView3DPosition] is true. */
    val view3DPreviousSeatId: String? = null,
    /** Null is an authored row boundary when [reportsView3DPosition] is true. */
    val view3DNextSeatId: String? = null,
    /** Section focused by the 3D camera; independent of the 2D focus. */
    val view3DFocusedSectionId: String? = null,
    /** False means the older runtime omitted all additive 3D-position keys. */
    val reportsView3DPosition: Boolean = false,
) {
    public val isVenue3D: Boolean get() = buyerView == "venue3d"
    public val showsAllFloors: Boolean get() = floorMode == SEATLAYER_ALL_FLOORS
}

/** One immutable, revision-ordered native picker state from the renderer. */
public data class SeatLayerPickerSnapshot(
    val schema: String,
    val sessionId: String,
    val revision: Int,
    val event: SeatLayerPickerEventDetails,
    val branding: SeatLayerPickerBranding,
    val categories: List<SeatLayerPickerCategory>,
    val zones: List<SeatLayerPickerZone>,
    val sections: List<SeatLayerPickerSectionSummary>,
    val generalAdmissionAreas: List<SeatLayerPickerGeneralAdmissionArea>,
    val bestAvailableZones: List<SeatLayerPickerZone>,
    val map: SeatLayerPickerMapState,
    val selection: List<SeatLayerPickerSelectedSeat>,
    val selectionValidity: SelectionValidity?,
    val maxSelection: Int,
    val ticketCount: Int,
    val cartLines: List<SeatLayerPickerCartLine>,
    val cartTotal: Double,
    val currency: String,
    val hold: SeatLayerPickerHold,
    val accessConfigured: Boolean,
    val accessStatus: String,
    val accessReason: String?,
    val capabilities: Set<String>,
    /** Complete additive payload for diagnostics and future projections. */
    val raw: JsonElement,
)

public data class SeatLayerSeatView(
    val seatId: String?,
    val title: String?,
    val caption: String?,
    val badge: String?,
    val real: Boolean,
    val generated: Boolean,
    val dragHint: String?,
)

/** Monotonic protocol-2 handshake timing without changing legacy [ReadyInfo]. */
public data class SeatLayerPickerReadyTiming(
    val timeToHelloMs: Long? = null,
    val timeToReadyMs: Long? = null,
    /** Complete additive ready payload for diagnostics and future projections. */
    val raw: JsonElement? = null,
)

public sealed interface SeatLayerPickerPhase {
    public data object Idle : SeatLayerPickerPhase
    public data object Loading : SeatLayerPickerPhase
    public data class Ready(
        val info: ReadyInfo,
        val timing: SeatLayerPickerReadyTiming = SeatLayerPickerReadyTiming(),
    ) : SeatLayerPickerPhase
    public data class Failed(val error: SeatLayerException) : SeatLayerPickerPhase
    public data object Destroyed : SeatLayerPickerPhase
}

/** Runtime behavior owned by the headless core rather than a visual toolkit. */
public data class SeatLayerPickerBehavior(
    val readOnly: Boolean = false,
    val confirmSelection: Boolean = true,
    val enableBestAvailable: Boolean = true,
    val enableVenue3D: Boolean = true,
    val enableSeatView: Boolean = true,
    val holdTtlMillis: Int? = null,
    val initialHoldId: String? = null,
    val max3DSeats: Int? = null,
    val hideEventDetails: Boolean = false,
    val panelInitiallyCollapsed: Boolean = true,
    val refreshOnResume: Boolean = true,
    val announceHoldLapse: Boolean = true,
    val languages: List<String> = emptyList(),
) {
    init {
        require(holdTtlMillis == null || holdTtlMillis > 0) {
            "holdTtlMillis must be positive"
        }
        require(initialHoldId == null || initialHoldId.isNotBlank()) {
            "initialHoldId must not be blank"
        }
        require(max3DSeats == null || max3DSeats > 0) {
            "max3DSeats must be positive"
        }
        require(languages.all(String::isNotBlank)) {
            "languages must contain non-blank values"
        }
    }
}

/** Immutable state exposed to custom Compose and View/XML applications. */
public data class SeatLayerPickerState(
    val phase: SeatLayerPickerPhase = SeatLayerPickerPhase.Idle,
    val snapshot: SeatLayerPickerSnapshot? = null,
    val seatView: SeatLayerSeatView? = null,
    val lastError: SeatLayerException? = null,
    val availabilityOutcome: SeatLayerPickerAvailabilityOutcome? = null,
    val holdLapse: SeatLayerPickerHoldLapse? = null,
    val presentation: SeatLayerPickerPresentationState = SeatLayerPickerPresentationState(),
    val bundleInfo: BundleInfo? = null,
) {
    public val isReady: Boolean get() = phase is SeatLayerPickerPhase.Ready
}
