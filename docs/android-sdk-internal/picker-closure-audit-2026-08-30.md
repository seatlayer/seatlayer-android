# Android picker closure audit — 2026-08-31 final candidate

## Outcome

The Android native-picker implementation candidate is complete, merged to
`main`, and independent of iOS. It contains the ready-made Compose picker,
protocol-2 headless core, all 25 replacement builders and public stock
components, View/XML hosting, the DesiPass buyer demo, deterministic
native-chrome evidence, release artifacts, consumer checks, API locks, and
updated documentation.

The selected feature checkout was the latest candidate based on
`52255d347cde76c3d99a707e45ccbea3ad9825c8`. Its validated tree was
byte-for-byte squash-merged and pushed to `main` as
`2a4e1705a8638a5503159a29c4235da12c1cfdaa`; temporary public feature branches
were deleted. The separate primary checkout had unrelated user changes and was
left untouched. No tag, Maven publication, or release was performed. An
authorized DesiPass development client key was consumed from an ignored
existing local environment for the debug sample; it was never printed, logged,
rendered, added to a command line, staged, or passed to SeatLayer. Release and
benchmark variants compiled blank host values.

This is a green source/build/API 35 hosted-event candidate, not final production
acceptance. Hosted-runtime rollout, physical devices/performance evidence, and
owner approval still bound the claims listed below.

## Closure matrix

