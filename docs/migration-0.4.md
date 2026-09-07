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

The remaining two, `dock` and `fit`, have no successor on this class; see
[Changed defaults](#changed-defaults) below. Two switches are new:
`showVenue3DChrome` and `showSeatViewChrome`, for the 3D and panorama chrome,
both on by default.

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

The dense run collapsed structurally equivalent addressed seats into one row.
It hid the seat a buyer was looking for behind a summary they then had to open,
and a row they cannot act on directly is not a shorter list. A host that drew
its own list from `denseRuns` iterates `confirmedCart(cartLines, pendingSeat).items`
instead; each item is one `SeatLayerPickerCartLine` and carries its own removal
identity.

## Changed defaults

**The section dock bar is off on every width.** `SeatLayerPickerChromeOptions.dock`
no longer controls it and always reads `false`; the switch moved to
`SeatLayerPickerOptions(showDockBar = true)`, which brings it back for a host
that wants it. The section it named is already on the map, under the buyer's
thumb.

**The built-in layouts no longer draw a fit control.**
`SeatLayerPickerChromeOptions.fit` no longer controls anything and always reads
`false`. The whole-venue control (`showOverviewControl`) does the same job
without going stale under a pinch. `SeatLayerPickerFitControl` is still public,
so a host-owned Compose layout that wants the control can place it.

## Upgrade checklist

1. Move both coordinates to `0.4.0` together.
2. Build. Every renamed symbol reports a deprecation warning with a
   `ReplaceWith`; apply them.
3. Replace `holdTtlMillis` with a `Duration`.
4. Replace any use of `denseRuns` or `SeatLayerPickerDenseRun`.
5. If you relied on the section dock bar, set
   `SeatLayerPickerOptions(showDockBar = true)`.
6. If you relied on the built-in fit control, place `SeatLayerPickerFitControl`
   in your own layout.
7. Confirm the renderer origin `https://cdn.seatlayer.io` is still registered on
   your publishable key; the pinned page moves to
   `seatlayer-js@0.84.1/mobile.html`.
8. Re-run your own checkout path end to end against real inventory. The hold,
   handoff, and rejection contracts are unchanged, but the pin is not.
