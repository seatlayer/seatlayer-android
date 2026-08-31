# Native Android picker

SeatLayer Android `0.3.0` is the candidate for two aligned artifacts with one
release version:

- `seatlayer-android` contains the hardened map host, protocol-1 raw API, and
  protocol-2 headless picker state/controller.
- `seatlayer-android-compose` adds the ready-made adaptive picker, every
  reusable native component, per-part styling and replacement builders, plus a
  `ComposeView`-backed host for View/XML applications.

The venue map remains renderer-owned. Header, legend, navigation, filters,
confirmation, quantities, cart, checkout, loading/error/empty states, 3D
controls, seat-view chrome, hold recovery, and back behavior are native Android
UI. A custom integration can use the same state and controller without using
the default composition.

`0.2.0` remains the published Maven Central release until the `0.3.0` release
gate passes and an owner approves publication. The coordinates and examples in
this guide describe the candidate API; they do not mean the Compose artifact is
already available from Maven Central.

## Native ownership and integration paths

“Native picker” describes the buyer journey, not a replacement venue renderer.
SeatLayer deliberately divides ownership at the boundary where each side has
the strongest information:

| Native Android UI owns | Hosted SeatLayer renderer owns |
| --- | --- |
| Header, event identity, filters, floors, sections, confirmation, tiers, quantities, cart, hold UI, checkout, errors, test disclosure, attribution | Seats, labels, section shells, authoritative map focus, venue camera, real 3D scene, panorama photograph |
| Compact/wide layout, Compose/View hierarchy, TalkBack, 48dp targets, Android Back, safe areas, IME and cutout behavior | Chart pan/pinch, seat hit testing, panorama drag, 3D gestures, authoritative inventory projection |
| Theme and strings around the map; capability-gated controls over it | Runtime-authored categories, accessibility groups, tiers, immersive metadata and capability advertisement |

The renderer suppresses duplicate web chrome in protocol 2. A ready picker
therefore has one confirmation surface, one cart, one test badge, and one
checkout action, while the actual venue pixels remain consistent with the Web,
Flutter, React Native, and iOS SDKs.

The public entry points form a customization ladder:

| Path | Entry point | Host owns | SDK still owns |
| --- | --- | --- | --- |
| Ready Compose | `SeatLayerPicker` | Configuration and callbacks | Complete adaptive buyer flow |
| Branded/part-replaced Compose | `SeatLayerPicker` plus theme/styles/strings/options/builders | Visual identity or chosen parts | Layout, semantic state, holds and lifecycle |
| Custom Compose | `SeatLayerPickerScope` plus public components | Entire hierarchy | State holder, controller, map and checkout contract |
| Ready View/XML | `SeatLayerPickerView` | Existing View screen | Exact ready-made Compose tree |
| Custom Views/headless | `SeatLayerPickerStateHolder` plus `SeatLayerPickerMapView` | Entire View hierarchy | Protocol-2 state, commands and renderer |
| Existing raw map | `SeatLayerView` | All buyer UI and orchestration | Frozen protocol-1 map API |

## Install

After `0.3.0` is published, add the aligned artifacts:

```kotlin
dependencies {
    implementation("io.seatlayer:seatlayer-android:0.3.0")
    implementation("io.seatlayer:seatlayer-android-compose:0.3.0")
}
```

The Compose artifact strictly aligns its core dependency to the same version.
Raw-only applications can keep only `seatlayer-android` and do not inherit a
Compose dependency. Until publication, production applications should remain on
the published `io.seatlayer:seatlayer-android:0.2.0` coordinate or build the
candidate locally for evaluation; do not assume the Compose coordinate resolves
from Maven Central.

Requirements are API 24+, JDK 17 for builds, and an Android System WebView that
supports AndroidX WebKit message listeners and document-start JavaScript.
For predictive-back animation on Android 15 and lower, set
`android:enableOnBackInvokedCallback="true"` on the picker activity (or its
application); Android 16 enables it by default for apps targeting API 36.

## Ready-made Compose picker

