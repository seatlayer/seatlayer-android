# Changelog

## 0.4.0

Runtime pin 0.84.1, up from 0.71.5. The public API takes the names every
SeatLayer buyer SDK shares, and every number and word the native chrome draws
now comes from one locked design source.

- **The picker reads what a seat actually is.** A seat's accommodations, its
  wheelchair provision, a restricted or obstructed view, a premium seat and the
  organizer's own note arrive with the seat and are modelled as notes in one
  fixed reading order, over the twelve accommodations the runtime reports, on
  `SeatLayerPickerSelectedSeat.seatNotes()`. The picker also reads the
  whole-venue and zoom-ceiling camera facts, per-section accessible counts,
  where a seat is on screen, and the seat-view thumbnail with its distance and
  how the view was produced. An older runtime simply sends none of it.
- **Every ticket is its own line in the cart.** The collapsed run the ticket
  list used to draw hid the seat a buyer was looking for behind a summary they
  then had to open, and a row they cannot act on directly is not a shorter
  list.
- **No section dock bar by default on any width.** The section it named is
  already on the map, under the buyer's thumb. `showDockBar = true` brings it
  back for a host that wants it.
- **The fit control became the whole-venue disc**, at the foot of the map's own
  control column and always live whatever the camera last reported, so a pinch
  can never strand a buyer with no way back to the venue.
  `showZoomToFitControl` is its switch.
- **`SeatLayerPickerStyles.scrimColor`** names the wash drawn over the map
  while a card is up. Unset, it takes the design source's own confirm scrim
  over the theme's background, so the wash stays in step with a rebranded
  picker.
- **The theme's brand mark is a composable slot**, not an image URL the SDK
  decides the shape of: a mark is a wordmark, a painter or a whole row
  depending on the brand.
- **The option and callback surface is the shared one.**
  `SeatLayerPickerOptions` gains `eventName`, `pricing` (per-category and
  per-tier price overrides with the money formatter), `showBookedOverlay`,
  `showDockBar`, `showExtendHoldPrompt`, `seatCardGlass`,
  `persistColorblindPreference` and `manageSystemOverlays`;
  `SeatLayerPickerCallbacks` gains `onBooked`, `onSeatConfidence`,
  `onSectionFocused`, `onSeatSelected`, `onSeatRemoved`, `onSeatViewOpened`,
  `onHoldExpired`, `onAccessExpired`, `onAccessUnavailable`,
  `onSelectedObjectUnavailable`, `onSelectionValidityChanged`,
  `onThemeResolved` and `onContinue`. A host can wire a moment once and not
  again at the release that starts raising it.
- **The controller reaches three newer runtime commands**, each gated on the
  runtime's own command table and a no-op without it: the tapped seat can be
  marked as the candidate (`picker.setSelectionFocus`), native chrome can tell
  the map which rectangles it covers (`picker.setBlockedRegions`), and one seat
  can be brought into the free part of the map (`picker.frameSeat`). It also
  reads the `seat.retap` event, gains `releasePickerOwnedHold`, `emitHaptic`,
  `retry`, `setCartSheetDetent`, the seat-card and seat-lift presentation
  calls, and `supportsAccessNeeds` / `supportsAccessibilityFocus` /
  `supportsSectionAccessCounts` / `supportsSeatViewThumbnail`.
- **The seat card is rebuilt to the anatomy the design source describes.**
  Section, row and seat read as one identity line; the category and price sit
  in a full-bleed band; each accommodation, view restriction and organizer note
  draws its own mark from one shared per-attribute icon set rather than a
  wheelchair for everything. Ink is contrast-checked against the ground it
  actually paints on, the card plays its own haptic cues, the view passport is
  offered only where a host can open it, and no card is raised on a seat nobody
  can take. The card is pinned at the foot of the map and the MAP moves to keep
  the seat clear of it.
- **The phone map has one anchored control column.** The Map/3D control, the
  floor strip and the test badge each hold their own anchor, the discs are
  drawn from the design tokens, and the whole column crossfades and goes inert
  under a seat card so nothing reflows under a thumb already reaching for it.
  The header, the price rail and the ticket panel are rows of the map's own
  column, so the map begins and ends where they do.
- **The ticket panel is one surface with a footer-only rest.** The checkout
  footer is drawn in both sheet states — a buyer holding seats could not see
  the button that takes their money — the sheet can be dragged between its
  detents, a tap on the venue puts it away, and removing a ticket tells the
  host which seat it was.
- **One toast band says every transient sentence**, including the undo window
  after a removal and the outcome of asking for more time. It sits above the
  seat card rather than across its buttons.
