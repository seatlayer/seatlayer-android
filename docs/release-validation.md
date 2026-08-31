# Android native picker release validation

This checklist produces the final evidence for an immutable SeatLayer Android
candidate. Android validation is independent; no iOS build, tag, or approval is
required. Never paste a host credential, buyer token, event key, or hold id into
an evidence file, command line, log filter, screenshot name, or issue.

## 1. Freeze and build the candidate

From a clean candidate checkout on JDK 17:

```bash
./gradlew validate
```

This builds both artifacts, API/ABI locks, R8 samples, publication metadata,
and raw plus Compose/View consumers on the oldest and current supported
toolchains. Record the immutable source commit only after the full gate is
green. Do not publish from a dirty tree.

## 2. Record physical-device environments

Run the collector once on physical hardware from the API-floor family and once
on a current-target family. It intentionally rejects emulators and hashes the
device serial:

```bash
bash scripts/collect-physical-device-evidence.sh DEVICE_SERIAL
```

The report records Android/API/build/security-patch, ABI, display, active
System WebView, candidate commit/version, and hosted renderer pin. Confirm the
recorded WebView is the intended current stable build before approval. An
emulator remains useful for functional iteration but is not device or timing
evidence.

## 3. Measure cold, warm, and prewarmed startup

Connect only the intended physical timing device, then run:

```bash
ANDROID_SERIAL=DEVICE_SERIAL \
  ./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Retain the generated benchmark JSON and trace files. Report all five iterations
for cold, warm, and runtime-prewarmed startup, plus device/WebView evidence; do
not publish a hand-picked fastest iteration. Prewarm is valid only when its
marker states `engine=true page=true session=false`; a `page=false` result is a
graceful development fallback, not release performance evidence.

## 4. Run the hosted DesiPass buyer matrix

Keep the DesiPass development configuration in an ignored local environment
file. Point the ignored Android `local.properties` at that existing file (or
use `sample/.env.local` / `DESIPASS_ENV_FILE`) and rebuild the debug sample:

```properties
# local.properties -- never commit this file
desipass.envFile=/absolute/path/to/an/ignored/.env.local
```

Both `DESIPASS_*` and `EXPO_PUBLIC_DESIPASS_*` names are accepted. Never put a
key directly in a shell command. The debug sample resolves the values at build
time and presents no credential UI; release and benchmark variants must be
verified to contain empty DesiPass values.

```bash
./gradlew :sample:installDebug

adb shell am start -n io.seatlayer.sample/.HostedValidationActivity \
  --es seatlayerHostedIntegration ready-compose \
  --ez seatlayerHostedValidationControls true