```kotlin
setContent {
    SeatLayerPicker(
        configuration = SeatLayerConfiguration(
            event = "ev_your_event_key",
            publicKey = "pk_test_your_key",
            currency = "USD",
            maxSelection = 6,
        ),
        modifier = Modifier.fillMaxSize(),
        themeMode = SeatLayerPickerThemeMode.Auto,
        onCheckout = { handoff ->
            checkoutBackend.start(
                holdId = handoff.holdId,
                total = handoff.total,
                currency = handoff.currency,
            )
        },
        onError = ::reportPickerError,
        onClose = ::finish,
    )
}
```

`SeatLayerPicker` supplies adaptive compact/wide layouts, lifecycle refresh,
hardware and predictive back, haptics, viewport insets, test-event disclosure,
required attribution, and orderly hold-aware shutdown. Keep it in a
non-scrolling container with a definite size.

Register `https://cdn.seatlayer.io` as an allowed origin for the publishable key
used by public inventory. For private inventory, omit `publicKey` and provide a
renewable buyer-access provider from a trusted backend.

Android capability-gates the additive `picker.closeSeatView` command. When a
runtime advertises it, hardware/predictive Back closes panorama without changing
selection or the 3D target. The pinned hosted `0.71.5` runtime exposes only its
own close control, so native Back sends no unsupported command and does not
claim to close panorama there. Runtime rollout and live Back validation remain
release gates.

Checkout transfers an opaque `SeatLayerPickerCheckoutHandoff` to the host.
Booking and price verification stay on your trusted server. If the host cannot
open checkout, throw from `onCheckout`; the picker rejects that exact handoff
and retains ownership safely.

## Configure behavior and chrome

```kotlin
SeatLayerPicker(
    configuration = configuration,
    options = SeatLayerPickerOptions(
        layout = SeatLayerPickerLayoutMode.Adaptive,
        confirmSelection = true,
        enableBestAvailable = true,
        enableVenue3D = true,
        enableSeatView = true,
        holdTtlMillis = 10 * 60 * 1_000,
        panelInitiallyCollapsed = true,
        chrome = SeatLayerPickerChromeOptions(
            header = true,
            priceLegend = true,
            floorStrip = true,
            accessibility = true,
            cartSheet = true,
        ),
    ),
    onClose = onClose,
)
```

Unsupported optional capabilities remain hidden. Capabilities enabled in
`SeatLayerPickerOptions` are negotiated before ready, so an incompatible
runtime fails clearly instead of showing controls that cannot work.

### Option and callback reference

| `SeatLayerPickerOptions` | Behavior |
| --- | --- |
| `layout` | Adaptive, forced compact, or forced wide native composition |
| `chrome` | Visibility for header, legend, floor UI, map controls, cart, dock, confirmation, hold and attribution surfaces |
| `readOnly` | Refuses inventory mutation in native controller and renderer |
| `confirmSelection` | Keeps reserved seats pending until native confirmation |
| `enableBestAvailable` | Negotiates and exposes the Best Available flow |
| `enableVenue3D` / `enableSeatView` | Negotiates and exposes supported immersive experiences |
| `holdTtlMillis` | Requested picker-owned hold duration; server remains authoritative |
| `initialHoldId` | Restores an opaque host-owned hold into a new session |
| `max3DSeats` | Caps the selected-seat set offered to venue 3D |
| `hideEventDetails` | Suppresses duplicate host-provided event details in picker chrome |
| `panelInitiallyCollapsed` | Chooses the compact cart sheet's initial state |
| `refreshOnResume` | Requests availability/hold reconciliation on foreground |
| `announceHoldLapse` | Controls native lapse notice; expiry callbacks/state still update |
| `haptics` | Enables semantic Android haptic cues |
| `languages` | Advertises the host's preferred language sequence to the runtime |

`SeatLayerPickerChromeOptions` independently controls `header`, `priceLegend`,
`floorSelector`, `floorStrip`, `mapControls`, `overview`, `zoom`, `colorblind`,
`fit`, `map3D`, `accessibility`, `cartSheet`, `dock`, `confirmCard`,
`holdCountdown`, and `attribution`. Hiding a stock surface does not disable the
underlying controller capability for a custom host. Attribution is still a
product/legal obligation when the accepted branding state requires it.

