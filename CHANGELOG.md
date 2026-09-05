# Changelog

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