| Area | Status | Evidence and boundary |
| --- | --- | --- |
| Ready-made Compose picker | **Covered** | Adaptive compact/wide production tree builds, has deterministic API 35 evidence, and completed a real event from BOOK NOW through selection/cart/checkout plus live 3D/panorama. |
| Custom Compose integration | **Covered** | `SeatLayerPickerScope`, lifecycle/Back/haptics helpers, styles/tokens/strings, and every public stock component compile in repository/external consumers; the custom sample independently loaded the real hosted event with premium loading/error/empty ownership. |
| All 25 builders | **Covered** | Header, legend, floor selector, floor strip, section navigator, dock, accessibility filters, map, map controls, best available, seat confirmation, confirm card, GA prompt, table prompt, cart list, cart sheet, venue 3D, seat-view chrome, hold countdown, hold lapse, action error, checkout bar, loading, error, and empty each receive semantic state, snapshot, presentation, controller, resolved theme/strings/options/styles, and part style. Focused customization tests pass. |
| Ready View/XML picker | **Covered** | `SeatLayerPickerView` hosts the canonical tree and compiles in the sample/coordinate consumer. Installed directly as Activity content, it independently completed the real hosted overview, selection, one-ticket cart, and typed checkout handoff. |
| Fully headless integration | **Covered** | `SeatLayerPickerStateHolder`, typed controller, and map-only `SeatLayerPickerMapView` compile without Compose in core; raw-consumer dependency checks reject Compose leakage. |
| DesiPass list → details → BOOK NOW | **Covered on API 35** | The real list/details/BOOK NOW journey completed in ready Compose, custom Compose, and ready View modes. Details-time renewable buyer-access prefetch and immutable credential-free prewarm ran without creating a hidden session or receiving event credentials. Checkout evidence stayed buyer-safe. |
| Loading/handshake/failure/retry | **Covered** | Typed phases and transport errors have unit coverage. The Web-derived venue silhouette/2dp progress treatment, retry UI, and all three live integration loading states were captured. Cart chrome remains absent before ready; error chrome never exposes a raw event key. |
| Empty inventory, test mode, sales closed | **Covered** | Empty requires affirmative sold-out/all-unavailable evidence. Separate empty, sales-closed, and test-mode overview captures were inspected. |
| Venue overview, section focus, step-out, floors | **Covered** | Overview and focused-section states are distinct. One contextual 2D step-out is used. Duplicate runtime `all` floor sentinels are filtered before the native All floors action is added; focused unit and visual tests pass. |
| Categories, legend, accessibility filters | **Covered on emulator; physical TalkBack partial** | Price/category hierarchy, runtime-authored access needs/counts, zero-inventory disabled state, independent limited-view/colourblind filters, Apply/focus separation, TalkBack semantics, and 48dp actions are implemented. Expanded-sheet API 35 evidence passed; physical TalkBack acceptance remains open. |
| Adult/Child/guided tiers | **Covered in contract/evidence; live multi-tier partial** | Typed tier currency, restriction, and guidance are additive to protocol 2. The ready card shows visible choices and updates its decision. Controller/presentation tests prove `picker.setSeatTier` completes before confirmation. The hosted event had one tier, so it is not live Adult/Child proof. |
| GA and variable-table quantity | **Covered** | Dedicated quantity/tier flows have native visual evidence in light/dark states and use serialized typed mutations. |
| Cart, remove/undo, hold, expiry, checkout | **Covered; live expiry/rejection partial** | A real selection produced the one-ticket cart and exactly one typed checkout handoff in ready Compose and ready View. Dense list, countdown, lapse recovery, remove/undo, exact-hold rejection, and close/abort ownership are covered deterministically; the live hold was not allowed to expire and backend settlement was not attempted. |
| Selection-to-cart motion | **Covered** | Motion starts only after the authoritative cart retains the confirmed seat, does not intercept input, and respects disabled system animation. |
| 3D overview/target/neighbours/recenter/rotate-move | **Covered; additive hosted controls partial** | Real hosted 3D overview and explicit seat target rendered with runtime pixel ownership. Explicit target/3D focus, omitted-vs-null row boundaries, previous/next, recenter, and exact-gated rotate/move are implemented and deterministically rendered. Hosted `0.71.5` did not provide all additive position commands, so live same-row/rotate-move proof is not claimed. |
| Panorama open/drag/close/restoration/unavailable | **Covered** | Real panorama pixels opened, accepted drag gestures, and used the renderer-owned Close affordance to restore the same 3D target. Ordinary 2D chrome stood down. Metadata and unavailable treatment also have deterministic evidence. |
| Hardware and predictive Back | **Covered in reducers; hosted panorama path partial** | One semantic reducer covers prompts, sheets, panorama, 3D, one-level 2D step-out, and app close. `picker.closeSeatView` is sent only when the exact additive command is advertised; legacy runtimes receive no command. Hosted `0.71.5` does not advertise it, so native panorama Back is not claimed there. |
| Compact, landscape, split, RTL, large text | **Covered on API 35 evidence** | Portrait, compact landscape, 320dp split width, RTL, 1.5x font, and a short forced-wide cart/checkout composition were inspected. The 840dp adaptive threshold and 360dp side rail match the cross-SDK contract. |
| Tablet, cutout, IME, gesture, physical device | **Partial** | Safe-drawing and gesture-navigation insets were exercised on the Pixel 5 AVD. The ready picker contains no editable buyer input, but physical tablet/cutout/IME/gesture acceptance was not performed. |
| Light/dark and system appearance | **Covered; physical branding partial** | Explicit light/dark native chrome is captured. The live journey verified dark renderer/native-picker system bars and light event/loading system bars without invisible icons. Physical appearance acceptance remains open. |
| Activity/process recreation and rotation | **Rotation covered; destruction partial** | Portrait → landscape → portrait retained the same live selected cart and emitted no second ready/chart-load event. The renderer moves across adaptive branches and the sample handles window configuration. Explicit Activity/process destruction still creates a new session and requires `initialHoldId` plus renewed buyer access; that recovery was not live-exercised. |
| Chart-load telemetry and prewarm | **Covered; physical timing missing** | Monotonic hello/ready timing, additive raw ready payload, capability/event-gated chart-load traces, and no-session prewarm are implemented. A real hosted success outcome was emitted; this runtime did not author every detailed timing field. Prewarm never receives event identity or credentials. Physical medians remain open. |
| Raw 0.2.x JVM compatibility | **Covered** | The baseline was generated from the untouched `0.2.0` base artifact, then the current release AAR was compared against that exact 66-class list. The frozen surface verifies with no diff. |
| Protocol-2 additive API | **Covered** | New core/Compose API dumps are checked in. Typed tiers, 3D position/focus, panorama metadata, accessibility groups, telemetry, checkout, and hold ownership are additive. |
| Performance Groups / Seasons | **Missing by design** | These need separate multi-performance access, atomic hold, recovery, and checkout contracts and are not part of the single-event Android 0.3 objective. |

## Issues found and fixed during closure

- Removed a falsely advertised neutral `colorblind-safe` capability; optional
  controls now depend only on capabilities/commands the runtime actually
  reports.
- Prevented a duplicate-key Compose crash when the runtime authors an `all`
  floor alongside Android's native All floors action.
- Preserved checkout visibility in short wide/landscape windows by letting
  nonessential rail chrome yield to the cart and pinned action.
- Replaced low-contrast action-error dismissal text, raw pre-snapshot event-key
  fallback, and cramped large-font confirmation price spacing.