| `SeatLayerPickerCallbacks` | Delivered value |
| --- | --- |
| `onReady` | Negotiated `ReadyInfo` after protocol-2 readiness |
| `onSnapshot` | Every accepted, monotonic `SeatLayerPickerSnapshot` |
| `onSelectionChanged` | Typed selected seats after authoritative mutation |
| `onHoldChanged` | Hold status, server expiry, and owner without the hold id |
| `onChartLoad` | Optional runtime-authored local chart-load trace |
| `onCheckout` | One opaque `SeatLayerPickerCheckoutHandoff` |
| `onError` | Typed bridge, runtime, access, validation, or host-callback error |
| `onClose` | Host dismissal after orderly picker close |

## Theme, localized copy, and per-part styles

The picker follows light/dark system appearance by default and applies event
branding when the host does not supply an explicit theme.

```kotlin
val styles = SeatLayerPickerStyles(
    cardRadius = 20.dp,
    panelWidth = 400.dp,
    parts = mapOf(
        SeatLayerPickerPart.Header to SeatLayerPickerPartStyle(
            containerColor = Color(0xFF101828),
            contentColor = Color.White,
            elevation = 0.dp,
        ),
        SeatLayerPickerPart.CheckoutBar to SeatLayerPickerPartStyle(
            cornerRadius = 18.dp,
            horizontalPadding = 20.dp,
        ),
    ),
)

SeatLayerPicker(
    configuration = configuration,
    strings = SeatLayerPickerStrings.localized(
        languageTag = "fr-CA",
        overrides = mapOf("continueWord" to "Passer au paiement"),
    ),
    styles = styles,
    onClose = onClose,
)
```

The source-locked catalogue contains 37 locale dictionaries. Resolution tries
the exact BCP-47 tag, then the base language, then English. App overrides win.
Supplemental Android-only copy that is absent from a locale dictionary falls
back to its typed English value.

The default colors, radii, spacing, elevations, type sizes, motion durations,
haptic decisions, breakpoints, and minimum targets come from
`seatlayer-compose/src/main/resources/io/seatlayer/android/compose/picker_tokens.json`.
`scripts/generate-picker-tokens.py` generates `SeatLayerPickerTokens.kt`, and
the release gate rejects drift between them. Theme roles also include a paired
`SeatLayerPickerMapTheme`, so live light/dark or brand changes repaint native
chrome and renderer-owned map roles together without recreating the session.

`SeatLayerPickerPartStyle` can override container/content color, corner radius,
elevation, and horizontal/vertical padding for one part. Visual customization
cannot reduce semantic interaction targets below 48dp or remove required
TalkBack state.

## Replace individual parts

`SeatLayerPickerBuilders` has one independent replacement point for each of the
25 canonical parts. A builder receives current state, snapshot, presentation,
controller, resolved theme, strings, options, and the part style. Calling
`defaultContent()` decorates the stock component; omitting it replaces that
part.

```kotlin
val builders = SeatLayerPickerBuilders(
    header = { context, defaultContent ->
        Surface(color = context.theme.surface) {
            Column {
                MyCheckoutProgress()
                defaultContent()
            }
        }
    },
    empty = { context, _ ->
        MyEmptyVenue(
            eventName = context.snapshot?.event?.name,
            onClose = onClose,
        )
    },
)

SeatLayerPicker(
    configuration = configuration,
    builders = builders,
    onClose = onClose,
)
```

Test-event disclosure and required attribution are intentionally outside the
replacement catalogue in the ready-made tree.

### All 25 replacement parts

The enum, builder property, stock implementation, state and style slot stay
one-to-one. This table is also the custom-host checklist:

