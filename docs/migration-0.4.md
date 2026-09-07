# Migrate from Android SDK 0.3.x to 0.4.0

SeatLayer Android `0.4.0` renames the public Kotlin API to the names every
SeatLayer buyer SDK shares, so a team porting a screen between Android, iOS,
Flutter and the web reads the same words in each. It also removes the dense
ticket list and changes two chrome defaults.

**Every old name still compiles.** The renamed symbols are kept as
`@Deprecated` forwarders for exactly one release: they work in `0.4.0`, and
they are removed in `0.5.0`. Android Studio's *Code → Inspect Code* or a
`Replace with` quick-fix moves a call site in one step, because every
deprecation carries a `ReplaceWith`.

**No bridge wire name changed.** Capability strings, command names, event names
and snapshot fields are exactly what they were. A proxy, a log filter or a
runtime pinned to an older bridge needs no change. See
[the bridge reference](bridge.md).

## Update the version

```kotlin
dependencies {
    val seatlayerVersion = "0.4.0"
    implementation("io.seatlayer:seatlayer-android:$seatlayerVersion")
    implementation("io.seatlayer:seatlayer-android-compose:$seatlayerVersion")
}
```

Both artifacts always move together. The raw protocol-1 `SeatLayerView` API is
untouched by this release; a raw-only integration updates the version and stops
here.

## Renamed: options

| 0.3.x | 0.4.0 |
| --- | --- |
| `SeatLayerPickerOptions.enableVenue3D` | `enable3D` |
| `SeatLayerPickerOptions.holdTtlMillis: Int?` | `holdTtl: Duration?` |

`holdTtl` also changes type. `holdTtlMillis = 10 * 60 * 1_000` becomes
`holdTtl = 10.minutes` (`kotlin.time.Duration.Companion.minutes`). Left unset,
the server's own hold window applies and the SDK never names a number.

## Renamed: chrome switches

Every `SeatLayerPickerChromeOptions` flag takes its `showX` name. Fourteen are
straight renames:

| 0.3.x | 0.4.0 |
| --- | --- |
| `header` | `showHeader` |
| `priceLegend` | `showPriceRail` |
| `floorSelector` | `showFloorSelector` |
| `floorStrip` | `showFloorStrip` |
| `mapControls` | `showMapControls` |
| `overview` | `showOverviewControl` |
| `zoom` | `showZoomControls` |
| `colorblind` | `showColorblindControl` |
| `map3D` | `showViewModeControl` |
| `accessibility` | `showAccessibilityControl` |
| `cartSheet` | `showTicketPanel` |
| `confirmCard` | `showConfirmCard` |
| `holdCountdown` | `showHoldPill` |
| `attribution` | `showAttribution` |

