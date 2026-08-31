package io.seatlayer.android

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Protocol revision used only by the native picker surface. */
public const val SEATLAYER_PICKER_PROTOCOL_MIN: Int = 2

/** Protocol revision used only by the native picker surface. */
public const val SEATLAYER_PICKER_PROTOCOL_MAX: Int = 2

/** Schema understood by [SeatLayerPickerSnapshot]. */
public const val SEATLAYER_PICKER_SNAPSHOT_SCHEMA: String =
    "seatlayer.picker.snapshot/1"

/** Runtime floor sentinel meaning that every floor is visible. */
public const val SEATLAYER_ALL_FLOORS: String = "all"

/**
 * Internal handshake contract for the additive protocol-2 picker surface.
 *
 * The existing raw [SeatLayerView] deliberately continues to use
 * [ProtocolRange.Native] (`1..1`).
 */
internal data class SeatLayerPickerBridgeProfile(
    val protocolRange: ProtocolRange,
    val requiredCapabilities: Set<String>,
    val requiredCommands: Set<String>,
    val requiredEvents: Set<String>,
    val optionalCapabilities: Set<String>,
    val config: JsonObject,
) {
    fun validate(bundle: BundleInfo) {
        val agreed = negotiate(host = protocolRange, web = bundle.protocolRange)
        if (agreed == null) {
            throw SeatLayerException.Incompatible(
                native = protocolRange,
                web = bundle.protocolRange,
                reason = "No shared SeatLayer picker bridge protocol revision.",
            )
        }

        val missingCapabilities = requiredCapabilities - bundle.capabilities.toSet()
        val missingCommands = requiredCommands - bundle.commands.toSet()
        val missingEvents = requiredEvents - bundle.events.toSet()
        if (
            missingCapabilities.isEmpty() &&
            missingCommands.isEmpty() &&
            missingEvents.isEmpty()
        ) return

        val missing = buildList {
            if (missingCapabilities.isNotEmpty()) {
                add("capabilities: ${missingCapabilities.sorted().joinToString()}")
            }
            if (missingCommands.isNotEmpty()) {
                add("commands: ${missingCommands.sorted().joinToString()}")
            }
            if (missingEvents.isNotEmpty()) {
                add("events: ${missingEvents.sorted().joinToString()}")
            }
        }
        throw SeatLayerException.Incompatible(
            native = protocolRange,
            web = bundle.protocolRange,
            reason = "The bundle is missing required picker contract entries " +
                "(${missing.joinToString("; ")}).",
        )
    }

    fun initPayload(
        configuration: SeatLayerConfiguration,
        bundle: BundleInfo? = null,
    ): JsonObject {
        val fields = configuration.initPayload().toMutableMap()
        fields["protocol"] = protocolRange.toJson()

        val mergedConfig = (
            (fields["config"] as? JsonObject).orEmpty() + config
        )
        val chrome = (fields["chrome"] as? JsonObject).orEmpty().toMutableMap().apply {
            put("owner", JsonPrimitive("native"))
            put("seatTooltip", JsonPrimitive(false))
            put("testModeIndicator", JsonPrimitive(false))
            put("attribution", JsonPrimitive(false))
            if (
                config.boolean("enableSeatView") != false &&
                bundle?.supportsCapability("native-seat-view-chrome-v1") == true &&
                "seatView.changed" in bundle.events
            ) {
                put("seatViewTitle", JsonPrimitive(false))
                put("seatViewCaption", JsonPrimitive(false))
                put("seatViewBadge", JsonPrimitive(false))
            }
        }

        fields["surface"] = jsonObject(
            "kind" to JsonPrimitive("picker"),
            "stateContract" to JsonPrimitive(1),
            "chromeOwner" to JsonPrimitive("native"),
        )
        fields["requirements"] = jsonObject(
            "capabilities" to JsonArray(
                requiredCapabilities.sorted().map(::JsonPrimitive),
            ),
        )
        fields["chrome"] = JsonObject(chrome)
        fields["config"] = JsonObject(mergedConfig)
        return JsonObject(fields)
    }

    companion object {
        fun create(
            enable3D: Boolean = true,
            enableSeatView: Boolean = true,
            config: JsonObject = JsonObject(emptyMap()),
        ): SeatLayerPickerBridgeProfile {
            val capabilities = linkedSetOf(
                "picker-session-v2",
                "picker-snapshot-v1",
                "picker-actions-v1",
                "native-picker-chrome-v1",
                "native-chrome-contract-v1",
                "checkout-handoff-v1",
                "checkout-handoff-reject-v1",
                "hold-ownership-v1",
                "cart-line-remove-v1",
                "table-quantity-v1",
            )
            val commands = linkedSetOf(
                "picker.getSnapshot",
                "picker.selectObjects",
                "picker.deselectObjects",
                "picker.clearSelection",
                "picker.selectCategories",
                "picker.deselectCategories",
                "picker.setSeatTier",
                "picker.removeCartLine",
                "picker.setTableQuantity",
                "picker.setSelectableObjects",
                "picker.setMaxSelection",
                "picker.setCategoryFilter",
                "picker.setAccessibilityFilter",
                "picker.setLimitedViewFilter",
                "picker.focusSection",
                "picker.overview",
                "picker.setRung",
                "picker.setFloor",
                "picker.setColorblindSafe",
                "picker.setThemeMode",
                "picker.setViewMode",
                "picker.setInteractionEnabled",
                "picker.zoomIn",
                "picker.zoomOut",
                "picker.zoomToFit",
                "picker.holdGA",
                "picker.bestAvailable",
                "picker.resumeHold",
                "picker.extendHold",
                "picker.continue",
                "picker.rejectHandoff",
                "picker.abort",
                "picker.lifecycle",
                "picker.destroy",
            )
            if (enable3D) {
                capabilities += setOf("venue-3d-v1", "venue-3d-controls-v1")
                commands += setOf(
                    "picker.setBuyerView",
                    "picker.setVenue3DNavigationMode",
                )
            }
            if (enableSeatView) {
                capabilities += "seat-view-v1"
                commands += "picker.openSeatView"
            }
            return SeatLayerPickerBridgeProfile(
                protocolRange = ProtocolRange(
                    SEATLAYER_PICKER_PROTOCOL_MIN,
                    SEATLAYER_PICKER_PROTOCOL_MAX,
                ),
                requiredCapabilities = capabilities,
                requiredCommands = commands,
                requiredEvents = setOf("picker.snapshot"),
                optionalCapabilities = setOf(
                    "native-seat-view-chrome-v1",
                    "viewport-insets-v1",
                    "floor-stack-v1",
                    "chart-load-trace-v1",
                    "availability-refresh-v1",
                    "access-needs-v1",
                    "hold-selection-v1",
                ),
                config = config,
            )
        }
    }
}

private fun JsonElement?.orEmpty(): Map<String, JsonElement> =
    (this as? JsonObject)?.toMap().orEmpty()