- Added deterministic production-tree fixtures for every critical native buyer
  state, with only renderer-owned pixels substituted and clearly labelled.
- Split the internal picker command transport from the controller so the final
  source-limit gate remains green without changing JVM API or behavior.
- Replaced the generic loading spinner/empty rectangle with the Web picker's
  venue-shell silhouette and restrained indeterminate line, and withheld cart
  chrome until the session is ready.
- Matched Flutter/RN's square edge-to-edge compact cart dock after a real event
  exposed black renderer wedges behind rounded top corners.
- Kept the renderer movable between compact/wide branches and declared sample
  window configuration handling so live rotation does not discard the WebView,
  session, or cart.
- Corrected live light/dark system-bar icon contrast and gave the custom Compose
  sample the same loading/error/retry/empty states and resolved background as
  the ready widget.
- Hosted the ready View route directly as Activity content after live testing
  showed that nesting a Compose-backed `SeatLayerPickerView` inside another
  Compose `AndroidView` did not constitute correct View/XML ownership.
- Replaced manual credential entry with ignored debug-local environment
  resolution. Existing RN-style environment names are accepted; release and
  benchmark builds always use blank host configuration.

## Verification record

Environment:

- validated implementation tree: `2a4e1705a8638a5503159a29c4235da12c1cfdaa`
- base: `52255d347cde76c3d99a707e45ccbea3ad9825c8`
- AVD: `SeatLayer_RN_Pixel5_API35`
- Android: 15 / API 35
- model: `sdk_gphone64_arm64`
- Android System WebView: `124.0.6367.219`
- capture sizes: 1080×2340 portrait and 2340×1080 landscape

Commands and results:

```bash
./gradlew :seatlayer:testDebugUnitTest :seatlayer-compose:testDebugUnitTest
```

Passed: 58 core tests and 10 Compose tests, zero failures. This includes
contracts, decoding, controller serialization/gating, tier ordering, Back,
immersive planning, presentation, session, telemetry, frozen neutral fixtures,
component builders, and floor normalization.

```bash
./gradlew :seatlayer-compose:assembleDebug :seatlayer-compose:assembleDebugAndroidTest
```

Passed. The production ready tree and deterministic evidence APK compiled.

```bash
adb shell am instrument -w -r \
  -e class io.seatlayer.android.compose.SeatLayerPickerVisualEvidenceTest \
  io.seatlayer.android.compose.test/androidx.test.runner.AndroidJUnitRunner
```

Portrait batch: 16 scenarios passed and 2 landscape assumptions skipped. One
test-only content-description assertion used the typed fallback instead of the
resolved localized label; after correction, the exact overview test passed
`OK (1 test)`. The UI had rendered correctly in the failed assertion capture.

```bash
adb shell am instrument -w -r \
  -e class io.seatlayer.android.compose.SeatLayerPickerVisualEvidenceTest#compactLandscapeChrome \
  io.seatlayer.android.compose.test/androidx.test.runner.AndroidJUnitRunner

adb shell am instrument -w -r \
  -e class io.seatlayer.android.compose.SeatLayerPickerVisualEvidenceTest#wideLandscapeChrome \
  io.seatlayer.android.compose.test/androidx.test.runner.AndroidJUnitRunner
```

Both passed `OK (1 test)` in landscape. The focused accessibility evidence test
also passed `OK (1 test)` in portrait. Deterministic and buyer-safe hosted PNGs
are catalogued in
`android-native-picker-visual-parity-2026-08-30.md` and stored under
`docs/android-sdk-internal/evidence/`.

```bash
./gradlew :seatlayer-compose:compileDebugKotlin :sample:assembleDebug
adb install -r sample/build/outputs/apk/debug/sample-debug.apk
adb shell am start -W -n io.seatlayer.sample/.HostedValidationActivity
```

Passed. An ignored `local.properties` pointed `desipass.envFile` at an existing
ignored development environment; no value appeared in the command, build
output, UI, log, or screenshot. The sample installed and loaded a real event
list automatically. Details prefetched renewable buyer access, and BOOK NOW
completed overview → section → confirmation → one-ticket cart → typed checkout
in ready Compose and ready View. Custom Compose independently reached ready.

```bash
adb shell am start -n io.seatlayer.sample/.HostedValidationActivity \
  --es seatlayerHostedIntegration ready-compose
adb shell am start -n io.seatlayer.sample/.HostedValidationActivity \
  --es seatlayerHostedIntegration custom-compose
adb shell am start -n io.seatlayer.sample/.HostedValidationActivity \
  --es seatlayerHostedIntegration ready-view
```

