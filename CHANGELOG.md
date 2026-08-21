# Changelog

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