- **The buyer-facing states are designed statements**, not a map full of
  disabled controls: sold out, a completed booking, a lapsed seat session with
  one way back, a closed sale stated in the ticket panel as well as the wide
  panel, and an offer of more time inside the last minute of a hold. Each
  renders nothing at all when its own condition is false.
- **The accessibility sheet is switch rows that apply live**, each row wearing
  its own provision's mark with the number of seats free, a sold-out provision
  going dark rather than disappearing, and the colour choice beside them.
- **The hold countdown is drawn for a host-owned hold too.** The buyer's time
  is running whoever holds the seats; only the offer of more time is withheld
  from a hold the picker cannot mutate.
- **Tokens, strings and 37 locale dictionaries are generated** from a checked-in
  design source under `Design/`, locked by a four-key SHA-256 file and shared
  with the other SeatLayer buyer SDKs. The generated Kotlin is checked in and
  the release gate re-runs the generators, so a hand-edited constant cannot
  ship.
- **The offline JavaScript fixture is gone.** The SDK vendors no JavaScript: it
  loads the one pinned, immutable hosted runtime page, like every other
  SeatLayer buyer SDK.
- The release gate is split. `checkFast` runs on every push — unit and
  screenshot tests, the design lock, the generators, the public API and raw
  ABI dumps, hygiene and the source-line limit — and `validateRelease` runs on
  a tag. `./gradlew validate` is still both. JitPack is no longer configured;
  Maven Central is the only supported channel.

**Renamed.** The old names are kept as `@Deprecated` forwarders for one
release and are removed in 0.5.0. See
[the 0.3.x migration guide](docs/migration-0.4.md) for the full table.
`SeatLayerPickerOptions.enableVenue3D` becomes `enable3D`; `holdTtlMillis: Int?`
becomes `holdTtl: Duration?`; all sixteen `SeatLayerPickerChromeOptions` flags
take their `showX` names, and a seventeenth, `showZoomToFitControl`, is new; `SeatLayerPickerTheme.onSurface`, `muted` and
`cornerRadius` become `text`, `mutedText` and `radius`;
`SeatLayerPickerMoneyFormatter` becomes `SeatLayerMoneyFormatter`; and eleven
controller methods take their shared names, including `setLimitedViewFilter` →
`setLimitedViewHidden`, `holdGeneralAdmission` → `setGeneralAdmissionQuantity`,
`refreshAvailability` → `refreshAccess` and `rejectHandoff` →
`rejectCheckoutHandoff`. No bridge wire name changed: only the Kotlin names
moved.

**Breaking.** `SeatLayerPickerDenseRun` and
`SeatLayerPickerProjections.denseRuns` are removed with the dense ticket list.
`SeatLayerPickerChromeOptions.dock` and `.fit` no longer control anything: the
dock bar moved to `SeatLayerPickerOptions.showDockBar` and is off by default on
every width, and the fit control's switch is now `showZoomToFitControl`
(`SeatLayerPickerFitControl` remains public for a host-owned layout).
`showOverviewControl`, `showZoomControls`, `showZoomToFitControl` and
`showColorblindControl` read `Boolean?`, where null means "whatever this width
should do". `SeatLayerPickerZoomControls` is replaced by the individual
`SeatLayerPickerZoomInButton` and `SeatLayerPickerZoomOutButton`, and
`SeatLayerPickerUndoNotice` by `SeatLayerPickerToastLayer`.
`SeatLayerPickerTheme` requires the new field names when constructed
positionally.

## 0.3.5

- Clarifies the Android and Jetpack Compose package names and descriptions on
  Maven Central, with direct links to the Kotlin integration guide.
- Refreshes README setup and customization guidance. Both artifacts keep the
  same runtime and public APIs as 0.3.4.

## 0.3.4

- Adds `io.seatlayer:seatlayer-android-compose`, including the ready-made
  adaptive `SeatLayerPicker`, 25 independent replacement builders, per-part
  styles, themes, localized strings, and a View/XML host.
- Adds the protocol-2 headless picker to `seatlayer-android`: immutable snapshot
  state, a serialized semantic controller, native presentation reducers, and a
  map-only `SeatLayerPickerMapView` for fully custom Compose or View layouts.
- Adds native buyer flows for confirmation and tiers, GA and variable tables,
  best available, dense cart and remove/undo, checkout handoff/rejection, hold
  lapse recovery, floors/sections, accessibility filters, 2D/3D navigation,
  native seat-view chrome, lifecycle reconciliation, and predictive Back.