All three modes independently negotiated protocol 2. Ready Compose additionally
exercised 3D overview/target and panorama open/drag/runtime-close restoration.
Hardware Back in panorama left state unchanged because hosted `0.71.5` did not
advertise `picker.closeSeatView`; no optional command was sent. With one ticket
selected, portrait → landscape → portrait retained the same cart and emitted
one ready/chart-load sequence. The direct View mode also produced exactly one
checkout handoff. Buyer-safe screenshots are listed in the visual contract.

```bash
./gradlew :seatlayer-compose:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=io.seatlayer.android.compose.SeatLayerPickerVisualEvidenceTest#loadingState,io.seatlayer.android.compose.SeatLayerPickerVisualEvidenceTest#expandedDenseCartWithHold'
```

Passed: two focused API 35 instrumentation tests. The regenerated production
loading and cart captures confirm the venue-shell loading hierarchy, progress
semantics, ready-only cart visibility, and square compact dock.

```bash
./gradlew :sample:assembleRelease :sample:assembleBenchmark \
  :seatlayer:assembleRelease :seatlayer-compose:assembleRelease
```

Passed: `BUILD SUCCESSFUL in 2m 46s`. Both release AARs and both minified sample
variants built. The generated release and benchmark `BuildConfig` files were
then inspected and contained empty `DESIPASS_GRAPHQL_URL` and
`DESIPASS_API_KEY` values.

```bash
bash scripts/verify-public-api.sh --check
```

Passed: core API dump, Compose API dump, and raw `0.2.x` compatibility surface.
The raw expected dump came from the untouched base `0.2.0` release AAR rather
than from the new candidate.

```bash
./gradlew validate
```

Final pass after all implementation, evidence, and documentation edits:
`BUILD SUCCESSFUL in 2m 48s`, 323 actionable tasks (37 executed, 2 from cache,
284 up-to-date). This covered both unit suites, core and Compose
release AARs, sample release/benchmark builds, lint, generated locale/token
locks, publication metadata, AndroidTest/benchmark assembly, R8, repository and
coordinate-only raw/Compose consumers, oldest/current toolchains, raw no-Compose
dependency enforcement, source limits, Web fixture checksum, public API dumps,
and the frozen raw ABI.

The post-merge public-documentation correction was then validated independently:

- the original SEO title, introduction, product links, FAQ coverage, and
  ecosystem navigation from the `52255d347cde76c3d99a707e45ccbea3ad9825c8`
  baseline remain present, with the native-picker material layered on top;
- GitHub's Markdown renderer accepted the README and native-picker guide, and a
  local validator found no broken repository links or unclosed code fences;
- the documented 25 builders, 16 picker options, eight callbacks, controller
  operations, and referenced public types were checked against the candidate
  source;
- `docs/media/picker-flow.gif` was inspected at 480×1040, 10 fps, and 10.3
  seconds. It uses only buyer-safe Android picker captures (overview, section,
  confirmation, cart, 3D, and panorama); the DesiPass event list and details
  screen are intentionally excluded from public hero media;
- the staged text and GIF metadata were checked for credentials, local paths,
  and internal tooling attribution, and `git diff --cached --check` passed.

No implementation file changed during that correction, so the successful full
Gradle gate above remains the applicable code result; rerunning it would not
exercise a documentation- or GIF-specific failure mode.

## Evidence boundary and remaining release risks

Before production release, an owner still needs to:

1. Roll out a runtime that advertises the additive 3D position fields and
   `picker.closeSeatView`, then validate Android target boundaries and native
   panorama Back without claiming support on `0.71.5`.
2. On suitable live inventory, exercise multiple price tiers, hold expiry,
   remove/undo, checkout rejection, and explicit Activity/process recreation
   with `initialHoldId`. Host-handled rotation and same-target panorama
   restoration are already evidenced.
3. Record physical API-floor/current-target devices with TalkBack, cutout,
   gesture navigation, split/resizable windows, and any host IME interaction.
4. Run five physical cold/warm/prewarmed benchmark iterations and retain all
   JSON/traces; emulator timing is not release evidence.
5. Update the public Android documentation from the published `0.2.0` raw-only
   surface to the owner-approved `0.3.0` native-picker contract.
6. Obtain owner visual/API approval before any tag, Maven publication, or
   release.

These are explicit hosted-runtime, device, and owner gates. They do
not depend on iOS and do not invalidate the completed Android source candidate.
