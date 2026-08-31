# Native Android picker

SeatLayer `0.3.0` has two Android artifacts with one release version:

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

## Install

```kotlin
dependencies {
    implementation("io.seatlayer:seatlayer-android:0.3.0")
    implementation("io.seatlayer:seatlayer-android-compose:0.3.0")
}
```

The Compose artifact strictly aligns its core dependency to the same version.
Raw-only applications can keep only `seatlayer-android` and do not inherit a
Compose dependency.

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

Android capability-gates the additive `picker.closeSeatView` command. When a
runtime advertises it, hardware/predictive Back closes panorama without changing
selection or the 3D target. Current hosted runtimes expose only the runtime's
own close control, so native Back is safely consumed there. Runtime rollout and
live Back validation remain release gates.

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