| `SeatLayerPickerPart` | Builder property | Stock public implementation |
| --- | --- | --- |
| `Header` | `header` | `SeatLayerPickerHeader` |
| `Legend` | `legend` | `SeatLayerPriceLegend` |
| `FloorSelector` | `floorSelector` | `SeatLayerPickerFloorSelector` |
| `FloorStrip` | `floorStrip` | `SeatLayerFloorStrip` |
| `SectionNavigator` | `sectionNavigator` | `SeatLayerPickerSectionNavigator` |
| `DockBar` | `dockBar` | `SeatLayerDockBar` |
| `AccessibilityFilters` | `accessibilityFilters` | `SeatLayerPickerAccessibilityFilters` |
| `Map` | `map` | `SeatLayerPickerMap` |
| `MapControls` | `mapControls` | `SeatLayerPickerMapControls` / `SeatLayerPickerZoomControls` |
| `BestAvailable` | `bestAvailable` | `SeatLayerBestSeatsForm` |
| `SeatConfirmation` | `seatConfirmation` | `SeatLayerPickerSeatConfirmation`, tier selector and tier choices |
| `ConfirmCard` | `confirmCard` | `SeatLayerConfirmCard` |
| `GeneralAdmissionPrompt` | `generalAdmissionPrompt` | `SeatLayerPickerGeneralAdmissionPrompt` |
| `TablePrompt` | `tablePrompt` | `SeatLayerPickerTablePrompt` |
| `CartList` | `cartList` | `SeatLayerPickerCartList` |
| `CartSheet` | `cartSheet` | `SeatLayerPickerCartSheet` |
| `Venue3D` | `venue3D` | `SeatLayerVenue3D` |
| `SeatViewChrome` | `seatViewChrome` | `SeatLayerSeatViewChrome` |
| `HoldCountdown` | `holdCountdown` | `SeatLayerPickerHoldCountdown` |
| `HoldLapse` | `holdLapse` | `SeatLayerHoldLapseNotice` |
| `ActionError` | `actionError` | `SeatLayerPickerActionError` |
| `CheckoutBar` | `checkoutBar` | `SeatLayerPickerCheckoutBar` / `SeatLayerBookButton` |
| `Loading` | `loading` | `SeatLayerPickerLoadingView` |
| `Error` | `error` | `SeatLayerPickerErrorView` |
| `Empty` | `empty` | `SeatLayerPickerEmptyView` |

`SeatLayerPickerPartContext` gives every builder the same immutable
`SeatLayerPickerState`, accepted snapshot, presentation state, typed
controller, resolved theme mode/theme, localized strings, options, global
styles, and per-part style. A custom part must use those semantic values rather
than reverse-engineering UI text or renderer pixels.

Standalone public controls outside the builder catalogue include
`SeatLayerPickerFitControl`, `SeatLayerPickerOverviewControl`,
`SeatLayerPickerStepOutControl`, `SeatLayerPickerViewModeControl`,
`SeatLayerPicker3DNavigationControl`, `SeatLayerPickerColorblindControl`,
`SeatLayerPickerLimitedViewControl`, and `SeatLayerPickerUndoNotice`. Lifecycle,
Back, haptic, test-mode, and attribution components are public as well.

## Build a fully custom Compose picker

Use `SeatLayerPickerScope` when the app owns the hierarchy. Every stock
component is public and reads the same scoped state/controller.

```kotlin
SeatLayerPickerScope(
    configuration = configuration,
    callbacks = SeatLayerPickerCallbacks(
        onCheckout = ::openCheckout,
        onClose = onClose,
        onError = ::reportPickerError,
    ),
) {
    SeatLayerPickerLifecycle()
    SeatLayerPickerBackHandler()
    SeatLayerPickerHapticEffects()

    Column(Modifier.fillMaxSize()) {
        SeatLayerPickerHeader(onClose)
        SeatLayerPickerTestModeIndicator()
        SeatLayerPriceLegend()
        Box(Modifier.weight(1f)) {
            SeatLayerPickerMap(Modifier.fillMaxSize())
            SeatLayerPickerMapControls(Modifier.align(Alignment.CenterEnd))
            SeatLayerConfirmCard(Modifier.align(Alignment.BottomCenter))
        }
        SeatLayerPickerCartSheet()
        SeatLayerPickerAttribution()
    }
}
```

Custom layouts decide placement but must preserve these product truths:

- show `SeatLayerPickerTestModeIndicator` for test events;
- show `SeatLayerPickerAttribution` whenever branding requires attribution;
- use the controller's semantic actions instead of mutating snapshots;
- keep 48dp minimum interactive targets and accessible labels;
- install lifecycle/back handling or provide equivalent host behavior;
- call `SeatLayerPickerStateHolder.close()` before abandoning a live session.

Public components include the map, header, legend, floor selector/strip,
section navigator, dock, accessibility filters, map and individual camera/view
controls, best-available form, seat/tier confirmation, GA/table prompts, dense
cart/list/sheet, checkout actions, 3D and seat-view chrome, hold countdown and
lapse recovery, undo/error notices, and all loading/error/empty disclosures.

## View and XML applications

`SeatLayerPickerView` hosts the exact ready-made Compose tree without requiring
the application screen to be written in Compose.