`dock` has no successor on this class; see
[Changed defaults](#changed-defaults) below. Three switches are new:
`showVenue3DChrome` and `showSeatViewChrome`, for the 3D and panorama chrome,
both on by default, and `showZoomToFitControl` for the whole-venue disc, which
takes over from `fit`.

Four switches now read `Boolean?`, where null means "whatever this width
should do" rather than a fixed answer: `showOverviewControl`,
`showZoomControls`, `showZoomToFitControl` and `showColorblindControl`. A host
that stored one of them in a `Boolean` field widens it; `true` and `false`
still mean exactly what they meant.

## Renamed: theme, styles, and formatter

| 0.3.x | 0.4.0 |
| --- | --- |
| `SeatLayerPickerTheme.onSurface` | `text` |
| `SeatLayerPickerTheme.muted` | `mutedText` |
| `SeatLayerPickerTheme.cornerRadius` | `radius` |
| `SeatLayerPickerMoneyFormatter` | `SeatLayerMoneyFormatter` |

A `SeatLayerPickerTheme` built with named arguments needs the new names; one
built positionally needs checking against the current constructor, which also
grew `warnText`, `premium`, `premiumText`, `fontFamily`, `buttonRadius` and the
composable `logo` slot. All of the new fields have defaults.

## Renamed: controller methods

| 0.3.x | 0.4.0 |
| --- | --- |
| `removeCartLine(label)` | `removeObject(label)` |
| `setLimitedViewFilter(enabled)` | `setLimitedViewHidden(hidden)` |
| `setInteractionEnabled(enabled)` | `setMapInteractionEnabled(enabled)` |
| `setVenue3DNavigationMode(mode)` | `set3DNavigationMode(mode)` |
| `holdGeneralAdmission(...)` | `setGeneralAdmissionQuantity(...)` |
| `rejectHandoff(holdId)` | `rejectCheckoutHandoff(holdId)` |
| `refreshAvailability()` | `refreshAccess()` |
| `dismissActionError()` | `dismissError()` |
| `setCartExpanded(expanded)` | `setCartSheetExpanded(expanded)` |

`setLimitedViewHidden` is a pure rename: the old `enabled = true` already meant
"hide limited-view seats", and the parameter now says so.

`venue3D(...)` gains the shared alias `showSeatIn3D(seatId)`; both remain.

## Removed

| Removed | Use instead |
| --- | --- |
| `SeatLayerPickerDenseRun` | Every ticket is its own cart line; read `SeatLayerPickerCartLine` |
| `SeatLayerPickerProjections.denseRuns` | `SeatLayerPickerProjections.confirmedCart(items, pending).items` |
| `SeatLayerPickerZoomControls` | `SeatLayerPickerZoomInButton`, `SeatLayerPickerZoomOutButton` |
| `SeatLayerPickerAccessibility` | `SeatLayerPickerController.setAccessibilityFilter`, `setLimitedViewHidden`, `setColorblindSafe` |
| `SeatLayerPickerAccessibilityMutation` | — the plan is gone; each switch applies itself |

`SeatLayerPickerAccessibility` planned a batch of mutations from two drafts and
handed them back for a host to apply. The accessibility sheet is now switch rows
that apply live, so there is no draft to diff and no plan to carry: call the
controller method for the switch the buyer moved.
`SeatLayerPickerAccessibilityDraft` and `SeatLayerPickerAccessibilityAvailability`
both remain.

`SeatLayerPickerZoomControls` drew a fixed pair. The map's control column is
now assembled from one disc per control, so a host-owned layout places exactly
the discs it wants and each keeps its own enabled rule.

The dense run collapsed structurally equivalent addressed seats into one row.
It hid the seat a buyer was looking for behind a summary they then had to open,
and a row they cannot act on directly is not a shorter list. A host that drew
its own list from `denseRuns` iterates `confirmedCart(cartLines, pendingSeat).items`
instead; each item is one `SeatLayerPickerCartLine` and carries its own removal
identity.

## Changed signatures

`SeatLayerPickerUndoNotice` is **not** removed — a host that places its own
parts can still place it — but it no longer takes the undo window as a
parameter:

| 0.3.x | 0.4.0 |
| --- | --- |
| `SeatLayerPickerUndoNotice(modifier, durationMillis)` | `SeatLayerPickerUndoNotice(modifier)` |

The window is the design source's own value, so a picker that shows the notice
and the toast band cannot show two different windows. Drop the argument. If you
were not placing parts by hand, prefer `SeatLayerPickerToastLayer`: it is the
single surface for every transient sentence the picker says, undo included, and
the ready-made picker already draws it.

## Changed defaults

**The section dock bar is off on every width.** `SeatLayerPickerChromeOptions.dock`
no longer controls it and always reads `false`; the switch moved to
`SeatLayerPickerOptions(showDockBar = true)`, which brings it back for a host
that wants it. The section it named is already on the map, under the buyer's
thumb.

**The fit control became the whole-venue disc.**
`SeatLayerPickerChromeOptions.fit` no longer controls anything and always reads
`false`; `showZoomToFitControl` is the switch, and the disc is on at both
widths. It is always live, whatever the camera last reported, so a pinch can
never strand a buyer with no way back to the venue.
`SeatLayerPickerFitControl` is still public for a host-owned layout.

## Upgrade checklist

1. Move both coordinates to `0.4.0` together.
2. Build. Every renamed symbol reports a deprecation warning with a
   `ReplaceWith`; apply them.
3. Replace `holdTtlMillis` with a `Duration`.
4. Replace any use of `denseRuns` or `SeatLayerPickerDenseRun`.
5. If you relied on the section dock bar, set
   `SeatLayerPickerOptions(showDockBar = true)`.
6. Replace `SeatLayerPickerZoomControls` with the individual zoom buttons, and
   drop the duration argument from any `SeatLayerPickerUndoNotice` call.
7. Confirm the renderer origin `https://cdn.seatlayer.io` is still registered on
   your publishable key; the pinned page moves to
   `seatlayer-js@0.84.1/mobile.html`.
8. Re-run your own checkout path end to end against real inventory. The hold,
   handoff, and rejection contracts are unchanged, but the pin is not.