```

With validation controls enabled, press Volume Up (or run
`adb shell input keyevent 24`) to cycle Auto -> Light -> Dark -> Auto. Nothing
is drawn over the customer-facing picker. The hook deliberately applies the
canonical explicit light and dark palettes so live event branding cannot mask
the transition while native chrome and renderer roles are verified without a
remount. `Auto` returns to normal event branding.

Repeat with `custom-compose` and `ready-view`. Each mode must independently
complete:

- event list → event details → **BOOK NOW**;
- live overview → section focus → seat confirmation → one-ticket cart;
- test hold → exactly one typed checkout handoff; and
- Back/close through the documented internal ladder without duplicate handoff.

The optional validation control cycles Auto → Light → Dark → Auto on the live
picker. For every integration, verify the selection, focus, cart, camera, and
session survive each flip. A filtered `SeatLayerHosted` log must contain one
ready line for the session, safe theme-mode lines, and one checkout line—never
credentials or identifiers. In the ready-made path also verify available
floor navigation, venue 3D, and seat-view/panorama transitions.

While the picker remains live, verify:

- Home → foreground refresh, screen off/on, and activity recreation;
- portrait/landscape plus a live resizable or split-screen window;
- overview/focus/confirmation/cart/immersive predictive and hardware Back;
- app close from overview and hold ownership after close versus handoff;
- light/dark system appearance while the picker is in Auto mode; and
- no clipped native action at safe-drawing, display-cutout, IME, or gesture
  boundaries.

Use only buyer-safe captures: event title, ticket count, currency, total, and
visible picker UI. Redact notifications and unrelated device content.

## 5. Approval and publication gate

Publication requires all of the following on the exact commit to be tagged:

- full `validate` green, including both external-consumer toolchains;
- physical API-floor/current-target and active-System-WebView records;
- cold/warm/prewarm JSON and traces from physical hardware;
- hosted matrix evidence for ready Compose, custom Compose, and ready View;
- owner visual/API approval; and
- explicit authorization to commit, push, tag, and publish.

Publish `seatlayer-android` and `seatlayer-android-compose` together at the same
version, with the immutable renderer pin checked by the release workflow.

## 6. Candidate record — 2026-08-31

Branch `native-picker`, based on
`52255d347cde76c3d99a707e45ccbea3ad9825c8`, completed the source/build/API 35
hosted-event portion of this checklist:

- `./gradlew :seatlayer:testDebugUnitTest
  :seatlayer-compose:testDebugUnitTest` — passed, 58 core + 10 Compose tests.
- `./gradlew :seatlayer-compose:compileDebugKotlin :sample:assembleDebug` —
  passed; the APK installed and `HostedValidationActivity` cold-launched on the
  API 35 emulator.
- Focused `SeatLayerPickerVisualEvidenceTest#loadingState` and
  `#expandedDenseCartWithHold` connected instrumentation — passed, two tests;
  regenerated captures verify the Web-derived loading silhouette/progress and
  square compact ticket dock.
- `./gradlew :sample:assembleRelease :sample:assembleBenchmark
  :seatlayer:assembleRelease :seatlayer-compose:assembleRelease` — passed in
  2m 46s. Generated release/benchmark `BuildConfig` files contained blank
  DesiPass URL/key values.
- `bash scripts/verify-public-api.sh --check` — passed for core, Compose, and
  the raw `0.2.x` compatibility surface. The raw expected dump was generated
  from the untouched `0.2.0` base release AAR.
- Deterministic production-tree instrumentation covered 17 portrait states,
  compact/wide landscape, and the expanded accessibility sheet. Twenty reviewed
  deterministic captures plus buyer-safe hosted-event captures are stored
  under `android-sdk-internal/evidence/` and catalogued in the visual-parity
  contract.
- `./gradlew validate` — final post-edit pass `BUILD SUCCESSFUL in 2m 48s`;
  323 actionable tasks,
  including release AARs, lint, R8 sample, AndroidTest/benchmark assembly,
  publication metadata, source/token/locale locks, API/ABI checks, and
  repository plus oldest/current external consumers.

Emulator metadata was AVD `SeatLayer_RN_Pixel5_API35`, Android 15 / API 35,
model `sdk_gphone64_arm64`, System WebView `124.0.6367.219`, with 1080×2340
portrait and 2340×1080 landscape captures.

Using an authorized key from an ignored existing local environment, the real
DesiPass list → details → BOOK NOW journey independently reached protocol 2 in
ready Compose, custom Compose, and direct ready View modes. Ready Compose and
ready View completed a real one-ticket cart and typed checkout handoff. Ready
Compose also exercised hosted 3D overview/target, panorama open/drag/runtime
close with same-target restoration, and selected-cart retention through
portrait → landscape → portrait without a second ready/chart-load sequence.
Buyer-safe captures are catalogued in
`android-sdk-internal/android-native-picker-visual-parity-2026-08-30.md`.

The hosted `0.71.5` runtime did not advertise `picker.closeSeatView`, so native
panorama Back correctly sent no command and is not claimed. The following
remain intentionally unaccepted: physical devices and performance traces; a
runtime advertising additive 3D neighbours/rotate-move and panorama-close;
live multi-tier/hold-expiry/rejection; Activity/process restoration with
`initialHoldId`; and owner publication approval. The local client key was never
printed, logged, rendered, staged, or passed to SeatLayer.