```xml
<io.seatlayer.android.compose.SeatLayerPickerView
    android:id="@+id/picker"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```kotlin
binding.picker.bind(
    lifecycleOwner = this,
    configuration = configuration,
    onCheckout = ::openCheckout,
    onError = ::reportPickerError,
    onReady = ::observeReady,
    onSnapshot = ::renderHostAnalytics,
    onSelectionChanged = ::observeSelection,
    onHoldChanged = ::observeHold,
    onClose = ::finish,
)

// Prefer explicit orderly shutdown when the host closes the picker.
lifecycleScope.launch {
    binding.picker.close()
}
```

For a completely custom View hierarchy, create one
`SeatLayerPickerStateHolder`, bind it to `SeatLayerPickerMapView`, collect
`stateHolder.state`, and render your own Views around the map. Invoke only
`stateHolder.controller` commands, and call `stateHolder.close()` before
destroying the map.

## Buyer capability reference

Ready Compose, custom Compose, ready View/XML, and headless custom Views share
the same protocol-2 state and controller. The visual host changes; the buyer
contract does not.

### Startup, disclosure, failure, and empty inventory

- `Idle` and `Loading` render native skeleton/progress UI while document load,
  hello, compatibility negotiation, and the first accepted snapshot complete.
- Test inventory always renders `SeatLayerPickerTestModeIndicator`; required
  `SeatLayerPickerAttribution` remains present unless the server-side entitlement
  says otherwise.
- Handshake, bridge, timeout, renderer, access, and action failures retain a
  typed `SeatLayerException`. The ready widget presents Retry when retry is
  meaningful and Close when the host must leave.
- Missing or malformed optional inventory never becomes a false “sold out.”
  Empty UI requires affirmative sales-closed, sold-out, or all-unavailable
  evidence from an accepted snapshot.
- Disabled actions keep their semantics and explain their state; unsupported
  optional capabilities are absent instead of appearing as dead controls.

### Venue overview, sections, floors, and filters

- The map can move from venue overview into one focused section and one level
  back out. The dock and section navigator use runtime-authored labels, zone,
  entrance, category, price, and seats-left metadata.
- Multi-floor venues expose an active floor and, only with `floor-stack-v1`, an
  all-floors mode. Older runtimes receive no all-floors command.
- Category legend chips are filters, not decorative price swatches. Runtime
  availability decides enabled state and map focus.
- Accessibility filters are authored by the runtime through access-need keys and
  counts. Unknown future access groups remain selectable because the SDK does
  not hard-code a closed list.
- Limited-view and colorblind-safe controls update the authoritative renderer;
  a host theme alone never pretends those inventory filters changed.

### Reserved seats, tiers, general admission, and tables

- A seat can open a native confirmation card before mutation. Adult, Child,
  guided, restricted, or future tiers use typed id/name/price/currency,
  restriction, and buyer-message metadata.
- `confirmPending(tierId)` dispatches `picker.setSeatTier` before accepting the
  pending seat. A tier failure leaves confirmation open and cannot momentarily
  add the wrong-priced line to cart.
- GA uses runtime area capacity, available count, category, price, currency, and
  tier metadata. The quantity prompt creates one authoritative hold mutation.
- Variable tables use capacity plus min/max occupancy. The prompt prevents an
  out-of-bounds quantity before the controller validates it again.
- Selection validity, maximum selection, in-flight mutations, read-only mode,
  and host-owned holds all participate in enabled/disabled state.

### Cart, removal, undo, holds, and checkout

- `SeatLayerPickerProjections.confirmedCart` excludes an unconfirmed pending
  seat. Dense runs fold only structurally equivalent addressed seats; GA,
  tables, tiers, and multi-quantity lines remain individually actionable.
- Removal targets an authoritative line identity. Undo is session-scoped and is
  offered only after the line is absent; a revision from another session cannot
  restore it.
- Hold countdown uses server expiry. Foreground reconciliation catches expiry
  even when Android suspended the process and no local timer fired.
- Lapse recovery reports all/partial/none, offers only runtime-confirmed
  recoverable seats, and leaves conflicting seats unclaimed.
- Repeated Continue taps share one serialized checkout operation.
  `SeatLayerPickerCheckoutHandoff` is the only ordinary API carrying the opaque
  hold id; snapshots expose status, expiry, and owner but not that capability.
- Successful checkout transfers ownership to the host. Close before handoff
  releases picker-owned inventory; close after handoff never releases a
  host-owned hold. Callback failure rejects only the exact handoff just issued.

### Venue 3D and seat-view panorama

- `venue-3d-v1` gates the Map/3D choice. Runtime snapshots author the explicit
  target seat, same-row previous/next boundaries, 3D-focused section, and
  recenter state. Omitted additive position keys mean an older runtime; explicit
  null neighbours mean a real row boundary.
- Orbit/pan rotation controls are sent only with
  `venue-3d-controls-v1` plus the exact command. Native controls stand down when
  the runtime does not support the requested operation.
- `seat-view-v1` gates panorama. Panorama pixels and drag gestures stay inside
  the renderer; native UI owns caption, badge, close coordination, and the
  unavailable state. Closing restores the same target rather than changing the
  selection or 3D seat.
- Hardware/predictive Back walks prompt → expanded cart → panorama → 3D → seat
  confirmation → focused section → host close. `picker.closeSeatView` is sent
  only when advertised. Hosted runtime `0.71.5` does not advertise it, so its
  own panorama close affordance remains authoritative and hosted native Back is
  not claimed for that path.
- Ordinary 2D controls stand down during immersive scenes while cart/Continue
  remains reachable when the buyer has held inventory.

### Adaptive layout, system UI, and accessibility

- `Adaptive` responds to actual width, not device class. Compact phones,
  tablets, landscape, foldables, split-screen, and freeform resize use one state
  holder while native chrome moves between bottom-sheet and wide-panel layouts.
- The map receives viewport insets for native overlays. System bars, display
  cutouts, gesture navigation, and host IME interactions remain part of the
  physical-device release matrix; the API 35 evidence proves safe-drawing and
  gesture insets, not every physical configuration.
- Every stock action has a 48dp minimum target, TalkBack label/role/state,
  disabled semantics, and predictable traversal order. Selection and errors do
  not rely on color alone.
- Large font scale may grow content or make a panel scroll internally but does
  not clip confirmation or checkout. RTL mirrors directional controls and
  padding while preserving authored seat, row, section, and price text.
- `Auto` appearance follows Android system light or dark mode; a host can pass
  an explicit mode or theme. Theme changes update native roles and the renderer
  map theme without reloading, dropping cart, or moving the camera.

## Headless state guarantees

`SeatLayerPickerStateHolder` exposes a lifecycle-neutral `StateFlow` containing
the latest accepted protocol-2 snapshot and native presentation state. It:

- rejects snapshots from another event/session and stale or duplicate
  revisions;
- tolerates malformed optional fields while rejecting invalid snapshot
  identity fields;
- never infers an empty/sold-out venue from missing data;
- serializes inventory mutations and prevents duplicate checkout handoffs;
- models confirmation, table quantity, cart expansion, removal undo, hold
  lapse, and the back ladder independently of UI;
- refreshes availability on foreground when the runtime supports it and
  reconciles with a fresh snapshot otherwise.

The ready widget owns one Activity-scoped picker session and performs an
orderly hold-aware close when that lifecycle is destroyed. Ordinary
recomposition does not remount the session. Its renderer is movable across the
compact/wide adaptive branches, so a configuration-handled rotation or resize
does not dispose the WebView or cart. An Activity or process recreation does
create a new session; a host that must recover an existing hold should
retain the opaque hold id in appropriate host state and pass it back as
`SeatLayerPickerOptions(initialHoldId = restoredHoldId)` together with the same
renewable buyer-access provider. Do not log or place the hold id in screenshots
or command-line evidence. Real cart restoration must be verified against the
target hosted runtime and inventory; it is not implied by Compose saved state.

Protocol-2 models are additive without changing the frozen raw-map ABI.
`SeatLayerPickerCategoryTier` carries per-tier currency, restriction, and buyer
guidance, including tiers inside `SeatLayerPickerGeneralAdmissionArea`.
`SeatLayerPickerPhase.Ready.timing` exposes document-to-hello,
document-to-ready, and the complete additive ready payload through
`SeatLayerPickerReadyTiming`. Optional runtime-authored `chart-load-trace-v1`
records are emitted on `stateHolder.chartLoads` and through `onChartLoad`.

### Headless controller groups

`SeatLayerPickerController` is the only mutation path for ready and custom
hosts. Inventory-changing calls are serialized; presentation-only calls retain
the same capability checks but do not pretend to change authoritative inventory.

| Group | Public operations |
| --- | --- |
| Synchronization | `synchronize`, `lifecycle`, `setLifecycle`, `refreshAvailability` |
| Reserved selection | `selectObjects`, `deselectObjects`, `clearSelection`, `setSeatTier`, `setSelectableObjects`, `setMaxSelection` |
| Categories and access | `selectCategories`, `deselectCategories`, `setCategoryFilter`, `setAccessibilityFilter`, `setLimitedViewFilter`, `setColorblindSafe` |
| Venue navigation | `focusSection`, `overview`, `setRung`, `setFloor`, `showAllFloors`, `setViewMode`, `zoomIn`, `zoomOut`, `zoomToFit` |
| GA, tables, best available | `holdGeneralAdmission`, `setTableQuantity`, `bestAvailable`, `holdSelection` |
| Immersive views | `setBuyerView`, `venue3D`, `setVenue3DNavigationMode`, `openSeatView`, `closeSeatView` |
| Native-overlay coordination | `setThemeMode`, `setInteractionEnabled`, `setViewportInsets` |
| Cart and holds | `removeCartLine`, `removeWithUndo`, `undoLastRemoval`, `resumeHold`, `extendHold`, `abort`, `reselectLapsedSeats` |
| Checkout and ownership | `checkout`, `handoffCheckout`, `rejectHandoff` |
| Native presentation | `confirmPending`, `confirmPendingTable`, `cancelPending`, `cancelPendingTable`, `setCartExpanded`, `dismissActionError`, `dismissRemovalUndo`, `dismissHoldLapse` |
| Exit | `back`, `close`, `destroy` |

`supportsCapability`, `supportsCommand`, and `supportsEvent` expose the exact
negotiated surface. Helpers such as `supportsVenue3D`, `supportsSeatView`,
`supportsSeatViewClose`, `supportsNativeSeatViewChrome`, `supportsFloorStack`,
`supportsViewportInsets`, `supportsHoldSelection`, and
`supportsAvailabilityRefresh` combine the required capability and command.
When an optional operation is unavailable, the controller sends no bridge
message; the ready UI also omits its control.

`snapshot`, `presentation`, `isReady`, `canCheckout`, and `nextBackStep` are
immediate controller projections of the accepted state. `chartLoads` is a local
`SharedFlow` for runtime-authored load traces and must not be treated as an SDK
telemetry upload.

## WebView engine prewarm and startup measurement

`SeatLayerPickerPrewarmer` optionally starts the process-wide Android System
WebView engine asynchronously. Invoke it before any other Android WebView or
AndroidX WebKit call for the greatest benefit:

```kotlin
lifecycleScope.launch {
    val result = SeatLayerPickerPrewarmer.prewarm(applicationContext)
    Log.d(
        "PickerStartup",
        "engine=${result.engineStarted} " +
            "page=${result.rendererPageLoaded} " +
            "session=${result.pickerSessionCreated}",
    )
}
```

`rendererPageLoaded` is `true` when the pinned credential-free mobile document
finishes warming and `false` when that optional page step times out or fails
after the engine starts. `pickerSessionCreated` is always `false`. The
prewarmer receives no event identity, credentials, or hold; stores only
application context; and destroys its temporary hardened WebView immediately
after the attempt. Concurrent calls share the process startup. Cancelling a
caller stops its wait, not the platform startup already in flight.

The raw and picker map Views are created lazily during `load` and wait for an
in-flight prewarm first. Their load-to-ready spans are named
`SeatLayer.Raw.Load` and `SeatLayer.Picker.Load` in system traces on Android 10
and newer.

The repository's Macrobenchmark target performs five cold, warm, and
runtime-prewarmed iterations against a release-like, minified sample:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Run it on physical Android hardware with a current System WebView for release
numbers. Do not publish emulator timing as device evidence. The benchmark
requires no private event credentials; use a separate owner-approved hosted
inventory run for the release buyer-flow gate.

The release gate also compiles clean coordinate-only consumers at both ends of
the supported build-tool range. The oldest lane is JDK 17, Gradle 8.13, AGP
8.12.0, and Kotlin/Compose plugin 2.3.10. The current lane is JDK 17, Gradle
9.6.1, AGP 9.2.1 with built-in Kotlin, and Compose plugin 2.3.10. Both use
`compileSdk 36`, exercise `minSdk 24`, compile raw Java/Kotlin plus
ready-made/custom Compose/View usage, and reject Compose leakage from the raw
coordinate.

`sample/.HostedValidationActivity` is the default sample launcher and mirrors
the Flutter/React Native DesiPass journey: live event list, event details, BOOK
NOW, picker, and buyer-safe checkout evidence. The debug build resolves
DesiPass host configuration from the ignored `sample/.env.local`, an environment
file selected by `DESIPASS_ENV_FILE`, or `local.properties` key
`desipass.envFile`. Both `DESIPASS_*` and `EXPO_PUBLIC_DESIPASS_*` names are
accepted. Release and benchmark variants force the URL and client key to empty
strings. The key is never rendered or logged and is used only by the DesiPass
host client; the picker receives renewable buyer access instead. It does not
render or log the event key, buyer bearer, or opaque checkout hold id. The six
ready/branded/custom Compose/View/raw SDK examples remain available through the
explicit `sample/.MainActivity` intent.

Use the optional `seatlayerHostedIntegration` intent extra to run that exact
live journey through the ready-made Compose picker, a host-owned Compose
composition, or the ready-made View host:

```bash
adb shell am start -n io.seatlayer.sample/.HostedValidationActivity \
  --es seatlayerHostedIntegration ready-compose