- Adds source-locked native design tokens and 37 locale dictionaries with
  generated Kotlin verification tasks.
- Moves the native picker to the pinned `seatlayer-js@0.71.5/mobile.html`
  protocol-2 runtime while keeping the existing raw `SeatLayerView` on its
  separate protocol-1 contract.
- Consolidates both surfaces on one hardened renderer host and adds in-memory
  protocol-2 buyer-access renewal without leaking bearer or provider errors.
- Adds credential-free asynchronous Android System WebView engine prewarm,
  honest engine/page/session result fields, load-to-ready trace sections, and a
  release-like cold/warm/prewarmed Macrobenchmark target.
- Adds instrumented Compose accessibility regression coverage for control
  labels, toggle state, and Android 48dp minimum targets.
- Adds ready-widget UI regression coverage for loading, retry,
  empty/sales-closed, venue/floor/section navigation, tiers, GA/tables, cart and
  hold recovery, 3D/panorama chrome, compact/wide layout, RTL, and large text.
- Replaces the generic loading spinner with the established Web-style faint
  venue silhouette and restrained indeterminate progress line, and keeps cart
  chrome absent until the picker is ready.
- Aligns the compact edge-to-edge ticket dock with Flutter/React Native's
  square geometry so renderer pixels cannot appear as dark corner wedges.
- Keeps the renderer session movable across compact/wide adaptive branches,
  preserving live cart state through host-handled rotation and window resize.
- Capability-gates panorama hardware/predictive Back through the additive
  `picker.closeSeatView` command; older runtimes receive no unknown command and
  retain their runtime-owned close control.
- Removes duplicate renderer-authored All floors sentinels, preserves checkout
  priority in short wide windows, and improves status/action contrast and
  large-font confirmation spacing.
- Extends release validation and Maven Central publication to build, lint,
  test, generate POMs, and publish both aligned Android artifacts.
- Adds a dedicated `0.2.x` migration guide and checked-in external coordinate
  consumers for raw Java, raw Kotlin, ready-made Compose, and custom Compose;
  the raw consumer gate also rejects any accidental Compose dependency.
- Keeps the sample generic and runnable across ready/branded/custom Compose,
  ready/custom View, and raw integrations without embedding host credentials.
- Documents native-versus-renderer ownership, every public integration path,
  all 25 replacement parts, controller groups, lifecycle, accessibility, and
  immersive capability gates.

## 0.2.0

- Uses pinned `seatlayer-js@0.66.0/mobile.html` at `https://cdn.seatlayer.io`.
  Buyer access must be minted for that exact allowed origin.
- Separates the hosted runtime version (`0.66.0`) from the retained verified
  fixture version (`0.59.0`) while preserving the old constant as deprecated.
- Adds renewable private buyer access, programmatic selection/category
  controls, exact-count validators, typed validity/access events, and
  fail-closed capability negotiation.
- Locks both bridge messages and top-level navigation to the hosted origin/page.
- Restores the runnable consumer sample and compiles it against the new
  selection-policy API as part of the release validation task.

## 0.1.3

- Updated the vendored buyer runtime to `seatlayer-js@0.59.0` (sha256
  `89bc29fb…`), pulled from the production CDN and byte-verified against the
  published release. Brings the mobile buyer round and the engine fixes that
  reach every surface — section focus frames the section rather than its whole
  zone, the price filter dims section blocks and not only seats, and map type
  is sized for the device.
- Fixed `SEATLAYER_BUNDLED_WEB_VERSION`, which still read `0.30.1` while the
  package actually shipped `0.48.1`. Anything reading that constant for
  diagnostics was being told the wrong runtime. `docs/bridge.md` carried the
  same stale number.

## 0.1.2

- Updated the vendored buyer runtime to `seatlayer-js@0.48.1` (sha256
  `b459b0b6…`) for the current responsive picker, access-token, checkout, and
  duplicate-title behavior.
- Corrected the runtime SDK version constant and installation documentation to
  match the released package.

## 0.1.1

- Re-vendored the buyer bundle at `seatlayer-js@0.35.0` (sha256 `814657ba…`),
  up from 0.30.1.

## 0.1.0 — 2026-07-26

- Initial public preview.
- Native Kotlin `SeatLayerView` and coroutine-based controller.
- Secure, origin-restricted AndroidX WebKit bridge.
- Holds, best available, GA, tiers, floors, view modes, and zoom commands.
- Typed events, structured errors, command correlation, and stale-event guards.
- Vendored SeatLayer Web SDK `0.30.1` and sample Android application.