adb shell am start -n io.seatlayer.sample/.HostedValidationActivity \
  --es seatlayerHostedIntegration custom-compose
adb shell am start -n io.seatlayer.sample/.HostedValidationActivity \
  --es seatlayerHostedIntegration ready-view
```

The ready View route installs `SeatLayerPickerView` directly as the Activity
content. This proves the View/XML host itself and avoids nesting its
Compose-backed implementation inside another Compose `AndroidView`. The sample
declares configuration handling for orientation and window-size changes; the
movable ready renderer therefore keeps the live session and cart while moving
between adaptive layouts. Explicit Activity/process destruction still follows
the `initialHoldId` recovery contract above.

`ready-compose` is the default when the extra is absent or unrecognized. The
mode changes only the Android integration surface; event retrieval, renewable
buyer access, picker configuration, and checkout evidence remain identical.

## Private buyer access

Use a renewable provider for private inventory. It is called off the UI thread,
and neither bearer values nor provider exception messages enter bridge
diagnostics.

```kotlin
SeatLayerConfiguration(
    event = "ev_private",
    buyerAccessTokenProvider = { context ->
        buyerBackend.mintSeatLayerBuyerAccess(context.reason)
    },
)
```

Never put a SeatLayer secret key in the APK. Send the handoff hold id to your
backend, inspect the hold server-side, calculate the payable amount there, and
book only after payment/order validation.

## Existing raw API

`SeatLayerView` and `SeatLayerController` remain available from
`seatlayer-android` for existing low-level integrations. Their protocol-1
surface is separate from the protocol-2 picker and remains source/binary
compatible with `0.2.x`. New native-picker work should prefer the ready-made
picker or the headless picker state/controller described above.

## Before publishing 0.3.0

Source, unit, Compose instrumentation, API/ABI, release AAR, consumer, sample,
and API 35 emulator/hosted-event validation are complete for the current
candidate. Publication is still a separate owner-approved action.

The remaining release gates are intentionally explicit:

- physical-device API-floor/current-target records and cold/warm/prewarm traces;
- hosted evidence from a runtime advertising additive 3D neighbours,
  rotate/move controls, and `picker.closeSeatView`;
- live multi-tier, hold-expiry/rejection, and Activity/process restoration with
  `initialHoldId` against suitable inventory; and
- synchronization of the public Android documentation with this approved
  native-picker contract; and
- owner visual/API approval plus separate authorization to tag and publish both
  Maven artifacts together.

The pinned hosted `0.71.5` runtime does not advertise native panorama close, so
no release note or guide may claim that hardware/predictive Back closes hosted
panorama today. See [release validation](release-validation.md) for exact
commands, environments, evidence, and the publication checklist.
